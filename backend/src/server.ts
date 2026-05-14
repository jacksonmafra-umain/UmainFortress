import express from "express";
import cors from "cors";
import authRouter from "./routes/auth.js";
import meRouter from "./routes/me.js";
import { seedIfEmpty } from "./db/seed.js";

const app = express();
app.use(cors());
app.use(express.json({ limit: "256kb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true, name: "fortress-backend", time: new Date().toISOString() });
});

app.use("/auth", authRouter);
app.use("/me", meRouter);

// Generic error handler — keeps stack traces out of the response body.
app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error("[fortress] unhandled", err);
  res.status(500).json({ code: "INTERNAL", message: "Internal error" });
});

const PORT = Number(process.env.PORT ?? 8787);

async function main(): Promise<void> {
  await seedIfEmpty();
  app.listen(PORT, () => {
    console.log(`[fortress] listening on http://0.0.0.0:${PORT}`);
  });
}

main().catch((err) => {
  console.error("[fortress] failed to start", err);
  process.exit(1);
});
