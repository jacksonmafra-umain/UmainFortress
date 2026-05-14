import { Router } from "express";
import argon2 from "argon2";
import crypto from "node:crypto";
import { refreshTokens, users, type RefreshTokenRecord } from "../db/seed.js";
import {
  ACCESS_TOKEN_TTL_SEC,
  generateRefreshToken,
  hashRefreshToken,
  issueAccessToken,
} from "../utils/jwt.js";

const router = Router();

router.post("/login", async (req, res) => {
  const { email, password, deviceId } = req.body ?? {};
  if (!email || !password || !deviceId) {
    res.status(400).json({ code: "BAD_REQUEST", message: "email, password, deviceId required" });
    return;
  }
  const user = await users.find((u) => u.email.toLowerCase() === String(email).toLowerCase());
  if (!user) {
    res.status(401).json({ code: "INVALID_CREDENTIALS", message: "Invalid email or password" });
    return;
  }
  const ok = await argon2.verify(user.passwordHash, password);
  if (!ok) {
    res.status(401).json({ code: "INVALID_CREDENTIALS", message: "Invalid email or password" });
    return;
  }

  const access = await issueAccessToken({
    sub: user.id,
    email: user.email,
    displayName: user.displayName,
  });
  const refresh = generateRefreshToken();
  const record: RefreshTokenRecord = {
    id: crypto.randomUUID(),
    userId: user.id,
    tokenHash: refresh.hash,
    deviceId,
    issuedAtEpochMs: Date.now(),
    expiresAtEpochMs: refresh.expiresAtEpochMs,
    revoked: false,
  };
  await refreshTokens.upsert(record, "id");

  res.json({
    accessToken: access.token,
    refreshToken: refresh.raw,
    accessExpiresAtEpochMs: access.expiresAtEpochMs,
    user: { id: user.id, email: user.email, displayName: user.displayName },
  });
});

router.post("/refresh", async (req, res) => {
  const { refreshToken, deviceId } = req.body ?? {};
  if (!refreshToken || !deviceId) {
    res.status(400).json({ code: "BAD_REQUEST", message: "refreshToken, deviceId required" });
    return;
  }
  const tokenHash = hashRefreshToken(String(refreshToken));
  const record = await refreshTokens.find((rt) => rt.tokenHash === tokenHash);
  const now = Date.now();
  if (!record || record.revoked || record.expiresAtEpochMs < now || record.deviceId !== deviceId) {
    res.status(401).json({ code: "REFRESH_REJECTED", message: "Refresh token rejected" });
    return;
  }
  const user = await users.find((u) => u.id === record.userId);
  if (!user) {
    res.status(401).json({ code: "USER_NOT_FOUND", message: "User no longer exists" });
    return;
  }

  // Rotate the refresh token (revoke old, issue new) — limits the blast radius of a leak.
  await refreshTokens.upsert({ ...record, revoked: true }, "id");

  const access = await issueAccessToken({
    sub: user.id,
    email: user.email,
    displayName: user.displayName,
  });
  const fresh = generateRefreshToken();
  await refreshTokens.upsert(
    {
      id: crypto.randomUUID(),
      userId: user.id,
      tokenHash: fresh.hash,
      deviceId,
      issuedAtEpochMs: now,
      expiresAtEpochMs: fresh.expiresAtEpochMs,
      revoked: false,
    },
    "id",
  );

  res.json({
    accessToken: access.token,
    refreshToken: fresh.raw,
    accessExpiresAtEpochMs: access.expiresAtEpochMs,
    user: { id: user.id, email: user.email, displayName: user.displayName },
  });
});

router.post("/logout", async (req, res) => {
  const { refreshToken } = req.body ?? {};
  if (refreshToken) {
    const tokenHash = hashRefreshToken(String(refreshToken));
    const record = await refreshTokens.find((rt) => rt.tokenHash === tokenHash);
    if (record) await refreshTokens.upsert({ ...record, revoked: true }, "id");
  }
  res.json({ ok: true });
});

// Surfaces the configured TTLs to clients that want to schedule proactive refresh.
router.get("/policy", (_req, res) => {
  res.json({ accessTokenTtlSeconds: ACCESS_TOKEN_TTL_SEC });
});

export default router;
