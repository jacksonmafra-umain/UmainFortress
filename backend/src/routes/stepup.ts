import { Router } from "express";
import crypto from "node:crypto";
import { accounts, deviceBindings, stepUpChallenges } from "../db/seed.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

router.use(requireAuth);

const CHALLENGE_TTL_MS = 60_000;

/**
 * Step-up challenge for revealing an account's full IBAN.
 *
 * The server mints a random 32-byte nonce, binds it to (userId, action), persists it with a
 * short TTL, and returns it to the client. The client signs the nonce inside a BiometricPrompt
 * CryptoObject and posts the signature back to /verify. See docs/07-biometric-hardening.md and
 * docs/09-zero-trust.md for the full rationale.
 */
router.post("/reveal/account/:accountId/challenge", async (req, res) => {
  const userId = req.claims!.sub;
  const accountId = req.params.accountId;
  const account = await accounts.find((a) => a.id === accountId && a.userId === userId);
  if (!account) {
    res.status(404).json({ code: "NOT_FOUND", message: "Account not found" });
    return;
  }

  const nonce = crypto.randomBytes(32);
  const action = `reveal:account:${accountId}`;
  const payloadDigest = crypto.createHash("sha256").update(action).digest();
  const now = Date.now();

  await stepUpChallenges.upsert(
    {
      id: crypto.randomUUID(),
      nonceB64: nonce.toString("base64"),
      userId,
      action,
      payloadDigestB64: payloadDigest.toString("base64"),
      expiresAtEpochMs: now + CHALLENGE_TTL_MS,
      consumed: false,
    },
    "id",
  );

  res.json({
    nonceB64: nonce.toString("base64"),
    expiresAtEpochMs: now + CHALLENGE_TTL_MS,
  });
});

/**
 * Step-up verify — checks the signature against the user × device's bound public key, marks the
 * challenge consumed, and returns the unmasked IBAN on success.
 *
 * Body: { nonceB64, signatureB64, deviceId }
 */
router.post("/reveal/account/:accountId/verify", async (req, res) => {
  const userId = req.claims!.sub;
  const accountId = req.params.accountId;
  const { nonceB64, signatureB64, deviceId } = req.body ?? {};
  if (
    typeof nonceB64 !== "string" ||
    typeof signatureB64 !== "string" ||
    typeof deviceId !== "string"
  ) {
    res.status(400).json({ code: "BAD_REQUEST", message: "nonceB64, signatureB64, deviceId required" });
    return;
  }

  const challenge = await stepUpChallenges.find((c) => c.nonceB64 === nonceB64);
  const now = Date.now();
  const expectedAction = `reveal:account:${accountId}`;
  if (
    !challenge ||
    challenge.consumed ||
    challenge.expiresAtEpochMs < now ||
    challenge.userId !== userId ||
    challenge.action !== expectedAction
  ) {
    res.status(401).json({ code: "CHALLENGE_REJECTED", message: "Challenge invalid or expired" });
    return;
  }

  const binding = await deviceBindings.find((b) => b.userId === userId && b.deviceId === deviceId);
  if (!binding) {
    res.status(401).json({ code: "NO_BINDING", message: "Device is not enrolled for step-up" });
    return;
  }

  let verified = false;
  try {
    const publicKey = crypto.createPublicKey({
      key: Buffer.from(binding.publicKeySpkiB64, "base64"),
      format: "der",
      type: "spki",
    });
    verified = crypto.verify(
      "SHA256",
      Buffer.from(nonceB64, "base64"),
      publicKey,
      Buffer.from(signatureB64, "base64"),
    );
  } catch (_err) {
    verified = false;
  }

  if (!verified) {
    res.status(401).json({ code: "SIGNATURE_INVALID", message: "Signature did not verify" });
    return;
  }

  await stepUpChallenges.upsert({ ...challenge, consumed: true }, "id");

  const account = await accounts.find((a) => a.id === accountId && a.userId === userId);
  if (!account) {
    res.status(404).json({ code: "NOT_FOUND", message: "Account vanished mid-flow" });
    return;
  }

  res.json({ ibanFull: account.ibanFull });
});

export default router;
