# 06 — Token Lifecycle: Rotation, Revocation, Reuse Detection

> "A long-lived token is a contract you signed with your future self that you'd rather not have
> to honour. A rotating token is a contract you rewrite on every interaction." — *Fortress field notes*

**TL;DR** — Refresh tokens rotate on every use, are stored hashed (SHA-256) at rest, and the
server tracks a lineage so refresh-token **reuse** is the canonical signal that something was
stolen. Reuse → revoke the entire family → kick all devices for that lineage → tell fraud. The
server endpoints in [`backend/src/routes/auth.ts`](../backend/src/routes/auth.ts) wire this up.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | One refresh, one use; reuse means compromise | Steal once, refresh forever |
| **Key idea** | Rotation is cheap; the cost is borne by the legitimate user once per session | If the server forgets the rotation lineage, a stolen refresh is forever |
| **Worst failure** | Static refresh tokens, no reuse detection, no rotation, no revocation | Single token endpoint with no rate limit, no device binding |

---

## 🛡️ Defender — "Every refresh is a contract renegotiation"

### State per (user × device)

The minimum metadata for a token-family lineage:

```ts
interface RefreshTokenRecord {
  id: string;              // unique ID for this single token
  userId: string;          // who this token belongs to
  tokenHash: string;       // SHA-256 of the raw token bytes (never the raw token!)
  deviceId: string;        // bound at issuance, validated on every refresh
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  revoked: boolean;        // explicit revocation flag
}
```

Production should add:

- `parentId` — the token this rotation replaces. Lets the server walk the lineage.
- `familyId` — same across all tokens that descend from one login. Lets you revoke the family in one query.
- `userAgent` / `clientVersion` — risk signals.
- `ipFingerprint` — for outlier detection (note: not the raw IP, just a stable hash, GDPR-friendly).

### The rotation flow

[`backend/src/routes/auth.ts:POST /auth/refresh`](../backend/src/routes/auth.ts) does:

```
1. hash(rawRefreshToken) → look up the record
2. if !record OR revoked OR expired OR deviceId mismatch → 401
3. revoke the record (set revoked=true)
4. mint new access + new refresh token
5. persist new refresh-token record (same userId, same deviceId)
6. return the new pair
```

The legitimate client receives the pair. The server has now revoked the old token; any *future*
attempt to use the old refresh fails. That's the property reuse detection relies on.

### Reuse detection

Two refreshes within seconds of each other, using the *same* refresh token: at least one is an
attacker. The server should:

1. Refuse both — the second clearly, but the first too if it's still alive when the second
   arrives.
2. Revoke the entire family (every token descended from this login).
3. Increment risk score for this user × device.
4. Notify the legitimate user out-of-band (push, email): "You were signed out because something
   unusual happened. If this wasn't you, change your password and review recent activity."

The naive implementation just revokes on use and returns 401 to the second caller. The robust
one revokes the *family*. The demo backend rotates and revokes the single record; production
should add `familyId` and a deny-list-by-family.

### Why hash the refresh token?

If the database leaks, an attacker with the dump of `refresh_tokens.json` should get nothing
they can hand to `/auth/refresh`. SHA-256 of a 384-bit-entropy random string is preimage-resistant
in the practical sense. The attacker can verify whether a token they *already* have is in the
table, but cannot manufacture a usable one.

This is exactly why we do **not** use bcrypt/argon2 here: the entropy is already maximal, the
slow hash buys nothing, and refresh latency matters.

### Access-token TTL choice

| TTL | Pros | Cons |
|---|---|---|
| 1 min | Tiny replay window | Refresh storm; battery / data cost |
| 5 min | Small replay window | Frequent refreshes |
| 15 min (this repo) | Balanced for mobile | Reasonable replay window |
| 60 min | Cheap refreshes | Stolen access token works for 1h |
| 24 h | "Stateless" in practice | Stolen access token is a long-lived credential |

15 min is the modern fintech default. For the highest-value actions, layer biometric step-up on
top so the access token alone never authorises money movement ([07-biometric-hardening.md](07-biometric-hardening.md)).

### Revocation strategies

Pure stateless verification cannot revoke an unexpired access token. Three patterns:

1. **Short TTL** — wait it out. This repo's primary mechanism. 15 minutes worst case.
2. **Deny-list by `jti`** — verifying services consult a small Redis set keyed by token ID. Cheap
   to read, written rarely (only on explicit revocation). Sub-millisecond latency if the set is
   in-memory at each service.
