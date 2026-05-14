import argon2 from "argon2";
import { collection } from "./jsonStore.js";

export interface User {
  id: string;
  email: string;
  displayName: string;
  passwordHash: string;
  createdAtEpochMs: number;
}

export interface RefreshTokenRecord {
  id: string;
  userId: string;
  tokenHash: string;
  deviceId: string;
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  revoked: boolean;
}

export interface DeviceBinding {
  id: string;            // synthetic uuid, primary key
  userId: string;
  deviceId: string;      // (userId, deviceId) is logically unique
  publicKeySpkiB64: string; // SPKI / X.509 SubjectPublicKeyInfo, base64
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
}

export interface StepUpChallenge {
  id: string;            // uuid; client never sees it
  nonceB64: string;      // base64url, what the client signs
  userId: string;
  action: string;        // "reveal:account:<accountId>", "transfer:<sourceAccountId>", etc.
  payloadDigestB64: string; // SHA-256 of the canonical payload, bound at issuance
  payloadJson?: string;  // action-specific payload restored at verify time (transfers etc.)
  expiresAtEpochMs: number;
  consumed: boolean;
}

export interface Account {
  id: string;
  userId: string;
  displayName: string;
  type: "checking" | "savings" | "investment";
  maskedNumber: string;
  ibanFull: string;       // only returned via the step-up reveal flow
  balanceMinorUnits: number;
  currency: string;
  isPrimary: boolean;
}

export interface Transaction {
  id: string;
  accountId: string;
  timestampEpochMs: number;
  description: string;
  counterparty: string;
  amountMinorUnits: number;
  currency: string;
  category: string;
  riskLevel: "low" | "medium" | "high";
}

export interface Card {
  id: string;
  userId: string;
  brand: "visa" | "mastercard" | "amex";
  variant: "debit" | "credit" | "virtual";
  holderName: string;
  panMasked: string;       // "•••• •••• •••• 1452"
  panFull: string;         // only emitted via step-up reveal
  cvvFull: string;         // only emitted via step-up reveal
  expMonth: number;        // 1..12
  expYear: number;         // four-digit
  frozen: boolean;
  linkedAccountId?: string;
}

export const users = collection<User>("users.json", () => []);
export const refreshTokens = collection<RefreshTokenRecord>("refresh_tokens.json", () => []);
export const accounts = collection<Account>("accounts.json", () => []);
export const transactions = collection<Transaction>("transactions.json", () => []);
export const deviceBindings = collection<DeviceBinding>("device_bindings.json", () => []);
export const stepUpChallenges = collection<StepUpChallenge>("step_up_challenges.json", () => []);
export const cards = collection<Card>("cards.json", () => []);

/**
 * Idempotent seed used on cold start. Creates the demo user, accounts, and a handful of
 * transactions so the app shows something interesting right after `npm run dev`.
 */
export async function seedIfEmpty(): Promise<void> {
  const existing = await users.all();
  if (existing.length > 0) return;

  const passwordHash = await argon2.hash("passw0rd!", { type: argon2.argon2id });
  const userId = "u_alice";

  await users.upsert(
    {
      id: userId,
      email: "alice@fortress.dev",
      displayName: "Alice Hartman",
      passwordHash,
      createdAtEpochMs: Date.now(),
    },
    "id",
  );

  const primaryId = "acct_checking_eur";
  await accounts.replace([
    {
      id: primaryId,
      userId,
      displayName: "Everyday Checking",
      type: "checking",
      maskedNumber: "•••• 1452",
      ibanFull: "SE45 5000 0000 0583 9826 1452",
      balanceMinorUnits: 1_245_300, // 12 453.00 EUR
      currency: "EUR",
      isPrimary: true,
    },
    {
      id: "acct_savings_eur",
      userId,
      displayName: "Vault Savings",
      type: "savings",
      maskedNumber: "•••• 8821",
      ibanFull: "SE45 5000 0000 0583 9826 8821",
      balanceMinorUnits: 8_420_000, // 84 200.00 EUR
      currency: "EUR",
      isPrimary: false,
    },
    {
      id: "acct_investment_eur",
      userId,
      displayName: "Index Portfolio",
      type: "investment",
      maskedNumber: "•••• 7733",
      ibanFull: "SE45 5000 0000 0583 9826 7733",
      balanceMinorUnits: 15_350_000,
      currency: "EUR",
      isPrimary: false,
    },
  ]);

  const now = Date.now();
  const day = 24 * 60 * 60 * 1000;
  await transactions.replace([
    {
      id: "tx_001",
      accountId: primaryId,
      timestampEpochMs: now - 2 * day,
      description: "Salary — Umain AB",
      counterparty: "Umain AB",
      amountMinorUnits: 412_000,
      currency: "EUR",
      category: "payroll",
      riskLevel: "low",
    },
    {
      id: "tx_002",
      accountId: primaryId,
      timestampEpochMs: now - 2 * day + 60_000,
      description: "Rent — September",
      counterparty: "Hammarby Bostäder",
      amountMinorUnits: -148_000,
      currency: "EUR",
      category: "housing",
      riskLevel: "low",
    },
    {
      id: "tx_003",
      accountId: primaryId,
      timestampEpochMs: now - 1 * day,
      description: "Espresso bar",
      counterparty: "Drop Coffee",
      amountMinorUnits: -540,
      currency: "EUR",
      category: "food",
      riskLevel: "low",
    },
    {
      id: "tx_004",
      accountId: primaryId,
      timestampEpochMs: now - 6 * 60 * 60 * 1000,
      description: "Transfer to new payee — investigating",
      counterparty: "Z. Carmichael (new)",
      amountMinorUnits: -82_000,
      currency: "EUR",
      category: "transfer",
      riskLevel: "high",
    },
    {
      id: "tx_005",
      accountId: primaryId,
      timestampEpochMs: now - 3 * 60 * 60 * 1000,
      description: "Subway",
      counterparty: "SL — Stockholm Public Transit",
      amountMinorUnits: -380,
      currency: "EUR",
      category: "transit",
      riskLevel: "low",
    },
  ]);

  await cards.replace([
    {
      id: "card_debit_primary",
      userId,
      brand: "visa",
      variant: "debit",
      holderName: "Alice Hartman",
      panMasked: "•••• •••• •••• 4291",
      panFull: "4716 9219 5302 4291",
      cvvFull: "317",
      expMonth: 11,
      expYear: 2029,
      frozen: false,
      linkedAccountId: primaryId,
    },
    {
      id: "card_credit_travel",
      userId,
      brand: "mastercard",
      variant: "virtual",
      holderName: "Alice Hartman",
      panMasked: "•••• •••• •••• 0883",
      panFull: "5413 6677 4421 0883",
      cvvFull: "942",
      expMonth: 6,
      expYear: 2028,
      frozen: true,
      linkedAccountId: primaryId,
    },
  ]);
}
