# 09 — Zero Trust: Device Binding & Risk Signals

> "A user's identity is one input. The device making the request is another. The patterns of
> how they ask are a third. A safe action is the conjunction. Stack the inputs; refuse the
> single point of trust." — *Fortress field notes*

**TL;DR** — Bearer tokens prove *someone has my access token*. Device binding adds: *and that
someone is operating from this specific TEE, holding the private key whose public counterpart we
registered at enrolment*. Together with risk signals (geolocation, behaviour, sign-in cadence),
the server makes a per-action call: green-light, step-up, refuse. This file walks the
implementation already in Fortress — the device-binding enrolment, the step-up flow that signs
IBAN reveals, and the wider risk picture that's staged for the next pass.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Make a stolen token useless without the bound device | Steal the binding too, or skip past it via a softer path |
| **Key idea** | Token + signed proof-of-possession of a device-bound key | If the binding key is exportable, weakly enforced, or trustfully replaced, I'm in |
| **Worst failure** | Storing the bound public key without authentication; trusting `deviceId` alone | Allowing key re-registration without out-of-band confirmation |

---

## 🛡️ Defender — "I bind the token to the silicon"

### The picture

Three things travel together on every sensitive request:

```
Authorization: Bearer <JWT>           ← who you are
X-Fortress-Device: <deviceId>         ← what install you came from
Body: { signatureB64, nonceB64 }      ← TEE-signed proof the device is the same one that enrolled
```

The JWT alone gets you read-only routes. The signature + nonce gates the mutating ones.

### Enrolment, once per device

[`DeviceBindingEnroller`](../app/src/main/kotlin/com/umain/fortress/auth/DeviceBindingEnroller.kt) does the work on first login:

```kotlin
val publicKey = biometricKeyStore.getOrCreatePublicKey(ALIAS_DEVICE_BINDING)
val spki = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
deviceBindingApi.register(deviceIdProvider.current(), spki)
```

- The keypair is generated inside the Android Keystore — the private half **never** leaves the
  TEE. `publicKey.encoded` returns the X.509 SPKI bytes; that's what the server receives.
- The key uses `setUserAuthenticationRequired(true)` so signing requires a fresh biometric.
- `setInvalidatedByBiometricEnrollment(true)` — if anyone adds a fingerprint, the key dies.

Server side: [`/auth/device-binding/register`](../backend/src/routes/devicebinding.ts) stores
`{userId, deviceId, publicKeySpkiB64, createdAt, updatedAt}`. Subsequent re-registrations from
the same `(userId, deviceId)` pair rotate the stored key — useful when the local key gets
invalidated by biometric enrolment and the client regenerates.

### Step-up, every sensitive action

A "step-up" action is anything that touches money or reveals long-lived identifiers (IBAN, PAN,
account-recovery email). The flow in this repo is implemented for IBAN reveal in [`AccountDetailViewModel.revealIban`](../app/src/main/kotlin/com/umain/fortress/ui/screens/accountdetail/AccountDetailViewModel.kt) and the matching backend route in [`backend/src/routes/stepup.ts`](../backend/src/routes/stepup.ts):

```
Client                                    Server
  │                                         │
  │  POST /stepup/reveal/account/:id/challenge
  │ ───────────────────────────────────────►│  generate 32-byte nonce
  │                                         │  bind to (userId, action, payloadDigest, expiresAt)
  │                                         │  persist as StepUpChallenge
  │  ◄─────────────────────────────────────  │  {nonceB64, expiresAtEpochMs}
  │                                         │
  │  BiometricPrompt + CryptoObject         │
  │  signature.sign(nonceBytes)             │
  │                                         │
  │  POST /stepup/reveal/account/:id/verify │
  │  body: {nonceB64, signatureB64, deviceId}
  │ ───────────────────────────────────────►│  lookup challenge by nonce
  │                                         │  lookup device-binding by (user, deviceId)
  │                                         │  crypto.verify('SHA256', nonce, pubKey, sig)
  │                                         │  on success: mark consumed, return IBAN
  │  ◄─────────────────────────────────────  │  {ibanFull}
```

What this proves at the server side:

1. The user holds a valid access token (Authorization header).
2. The device matches a binding we know.
3. A biometric ceremony happened on that device in the last ~60 seconds (because the TEE
   only signs after a successful `BiometricPrompt`).
4. The biometric authorised *this specific action* — the nonce was minted for it.

Any one of those, alone, is insufficient. The conjunction is the gate.

