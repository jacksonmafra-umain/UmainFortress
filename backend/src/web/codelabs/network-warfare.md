---
title: "Network warfare — certificate pinning and MITM defence"
slug: network-warfare
level: intermediate
estimated_minutes: 30
status: published
company: Fortress
tags:
  - tls
  - pinning
  - mitm
  - okhttp
summary: >
  Pin to subject public keys (not certificates), distribute pins dynamically, fail closed
  on unexpected leaves, and design the rotation story so a key compromise is recoverable
  without an app uninstall.
references:
  - title: "Network warfare (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/08-network-warfare.md
  - title: "Android Network Security Config"
    url: https://developer.android.com/training/articles/security-config
  - title: "OkHttp CertificatePinner"
    url: https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/
  - title: "RFC 7469 — HTTP Public Key Pinning (HPKP) (deprecated but instructive)"
    url: https://datatracker.ietf.org/doc/html/rfc7469
---

## Welcome to network warfare

Understand why Android's default trust store is hostile by design and what pinning
actually buys you.

By default, Android trusts every certificate authority shipped with the OS — and on
Android 7+ that does **not** include user-added or MDM-installed CAs for apps that target
API 24+. That removes the worst category of attack (a corporate proxy with an installed
root CA silently MITM-ing your traffic). But it leaves another: a *real* CA being
compromised, mis-issuing a certificate, or being coerced by a state actor. Pinning is the
defence against that residual risk.

> **Why this matters.** A MITM with a valid certificate is invisible to users. Pinning
> turns "the device trusts this CA" into "the device trusts this specific key", which is
> a much stronger and verifiable claim.

---

## Step 1: Decide what to pin

Three options, ranked by safety:

1. **Subject Public Key Info (SPKI) hash** — pins the key itself. Survives certificate
   re-issuance with the same key. Best default.
2. **Leaf certificate hash** — pins the exact leaf. Breaks on any re-issue. Almost always
   wrong.
3. **Intermediate certificate hash** — pins the CA's intermediate. Survives leaf re-issues
   but trusts a wider surface. Usable as a backup pin.

We will pin SPKI hashes for the live key and a backup intermediate.

> **Why this matters.** Picking the wrong layer pins your release cadence to your
> certificate provider's. Pinning SPKI buys flexibility without losing the guarantee.

---

## Step 2: Compute the SPKI hash from your real certificate

For your production API at `api.fortress.dev`:

```bash
echo | openssl s_client -servername api.fortress.dev -connect api.fortress.dev:443 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
# → 7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y=
```

Repeat for the intermediate CA certificate to get a backup pin. Store both — the live pin
in code, the backup in a remote config so it can be rotated without a release.

> **Why this matters.** A single pin is a single point of failure. Two pins (live + one
> backup) is the minimum to recover from a private-key loss without bricking the app.

---

## Step 3: Configure Network Security Config

The XML config is the OS-level enforcement. It runs before any in-app pinning, so
malformed configurations are caught early.

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <base-config cleartextTrafficPermitted="false">
    <trust-anchors>
      <certificates src="system" />
    </trust-anchors>
  </base-config>
  <domain-config>
    <domain includeSubdomains="true">api.fortress.dev</domain>
    <pin-set expiration="2026-12-31">
      <pin digest="SHA-256">7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y=</pin>
      <pin digest="SHA-256">YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=</pin>
    </pin-set>
  </domain-config>
</network-security-config>
```

Reference from the manifest:

```xml
<application
  android:networkSecurityConfig="@xml/network_security_config"
  android:usesCleartextTraffic="false"
  ...>
</application>
```

> **Why this matters.** The XML config means an attacker who roots the app cannot
> circumvent pinning by swapping the OkHttp client at runtime — pin verification happens
> at the platform layer.

---

## Step 4: Belt-and-braces in-app pinning with OkHttp

`CertificatePinner` is the runtime check. It catches pin violations the platform-level
check might miss (older OS, vendor patches, edge cases) and emits a typed exception your
telemetry can act on.

```kotlin
val pinner = CertificatePinner.Builder()
  .add("api.fortress.dev",
       "sha256/7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y=",
       "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=")
  .build()

val client = OkHttpClient.Builder()
  .certificatePinner(pinner)
  .addInterceptor(AuthInterceptor(sessionManager))
  .build()
