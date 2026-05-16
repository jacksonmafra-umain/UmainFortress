---
title: "Token lifecycle — rotation, revocation, reuse detection"
slug: token-lifecycle
level: intermediate
estimated_minutes: 25
status: published
company: Fortress
tags:
  - tokens
  - rotation
  - revocation
  - fraud
summary: >
  Run refresh-token rotation on every use, treat reuse as the canonical fraud signal,
  revoke the entire family on misuse, and design the operational story (logout-everywhere,
  password change, lost device) so it survives real attacker behaviour.
references:
  - title: "Token lifecycle (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/06-token-lifecycle.md
  - title: "OAuth 2.0 Refresh-Token Rotation"
    url: https://www.rfc-editor.org/rfc/rfc6819#section-5.2.2.3
  - title: "Auth0 — Refresh Token Rotation"
    url: https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation
---

## Welcome to token lifecycle

Understand the lifetimes, transitions, and operational obligations of every token your
auth service mints — and why "rotate refresh tokens" is the single highest-leverage
control your backend can implement.

The Stateless Auth codelab introduced refresh-token rotation. This codelab turns it into
a complete operational discipline: how to model the data, how to detect reuse, how to
revoke cleanly, and how to handle the awkward events (password change, lost device,
admin force-logout) that production hands you.

> **Why this matters.** A leaked long-lived bearer token is a session takeover that lasts
> until the user uninstalls the app. Rotation + reuse detection turns the same leak into
> a one-call self-defeat.

---

## Step 1: Model the refresh-token record

Persist a hash, not the token. Track lineage. Carry enough metadata to revoke families
without scanning the whole table.

```ts
interface RefreshTokenRecord {
  id: string;                     // uuid, primary key
  userId: string;
  deviceId: string;               // (userId, deviceId) for bulk-revoke-per-device
  tokenHash: string;              // SHA-256(token), hex
  family: string;                 // shared across the rotation chain
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  revoked: boolean;
  revokedReason?: "rotated" | "reused" | "logout" | "passwordChanged" | "adminForce";
  replacedBy?: string;            // id of the next record in this family
}
```

Two indexes: `(tokenHash)` for lookup on refresh, `(userId, family)` for family-wide
revocation. Skip the lineage and you cannot revoke a chain in one query.

> **Why this matters.** Production token tables grow to hundreds of millions of rows.
> Schema mistakes here become "rewrite at 2am" later.

---

## Step 2: Mint a refresh token correctly

CSPRNG, base64url, paired with a fresh family id when the credential is new (e.g. fresh
login), reusing the parent's family id when rotating.

```ts
import crypto from "node:crypto";

function sha256Hex(s: string): string {
  return crypto.createHash("sha256").update(s).digest("hex");
}

async function mintRefresh(userId: string, deviceId: string, family?: string) {
  const token = crypto.randomBytes(32).toString("base64url");
  const record: RefreshTokenRecord = {
    id: crypto.randomUUID(),
    userId, deviceId,
    tokenHash: sha256Hex(token),
    family: family ?? crypto.randomUUID(),
    issuedAtEpochMs: Date.now(),
    expiresAtEpochMs: Date.now() + 30 * 24 * 3600 * 1000,
    revoked: false,
  };
  await refreshTokens.upsert(record, "id");
  return { token, record };
}
```

The token returned is the only time the plaintext is held on the server; immediately
after, only its hash survives.

> **Why this matters.** A database breach with hashed tokens hands the attacker nothing
> usable. A breach with plaintext tokens hands them every active session.

---

## Step 3: The rotation transition

Every successful `/auth/refresh` call atomically:

1. Verifies the presented token (hash match, not revoked, not expired).
2. Mints a new (access, refresh) pair under the same `family`.
3. Marks the old record `revoked = true`, `revokedReason = "rotated"`,
   `replacedBy = newId`.
4. Returns the new pair.

Atomicity matters: if the rotation fails halfway, you cannot leave the old token usable
*and* the new token issued. Use a transaction or compensating delete.