### The `cnf` claim — for production hardening

The pattern above proves the device per-action. Production extends it to **every** access-token
verification by embedding a confirmation key in the token:

```json
{
  "iss": "fortress.bank",
  "sub": "u_alice",
  "exp": 1717250400,
  "cnf": {
    "jkt": "SHA-256 thumbprint of the device-binding public key"
  }
}
```

Every authenticated request then carries either:
- A **DPoP** header (RFC 9449) — a JWS over the request method, URL, and a fresh nonce,
  signed by the device-binding key — verified by every backend service, or
- The same signed-nonce pattern as step-up, for every call.

This makes a stolen access token unusable from any other device. Fortress's current
implementation does step-up only for high-value actions; the `cnf`-everywhere model is staged
in [10-system-design.md](10-system-design.md) under "session-bound tokens".

### Risk signals to blend

The defender's calculus shouldn't be binary. Stack signals:

| Signal | Source | What it tells you |
|---|---|---|
| Device integrity | Play Integrity verdict ([05](05-play-integrity.md)) | Is this a real, unmodified Android device? |
| App integrity | Play Integrity | Is this our APK, signed by us? |
| Geolocation cluster | IP geo + history | Is this a normal location for this user? |
| Velocity | Sign-in cadence | Did the user just sign in from Stockholm and now Beijing? |
| Biometric mode | `setUserAuthenticationParameters` returns class | STRONG vs WEAK |
| Time since enrolment | `device_binding.createdAt` | Trust a brand-new binding less |
| Behaviour pattern | Action history | Is this user's first ever transfer to a new payee at 03:00? |
| Network quality | Connection type, RTT | TOR / commercial VPN / mobile data |

Each signal gets a weight; the sum is a risk score; the policy table maps score → action:

```
score < 20:  allow
20 ≤ score < 60:  require step-up biometric
60 ≤ score < 90:  step-up + cooldown + email confirmation
score ≥ 90:  refuse, alert user, escalate to fraud
```

These thresholds are tuned per market, per action class, per user cohort. Fortress's demo
hard-codes a "step-up for IBAN reveal and any transfer" policy; production has a rules engine.

### The "new device" problem

Day-1 device binding shouldn't be the only protection. A genuine new device has no history with
the user — risk score starts elevated, drops as the user demonstrates normal behaviour over
days. Patterns:

- **Cooldown**: a new device can only spend up to €X / day for the first 72 hours.
- **Out-of-band confirmation**: enrolling a new device for the first time pings an existing
  trusted device ("approve this new device on phone X?").
- **Identity re-verification**: high-value accounts require document re-upload for new devices.

These don't live in code in this repo yet, but the data model supports them: each
`device_binding` row carries `createdAtEpochMs` and `updatedAtEpochMs`, ready for cooldown logic
in a future commit.

---

## ⚔️ Attacker — "I steal the token but the silicon doesn't follow"

### Bypass 1 — Use the stolen token without device binding

Old-school attack. Phishing site, malware, MITM, dumped backup — I have the bearer token. I
fire it at `/me/dashboard`: 200 OK, account balances are mine. I fire it at the reveal endpoint
challenge: I get a nonce. I fire `/verify` with empty/random signature: 401.

I'm stuck at read-only.

**Counter:** that's exactly the design. Bearer token alone is read-only, the bound key is
required for state-mutating ops. Tighten further: require DPoP-style proof of possession on the
read-only routes too for high-value accounts.

### Bypass 2 — Generate my own key and register it on the legitimate user

If the device-binding registration endpoint accepts any (userId, deviceId, publicKey) tuple
from an authenticated session, I — having the stolen token — can register my key alongside the
legitimate one. Now the legit user has *two* registered devices: theirs and mine. My signed
challenges verify.

**Counter:**
- **Single binding per (userId, deviceId)**: the demo enforces this — re-registration on the
  same `deviceId` rotates the key, doesn't add a new one. So I'd need a different deviceId.
- **Multiple devices need OOB confirmation**: a new (userId, deviceId) tuple beyond N=2 emits a
  push to existing trusted devices: "approve new device $X?". Until confirmed, the new binding
  is in a "pending" state — useful for read-only but cannot complete step-up.
- Telemetry: spike in new-device registrations per user is a fraud-engine signal.

### Bypass 3 — Race the legit user's first enrolment

