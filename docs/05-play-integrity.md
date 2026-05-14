# 05 — Environment Trust: Play Integrity Attestation

> "Your app cannot prove its own integrity. That's the whole point — the OS proves it to your
> backend, and your backend decides what to believe." — *Fortress field notes*

**TL;DR** — **Play Integrity API** lets the Android OS (signed by Google) attest *for* your app
that the device, app binary, and licence are what you expect. The verdict is a signed JWT-like
token that **your backend** verifies — not your client. Anything the client decides about
integrity is theatre. This file walks the standard request flow, what the four verdicts mean,
how to wire it into Fortress, and the long, motivated list of bypass attempts.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Get a Google-signed second opinion on the device and app | Make Google sign for a lie |
| **Key idea** | The verdict is server-side gospel; the client is just a courier | The Play Integrity service trusts the device's TEE — break the TEE or imitate it |
| **Worst failure** | Letting the *client* parse and decide on the verdict | A "device-was-Trusted" bypass that fools your `if (verdict == TRUSTED)` gate |

---

## 🛡️ Defender — "I ask Google, I don't ask the client"

### The four verdicts

A Play Integrity response packs four independent verdicts:

| Field | What it tells you |
|---|---|
| **`deviceIntegrity`** | The device runs unmodified Android: bootloader locked, no Magisk, no rooted partitions. Reports back as a set of "labels" — `MEETS_DEVICE_INTEGRITY`, `MEETS_BASIC_INTEGRITY`, `MEETS_STRONG_INTEGRITY`, `MEETS_VIRTUAL_INTEGRITY`. |
| **`appIntegrity`** | The exact APK that's calling is the one you signed. Reports `PLAY_RECOGNIZED` (matches Play-signed binary), `UNRECOGNIZED_VERSION` (binary is unknown), or `UNEVALUATED`. |
| **`accountDetails`** | The user installed via Play and the licence is valid. `LICENSED`, `UNLICENSED`, or `UNEVALUATED`. |
| **`environmentDetails`** | (Standard requests only.) Risk signals about the runtime environment — e.g. **`NO_ISSUES`**, `APP_ACCESS_RISK` (sideloaded XAPK companions, overlay attacks). |

Each is a list. A device can report `MEETS_DEVICE_INTEGRITY` *and* `MEETS_VIRTUAL_INTEGRITY` —
meaning real device, real bootloader, but currently running in an emulator that passes the
virtual-integrity check. (Yes, emulators can pass — see the attacker section.)

### Standard vs Classic requests

| | Standard | Classic |
|---|---|---|
| Best for | High-frequency calls (every screen change) | One-shot decisions (sign-in, big transfer) |
| Latency | Low — token is pre-warmed | Higher — fresh roundtrip every time |
| Replay window | Refresh + reuse possible | Single-use against a freshly-issued nonce |
| Cost (quota) | High frequency, lower per-call info | Lower frequency, fuller verdict |
| Recommended | Continuous trust checks | Step-up authorisation |

Fortress strategy:
- **App launch + every 15 min**: standard request, set the `SecurityChip` in the UI bar.
- **Step-up moments** (transfer confirmation, IBAN reveal, new payee): classic request with a
  fresh nonce bound to the action.

### Wiring the standard request (Android side)

```kotlin
// Boot the integrity manager once. Provider needs Google Play Services >= 24.x.
private val provider = IntegrityManagerFactory.createStandard(context)

suspend fun getStandardToken(requestHash: ByteArray): String =
    provider.requestIntegrityToken(
        StandardIntegrityTokenRequest.builder()
            .setRequestHash(Base64.encodeToString(requestHash, Base64.URL_SAFE or Base64.NO_WRAP))
            .build()
    ).token()
```

The `requestHash` is **any** opaque bytes the server can later verify it issued — typically
`SHA-256(nonce || userId || actionContext)`. The Play Integrity service binds the token to this
hash. Without it, the same token can be replayed across requests.

### Wiring the classic request (high-value action)

```kotlin
private val provider = IntegrityManagerFactory.create(context)

suspend fun getClassicToken(nonce: String): String =
    provider.requestIntegrityToken(
        IntegrityTokenRequest.builder()
            .setNonce(nonce)             // 16–500 chars, URL-safe base64, single-use
            .setCloudProjectNumber(123)  // your Google Cloud project for verification
            .build()
    ).token()
```

The nonce comes from the server, expires in 60s, is bound to the user and action, and is
single-use. Mint it for each step-up moment, throw it away after verification.

### Server-side verification

