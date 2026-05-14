import { Router } from "express";
import { accounts, transactions } from "../db/seed.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

router.use(requireAuth);

router.get("/dashboard", async (req, res) => {
  const userId = req.claims!.sub;
  const userAccounts = (await accounts.all())
    .filter((a) => a.userId === userId)
    .sort((a, b) => (b.isPrimary ? 1 : 0) - (a.isPrimary ? 1 : 0));
  const primary = userAccounts.find((a) => a.isPrimary) ?? userAccounts[0];

  if (!primary) {
    res.status(404).json({ code: "NO_ACCOUNTS", message: "No accounts for user" });
    return;
  }

  const recentTransactions = (await transactions.all())
    .filter((t) => userAccounts.some((a) => a.id === t.accountId))
    .sort((a, b) => b.timestampEpochMs - a.timestampEpochMs)
    .slice(0, 10)
    .map((t) => ({ ...t, riskLevel: t.riskLevel }));

  res.json({
    primaryAccount: dtoOf(primary),
    accounts: userAccounts.map(dtoOf),
    recentTransactions,
  });
});

router.get("/", async (req, res) => {
  res.json({
    id: req.claims!.sub,
    email: req.claims!.email,
    displayName: req.claims!.displayName,
  });
});

function dtoOf(account: {
  id: string;
  displayName: string;
  type: string;
  maskedNumber: string;
  balanceMinorUnits: number;
  currency: string;
}) {
  return {
    id: account.id,
    displayName: account.displayName,
    type: account.type,
    maskedNumber: account.maskedNumber,
    balanceMinorUnits: account.balanceMinorUnits,
    currency: account.currency,
  };
}

export default router;
