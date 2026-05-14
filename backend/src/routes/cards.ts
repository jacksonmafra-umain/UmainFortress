import { Router } from "express";
import { cards } from "../db/seed.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

router.use(requireAuth);

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
