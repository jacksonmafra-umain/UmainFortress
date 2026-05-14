# fortress-backend

The TypeScript + Express half of the [Fortress](../README.md) project — a working Android
security showcase paired with a dual-narrative documentation library. This server backs the
Android app (login, refresh, device-binding registration, step-up signatures, accounts, cards,
transfers, security center) and serves a public-facing landing page at `/`.

## How to run

```bash
cd backend
npm install
npm run dev
```

Listens on `http://localhost:8787`. On first start it seeds the demo user
`alice@fortress.dev / passw0rd!` plus a small set of accounts, transactions and cards.

State lives on disk under `backend/data/*.json` (gitignored). Reset with `rm backend/data/*.json`
and re-start to get a clean slate.

## Deploy to Vercel

The repo's `vercel.json` already wires this `src/server.ts` as a `@vercel/node` serverless
function. The server detects `process.env.VERCEL` and:

- Skips `app.listen()` — Vercel invokes the exported Express app directly.
- Redirects the data directory to `/tmp/fortress-data` because the bundled filesystem is
  read-only on Vercel functions. State is **ephemeral per instance** on Vercel — cold starts
  re-seed. The demo backend is intentionally stateless-by-default; for persistent demos,
  swap in Vercel Postgres / KV (see [docs/01-stateless-auth.md](../docs/01-stateless-auth.md)
  for the production picture).

Deploy:

```bash
vercel deploy           # preview
vercel deploy --prod    # production
```

The landing page is at the root URL (`/`); the API lives under `/auth`, `/me`, `/stepup`.
Vercel Web Analytics is wired via the script tag in [`web/landing.ts`](src/web/landing.ts).

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/` | none | Landing page (HTML) |
| `GET` | `/health` | none | Liveness probe |
| `POST` | `/auth/login` | none | Email + password → access + refresh |
| `POST` | `/auth/refresh` | none | Refresh-token → new pair (old is revoked) |
| `POST` | `/auth/logout` | none | Revoke a refresh token |
| `GET` | `/auth/policy` | none | Access-token TTL |
| `POST` | `/auth/device-binding/register` | bearer | Register the device's ECDSA public key |
| `GET` | `/me` | bearer | Current user |
| `GET` | `/me/dashboard` | bearer | Balance card + recent transactions |
| `GET` | `/me/accounts` | bearer | List user's accounts |
| `GET` | `/me/accounts/:id` | bearer | Account detail with transaction history |
| `GET` | `/me/cards` | bearer | List user's cards |
| `POST` | `/me/cards` | bearer | Enrol a new card (server generates PAN + CVV) |
| `POST` | `/me/cards/:id/freeze` | bearer | Freeze a card |
| `POST` | `/me/cards/:id/unfreeze` | bearer | Unfreeze a card |
| `GET` | `/me/security/devices` | bearer | List trusted devices |
| `GET` | `/me/security/sessions` | bearer | List active sessions |
| `DELETE` | `/me/security/devices/:id` | bearer | Revoke a device binding |
| `POST` | `/me/security/sign-out-all` | bearer | Revoke all active sessions |
| `POST` | `/stepup/reveal/account/:id/challenge` | bearer | Issue IBAN reveal challenge |
| `POST` | `/stepup/reveal/account/:id/verify` | bearer | Verify signed challenge, return IBAN |
| `POST` | `/stepup/reveal/card/:id/challenge` | bearer | Issue PAN reveal challenge |
| `POST` | `/stepup/reveal/card/:id/verify` | bearer | Verify signed challenge, return PAN + CVV |
| `POST` | `/stepup/transfer/challenge` | bearer | Issue transfer challenge with bound payload |
| `POST` | `/stepup/transfer/verify` | bearer | Verify signature, execute debit, emit transaction |

Stack and pinned versions live in [`package.json`](package.json) — read that, not this file,
when picking a dependency.

## License

MIT. The store + seed are demo-grade (no concurrency control beyond a per-file write queue,
no rate limiting, no replication, no HSM-backed signing). The threat-model notes in
[docs/01-stateless-auth.md](../docs/01-stateless-auth.md) and
[docs/10-system-design.md](../docs/10-system-design.md) cover what production should look like.