```ts
await db.transaction(async (tx) => {
  const next = await mintRefresh(record.userId, record.deviceId, record.family);
  await tx.refreshTokens.upsert(
    { ...record, revoked: true, revokedReason: "rotated", replacedBy: next.record.id },
    "id",
  );
});
```

> **Why this matters.** A non-atomic rotation creates a window where two refresh tokens
> work simultaneously, which defeats reuse detection.

---

## Step 4: Reuse detection — the canonical fraud signal

If a refresh token is presented *after* its `replacedBy` is set, the legitimate client
has the newer one. The presenter does not. Treat it as a leak: revoke the entire family,
log to telemetry, return 401.

```ts
if (record.replacedBy) {
  await refreshTokens.updateMany(
    { family: record.family, revoked: false },
    { revoked: true, revokedReason: "reused" },
  );
  await telemetry.refreshReuse({ userId: record.userId, family: record.family });
  return res.status(401).json({ code: "REFRESH_REUSED" });
}
```

The legitimate client will hit 401 on its next refresh and re-authenticate fresh. The
attacker also hits 401. Neither can tell which one of them was the legitimate client —
that asymmetry is the point.

> **Why this matters.** This single check is the difference between "refresh tokens are
> safer than session cookies" and "refresh tokens are session cookies with a different
> name".

---

## Step 5: Server-side telemetry on the reuse signal

A reuse event on a single user is almost certainly a leaked token. A reuse spike across
hundreds of users is a compromise of your storage layer. Aggregate at both levels.

```ts
async function refreshReuse(evt: { userId: string; family: string }) {
  metrics.increment("auth.refresh.reused", { region: process.env.REGION ?? "unknown" });
  if (await reuseRate.exceeded("global", 100, "1h")) {
    await pagerDuty.fire("refresh-token-mass-reuse");
  }
  if (await reuseRate.exceeded(evt.userId, 3, "24h")) {
    await actions.lockUser(evt.userId, "Repeated refresh-token reuse");
  }
}
```

Lock-user thresholds are policy. Three reuse events in 24h on one account is a strong
signal; one reuse event is "maybe an old phone they forgot to sign out".

> **Why this matters.** The reuse signal is only valuable if someone is watching for it.
> Bake the alerts into the same release.

---

## Step 6: Logout and explicit revocation

Three distinct logout types. Each has different scope.

```ts
// Single device — current session only.
router.post("/auth/logout", requireAuth, async (req, res) => {
  const { refreshToken } = req.body;
  await refreshTokens.updateOne(
    { tokenHash: sha256Hex(refreshToken), userId: req.claims.sub },
    { revoked: true, revokedReason: "logout" },
  );
  res.status(204).end();
});

// All devices for this user — "sign out everywhere".
router.post("/auth/logout-all", requireAuth, async (req, res) => {
  await refreshTokens.updateMany(
    { userId: req.claims.sub, revoked: false },
    { revoked: true, revokedReason: "logout" },
  );
  res.status(204).end();
});

// Bulk-revoke a single device family — used when a device is reported lost.
router.post("/auth/logout-device/:deviceId", requireAuth, async (req, res) => {
  await refreshTokens.updateMany(
    { userId: req.claims.sub, deviceId: req.params.deviceId, revoked: false },
    { revoked: true, revokedReason: "logout" },
  );
  res.status(204).end();
});
```

The third one is what the user reaches for after they lose their phone. The Security
Center screen in your app should expose it explicitly.

> **Why this matters.** "Lost device" is the support call you do not want to scramble
> for at 11pm. Ship the endpoint upfront.

---

## Step 7: Password-change revocation

When a user changes their password, every refresh token for that user — including the
one belonging to the device that did the change — is suspect. Revoke and re-issue.

```ts
router.post("/me/password", requireAuth, async (req, res) => {
  await users.upsert({ ...currentUser, passwordHash: await argon2Hash(req.body.newPassword) }, "id");
  await refreshTokens.updateMany(
    { userId: currentUser.id, revoked: false },
    { revoked: true, revokedReason: "passwordChanged" },
  );
  const { token: newRefresh } = await mintRefresh(currentUser.id, req.deviceId);
  const access = await mintAccessToken(currentUser.id);
  res.json({ accessToken: access, refreshToken: newRefresh });
});
```

