import { Router } from "express";
import crypto from "node:crypto";
import { cards, type Card } from "../db/seed.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

router.use(requireAuth);

const ALLOWED_BRANDS = new Set<Card["brand"]>(["visa", "mastercard", "amex"]);
const ALLOWED_VARIANTS = new Set<Card["variant"]>(["debit", "credit", "virtual"]);

function randomDigits(n: number): string {
  let out = "";
  for (let i = 0; i < n; i++) out += Math.floor(Math.random() * 10).toString();
  return out;
}

function maskPan(last4: string): string {
  return `•••• •••• •••• ${last4}`;
}

interface CardDto {
  id: string;
  brand: string;
  variant: string;
  holderName: string;
  panMasked: string;
  expMonth: number;
  expYear: number;
  frozen: boolean;
  linkedAccountId?: string;
}

function toDto(c: {
  id: string;
  brand: string;
  variant: string;
  holderName: string;
  panMasked: string;
  expMonth: number;
  expYear: number;
  frozen: boolean;
  linkedAccountId?: string;
}): CardDto {
  return {
    id: c.id,
    brand: c.brand,
    variant: c.variant,
    holderName: c.holderName,
    panMasked: c.panMasked,
    expMonth: c.expMonth,
    expYear: c.expYear,
    frozen: c.frozen,
    linkedAccountId: c.linkedAccountId,
  };
}

router.get("/", async (req, res) => {
  const userId = req.claims!.sub;
  const userCards = (await cards.all()).filter((c) => c.userId === userId);
  res.json({ cards: userCards.map(toDto) });
});

/**
 * Add a new card. This is the demo equivalent of linking a real card via a tokenisation
 * vendor — the PAN is generated server-side from the last four digits the user provides,
 * the CVV is a random three-digit value, and the masked form is the only thing returned
 * over the wire. Step-up reveal still gates the full PAN exactly like the seeded cards.
 */
router.post("/", async (req, res) => {
  const userId = req.claims!.sub;
  const body = (req.body ?? {}) as Partial<{
    brand: string;
    variant: string;
    holderName: string;
    last4: string;
    expMonth: number;
    expYear: number;
    linkedAccountId: string;
  }>;

  const brand = (body.brand ?? "").toLowerCase() as Card["brand"];
  const variant = (body.variant ?? "virtual").toLowerCase() as Card["variant"];
  const holderName = (body.holderName ?? "").trim();
  const last4 = (body.last4 ?? "").trim();
  const expMonth = Number(body.expMonth);
  const expYear = Number(body.expYear);

  const errors: string[] = [];
  if (!ALLOWED_BRANDS.has(brand)) errors.push("brand must be visa, mastercard or amex");
  if (!ALLOWED_VARIANTS.has(variant)) errors.push("variant must be debit, credit or virtual");
  if (holderName.length < 2) errors.push("holderName is required");
  if (!/^\d{4}$/.test(last4)) errors.push("last4 must be exactly four digits");
  if (!Number.isInteger(expMonth) || expMonth < 1 || expMonth > 12) {
    errors.push("expMonth must be between 1 and 12");
  }
  const thisYear = new Date().getUTCFullYear();
  if (!Number.isInteger(expYear) || expYear < thisYear || expYear > thisYear + 20) {
    errors.push(`expYear must be between ${thisYear} and ${thisYear + 20}`);
  }
  if (errors.length > 0) {
    res.status(400).json({ code: "INVALID_REQUEST", message: errors.join("; ") });
    return;
  }

  const card: Card = {
    id: crypto.randomUUID(),
    userId,
    brand,
    variant,
    holderName,
    panMasked: maskPan(last4),
    panFull: `${randomDigits(12)}${last4}`,
    cvvFull: randomDigits(3),
    expMonth,
    expYear,
    frozen: false,
    linkedAccountId: body.linkedAccountId,
  };
  await cards.upsert(card, "id");
  res.status(201).json({ card: toDto(card) });
});

/**
 * Freeze and unfreeze are intentionally lightweight (no step-up). Freeze tightens the security
 * envelope; unfreeze restores prior state without changing it. PAN reveal — the genuinely
 * sensitive action — lives behind the step-up flow in routes/stepup.ts.
 */
router.post("/:cardId/freeze", async (req, res) => {
  const userId = req.claims!.sub;
  const card = await cards.find((c) => c.id === req.params.cardId && c.userId === userId);
  if (!card) {
    res.status(404).json({ code: "NOT_FOUND", message: "Card not found" });
    return;
  }
  await cards.upsert({ ...card, frozen: true }, "id");
  res.json({ card: toDto({ ...card, frozen: true }) });
});

router.post("/:cardId/unfreeze", async (req, res) => {
  const userId = req.claims!.sub;
  const card = await cards.find((c) => c.id === req.params.cardId && c.userId === userId);
  if (!card) {
    res.status(404).json({ code: "NOT_FOUND", message: "Card not found" });
    return;
  }
  await cards.upsert({ ...card, frozen: false }, "id");
  res.json({ card: toDto({ ...card, frozen: false }) });
});

export default router;
