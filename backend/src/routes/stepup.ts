import { Router } from "express";
import crypto from "node:crypto";
import { accounts, cards, deviceBindings, stepUpChallenges, transactions } from "../db/seed.js";
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

interface TransferPayload {
  sourceAccountId: string;
  recipientName: string;
  recipientIban: string;
  amountMinorUnits: number;
  currency: string;
  memo?: string;
}

/**
 * Issues a step-up challenge for a money transfer.
 *
 * The full payload is hashed and the digest is bound to the nonce; the payload itself is also
 * stored on the challenge record so verify can execute the transfer without trusting the client
 * to repeat the parameters.
 */
router.post("/transfer/challenge", async (req, res) => {
  const userId = req.claims!.sub;
  const body = req.body ?? {};
  const sourceAccountId = String(body.sourceAccountId ?? "");
  const recipientName = String(body.recipientName ?? "").trim();
  const recipientIban = String(body.recipientIban ?? "").replace(/\s+/g, "");
  const amountMinorUnits = Number(body.amountMinorUnits);
  const currency = String(body.currency ?? "");
  const memo = typeof body.memo === "string" ? body.memo.slice(0, 140) : undefined;

  if (!sourceAccountId || !recipientName || !recipientIban || !Number.isInteger(amountMinorUnits) || amountMinorUnits <= 0 || !currency) {
    res.status(400).json({ code: "BAD_REQUEST", message: "sourceAccountId, recipientName, recipientIban, amountMinorUnits (>0), currency required" });
    return;
  }

  const source = await accounts.find((a) => a.id === sourceAccountId && a.userId === userId);
  if (!source) {
    res.status(404).json({ code: "SOURCE_NOT_FOUND", message: "Source account not found" });
    return;
  }
  if (source.currency !== currency) {
    res.status(400).json({ code: "CURRENCY_MISMATCH", message: `Account is ${source.currency}, transfer is ${currency}` });
    return;
  }
  if (source.balanceMinorUnits < amountMinorUnits) {
    res.status(400).json({ code: "INSUFFICIENT_FUNDS", message: "Balance below requested amount" });
    return;
  }

  const payload: TransferPayload = { sourceAccountId, recipientName, recipientIban, amountMinorUnits, currency, memo };
  const payloadJson = JSON.stringify(payload);
  const payloadDigest = crypto.createHash("sha256").update(payloadJson).digest();

  const nonce = crypto.randomBytes(32);
  const now = Date.now();
  await stepUpChallenges.upsert(
    {
      id: crypto.randomUUID(),
      nonceB64: nonce.toString("base64"),
      userId,
      action: `transfer:${sourceAccountId}`,
      payloadDigestB64: payloadDigest.toString("base64"),
      payloadJson,
      expiresAtEpochMs: now + CHALLENGE_TTL_MS,
      consumed: false,
    },
    "id",
  );

  res.json({
    nonceB64: nonce.toString("base64"),
    expiresAtEpochMs: now + CHALLENGE_TTL_MS,
    summary: {
      sourceAccountDisplayName: source.displayName,
      sourceMaskedNumber: source.maskedNumber,
      recipientName,
      recipientIban,
      amountMinorUnits,
      currency,
      memo: memo ?? null,
    },
  });
});

/**
 * Verifies the step-up signature and, on success, executes the bound transfer atomically
 * relative to the per-file write queue in jsonStore.
 */
router.post("/transfer/verify", async (req, res) => {
  const userId = req.claims!.sub;
  const { nonceB64, signatureB64, deviceId } = req.body ?? {};
  if (typeof nonceB64 !== "string" || typeof signatureB64 !== "string" || typeof deviceId !== "string") {
    res.status(400).json({ code: "BAD_REQUEST", message: "nonceB64, signatureB64, deviceId required" });
    return;
  }

  const challenge = await stepUpChallenges.find((c) => c.nonceB64 === nonceB64);
  const now = Date.now();
  if (!challenge || challenge.consumed || challenge.expiresAtEpochMs < now || challenge.userId !== userId || !challenge.action.startsWith("transfer:") || !challenge.payloadJson) {
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

  const payload = JSON.parse(challenge.payloadJson) as TransferPayload;
  const source = await accounts.find((a) => a.id === payload.sourceAccountId && a.userId === userId);
  if (!source) {
    res.status(404).json({ code: "SOURCE_NOT_FOUND", message: "Source account vanished mid-flow" });
    return;
  }
  if (source.balanceMinorUnits < payload.amountMinorUnits) {
    res.status(400).json({ code: "INSUFFICIENT_FUNDS", message: "Balance dropped below amount between challenge and verify" });
    return;
  }

  // Mark challenge consumed first — even on later failure we don't replay this signature.
  await stepUpChallenges.upsert({ ...challenge, consumed: true }, "id");

  // Atomic-ish for the demo: write balance, then transaction. The JSON store serialises per-file
  // writes; in a real backend this would be a single DB transaction.
  await accounts.upsert(
    { ...source, balanceMinorUnits: source.balanceMinorUnits - payload.amountMinorUnits },
    "id",
  );

  const transaction = {
    id: `tx_${crypto.randomUUID()}`,
    accountId: source.id,
    timestampEpochMs: now,
    description: payload.memo ? `Transfer · ${payload.memo}` : "Transfer",
    counterparty: payload.recipientName,
    amountMinorUnits: -payload.amountMinorUnits,
    currency: payload.currency,
    category: "transfer" as const,
    riskLevel: "low" as const,
  };
  await transactions.upsert(transaction, "id");

  res.json({
    transactionId: transaction.id,
    newBalanceMinorUnits: source.balanceMinorUnits - payload.amountMinorUnits,
    currency: payload.currency,
  });
});

/**
 * Card PAN reveal — same pattern as account IBAN reveal, different action prefix.
 */
router.post("/reveal/card/:cardId/challenge", async (req, res) => {
  const userId = req.claims!.sub;
  const cardId = req.params.cardId;
  const card = await cards.find((c) => c.id === cardId && c.userId === userId);
  if (!card) {
    res.status(404).json({ code: "NOT_FOUND", message: "Card not found" });
    return;
  }
  if (card.frozen) {
    res.status(409).json({ code: "CARD_FROZEN", message: "Unfreeze the card before revealing the PAN" });
    return;
  }
  const nonce = crypto.randomBytes(32);
  const action = `reveal:card:${cardId}`;
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
  res.json({ nonceB64: nonce.toString("base64"), expiresAtEpochMs: now + CHALLENGE_TTL_MS });
});

router.post("/reveal/card/:cardId/verify", async (req, res) => {
  const userId = req.claims!.sub;
  const cardId = req.params.cardId;
  const { nonceB64, signatureB64, deviceId } = req.body ?? {};
  if (typeof nonceB64 !== "string" || typeof signatureB64 !== "string" || typeof deviceId !== "string") {
    res.status(400).json({ code: "BAD_REQUEST", message: "nonceB64, signatureB64, deviceId required" });
    return;
  }
  const challenge = await stepUpChallenges.find((c) => c.nonceB64 === nonceB64);
  const now = Date.now();
  const expectedAction = `reveal:card:${cardId}`;
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
  const card = await cards.find((c) => c.id === cardId && c.userId === userId);
  if (!card) {
    res.status(404).json({ code: "NOT_FOUND", message: "Card vanished mid-flow" });
    return;
  }
  res.json({
    panFull: card.panFull,
    cvvFull: card.cvvFull,
    expMonth: card.expMonth,
    expYear: card.expYear,
  });
});

export default router;