```ts
import { google } from "googleapis";

const playintegrity = google.playintegrity({
  version: "v1",
  auth: await getCloudAuth(),
});

const decoded = await playintegrity.v1.decodeIntegrityToken({
  packageName: "com.umain.fortress",
  requestBody: { integrityToken: token },
});

const payload = decoded.data.tokenPayloadExternal;
//  payload.deviceIntegrity.deviceRecognitionVerdict: string[]
//  payload.appIntegrity.appRecognitionVerdict: string
//  payload.appIntegrity.packageName: string
//  payload.appIntegrity.certificateSha256Digest: string[]
//  payload.accountDetails.appLicensingVerdict: string
//  payload.requestDetails.requestHash: string  (== the hash we issued)
//  payload.requestDetails.timestampMillis: number  (close to now)
```

Critical checks **the server** must perform:

1. **`requestDetails.requestHash` matches the one I issued for this user/action.** If it doesn't,
   discard — this is a replay from a different context.
2. **`requestDetails.timestampMillis` is fresh** (≤60s for classic, ≤5 min for standard).
3. **`appIntegrity.packageName === "com.umain.fortress"`**.
4. **`appIntegrity.certificateSha256Digest` matches our release cert fingerprint** (the SHA-256
   of the App Signing key from the Play Console).
5. **`appIntegrity.appRecognitionVerdict === "PLAY_RECOGNIZED"`** for high-value gates; allow
   `UNRECOGNIZED_VERSION` only for known-staged-rollout windows.
6. **`deviceIntegrity.deviceRecognitionVerdict` contains** at minimum `MEETS_BASIC_INTEGRITY`;
   step-up actions require `MEETS_DEVICE_INTEGRITY` or `MEETS_STRONG_INTEGRITY`.
7. **`accountDetails.appLicensingVerdict === "LICENSED"`** (catches pirated installs).

The output is your [`IntegrityVerdict`](../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt)
sealed type — `Trusted`, `Limited(reasons)`, `Untrusted(reasons)`. The reasons are why, not
what's wrong with the user — they decide whether to show the SecurityChip amber or red.

### Caching the verdict server-side

A standard token can be reissued by Play for ~10 minutes. Trust **the timestamp**, not the
client. Server policy:

- `≤ 5 min old`: trust, no re-fetch.
- `5 – 10 min old`: trust for read-only operations, force a fresh classic request for any
  state-mutating one.
- `> 10 min old`: discard, force the client to request a fresh token.

### What to do when the verdict goes red

| Verdict | UX |
|---|---|
| `MEETS_STRONG_INTEGRITY` | Full access |
| `MEETS_DEVICE_INTEGRITY` | Full access |
| `MEETS_BASIC_INTEGRITY` only | Sensitive ops require an additional biometric step-up bound to a TEE key |
| `MEETS_VIRTUAL_INTEGRITY` only | Read-only, no money movement, no payee changes |
| None | Block, show a "this device cannot run Fortress safely" screen |