```

The OkHttp pinner throws `SSLPeerUnverifiedException` on mismatch; surface that to
analytics with the leaf SHA-256 so you can investigate without leaking PII.

> **Why this matters.** Two layers (XML + OkHttp) means a misconfiguration in one is
> caught by the other. Defense in depth.

---

## Step 5: Distribute pins dynamically

Hard-coded pins are a release-coupling problem. The fix: ship two pins in code as a
baseline, and let a signed remote config update them between releases.

```kotlin
class PinManifest(
  val current: List<String>,        // sha256/...
  val backup: List<String>,
  val notBeforeEpochMs: Long,
  val notAfterEpochMs: Long,
  val signatureB64: String,         // signature over the JSON, key in Manifest
)
```

The manifest is fetched at startup, signature-checked against an embedded Ed25519 public
key, and merged with the baseline. Treat the manifest as TOFU + signature — if the
signature fails, fall back to baseline pins and refuse to update.

> **Why this matters.** Without dynamic pinning, a key rotation requires a coordinated
> backend cutover + app release across every user. With dynamic pinning, it is a config
> push.

---

## Step 6: Refuse cleartext everywhere

`cleartextTrafficPermitted="false"` in the base config closes the trivial path. Audit your
own code for any leftover `http://` URLs; in 2026 there should be zero.

```bash
# Quick audit
git grep -nE 'http://' app/src/main/ | grep -v '\.md\|comment\|// '
```

Also check third-party SDKs. Some still default to plain HTTP for legacy analytics
endpoints. Strip them or wrap them in a no-op stub for sensitive flows.

> **Why this matters.** Plain HTTP is invisible to your TLS defences. One legacy endpoint
> is enough for a network attacker to pivot.

---

## Step 7: Detect and refuse user-installed CAs (Android 6 and older)

On API 23 and lower, user-installed CAs are trusted by default. If you support those
versions (you probably do not in 2026), restrict to system trust:

```xml
<base-config cleartextTrafficPermitted="false">
  <trust-anchors>
    <certificates src="system" overridePins="false" />
    <!-- explicit absence of <certificates src="user"/> -->
  </trust-anchors>
</base-config>
```

Targeting API 24+ does this automatically. Worth a sanity check before shipping.

> **Why this matters.** Corporate MDM injecting a root CA into the user trust store is
> the canonical "easy MITM" scenario. The OS removes it by default; do not undo that.

---

## Step 8: Handle the pinning failure case

A pinning exception is *not* a generic network error. The right reaction is:

1. Refuse the request (the pinner already does this).
2. Log to telemetry with the leaf SHA-256, expected pins, and the time.
3. Surface a security-grade message to the user ("Couldn't verify network connection.
   Please check your network and try again."). Never the technical detail.
4. If failures persist, surface a "Possible network interception detected" status on the
   Security Center screen.

```kotlin
suspend fun safeCall(block: suspend () -> Response): NetworkResult {
  return try {
    NetworkResult.Success(block())
  } catch (e: SSLPeerUnverifiedException) {
    telemetry.logPinFailure(e)
    NetworkResult.PinFailure
  } catch (e: IOException) {
    NetworkResult.NetworkError(e.message ?: "unknown")
  }
}
```

> **Why this matters.** Conflating pinning failures with generic network errors hides the
> single signal that proves a MITM is in progress.

---

## Step 9: Test against a real MITM proxy

The Dev Mode toggle `Simulate MITM proxy` switches the integrity verdict to `Limited`.
Pair it with a real test: run mitmproxy on a separate machine, install its CA into the
device user store, and verify the app **refuses to connect**.

```bash
# On laptop
mitmproxy --listen-port 8080 --ssl-insecure
# On device: set HTTP/HTTPS proxy to laptop:8080, install mitmproxy CA into user store
# Expected: app fails to load any /me/* call, pin failure logged.
```

If the request succeeds, your pinning is misconfigured. Re-check.

> **Why this matters.** Pinning that works in dev only proves the configuration is
> internally consistent. Pinning that survives a real proxy proves it actually defends.

---

## Step 10: Plan rotation before you need it

The day you need to rotate the live key, the steps must be muscle memory:

1. Generate the new key + certificate, leave the backup pin as a safety net.
2. Push a signed pin manifest with `current = [newPin]`, `backup = [oldPin, newBackup]`.
3. Wait the manifest TTL (~24 hours) for client cache propagation.
4. Cut the server over to the new certificate.
5. After the transition stabilises, push another manifest dropping the old pin entirely.

Practice the rotation in staging at least once a year. The first time you do it in an
incident at 3am is the wrong time to learn the steps.

> **Why this matters.** Rotations are infrequent enough that the procedure rusts. Annual
> practice keeps the playbook live.

---

## Wrap-Up

Your network stack now refuses cleartext, pins to SPKI hashes at both the platform and
client layers, has a dynamic-update path that does not require a release, and rejects a
real MITM proxy.

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) for the device-side identity
  that lets the server bind sessions to specific devices.
- [OWASP Mobile Top 10](/codelabs/mobile-top-10) for the wider context this defence sits
  inside (M5 — Insecure Communication).

**Recap of what you just built:**

- SHA-256 SPKI pins for the live key and a backup intermediate.
- A platform-level Network Security Config that forbids cleartext and system-trust only.
- An OkHttp `CertificatePinner` as the in-app belt to the platform's braces.
- A signed dynamic-pin-manifest fetch with embedded fallback.
- Pin-failure telemetry that surfaces the leaf hash without leaking PII.
- A documented rotation playbook with a manifest-TTL ladder.
