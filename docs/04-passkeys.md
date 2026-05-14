# 04 — Beyond Passwords: The Passkey Revolution

> "Phishing dies the day your credential refuses to be presented to a domain it doesn't recognise.
> Passwords have never refused anything." — *Fortress field notes*

**TL;DR** — A **passkey** is a hardware-bound public/private key pair created via WebAuthn /
FIDO2. The private half lives in the TEE (or a synced password manager); the public half lives
on the server. Authentication is a signed challenge — there is no shared secret on the wire and
no password to phish. Android exposes the whole thing through `androidx.credentials`. This file
walks the registration + sign-in flow Fortress is staging, what the server has to do, and the
attacker toolkit aimed at the edges of the model.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Replace passwords with origin-bound, phishing-resistant credentials | Trick the user into authenticating to a site they don't realise is mine |
| **Key idea** | The browser/OS refuses to sign for a different `rp.id` than the one a credential was bound to | The recovery flow, the QR-cross-device flow, and the password-fallback are softer targets |
| **Worst failure** | Treating passkey sign-in as "another auth method" rather than as a replacement | Leaving a password recovery loop that bypasses the passkey entirely |

---

## 🛡️ Defender — "I never see a password, so neither does the attacker"

### Mental model in 60 seconds

```
Enrolment:
  client → server: "I want to make a passkey for alice@fortress.bank"
  server → client: challenge + rp.id="fortress.bank" + user info + pubKeyCredParams
  client (browser/OS): generates a P-256 keypair inside the TEE
  client → server: { credentialId, publicKey, attestation, signature(challenge) }
  server: stores { credentialId, publicKey, signCounter=0, userId, aaguid }

Sign-in:
  client → server: "I want to sign in as alice@fortress.bank"
  server → client: challenge + rp.id + allowedCredentials[credentialId]
  client (browser/OS): user verification (biometric/PIN) → sign(challenge)
  client → server: { credentialId, signature, clientDataJSON, authenticatorData }
  server: lookup credential by credentialId → verify signature with stored publicKey → check counter
```

Two things the user types: zero passwords, zero codes.

### Why this is phishing-resistant

