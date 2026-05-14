import { Router } from "express";
import { deviceBindings, refreshTokens } from "../db/seed.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

router.use(requireAuth);

/**
 * Lists the device-binding records for the current user. Each row is the public-key /
 * deviceId pair the server uses to verify step-up signatures.
 */
router.get("/devices", async (req, res) => {
  const userId = req.claims!.sub;
  const devices = (await deviceBindings.all())
    .filter((d) => d.userId === userId)
    .sort((a, b) => b.updatedAtEpochMs - a.updatedAtEpochMs);
  res.json({
    devices: devices.map((d) => ({
      id: d.id,
      deviceId: d.deviceId,
      createdAtEpochMs: d.createdAtEpochMs,
      updatedAtEpochMs: d.updatedAtEpochMs,
    })),
  });
});

/**
 * Lists active (non-revoked, non-expired) refresh tokens for the current user, used to show
 * "where you're signed in" in the Security Center.
 */
router.get("/sessions", async (req, res) => {
  const userId = req.claims!.sub;
  const now = Date.now();
  const active = (await refreshTokens.all())
    .filter((r) => r.userId === userId && !r.revoked && r.expiresAtEpochMs > now)
    .sort((a, b) => b.issuedAtEpochMs - a.issuedAtEpochMs);
  res.json({
    sessions: active.map((r) => ({
      id: r.id,
      deviceId: r.deviceId,
      issuedAtEpochMs: r.issuedAtEpochMs,
      expiresAtEpochMs: r.expiresAtEpochMs,
    })),
  });
});

/**
 * Revokes a single device binding plus every refresh token tied to that deviceId. The next
 * step-up attempt from that device will fail with "no binding", and any cached access token
 * stops being refreshable.
 */
router.delete("/devices/:bindingId", async (req, res) => {
  const userId = req.claims!.sub;
  const target = await deviceBindings.find(
    (d) => d.id === req.params.bindingId && d.userId === userId,
  );
  if (!target) {
    res.status(404).json({ code: "NOT_FOUND", message: "Device binding not found" });
    return;
  }
  await deviceBindings.remove((d) => d.id === target.id);
  // Cascade: revoke all refresh tokens issued to that deviceId.
  const all = await refreshTokens.all();
  await refreshTokens.replace(
    all.map((r) =>
      r.userId === userId && r.deviceId === target.deviceId && !r.revoked
        ? { ...r, revoked: true }
        : r,
    ),
  );
  res.json({ ok: true });
});

/**
 * Revokes every active refresh token for the current user. Existing access tokens continue
 * to be valid until their short TTL expires; no future refresh is possible.
 */
router.post("/sign-out-all", async (req, res) => {
  const userId = req.claims!.sub;
  const all = await refreshTokens.all();
  await refreshTokens.replace(
    all.map((r) => (r.userId === userId && !r.revoked ? { ...r, revoked: true } : r)),
  );
  res.json({ ok: true });
});

export default router;
