import express from "express";
import cors from "cors";
import authRouter from "./routes/auth.js";
import cardsRouter from "./routes/cards.js";
import deviceBindingRouter from "./routes/devicebinding.js";
import meRouter from "./routes/me.js";
import securityRouter from "./routes/security.js";
import stepUpRouter from "./routes/stepup.js";
import { seedIfEmpty } from "./db/seed.js";
import { renderLanding } from "./web/landing.js";

const app = express();
app.use(cors());
app.use(express.json({ limit: "256kb" }));

// Landing page — public, server-rendered HTML describing the project.
// API routes (/auth, /me, /health) are unaffected.
app.get("/", (_req, res) => {
  res
    .status(200)
    .set("Content-Type", "text/html; charset=utf-8")
    .set("Cache-Control", "public, max-age=300")
    .send(renderLanding());
});

app.get("/health", (_req, res) => {
  res.json({ ok: true, name: "fortress-backend", time: new Date().toISOString() });
});

app.use("/auth", authRouter);
app.use("/auth/device-binding", deviceBindingRouter);
app.use("/me", meRouter);
app.use("/me/cards", cardsRouter);
app.use("/me/security", securityRouter);
app.use("/stepup", stepUpRouter);

// Generic error handler — keeps stack traces out of the response body.
app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error("[fortress] unhandled", err);
  res.status(500).json({ code: "INTERNAL", message: "Internal error" });
});

const PORT = Number(process.env.PORT ?? 8787);

// Seed runs at module load so the first request after a cold start (local or Vercel) has
// data available. Failures are logged but never crash the function — a fresh deploy on
// Vercel can repeatedly re-seed `/tmp` per invocation, which is the demo-friendly behaviour.
const seedReady = seedIfEmpty().catch((err) => {
  console.error("[fortress] seed failed", err);
});

// Block API handlers until the seed promise settles, so the first request can't see an
// empty store. Cheap (single in-flight await per invocation) and idempotent.
app.use(async (_req, _res, next) => {
  await seedReady;
  next();
});

// Local dev — start a listening HTTP server. On Vercel `process.env.VERCEL = "1"` and we
// skip listen(); the platform invokes the exported `app` as a serverless function handler.
if (!process.env.VERCEL) {
  app.listen(PORT, () => {
    console.log(`[fortress] listening on http://0.0.0.0:${PORT}`);
  });
}

export default app;
