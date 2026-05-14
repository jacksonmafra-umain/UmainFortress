# 08 — Network Warfare: MITM Defence & Certificate Pinning

> "TLS without pinning is a friendly handshake with whichever certificate the user accidentally
> agreed to trust." — *Fortress field notes*

**TL;DR** — TLS proves the server is "somebody legitimate" — but the trust store says "legitimate"
means anyone with a valid certificate from any of ~150 CAs (plus whatever the user added). Pinning
narrows that to "the issuer Fortress actually uses". This file walks the OkHttp `CertificatePinner`
setup, the trade-offs (rotation pain, bricked apps if you get it wrong), and the attacker
toolkit from mitmproxy to user-installed root certs to BURP.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Refuse to talk to any cert chain we didn't expect | Sit between the app and the server, read/modify everything |
| **Key idea** | Validate the chain against a static list of SPKI hashes | Install a CA, hope the app trusts the user store |
| **Worst failure** | Trusting the user store in release builds | Pinning one specific certificate that you can't rotate |

---

## 🛡️ Defender — "I only believe one set of fingerprints"

### Pinning at the right layer

[`FortressHttpClient.buildPinner`](../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
returns an OkHttp `CertificatePinner`. The Ktor client inherits it for free because both clients
share the OkHttp engine. Pinning at OkHttp means **every** HTTPS call (Ktor, Coil image loads,
manual OkHttp) is protected by the same policy.

### Pin the SPKI hash, not the cert

```kotlin
CertificatePinner.Builder()
    .add("api.fortress.bank",
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",  // leaf
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",  // intermediate (backup)
        "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")  // intermediate (backup #2)
    .build()
```

- **SPKI (Subject Public Key Info) hash**, not full-certificate hash. SPKI survives renewals that
  reuse the keypair; cert hashes don't.
- **Multiple pins** — at minimum: current leaf + backup intermediate(s). RFC 7469 (deprecated
  for browsers, still useful as a mental model) recommends N+1 pins.
- **Backup pin off-line / out of fleet** — keep one in a vault so you can rotate to it when the
  primary needs to change.

In the demo build the pin set is intentionally empty — the backend is local, no HTTPS. The
production deploy should bake pins in at build time and have a rotation playbook.

### Why not Trust Manager replacement?

You can write a custom `X509TrustManager` to do the same thing. OkHttp's `CertificatePinner` is:

- Less code.
- Validated against the post-TLS-handshake certificate chain (after OkHttp's own chain check).
- Composable with debug overrides — release builds enforce strictly, debug builds can be wired
  to mitmproxy via [`network_security_config.xml`](../app/src/main/res/xml/network_security_config.xml).

### Network security config

[`network_security_config.xml`](../app/src/main/res/xml/network_security_config.xml):

```xml
<base-config cleartextTrafficPermitted="false">
    <trust-anchors><certificates src="system" /></trust-anchors>
</base-config>

<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">10.0.2.2</domain>
</domain-config>

<debug-overrides>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />
    </trust-anchors>
</debug-overrides>
```

The relevant properties:

- **`cleartextTrafficPermitted="false"`** in `base-config` — production refuses HTTP. The local
  dev exception is `10.0.2.2` only.
- **`<trust-anchors>` excludes `user`** in `base-config` — even if the user installs a CA, the
  app's release build does not trust it. mitmproxy / Burp need a *system-level* CA, which
  requires root.
- **`<debug-overrides>` adds `user`** — debug builds DO trust the user store, so the same
  toolchain can be wired up by your QA team for testing without affecting release behaviour.

The `<debug-overrides>` block is stripped from `release` APKs at merge time. The release manifest
never sees it.

### Verify the merged manifest

After every release build, confirm the merged manifest does not include `<debug-overrides>` and
`cleartextTrafficPermitted` is `false` everywhere it matters:

```bash
$ apkanalyzer manifest print build/outputs/apk/release/app-release.apk \
    | grep -A2 'networkSecurityConfig\|cleartextTrafficPermitted'
```

This belongs in CI. Manifests drift; checking them mechanically is the only way to know.

### Rotation playbook

When your TLS leaf needs to rotate:

1. **Six weeks before**: add the *new* leaf pin alongside the current pins. Ship a release.
2. **At the cutover**: change DNS / load balancer to serve the new cert. Existing clients accept
   either.
3. **After the new cert has been live for one TTL cycle**: remove the old pin. Ship a release.

The window between step 1 and step 3 is your safe rollback zone. Without backup pins, a botched
cutover bricks the app for everyone until they update.

### `MaxAge` and stale-pin disasters

Browser HPKP died because a misconfigured `Public-Key-Pins` header with a long `max-age` could
brick a domain. The mobile equivalent is shipping a pin set without backups. If the leaf has to
rotate emergency-style (CA compromise, certificate revocation), and your only pin is the leaf,
you have **no way to update the app** without users actually downloading a release. Their app
just stops working.

**Always**: at least one *backup* pin tied to an intermediate or backup keypair. Keep the
backup private key offline. The backup pin's purpose is exactly the emergency.

### App-bundle download blob

Pins are usually baked at build time. If you operate at fintech scale and need *dynamic* pin
updates (a leak forces an immediate rotation), serve a signed pin set:

- Server publishes `pins-v1.json` signed by an offline signing key.
- App on launch fetches it, verifies the signature against a bundled public key, caches.
- If the signature fails, fall back to the baked-in set.

This adds a bootstrap problem (you have to *get* the pin set somehow), but it lets you rotate
without app updates. Worth it only if your rotation cadence is faster than your app's release
cadence.

---

## ⚔️ Attacker — "I sit on your wire"

### Bypass 1 — Install my CA in the user store, hope you trust it

On any unrooted Android phone I can install a CA into the user store. If the target app's
`networkSecurityConfig` trusts `user` in `base-config`, mitmproxy / Burp / Charles works out
of the box. I see every request, every response, every token.

**Counter:** don't trust the user store in `base-config`. Put it in `<debug-overrides>` only.

### Bypass 2 — Root the device and add my CA to the system store

Now the app does trust me, regardless of config — as long as it doesn't pin.

**Counter:** pin. The TLS connection completes (the cert is "valid"), but `CertificatePinner`
sees a chain that doesn't match the pin set and tears the connection down.

### Bypass 3 — Frida-patch the CertificatePinner

If I have code execution inside the app (root + Frida), I can `Java.use("okhttp3.CertificatePinner")`
and replace `check$okhttp` to be a no-op. Now my CA is trusted again.

**Counter:**
- RASP: detect Frida injection, refuse to start the network layer. See [14-rasp-strategies.md](14-rasp-strategies.md).
- Play Integrity verdict required at session resume — a tampered process should fail integrity.
- Bind sensitive operations to a TEE-resident key — patching the pinner doesn't help me extract
  the key. Even if I read all traffic, signing operations still require the TEE.

### Bypass 4 — Replace OkHttp at build time

I get into your CI, swap your OkHttp dependency for a fork that ignores pins, build the release.
Now nothing protects the wire.

**Counter:**
- CI integrity. Sign your build pipeline; require provenance.
- App-store signing certs (you sign release builds yourself, not just CI).
- Verify reproducible builds where possible.

### Bypass 5 — Bypass via VPN-with-mitmproxy

Configure the device's VPN to route through mitmproxy with a CA the user has trusted. Same
result as Bypass 1, different transport.

**Counter:** pinning fights this the same way as Bypass 1. The CA path doesn't matter — only
the SPKI fingerprint at the end of the chain does.

### Bypass 6 — Downgrade to HTTP

If `cleartextTrafficPermitted="true"` exists anywhere your app might talk to, I'll find it.
Misconfigurations: dev URLs that escape into release builds; image CDNs served over HTTP; legacy
API hosts.

**Counter:**
- `cleartextTrafficPermitted="false"` at the top level.
- Domain-specific exceptions only for known dev hosts (loopback/emulator).
- CI assertion on the merged release manifest.

### Bypass 7 — TLS 1.0 / 1.1 / cipher downgrade

If the server accepts ancient TLS or RC4 / 3DES, I downgrade and break the crypto.

**Counter:** server-side problem mostly. Client-side: configure OkHttp with `ConnectionSpec`
restricting to `MODERN_TLS` only. Refuse anything below TLS 1.2; prefer TLS 1.3.

### Bypass 8 — `setSSLSocketFactory` overrides

Anywhere in your codebase that constructs an `OkHttpClient` with a custom `SSLSocketFactory`
that doesn't carry the pinning policy is a hole. Common bug: a *separate* OkHttp client for
analytics / image loading that skips pinning.

**Counter:** centralize OkHttp construction. This repo's [`FortressHttpClient`](../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
exposes two clients (anonymous + authenticated) — every consumer flows through it.

---

## Cross-reference

- **Where the pin policy gets enforced** → [`FortressHttpClient`](../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
- **What network-config matters at install time** → [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml), [`network_security_config.xml`](../app/src/main/res/xml/network_security_config.xml)
- **What still works when the wire is compromised** → [07-biometric-hardening.md](07-biometric-hardening.md) (TEE-signed actions)
- **How an attacker spots an unpinned client** → [12-decompiling.md](12-decompiling.md)
- **Anti-hooking** → [14-rasp-strategies.md](14-rasp-strategies.md)

## References

- [Part 8 — Network Warfare: MITM Defence, Certificate Pinning](https://blog.stackademic.com/part-8-network-warfare-mitm-defense-certificate-pinning-8abeb5685aae)
- [OkHttp — CertificatePinner](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [Android Developers — Network security configuration](https://developer.android.com/training/articles/security-config)
- [Android Developers — Trust Manager and certificate pinning](https://developer.android.com/privacy-and-security/security-ssl)
