import crypto from "node:crypto";
import { SignJWT, jwtVerify } from "jose";

/**
 * Demo signing material — a fixed dev secret unless overridden by FORTRESS_JWT_SECRET.
 *
 * In production: use an asymmetric key (RS256 / ES256), keep the private key in a KMS, and
 * publish the JWKS so each service can verify without sharing secrets. See docs/01-stateless-auth.md.
 */
const SECRET = new TextEncoder().encode(
  process.env.FORTRESS_JWT_SECRET ??
    "fortress-demo-secret-do-not-use-in-prod-please-and-thank-you-32bytes",
);

const ISSUER = "fortress.demo";
const AUDIENCE = "fortress.client";

export const ACCESS_TOKEN_TTL_SEC = 15 * 60;
export const REFRESH_TOKEN_TTL_SEC = 30 * 24 * 60 * 60;

export interface AccessClaims {
  sub: string;
  email: string;
  displayName: string;
}

export async function issueAccessToken(claims: AccessClaims): Promise<{
  token: string;
  expiresAtEpochMs: number;
}> {
  const now = Math.floor(Date.now() / 1000);
  const exp = now + ACCESS_TOKEN_TTL_SEC;
  const token = await new SignJWT({ ...claims })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setSubject(claims.sub)
    .setIssuedAt(now)
    .setExpirationTime(exp)
    .setJti(crypto.randomUUID())
    .sign(SECRET);
  return { token, expiresAtEpochMs: exp * 1000 };
}

export async function verifyAccessToken(token: string): Promise<AccessClaims> {
  const { payload } = await jwtVerify(token, SECRET, {
    issuer: ISSUER,
    audience: AUDIENCE,
  });
  return {
    sub: String(payload.sub),
    email: String(payload.email ?? ""),
    displayName: String(payload.displayName ?? ""),
  };
}

export function generateRefreshToken(): { raw: string; hash: string; expiresAtEpochMs: number } {
  const raw = crypto.randomBytes(48).toString("base64url");
  const hash = crypto.createHash("sha256").update(raw).digest("hex");
  const expiresAtEpochMs = Date.now() + REFRESH_TOKEN_TTL_SEC * 1000;
  return { raw, hash, expiresAtEpochMs };
}

export function hashRefreshToken(raw: string): string {
  return crypto.createHash("sha256").update(raw).digest("hex");
}
