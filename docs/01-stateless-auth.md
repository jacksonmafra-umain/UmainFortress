# 01 — The Stateless Blueprint

> "Sessions are easy at one server, tractable at ten, and a coordination nightmare at a thousand.
> Stateless tokens trade one problem (server coordination) for another (token revocation) — and
> the second problem is mostly solvable." — *Fortress field notes*

**TL;DR** — Fortress Bank issues short-lived **HS256 JWT access tokens** (15 min) plus long-lived,
**opaque rotating refresh tokens** (30 days, hashed at rest). The access token is verified by
signature alone at each service. The refresh token is the only stateful object; rotation on every
use limits leak blast radius, and a deny-list handles explicit revocation. This file walks the
architecture, the trade-offs, the implementation in this repo, and how an attacker would try to
weaponise each design choice.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Verify identity at scale without coordinating session state across N servers | Steal, forge, replay, or extend the lifetime of a token |
| **Key idea** | Signature = authorization; tokens carry just enough to authorise, nothing more | If your secret leaks, my forged token verifies forever |
| **Worst failure** | Long-lived access tokens with no revocation, single shared HMAC secret | Single sym-key shared across many services and rotated never |

---

## 🛡️ Defender — "I scale by minting, not by remembering"

### The mental model

A request arrives at any service in the fleet carrying `Authorization: Bearer <jwt>`. The
service:

1. Verifies the JWT's signature against the public verifier material (HMAC secret here; in
   production: JWKS-distributed public key).
2. Validates `iss`, `aud`, `exp`, `nbf`, `iat`.
3. Reads claims (`sub`, `email`, optional roles).
4. Acts on the request.

No session lookup. No database read on the hot path. The system scales horizontally as far as
the underlying issuer can.

### The token pair

| | Access token | Refresh token |
|---|---|---|
| Format | JWT (header.payload.sig) | Opaque random base64url, 48 bytes |
| Algorithm | HS256 (demo) / ES256 or RS256 (prod) | n/a |
| Lifetime | 15 min | 30 days |
| Storage server side | none | hash (SHA-256) + metadata (deviceId, userId, expiresAt, revoked) |
| Storage client side | encrypted with Android Keystore AES-GCM ([`TokenStore`](../app/src/main/java/com/umain/fortress/security/TokenStore.kt)) | same |
| Sent on every call | yes, as `Authorization: Bearer …` | only to `/auth/refresh` |
| Revocation | implicit (it expires) | explicit (deny list / rotated on use) |

### Why short access + long refresh

- **Short access** keeps blast radius small: a stolen access token is useless once 15 min pass.
- **Long refresh** keeps UX smooth: the user does not retype credentials every 15 min.
- **Rotation on every refresh** turns a stolen refresh into a single-use object. If the attacker
  refreshes, the legitimate client's next refresh fails — and the failure is the signal that
  triggers session invalidation / risk review.

### Refresh rotation in this repo

[`backend/src/routes/auth.ts`](../backend/src/routes/auth.ts) implements the dance:

```ts
// 1. Look up the refresh token by its SHA-256 hash (raw token is never stored).
// 2. Check: not revoked, not expired, deviceId matches the one bound at issuance.
// 3. Revoke the old record.
// 4. Mint a new access token + new refresh token.
// 5. Persist the new refresh token alongside the same deviceId.
// 6. Return the new pair to the client.
```

Critical detail: **the refresh token is stored hashed**. If the database leaks, an attacker holds
preimage-resistance bytes — not usable tokens. (We're not using a slow hash because the secret is
high-entropy by construction; SHA-256 is appropriate here. See *Why SHA-256 not bcrypt for
refresh tokens?* below.)

### Why HMAC (HS256) for the demo, asymmetric (ES256/RS256) for production

| | HS256 | ES256 / RS256 |
|---|---|---|
| Secret distribution | Every verifying service holds the same secret | Verifiers hold a public key; only the issuer holds the private key |
| Compromise blast radius | Any service can mint tokens | Only the issuer can mint; verifiers cannot |
| Rotation | All-at-once across the fleet | Issuer rotates private key; JWKS endpoint advertises new public keys; verifiers pick them up via key ID (`kid`) |
| Operationally | Trivial to set up | Trivial *for verifiers*; the issuer needs HSM / KMS / careful key custody |

Demo uses HS256 because the demo runs in one process. Production should use ES256: smaller tokens
than RS256, modern curve, broad library support. The private key lives in a KMS, the public JWKS
is served at `/.well-known/jwks.json`, and clients/services rotate keys by their `kid`.

### Claim hygiene

Only the minimum needed:

```json
{
  "iss": "fortress.demo",
  "aud": "fortress.client",
  "sub": "u_alice",
  "email": "alice@fortress.dev",
  "displayName": "Alice Hartman",
  "iat": 1741875600,
  "exp": 1741876500,
  "jti": "fdfa..."
}
```

Things to **not** put in a JWT:

- Anything you'd rotate independently of the token (e.g. roles that change daily). Put a
  short-lived role map keyed by `sub` in the auth service and fetch lazily — or include role
  fingerprints + force a refresh when they change.
- PII you'd rather not pass through every log line. The token shows up in HTTP logs, in browser
  storage, on intermediate proxies if any of them log headers.
- Permissions for systems that aren't the audience. A token for `fortress.client` should not also
  authorise `fortress.admin`.

### Why SHA-256 not bcrypt for refresh tokens?

bcrypt / argon2 exist to slow down brute force against **low-entropy** inputs (passwords). The
refresh token is 48 random bytes — 384 bits of entropy. A slow hash buys nothing here; a stolen
database does not become brute-forceable. SHA-256 is fast, deterministic, collision-resistant in
the relevant sense, and lets the `/auth/refresh` path stay sub-millisecond.

