import { Router } from "express";
import crypto from "node:crypto";
import { deviceBindings } from "../db/seed.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

router.use(requireAuth);

/**
 * Registers (or rotates) the device-binding public key for the authenticated user × device.
 *
 * The client generates an ECDSA P-256 keypair inside the Android Keystore on first launch and
 * posts the public key here as SPKI base64. The server stores it keyed by (userId, deviceId);
 * subsequent step-up flows verify signatures against this stored key.
 *
 * Body: { deviceId: string, publicKeySpkiB64: string }
 */
router.post("/register", async (req, res) => {
  const userId = req.claims!.sub;
  const { deviceId, publicKeySpkiB64 } = req.body ?? {};
  if (typeof deviceId !== "string" || typeof publicKeySpkiB64 !== "string") {
    res.status(400).json({ code: "BAD_REQUEST", message: "deviceId and publicKeySpkiB64 required" });
    return;
  }

  // Sanity-check the key parses as an EC public key. A malformed key here is either a bug or
  // a probing attempt; reject before persisting.
  try {
    crypto.createPublicKey({
      key: Buffer.from(publicKeySpkiB64, "base64"),
      format: "der",
      type: "spki",
    });
  } catch (_err) {
    res.status(400).json({ code: "BAD_KEY", message: "publicKeySpkiB64 is not a valid SPKI key" });
    return;
  }

  const existing = await deviceBindings.find(
    (b) => b.userId === userId && b.deviceId === deviceId,
  );
  const now = Date.now();
  if (existing) {
    await deviceBindings.upsert(
      {
        ...existing,
        publicKeySpkiB64,
        updatedAtEpochMs: now,
      },
      "id",
    );
  } else {
    await deviceBindings.upsert(
      {
        id: crypto.randomUUID(),
        userId,
        deviceId,
        publicKeySpkiB64,
        createdAtEpochMs: now,
        updatedAtEpochMs: now,
      },
      "id",
    );
  }

  res.json({ ok: true });
});

export default router;
