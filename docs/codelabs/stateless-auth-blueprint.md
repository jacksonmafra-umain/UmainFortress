---
title: "Stateless auth blueprint"
slug: stateless-auth-blueprint
level: intermediate
estimated_minutes: 30
status: published
company: Fortress
tags:
  - auth
  - jwt
  - session
  - refresh
summary: >
  Design a JWT-based stateless auth flow that scales — asymmetric signing, refresh-token
  rotation, short access-token lifetime, claim hygiene — and understand the trade-offs
  against a stateful session table.
references:
  - title: "Stateless auth blueprint (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/01-stateless-auth.md
  - title: "RFC 7519 — JSON Web Token"
    url: https://datatracker.ietf.org/doc/html/rfc7519
  - title: "jose — JavaScript Object Signing & Encryption (npm)"
    url: https://github.com/panva/jose
---

## Welcome to stateless auth

Understand why a fintech demo would pick JWTs over a stateful session table and where the
trade-offs land.

A stateless auth flow trades one expensive lookup (the session row in your auth database)
for a tiny CPU cost (verify a signature). It scales horizontally without coordination — any
service instance can validate a token without consulting a shared store. The catch: every
trade-off lives on the *revocation* side. We cover both.

> **Why this matters.** "JWT is bad" is internet folklore born from people who treated
> access tokens like session cookies. Stateless auth done right is fast, simple, and
> auditable. Done wrong it is a long-lived bearer-token leak with no kill switch.

---

## Step 1: Pick the right algorithm

Asymmetric (`ES256` / `EdDSA`) for access tokens. The auth service holds the private key;
every other service holds the public key. Symmetric (`HS256`) only when one process both
mints and verifies, which is rare.

```ts
import { generateKeyPair, SignJWT } from "jose";

const { privateKey, publicKey } = await generateKeyPair("ES256");
// Persist the private key in your secrets manager. Distribute the public key as a JWK
// set at /.well-known/jwks.json so verifiers can rotate without redeploying.
```

> **Why this matters.** Asymmetric signing means a leak of any verifier cannot mint
> tokens. With `HS256` every service that verifies can forge.

---

## Step 2: Decide on a claim set

Claims are pragma, not aesthetics. Pick a minimal, fixed shape and freeze it.

```ts
interface FortressAccessClaims {
  iss: "fortress.auth";    // issuer
  sub: string;             // user id
  aud: "fortress.api";     // audience
  iat: number;             // issued-at, seconds
  exp: number;             // expiry, seconds
  jti: string;             // unique token id, for revocation lists
  cnf?: { kid: string };   // device-binding confirmation, see Zero Trust codelab
}
```

Resist the urge to dump profile data, roles, or permissions into the token. Roles change.
Tokens are cached. Stale roles in a five-minute access window are how privilege escalations
sneak in. Keep tokens to identity only; query authorisation at the API.

> **Why this matters.** Every claim you add is a footgun the day it changes. The smallest
> useful payload survives roadmap shifts the longest.

---

## Step 3: Mint the access token

Short-lived (5–15 minutes) so revocation surface is bounded by lifetime alone.

```ts
async function mintAccessToken(userId: string, deviceKid?: string) {
  return await new SignJWT({ cnf: deviceKid ? { kid: deviceKid } : undefined })
    .setProtectedHeader({ alg: "ES256", kid: ACTIVE_KEY_ID })
    .setIssuer("fortress.auth")
    .setSubject(userId)
    .setAudience("fortress.api")
    .setIssuedAt()
    .setExpirationTime("10m")
    .setJti(crypto.randomUUID())
    .sign(privateKey);
}
```

Five minutes is fine when paired with a working refresh flow. Fifteen is the upper bound
before the security/UX trade-off tilts.

> **Why this matters.** A leaked access token is bounded by its TTL. Long tokens make
> recovery from compromise expensive; short ones keep the blast radius cheap.

---

## Step 4: Mint and rotate the refresh token

The refresh token is the long-lived credential. Treat it like one. Persist a hash (not the
token itself) and rotate on every use.

```ts
interface RefreshTokenRecord {
  id: string;            // uuid, primary key
  userId: string;
  tokenHash: string;     // SHA-256(token), hex
  deviceId: string;
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  revoked: boolean;
  replacedBy?: string;   // id of the next record, for reuse detection (see Step 8)
}
```

Refresh TTL: 30 days is reasonable for mobile. Track family lineage via `replacedBy` so a
single leaked token tells you the entire chain to invalidate. Hash storage means a database
breach does not yield usable tokens.

> **Why this matters.** Refresh-token leaks are the canonical persistent-session compromise.
> Hashed storage plus rotation makes the leaked artefact useless within the next call.

---

## Step 5: Verify on the API path

Every protected route verifies the access token. With `jose` and a JWKS endpoint the
verifier never sees the private key.

```ts
import { createRemoteJWKSet, jwtVerify } from "jose";

const JWKS = createRemoteJWKSet(new URL("https://auth.fortress.dev/.well-known/jwks.json"));

export async function requireAuth(req, res, next) {
  const header = req.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return res.status(401).end();
  try {
    const { payload } = await jwtVerify(header.slice(7), JWKS, {
      issuer: "fortress.auth",
      audience: "fortress.api",
    });
    req.claims = payload;
    next();
  } catch (err) {
    res.status(401).json({ code: "TOKEN_INVALID" });
  }
}
```