The signed `clientDataJSON` includes the **`origin`** the browser saw when the user authenticated.
The server verifies `origin === expected_origin`. If a phishing site at `frortress.bank` (with two
r's) tries to relay a challenge it scraped from the real server, the browser signs `origin =
"frortress.bank"`, the real server sees the mismatch and rejects. The user could not, by typing
or clicking anywhere, authenticate to the wrong site — because the *browser*, not the user,
refuses.

This is the single most important property of WebAuthn. Everything else is supporting cast.

### Where Fortress will live in the spec

| Spec piece | Fortress value | Why |
|---|---|---|
| `rp.id` | `fortress.bank` | The DNS suffix the app authenticates against |
| `rp.name` | `Fortress` | Shown in the OS picker |
| `user.id` | opaque, ≤64 bytes, stable per user | Spec forbids using the email |
| `user.name` | `alice@fortress.bank` | Shown in OS picker |
| `pubKeyCredParams` | `[{ type: "public-key", alg: -7 }]` (ES256) | Modern curve; widely supported |
| `attestation` | `"none"` for consumer; `"direct"` only for hardware-attested enterprise | Most consumer flows don't need attestation, and "direct" exposes AAGUID for privacy concerns |
| `authenticatorSelection.userVerification` | `"required"` for sign-in, `"preferred"` for enrol | Verifying biometric/PIN is the difference between "I have your key" and "I have you" |
| `authenticatorSelection.residentKey` | `"required"` | Discoverable credentials enable usernameless flows |
| `authenticatorSelection.authenticatorAttachment` | `"platform"` for device-bound; omit for cross-device | The OS lists both options |

### Android client — `androidx.credentials`

Modern Android uses **Credential Manager** (`androidx.credentials`) to talk to the platform
authenticator. Three calls cover the whole surface:

```kotlin
// Enrolment (after the user is already authenticated by password / passkey / link).
val rpJson = """{
    "challenge": "$base64UrlChallenge",
    "rp": { "id": "fortress.bank", "name": "Fortress" },
    "user": {
        "id": "$base64UrlUserId",
        "name": "alice@fortress.bank",
        "displayName": "Alice Hartman"
    },
    "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
    "authenticatorSelection": {
        "authenticatorAttachment": "platform",
        "residentKey": "required",
        "userVerification": "preferred"
    },
    "attestation": "none",
    "timeout": 60000
}"""

val request = CreatePublicKeyCredentialRequest(requestJson = rpJson)
val response = CredentialManager.create(context).createCredential(activity, request)
val publicKeyCred = response as CreatePublicKeyCredentialResponse
val registrationJson = publicKeyCred.registrationResponseJson
// POST to server: /auth/passkey/register/finish with registrationJson
```

```kotlin
// Sign-in — server has already issued a challenge and the list of allowed credentials.
val signInJson = """{
    "challenge": "$base64UrlChallenge",
    "rpId": "fortress.bank",
    "allowCredentials": ${allowList.toJsonArray()},
    "userVerification": "required",
    "timeout": 60000
}"""

val request = GetCredentialRequest(
    credentialOptions = listOf(GetPublicKeyCredentialOption(signInJson))
)
val result = CredentialManager.create(context).getCredential(activity, request)
val cred = result.credential as PublicKeyCredential
val assertionJson = cred.authenticationResponseJson
// POST to server: /auth/passkey/signin/finish with assertionJson
```

The OS surfaces its own picker — same one Chrome uses on the web. Synced passkeys (Google
Password Manager) and device-bound passkeys (TEE) appear together; the user picks.

### Server-side responsibilities

[`backend/src/routes/passkey.ts`](../backend/src/routes/passkey.ts) (staged for next pass)
needs to:

1. **Issue a per-attempt random challenge** — 32 random bytes, opaque, bound to the user (or
   anonymous for sign-in by discovery). Short TTL (60 s). Single-use.
2. **Verify origin + RP-ID** — reject anything where the `clientDataJSON.origin` doesn't match
   what we expect, or where `clientDataJSON.challenge` doesn't equal what we issued, or where
   `authenticatorData.rpIdHash` doesn't equal `SHA-256(rp.id)`.
3. **Verify the signature** — using the stored public key from enrolment.
4. **Sign-counter checks** — `authenticatorData.signCount` should be ≥ the previously stored
   value. If it regresses, the credential may be cloned. (Synced passkeys often always report 0,
   so be permissive in that direction — see below.)
5. **AAGUID inspection** — `attestationObject.authData.attestedCredentialData.aaguid` identifies
   the authenticator model. Used for FIDO Metadata Service lookups in enterprise. Consumer flows
   typically ignore it.

### Device-bound vs synced passkeys

| | Device-bound (TEE) | Synced (Google Password Manager / iCloud Keychain) |
|---|---|---|
| Storage | TEE / StrongBox on the device | Encrypted in the user's cloud account |
| Phishing-resistance | Identical | Identical |
| Theft model | Get the device + biometric → access | Compromise the cloud account → access on a new device |
| Sign counter | Increments per use, useful clone detection | Often always 0 (cloud doesn't trust per-device increments) |
| Recovery | Lost device → enrol again from a different one | Cloud sync continues across devices |
| Suitable for | High-value confirmation, step-up | Day-to-day sign-in |

Fortress strategy:
- **Day-to-day sign-in** accepts either kind.
- **Step-up authentication** (transfers, IBAN reveal) requires a device-bound credential or a
  signed challenge from the action-gated TEE key — see [07-biometric-hardening.md](07-biometric-hardening.md).
  Cloud-synced credentials alone don't get to authorize money movement.

### Replacing the password endpoint

Fortress will keep `POST /auth/login` (email + password) as a **bootstrap** path: it's how a
user signs in from a brand-new device. After that, the device enrols a passkey, and the password
becomes vestigial — usable only via an explicit recovery flow that requires re-verification.

The eventual goal is to delete the password column entirely. A user who never types a password
into your service can never have their password phished from you.

### Logout, revocation, multi-device

| Event | Server action |
|---|---|
| User signs out | Revoke session (refresh token); leave passkey alive |
| User removes a passkey from one device | Server deletes the credential record; future assertions with that `credentialId` fail |
| User loses device | Recovery flow → re-verify identity → invalidate all credentials bound to that device → enrol new credential elsewhere |
| Suspicious activity | Optionally invalidate all credentials, force full re-enrol |

### Allow-list size

`allowCredentials` is sent **every** sign-in. If a user has 17 passkeys across devices, the OS
shows 17 options. Cap it: store credentials by recency, show top 5–10 most recently used,
provide a "more options" hook for the long tail.

For usernameless / discoverable flows: omit `allowCredentials` entirely, let the OS show all
credentials it has for `rp.id`.

---

## ⚔️ Attacker — "Phishing is dead, long live everything else"

### Bypass 1 — Just phish the password fallback

The passkey flow is unphishable. The password recovery flow probably is not. If the user can
say "I forgot my passkey" and re-enrol after an email link, I phish the email link.

**Counter:**
- Treat passkey enrolment from a new device with the same suspicion as a password change.
- Require recovery to involve a second factor that isn't email (existing passkey on another
  device; identity verification via document upload; in-person KYC for high-value accounts).
- Throttle recovery aggressively; alert on rapid sequential recoveries.

### Bypass 2 — RP-ID misconfiguration

If your `rp.id` is `*.fortress.bank` and you accept assertions from `attacker.fortress.bank`
(subdomain takeover, customer-controlled subdomain via a CDN config), I host a malicious page
there and harvest passkey assertions.

**Counter:**
- Strict `rp.id` (`fortress.bank` exactly, no wildcards).
- DNS hygiene; subdomain takeover is a separate but related ongoing problem.
- Allow-list of accepted `origin` values, not just the `rp.id` suffix check.

### Bypass 3 — Origin via local HTTPS interception

If I can get the user to install a system CA (rooted device, MDM compromise) and proxy the
device through me, *and* the app is running without cert pinning, I'd love to inject a malicious
RP-ID into the challenge response. Except: the *browser/OS* enforces `rp.id`, not your app code.
So even with full network control, the OS refuses to sign for the wrong RP-ID.

**Counter:** the spec is the counter. Don't subvert it. Cert pinning ([08-network-warfare.md](08-network-warfare.md))
closes the network channel but the assertion flow has its own guard.

### Bypass 4 — Sign-counter regression as a tell (and how to ignore me)

If the same credential ID asserts with a counter of 47, then 48, then 12, the credential has
been cloned: my device has counter 47 + 1 = 12 (I started fresh) while the user's has 49. The
defender should detect this and invalidate the credential.

**My move:** target users with synced passkeys (cloud), where the counter is always 0 and
regression is invisible.

**Counter:**
- Treat counter=0-always credentials as "synced" — relax the regression check, raise the bar on
  other signals (geolocation, device fingerprint, behaviour analytics).
- For high-value accounts, require **device-bound** credentials for step-up, not synced ones.

### Bypass 5 — Cross-device QR (CTAP 2.2 hybrid) phishing

WebAuthn allows the user to scan a QR with their phone to authenticate on a laptop browser
they don't have a passkey on. If I trick the user into scanning a QR on *my* phishing page, the
browser on the phone signs an assertion bound to my phishing origin — and the user's phone has
no idea this is wrong, because the origin shown is the one in the QR-encoded challenge.

Actually wait — the spec gets this right too: the phone's authenticator signs the *origin the
QR-initiating page presents to its own user agent*. If the QR was generated by `attacker.com`,
the phone signs `origin=attacker.com`. The real Fortress server rejects.

**My counter-move:** social engineering. I show a convincing UI on my phishing site that says
"we successfully signed you in". The user thinks they've signed in to Fortress. I haven't
captured their credentials but I have their attention and they're confused.

**Counter:** out-of-band confirmation (push to the user's phone showing "Did you just sign in
from $LOCATION on $DEVICE?"). The user clicks "no" → invalidate the session.

### Bypass 6 — Steal the credential blob

Day-to-day passkeys live in the TEE; can't steal them. *Synced* passkeys are in the cloud
account. Compromise the cloud account → restore on a new device under my control → I am the
user.

**Counter:**
- Cloud account is now the user's most important account. The cloud provider's security is the
  ceiling on yours.
- For step-up, require device-bound credentials (above).
- Enrolment of a new device from cold — even with valid cloud creds — should require the
  recovery flow's second factor.

### Bypass 7 — AAGUID-based filtering bypass

If your enterprise policy only allows credentials from approved authenticators (allow-list by
AAGUID), and the policy is enforced *server-side at enrolment*, I create a malicious authenticator
that lies about its AAGUID. Without attestation, you can't tell.

**Counter:**
- For enterprise / high-assurance: require `attestation: "direct"` and validate against the FIDO
  Metadata Service. Then I can't lie about AAGUID without a cert.
- Consumer flows don't need this; AAGUID inspection is a privacy concern when done at scale.

### Bypass 8 — Credential ID enumeration

If your sign-in endpoint behaves differently for "credentialId not found" vs "credentialId
found but failed verification", I enumerate user accounts.

**Counter:**
- Constant-time response shape (always look like "verifying", then return generic 401 on any
  failure).
- For usernameless flow, server doesn't even know who is signing in until the assertion verifies
  — no enumeration surface.

### Bypass 9 — Resident-key abuse on shared devices

Discoverable credentials (resident keys) on a shared device → the OS picker shows my passkey
without me typing anything. A coworker grabbing my unlocked phone has access.

**Counter:**
- `userVerification: "required"` — the OS asks for biometric/PIN before signing. The coworker
  has my phone but not my fingerprint.
- For office shared kiosks: don't enrol passkeys; use cross-device QR flow that requires the
  user's personal phone.

---

## Cross-reference

- **The TEE that holds the device-bound private key** → [02-hardware-vault.md](02-hardware-vault.md)
- **Action-authorisation TEE key separate from the sign-in passkey** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Why this still needs cert pinning** → [08-network-warfare.md](08-network-warfare.md)
- **What the device-binding `cnf` claim looks like on tokens** → [09-zero-trust.md](09-zero-trust.md)

## References

- [Part 4 — Beyond Passwords: The Passkey Revolution](https://blog.stackademic.com/part-4-beyond-passwords-the-passkey-revolution-793ea2f291fe)
- [WebAuthn — Level 3 spec](https://www.w3.org/TR/webauthn-3/)
- [FIDO2 — Client to Authenticator Protocol (CTAP)](https://fidoalliance.org/specs/fido-v2.2-rd-20230321/fido-client-to-authenticator-protocol-v2.2-rd-20230321.html)
- [Android Developers — Credential Manager](https://developer.android.com/training/sign-in/credential-manager)
- [passkeys.dev](https://passkeys.dev/)