I steal the token *before* the user enrols their device-binding key. I register my key first
(remember — there's no existing binding yet, the server accepts the first one). When the legit
user opens the app later and enrols, my binding gets overwritten — but I've already used my
window.

**Counter:**
- Enrol device-binding **before** the first sensitive call, ideally as part of the same flow
  as `login` (the demo does this in [`AuthRepository.login`](../app/src/main/kotlin/com/umain/fortress/auth/AuthRepository.kt)).
- The window between "logged in" and "enrolled" is the dangerous one. Keep it minimal.
- Step-up endpoints reject if no binding exists for the deviceId — so my race doesn't open the
  step-up surface even if I beat the user.

### Bypass 4 — Replay a signed challenge

I capture a signed nonce + signature from a legit user (snooped network, debug log,
screen-capture exploit). I replay it on `/verify`. If the server doesn't burn the nonce, I
unlock the IBAN.

**Counter:**
- Server marks the challenge `consumed: true` on first verify. Second attempt with same nonce:
  rejected because `consumed === true`.
- Short TTL (60 s) limits the replay window even if reuse-detection is buggy.

### Bypass 5 — Lift the bound private key

The dream attack: extract the device-binding private key from the TEE. Then I can sign
challenges from anywhere.

The TEE is the floor of this — Android Keystore symmetric keys are non-exportable; EC private
keys are non-exportable. The `BiometricKeyStore` keys use `PURPOSE_SIGN` only, no export path.

**Counter:** the TEE is the counter. This bypass is "compromise the secure element" — out of
scope for software defence, in scope for Pixel/Knox certification work. If you're worried about
this, you're a sovereign defender, not a banking app.

### Bypass 6 — Patch the client to skip the BiometricPrompt and call signature.sign() directly

If I have code execution inside the app, can I just call `signature.sign(nonceBytes)` without
the prompt?

No — the key is created with `setUserAuthenticationRequired(true)` and
`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`. The TEE refuses `sign()` unless a
biometric ceremony happened in the last operation. Calling `signature.sign()` without the
prompt throws `UserNotAuthenticatedException`. My hooked code can't fake this in userspace —
the gate is in the TEE.

**Counter:** keep the auth-gating spec exactly as it is. Never relax to time-based validity for
production builds. See [07-biometric-hardening.md](07-biometric-hardening.md).

### Bypass 7 — Cross-device key sharing (synced credentials abuse)

If the user signs in to a malicious second device via a synced cloud passkey, that second
device enrols its own device-binding key (with the same userId). Now I'm a "legitimate" device
from the server's perspective — but I'm holding the malicious second device.

**Counter:**
- New (userId, deviceId) binding requires OOB confirmation from an existing trusted device
  before it can complete step-up — see Bypass 2.
- Risk engine watches for "fresh device starts using step-up immediately after enrolment" as a
  flag.
- Cap how much money a fresh device can move in its first 72 hours.

### Bypass 8 — Replay a challenge issued for a different action

If `/verify` for action A accepts a nonce that was issued for action B, I can issue a low-risk
"reveal" challenge with my session, then use that nonce's signature to authorise a transfer.

**Counter:** the demo binds each nonce to `action: "reveal:account:<id>"` and the verify
endpoint asserts `expectedAction === storedChallenge.action`. Mismatch → reject.
[`stepup.ts`](../backend/src/routes/stepup.ts) shows the check.

### Bypass 9 — Strip `deviceId` from the verify body

If the server falls back to "no deviceId → use any of the user's bindings", I omit `deviceId`
in my crafted request and the server tries all of them in turn until one matches my forged
signature. Combined with Bypass 2 (registered my own binding for the user), I'm in.

**Counter:** require `deviceId`, refuse the request if missing. The demo's verify does this:
type-check + 400 on absence.

---

## Cross-reference

- **What "TEE-bound" actually means under the hood** → [02-hardware-vault.md](02-hardware-vault.md)
- **How the biometric ceremony gates the sign call** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Token lifecycle these signals complement** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Anti-Frida / anti-debug** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **The risk-aware system design at scale** → [10-system-design.md](10-system-design.md)

## References

- [Part 9 — Zero Trust: Device Binding, Risk Signals](https://blog.stackademic.com/part-9-zero-trust-device-binding-risk-signals-e2f2796ceefd)
- [RFC 9449 — OAuth 2.0 Demonstrating Proof of Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)
- [Android Developers — Hardware-backed Keystore](https://developer.android.com/privacy-and-security/keystore)
- [NIST SP 800-63B — Digital Identity Guidelines, Authentication](https://pages.nist.gov/800-63-3/sp800-63b.html)
