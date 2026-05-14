import type { NextFunction, Request, Response } from "express";
import { verifyAccessToken, type AccessClaims } from "../utils/jwt.js";

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      claims?: AccessClaims;
    }
  }
}

export async function requireAuth(req: Request, res: Response, next: NextFunction): Promise<void> {
  const header = req.header("authorization");
  if (!header || !header.toLowerCase().startsWith("bearer ")) {
    res.status(401).json({ code: "MISSING_TOKEN", message: "Authorization header required" });
    return;
  }
  const token = header.slice("bearer ".length).trim();
  try {
    req.claims = await verifyAccessToken(token);
    next();
  } catch (_err) {
    res.status(401).json({ code: "INVALID_TOKEN", message: "Token rejected" });
  }
}