Issue a fresh pair for the current device so the user does not get bounced to login on
the screen where they just changed their password.

> **Why this matters.** Password-change is the canonical "I think I was compromised"
> action. Anything less than full revoke + reissue undermines the user's intent.

---

## Step 8: Admin force-logout for the support desk

When fraud reports a specific account as compromised, support should be able to revoke
every active session for that user with one click.

```ts
router.post("/admin/users/:userId/force-logout", requireAdmin, async (req, res) => {
  await refreshTokens.updateMany(
    { userId: req.params.userId, revoked: false },
    { revoked: true, revokedReason: "adminForce" },
  );
  await auditLog.write("admin.forceLogout", {
    targetUserId: req.params.userId, actorId: req.claims.sub,
  });
  res.status(204).end();
});
```

Always audit-log the admin action with the actor and the target. Regulators ask.

> **Why this matters.** The day fraud calls you to revoke a session, knowing where the
> button is is the difference between a five-minute call and a thirty-minute incident.

---

## Step 9: Expiry sweep and retention

Refresh tokens have a 30-day TTL. The records stay in the table after expiry for audit
purposes, but they should be pruned eventually.

```ts
async function pruneStaleRefreshTokens() {
  const cutoff = Date.now() - 90 * 24 * 3600 * 1000; // 90 days post-expiry
  await refreshTokens.deleteMany({
    expiresAtEpochMs: { $lt: cutoff },
    revoked: true,
  });
}

setInterval(pruneStaleRefreshTokens, 24 * 3600 * 1000);
```

Keep revoked-but-recent records (under 90 days) — they are the trail of evidence for
the next reuse event. Prune only the cold ones.

> **Why this matters.** An unbounded refresh-token table is an operational liability.
> A pruning policy keeps both costs and audit scope predictable.

---

## Step 10: Test the lifecycle as a black box

Eight scenarios. Pass them all before you ship.

1. **Happy refresh.** Old token → 401 on re-use. New token works once, then becomes the
   parent.
2. **Reused refresh.** Present old token twice → second call gets `REFRESH_REUSED`,
   family revoked.
3. **Expired refresh.** Wait past TTL → 401 with `REFRESH_INVALID`.
4. **Logout single.** `/auth/logout` → current refresh dead, others alive.
5. **Logout all.** `/auth/logout-all` → every device for the user re-authenticates.
6. **Password change.** `/me/password` → all sessions revoked except the changing one,
   which is re-issued.
7. **Lost device.** `/auth/logout-device/:id` → only that device's family revoked.
8. **Admin force-logout.** Support flow → audit log carries actor + target.

If any one fails, fix that before shipping. Mature token lifecycle is the difference
between a fintech that can recover from incidents and one that cannot.

> **Why this matters.** The lifecycle code path is exercised constantly; the *recovery*
> paths are exercised rarely. The tests are how you remember the recovery paths exist.

---

## Wrap-Up

You now own a complete refresh-token lifecycle: minting, rotation, reuse detection,
three logout flavours, password-change handling, admin force-logout, retention, and a
black-box test plan.

Next mission:
- [Stateless Auth Blueprint](/codelabs/stateless-auth-blueprint) for the mint side of
  this lifecycle.
- [Interceptor Pattern](/codelabs/interceptor-pattern) for the client-side single-flight
  refresh that consumes this server.
- [Zero Trust](/codelabs/zero-trust) (draft) for `cnf`-claim binding that pairs a
  refresh token to a specific device's Keystore signature.

**Recap of what you just built:**

- A refresh-token schema with `family` + `replacedBy` lineage.
- Atomic rotation on every refresh.
- A reuse-detection branch that revokes the entire family.
- Telemetry + auto-actions that turn the reuse signal into an alert.
- Three logout endpoints (current device, all devices, named device).
- Password-change revocation that re-issues a fresh pair for the changing device.
- Admin force-logout with audit logging.
- A 90-day prune policy and an 8-scenario lifecycle test plan.