### Scaling the issuer

The issuer is the only stateful node in this picture. It needs to write a refresh-token record
on every login + every refresh. Patterns from the source articles:

- **Read-heavy authn checks** (verifying tokens) scale horizontally — they only need the public
  key.
- **Write-heavy refresh-token records** scale via partitioning by `userId` (deny-list lookup is
  fast and bounded by per-user fan-out).
- **Revocation list** — a tiny Redis set of revoked `jti`s for emergency kill switches; the rest
  is taken care of by short access TTL.

For 5M users you can run the issuer as a small, partitioned service; the verifier fleet is the
big horizontal layer.

### Client side in this repo

The Android client treats both tokens as opaque bytes:

- They round-trip through [`TokenStore`](../app/src/main/java/com/umain/fortress/security/TokenStore.kt)
  encrypted with AES-256-GCM via the Android Keystore (TEE/StrongBox).
- [`SessionManager`](../app/src/main/java/com/umain/fortress/auth/SessionManager.kt) treats the
  store as truth and exposes `Active` / `Expired` / `SignedOut`.
- [`AuthInterceptor`](../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt)
  attaches the access token on every authenticated request and runs a **single-flight refresh on
  401** (the next file in the series).

---

## ⚔️ Attacker — "I treat your stateless design as a stateful liability"

### Bypass 1 — Forge tokens with the leaked HMAC secret

If you used HS256 and the secret leaked (env var dump, GitHub commit, log line that printed
`process.env`), I can mint tokens for any subject for any expiry. Your verifying fleet will
verify them happily.

**Counter:**
- Use ES256/RS256 in production. Then a leak of the *verifier* material grants me nothing.
- Keep the issuing key in a KMS. Audit access; alert on direct key-material reads.
- Make secrets short-lived: rotate the HMAC weekly. Force overlapping `kid`s so rotation is
  zero-downtime.

### Bypass 2 — Replay an access token I stole

If I snatched an access token (XSS, MITM on an unpinned client, debug log, screen recording, FaceID + sleep), I have ≤ 15 min. That window is enough for an automated exfil pipeline.

**Counter:**
- 15 min is a deliberate ceiling, not a target. Combine with device-bound proof of possession:
  the access token's `cnf` claim names a public key whose private half is in the device's TEE.
  Verifying services require a fresh signature over the request from that private key. See
  [09-zero-trust.md](09-zero-trust.md) for the device-binding flow.
- Pin certificates so I cannot MITM with a user-installed cert. See [08-network-warfare.md](08-network-warfare.md).

### Bypass 3 — Replay a refresh token I stole

Without rotation: every refresh I do prints a new long-lived pair. I never need the user's
password again.

With rotation: my first refresh succeeds; the legitimate client's next refresh fails. But this is
visible to the server as **two refreshes against the same parent** — the second one within a
short window. That's the canonical "refresh-reuse" signal: revoke the entire token family and
force re-auth.

**Counter:**
- Rotate every refresh, store the lineage (`parent_id`), detect reuse.
- On reuse: revoke the lineage + alert the user + risk-score the device higher for 24h.

### Bypass 4 — Mint long-lived access tokens by skewing the clock

If your verifier trusts `exp` without sanity-checking against its own clock — or if it accepts
extreme `iat` skew — I can craft tokens that look freshly minted from the issuer.

**Counter:**
- Reject tokens with `iat` in the future or more than a few minutes in the past.
- Reject `exp - iat` greater than the issuer's published max TTL.
- Sync server clocks via NTP; refuse to start on bad clock signals.

### Bypass 5 — Replay across audiences

You issued one token for `fortress.client`. Internal service `fortress.admin` shares the verifier
config and forgot to check `aud`. Now a stolen client token mints admin actions.

**Counter:**
- Every verifying service validates `aud` against its own service identity.
- Don't share verifier code that omits this; put it in a hard-to-bypass middleware.

### Bypass 6 — Snatch tokens from the device

If `TokenStore` wrote tokens to plaintext SharedPreferences, I take a backup (`adb backup`),
exfiltrate, and read them. If `allowBackup="true"` in the manifest, your tokens leave the device.

**Counter:**
- This repo: `android:allowBackup="false"` (see [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml))
- Tokens encrypted with a non-exportable Keystore AES-GCM key. Even with root + filesystem
  access, I get ciphertext.
- See [02-hardware-vault.md](02-hardware-vault.md) for the storage threat model.

### Bypass 7 — Algorithm confusion attack

I send a JWT with `"alg": "none"` or with `"alg": "HS256"` when you expected `RS256`. A naive
library accepts either, and now my unsigned token verifies.

**Counter:**
- Pin the algorithm explicitly when calling `jwtVerify`. This repo:
  `await jwtVerify(token, SECRET, { issuer, audience })` — `jose` infers `alg` from the secret
  type, not the header.
- Test the verifier against a curated set of bad tokens (`alg: none`, mismatched type, missing
  claims).

---

## Cross-reference

- **The vault that protects the tokens at rest** → [02-hardware-vault.md](02-hardware-vault.md)
- **Single-flight refresh on 401** → [03-interceptor-pattern.md](03-interceptor-pattern.md)
- **Refresh lifecycle, rotation, and the reuse-detection alert** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Why you still bind step-up actions to a biometric** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Device binding via `cnf` claim** → [09-zero-trust.md](09-zero-trust.md)

## References

- [Part 1 — The Stateless Blueprint: Scaling Android Auth for 5M Users](https://blog.stackademic.com/part-1-the-stateless-blueprint-scaling-android-auth-for-5m-users-56f10ed652a5)
- [RFC 7519 — JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
- [RFC 8725 — JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725)
- [OWASP — JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