Never **silently** degrade — the user sees the SecurityChip change. Loud failures keep your
support load down (people understand what's happening) and signal to attackers that the gate
exists.

### The bootstrap problem

Standard requests need the `cloudProjectNumber` and a working Google Play Services. On devices
without GMS (some Chinese OEMs, certain enterprise distributions), the standard API fails.

Options:
- **Refuse to run** on non-GMS devices — appropriate for high-assurance banking apps.
- **Degrade** to a more conservative trust posture (no transfers, read-only).
- **Substitute** another attestation (Samsung Knox on Samsung; KeyAttestation via Keystore
  certificate chain — see [11-root-detection.md](11-root-detection.md)).

Fortress will refuse for the demo. Adjust to your real population.

---

## ⚔️ Attacker — "I make Google sign for a lie, or I skip the signing entirely"

### Bypass 1 — Have the client decide

If your client app checks the verdict in Kotlin and gates the request based on a boolean, my
Frida hook replaces the boolean with `true`. The server never sees Play Integrity at all.

**Counter:** the client must never decide. The token is opaque to the client; the **server**
calls `decodeIntegrityToken`. There is nothing on the client to hook that changes the server's
decision.

### Bypass 2 — Replay a known-good token

If the server doesn't bind the token to `requestHash` or doesn't check the timestamp, I capture
one good token from a real device and reuse it for every subsequent request from my rooted
device.

**Counter:**
- Bind the token to a fresh per-request hash.
- Reject any token whose `timestampMillis` is older than the policy threshold.
- For classic: bind to a single-use nonce, burn it after verification.

### Bypass 3 — Pass through a real device

The "device farm" attack. I run the app on a real, unmodified device under my control. It
produces a good token. I extract the token, send it via my actual attacking session (running on
a rooted device or PC). The server sees a perfectly valid `MEETS_STRONG_INTEGRITY` token.

**Counter:**
- `requestHash` binding makes the token valid only for the specific request it was issued for.
  I'd need to keep the legit device making each subsequent attack request, which scales poorly.
- Device-bound public keys (`cnf` in tokens) — see [09-zero-trust.md](09-zero-trust.md) — make
  the integrity token *necessary but not sufficient*. My device-binding key on my attacking
  device doesn't match what the server expects.

### Bypass 4 — Magisk Hide / Zygisk + DenyList

Magisk has a long-running cat-and-mouse with Play Integrity. The current state of the art
(2026) is roughly:

- Devices with the bootloader unlocked **cannot** get `MEETS_DEVICE_INTEGRITY` regardless of
  Magisk Hide — the bootloader state is signed by the device hardware.
- But: a few residual paths exist where Magisk + Zygisk + DenyList + "Play Integrity Fix" modules
  spoof the verdict on specific Android versions. These are patched in waves.

**My move:** target older devices, older Android versions, OEMs that haven't fully integrated
hardware-backed attestation.

**Counter:**
- Require `MEETS_DEVICE_INTEGRITY` (not just `MEETS_BASIC_INTEGRITY`) for sensitive ops.
- Maintain a minimum Android-version floor.
- Telemetry on verdict distribution: a sudden spike of `MEETS_BASIC_INTEGRITY`-only users in a
  market is a sign of a bypass module circulating.

### Bypass 5 — Decryption servers (the "private API" path)

A handful of online services receive a Play Integrity token and return a "fixed" one with
better verdicts. They typically work by:

- Re-signing the token with stolen Play Integrity keys (rare; Google rotates and revokes),
- Or proxying the request to a real device (see Bypass 3),
- Or exploiting timing in the verification API.

**Counter:**
- Token revocation: Google occasionally invalidates compromised signing keys. Verify against the
  current public JWKS.
- `tokenPayloadExternal.requestDetails.requestPackageName` should match my package. Tokens from
  a different app are caught.

### Bypass 6 — Custom ROMs claiming Play certification

A custom ROM with a leaked Play certification key signs as a recognized device. These leaks
have happened in the past (Pixel 6 keys in 2023). Google revokes the affected keys.

**Counter:**
- Verify the token against the current Google public JWKS — revoked keys won't verify.
- The Google-side check enforces this; you just have to call `decodeIntegrityToken`.

### Bypass 7 — Force `appRecognitionVerdict` to "UNRECOGNIZED_VERSION" and hope you accept it

If the server's policy is "accept any of `PLAY_RECOGNIZED`, `UNRECOGNIZED_VERSION`, or
`UNEVALUATED`", I can sideload a modified APK with a known signing cert and get a verdict that
your server accepts.

**Counter:**
- For state-changing operations: only `PLAY_RECOGNIZED`. Block the rest.
- For read-only operations: `UNRECOGNIZED_VERSION` is OK during staged rollouts (your new
  version isn't yet recognized by Play). Document the policy.
- Always check `certificateSha256Digest` against your expected release cert.

### Bypass 8 — Emulator with full Play services

Modern emulators (Genymotion, Android Studio's own with GMS) can attain `MEETS_VIRTUAL_INTEGRITY`.
If your server accepts virtual-integrity for sensitive ops, you've welcomed an emulator-based
fuzzer.

**Counter:**
- Sensitive ops require `MEETS_DEVICE_INTEGRITY` or `MEETS_STRONG_INTEGRITY`.
- `MEETS_VIRTUAL_INTEGRITY` is fine for dev/QA workflows but should be flagged in production
  telemetry — a spike of virtual integrity is a developer/automation pattern, not real users.

### Bypass 9 — Race the freshness window

If the timestamp policy is "≤5 min old", I have 5 minutes to grab one good token and burn it
across many requests. Combined with Bypass 3, this is dangerous.

**Counter:**
- For high-value ops: ≤60 seconds. The token's tactical value drops sharply.
- Per-request `requestHash` binding makes "burn one token across many" infeasible regardless of
  freshness window.

---

## Cross-reference

- **The verdict struct on the client** → [`IntegrityCheck`](../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt), [`IntegrityVerdict`](../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt)
- **The UI surface that reflects the verdict** → [`SecurityChip`](../app/src/main/java/com/umain/fortress/ui/components/SecurityChip.kt)
- **Pre-Play-Integrity attestation (KeyAttestation)** → [11-root-detection.md](11-root-detection.md)
- **Known bypass techniques in the wild** → [13-play-integrity-bypass.md](13-play-integrity-bypass.md)
- **Hooking the integrity-token-fetch call** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **Why device-bound keys close the residual attack surface** → [09-zero-trust.md](09-zero-trust.md)

## References

- [Part 5 — Environment Trust: Play Integrity Attestation](https://blog.stackademic.com/part-5-environment-trust-play-integrity-attestation-9c3409764e2e)
- [Google Play Integrity API — Overview](https://developer.android.com/google/play/integrity/overview)
- [Play Integrity — Verdicts](https://developer.android.com/google/play/integrity/verdicts)
- [Play Integrity — Server-side verification](https://developer.android.com/google/play/integrity/standard#decrypt_and_verify)