3. **Online check-on-sensitive-actions** — call the issuer to validate currency of the session
   only when performing the money-moving action. Adds a network hop where it matters most.

The default is (1) + (2). Use (3) for transfers >€1000, password changes, payee additions.

### Logout flow

[`POST /auth/logout`](../backend/src/routes/auth.ts):

```
1. hash(refreshToken) → look up record
2. mark revoked=true
3. return ok
```

No access-token blacklisting needed if you use a short TTL. The access token already in flight
will expire in <= 15 min and the user will not be able to refresh.

Client side (this repo): [`SessionManager.clear()`](../app/src/main/java/com/umain/fortress/auth/SessionManager.kt)
removes the encrypted blob from DataStore. The Keystore key remains (will be reused on next
login).

---

## ⚔️ Attacker — "I covet your long-lived secrets"

### Bypass 1 — Steal a refresh, refresh forever

Without rotation: I make one refresh per access-token expiry, indefinitely. The server has no
way to tell my refresh from the user's.

**Counter:** rotation. My refresh works once; the user's next refresh fails (because I revoked
their lineage by using it before them). The user gets booted; they re-login; my stolen refresh
is now revoked and useless.

### Bypass 2 — Steal a refresh, race the user

Rotation alone isn't enough. If I refresh *before* the user, I get the new pair and they get
locked out. Then I drift the lineage with my own refreshes, and as long as I'm the one who used
the latest refresh, the server gives me a valid pair on demand. The user is signed out and
suspects nothing technical — they think their password expired or something.

**Counter:**
- Reuse detection by **family**. If the user's app then tries to refresh and presents a sibling
  in the lineage, the server says "this token belongs to a family I've watched bifurcate" and
  revokes the family.
- Out-of-band notification on family revocation.
- Risk engine flags "new device pattern, new IP block, new user-agent within minutes of
  refresh" as anomalous.

### Bypass 3 — Bind-jump

I steal the refresh and somehow also spoof or capture the `deviceId`. If the deviceId is the
only binding, I'm in.

**Counter:**
- Bind to a hardware-backed public key as well: the refresh is only valid when accompanied by a
  signature from the device-binding key in [`BiometricKeyStore`](../app/src/main/java/com/umain/fortress/security/BiometricKeyStore.kt).
- Then I'd need the device's TEE-resident private key — impractical without physical access.

### Bypass 4 — Long access TTL with no revocation

If you set access TTL to 24h and rely entirely on refresh revocation, a stolen access token is
a 24-hour credential. By the time the user signs out, the attacker has done the damage.

**Counter:**
- Short access TTL.
- For sensitive actions: online check at the issuer (pattern 3 above) regardless of TTL.

### Bypass 5 — Forge by signing-key compromise

If the HMAC secret leaks (env-var dump, log line, GitHub commit), I forge any access token I
want — no refresh needed. See [01-stateless-auth.md](01-stateless-auth.md) for the asymmetric-key
mitigation.

### Bypass 6 — Sneak past device-binding check

If the server doesn't validate `deviceId` on the refresh (just on login), I can steal a refresh
on device A and use it from device B, no questions asked.

**Counter:** the demo does check `record.deviceId !== deviceId`. Production: also verify a
signed nonce from the device-binding key on every refresh, not just login. (See [09-zero-trust.md](09-zero-trust.md).)

### Bypass 7 — Refresh-only DoS

If `/auth/refresh` has no rate limit, I hammer it with stolen tokens to keep them "alive"
indefinitely, or to brute-force a misconfigured server that returns useful 401-vs-403 distinctions.

**Counter:**
- Rate limit per IP, per user, per device — tight on `/auth/refresh`.
- Constant-time response shape (no leaking "wrong device" vs "expired" vs "not found").

---

## Cross-reference

- **The blueprint these tokens implement** → [01-stateless-auth.md](01-stateless-auth.md)
- **The interceptor that orchestrates the client-side rotation** → [03-interceptor-pattern.md](03-interceptor-pattern.md)
- **The vault that protects the refresh token at rest on the client** → [02-hardware-vault.md](02-hardware-vault.md)
- **Device binding to make a stolen refresh useless** → [09-zero-trust.md](09-zero-trust.md)

## References

- [Part 6 — The Interceptor Pattern: Mastering the Token Lifecycle](https://blog.stackademic.com/part-6-the-interceptor-pattern-mastering-the-token-lifecycle-83afa94b8dd0)
- [OAuth 2.0 — Refresh Token Rotation](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-22#section-4.13.2)
- [Auth0 — Refresh Token Rotation and Reuse Detection](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation)
