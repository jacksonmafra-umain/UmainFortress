# fortress-backend

TypeScript + Express demo backend for the Fortress Android app.

- **Stack**: Express 4 + TypeScript 5 + `jose` (JWT) + `argon2` (password hashing).
- **Persistence**: JSON files in `./data/` (gitignored). Wiped with `rm data/*.json`.
- **Auth**: HS256 access tokens (15 min) + opaque, rotating refresh tokens (30 d, SHA-256 hashed at rest).

## Run

```bash
cd backend
npm install
npm run dev          # tsx watch, port 8787
```

Health check: `curl http://localhost:8787/health`.

Demo creds (seeded on first start):

```
alice@fortress.dev / passw0rd!
```

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/auth/login` | none | Email + password → access + refresh |
| `POST` | `/auth/refresh` | none | Refresh-token → new pair (old is revoked) |
| `POST` | `/auth/logout` | none | Revoke a refresh token |
| `GET` | `/auth/policy` | none | Access-token TTL |
| `GET` | `/me` | bearer | Current user |
| `GET` | `/me/dashboard` | bearer | Balance card + recent transactions |
| `GET` | `/health` | none | Liveness |

See `../docs/01-stateless-auth.md` for the full design rationale.