The JWKS endpoint caches public keys with `Cache-Control` headers. Key rotation becomes a
zero-downtime operation: publish the new key, wait for caches to refresh, then start
signing with it.

> **Why this matters.** Rotation has to be boring. Anything that requires a coordinated
> redeploy across services is something an on-call engineer will avoid until it's an
> incident.

---

## Step 6: Client persists tokens under the right lock

On the device, the access token can live in process memory only — it is short-lived enough
that re-issuing on cold start is acceptable. The refresh token has to survive process
death and must therefore go behind the Android Keystore. See the Hardware Vault codelab
for the long form; the contract is short:

```kotlin
interface TokenStore {
  suspend fun saveRefresh(token: String)
  suspend fun loadRefresh(): String?
  suspend fun clear()
}
```

Backing implementation: AES-256-GCM with a key in `AndroidKeyStore`, key bound to user
authentication for high-stakes flows.

> **Why this matters.** A refresh token in SharedPreferences is a refresh token in plain
> text. adb backup, root, or a lost device hands every account to the next person who
> touches the device.

---

## Step 7: Refresh flow — happy path

Single endpoint. Client posts the current refresh token; server verifies, mints a new
access + refresh pair, marks the old refresh as `replacedBy = newId`.

```ts
router.post("/auth/refresh", async (req, res) => {
  const { refreshToken } = req.body;
  const hash = sha256Hex(refreshToken);
  const record = await refreshTokens.findOne({ tokenHash: hash });
  if (!record || record.revoked || record.expiresAtEpochMs < Date.now()) {
    return res.status(401).json({ code: "REFRESH_INVALID" });
  }
  const nextRefresh = crypto.randomBytes(32).toString("base64url");
  const nextRecord = {
    id: crypto.randomUUID(),
    userId: record.userId,
    tokenHash: sha256Hex(nextRefresh),
    deviceId: record.deviceId,
    issuedAtEpochMs: Date.now(),
    expiresAtEpochMs: Date.now() + 30 * 24 * 3600 * 1000,
    revoked: false,
  };
  await refreshTokens.upsert(nextRecord, "id");
  await refreshTokens.upsert({ ...record, replacedBy: nextRecord.id, revoked: true }, "id");

  const accessToken = await mintAccessToken(record.userId);
  res.json({ accessToken, refreshToken: nextRefresh });
});
```

> **Why this matters.** Every successful refresh hands the client a brand new pair. The
> server keeps a linear chain of who-replaced-whom, which becomes the bedrock of reuse
> detection in the next step.

---

## Step 8: Refresh-token reuse detection

If a refresh token is presented after it was already replaced, that is almost certainly a
leak. The legitimate client has the newer token; the presenter does not. The right reaction
is to revoke the entire chain and force the user to sign back in.

```ts
if (record.replacedBy) {
  await revokeFamily(record.id); // walk replacedBy graph, revoke every descendant
  return res.status(401).json({ code: "REFRESH_REUSED" });
}
```

The legitimate client will see a `401` on its next refresh. The illegitimate client got
the same. The user re-authenticates fresh; the attacker is locked out without ever knowing
what happened.

> **Why this matters.** Reuse detection is the canonical fraud signal for stateless auth.
> It converts a "long-lived bearer token" into something that self-destructs the moment it
> gets stolen.

---

## Step 9: Logout and explicit revocation

A real logout endpoint marks the current refresh-token record `revoked = true`. A bulk
logout (password change, security-center sign-out-everywhere) revokes every refresh for
the user.

```ts
router.post("/auth/logout", requireAuth, async (req, res) => {
  await refreshTokens.updateMany(
    { userId: req.claims.sub, revoked: false },
    { revoked: true },
  );
  res.status(204).end();
});
```

Access tokens are not revocable individually — that is the trade-off — but a 10-minute TTL
keeps the worst-case lifetime tolerable.

> **Why this matters.** Users expect "Sign out everywhere" to mean what it says. Without
> bulk revoke you cannot deliver it.

---

## Step 10: Test the failure modes

Before shipping, hammer every edge:

- Expired access token → 401, client refreshes, retries, succeeds.
- Expired refresh token → 401, client clears state, lands on Login.
- Revoked refresh (logout) → 401, same.
- Reused refresh → 401, family revoked, every device with that chain re-signs in.
- Two simultaneous refresh requests from the same client (race) → both succeed only if the
  server is idempotent on `replacedBy`; otherwise one gets `REFRESH_REUSED`. See the
  Interceptor Pattern codelab for the client-side single-flight protection.

> **Why this matters.** Auth bugs only show up at scale. A single-user demo will pass tests
> a thousand concurrent sessions will fail.

---

## Wrap-Up

You now have a defensible stateless-auth blueprint: short access tokens, rotating refresh
tokens, hashed storage, reuse detection as the canonical fraud signal, and a clean way to
revoke when needed.

Next mission: walk the
[Interceptor Pattern codelab](/codelabs/interceptor-pattern) for the client-side single-
flight refresh that pairs with this server design, then
[Hardware Vault](/codelabs/hardware-vault) to lock the refresh token to the Keystore.

**Recap of what you just built:**

- An asymmetric (`ES256`) signing key with public verification via JWKS.
- A minimal claim set bound by issuer/audience and identity only.
- A 10-minute access token + 30-day rotating refresh token.
- A reuse-detection check that revokes the entire family on misuse.
- Bulk and individual logout that mean what they say.
