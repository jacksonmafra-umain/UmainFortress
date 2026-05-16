# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A single dual-narrative Android security project split across three coordinated directories:

- `app/` — Kotlin + Jetpack Compose Android app ("Fortress Bank").
- `backend/` — TypeScript + Express demo backend, deployed on Vercel as a `@vercel/node` serverless function.
- `docs/` — 16-chapter documentation library + design-system contract + hands-on codelabs, mirrored under `docs/pt-BR/`.

Every chapter in `docs/` has a 🛡️ Defender half (how the control is built) paired with an ⚔️ Attacker half (numbered Bypass 1..N scenarios). `docs/07-biometric-hardening.md` is the canonical depth/tone reference; new chapters should mirror its structure.

## Commands

### Android (run from repo root, uses Gradle wrapper)

- `./gradlew assembleDebug` — build debug APK. Add `--no-daemon -q` for quick CI-style checks.
- `./gradlew :app:installDebug` — install on the running emulator.
- `./gradlew :app:testDebugUnitTest` — JVM unit tests. Single test via `--tests "com.umain.fortress.SomeTest"`.
- `./gradlew :app:lintDebug` — Android lint.
- `./gradlew fortressTunnel` — spawn `ngrok http 8787` and upsert the public URL into `local.properties` (requires `ngrok` in `$PATH` with an authtoken).
- `./gradlew fortressTunnelStop` — kill ngrok and remove the override.

### Backend (run from `backend/`)

- `npm install`
- `npm run dev` — `tsx watch src/server.ts`, listens on `:8787`.
- `npm run typecheck` — `tsc --noEmit`. Always run before committing TS changes.
- `npm run build` / `npm start`
- `rm backend/data/*.json` — reset the seeded demo state.
- `vercel deploy` / `vercel deploy --prod` — preview / production deploys.

After first start the backend seeds `alice@fortress.dev / passw0rd!` plus accounts, transactions, and cards.

## Architecture invariants

### `fortress.baseUrl` is never hardcoded

`app/build.gradle.kts` resolves it from (priority high → low):

1. `local.properties#fortress.baseUrl` — per-developer, gitignored. Written by `./gradlew fortressTunnel`.
2. `-Pfortress.baseUrl=https://…` — CLI override.
3. `gradle.properties#fortress.baseUrl` — committed default (Vercel production).

The build fails fast if none is defined. Android code reads `BuildConfig.BASE_URL`. The local backend port comes from `gradle.properties#fortress.localBackendPort`.

### Step-up signing is the central security contract

Every sensitive action (IBAN reveal, card PAN reveal, money transfer) follows the same flow:

1. Client → `POST /stepup/.../challenge`. Server persists `{nonce, userId, action, payloadDigest, expiresAt, consumed:false}` and returns the nonce.
2. Client opens `BiometricPrompt` wrapping a `CryptoObject(signature)` built against the `BiometricKeyStore.ALIAS_DEVICE_BINDING` ECDSA P-256 key. The key is hardware-bound, `AUTH_BIOMETRIC_STRONG`, per-operation auth (`setUserAuthenticationParameters(0, …)`), `setInvalidatedByBiometricEnrollment(true)`, StrongBox-backed best-effort.
3. Successful biometric → TEE-authorised signature signs the nonce → bytes return to the server with the device id.
4. Server looks up the registered public key by `(userId, deviceId)` (registered at login via `DeviceBindingEnroller`), verifies with `crypto.verify("SHA256", nonceBuf, pubKey, sigBuf)`, marks the challenge consumed, executes the action.

Adding a new sensitive action: extend `backend/src/routes/stepup.ts` with paired `challenge`/`verify` endpoints, mirror the reveal/transfer flow, drive the client from a Compose ViewModel that calls `StepUpAuthenticator.signChallenge(...)`.

### Vault design system is the visual contract

- All colour reaches `app/.../ui/theme/Color.kt` and only that file. Extended (non-M3) tokens via `FortressTheme.colors` (CompositionLocal-backed `FortressColors`): gradient stops, money tail grey, status surfaces.
- All icons route through `FortressIcons` in `ui/icons/FortressIcons.kt`. Never import `Icons.Default.*` directly in screens or components.
- Money rendering uses the `MoneyText` composable with paired head/tail typography. Don't concatenate major+minor units by hand.
- Previews wrap in `PreviewSurface` and use `DarkModeProvider` (`ui/components/preview/`) so every preview ships light + dark.
- Component shapes use `MaterialTheme.shapes` tokens; pill CTAs use `FortressPillShape`. Don't hand-tune radii.
- `docs/design-system.md` is the full contract — new colours and icons document themselves there.

### Dev Mode drives the integrity stub

`IntegrityCheck.current()` reads `DevModeStore.state` only in debug builds (`BuildConfig.ALLOW_DEV_MODE`). The Profile → Dev Mode screen exposes four toggles (`simulateRoot`, `simulateMitm`, `simulateReplay`, `simulateIntegrityFail`) that flip the verdict to `Limited` or `Untrusted` with human reasons. `SecurityChip` and the Security Center risk card react as if the signal were real. Release builds ignore the toggles and return `Trusted`. When wiring real Play Integrity / RASP signals, replace the stub body in `IntegrityCheck` — consumers don't need to change.

### Backend on Vercel — ephemeral state by design

`vercel.json` registers `backend/src/server.ts` as a `@vercel/node` function. The server:

- Exports the Express `app` as default; Vercel invokes it as the handler.
- Skips `app.listen()` when `process.env.VERCEL` is set (used only locally).
- Runs `seedIfEmpty()` at module load via a `seedReady` promise that all handlers await before responding.
- `jsonStore.ts` redirects `DATA_DIR` to `/tmp/fortress-data` on Vercel because the bundled filesystem is read-only. **State is ephemeral per instance**; cold starts re-seed. Persistent state requires a real DB — out of scope for the demo.

The Express landing page is rendered at `/` by `backend/src/web/landing.ts`. Vercel Web Analytics is wired via the auto-routed `/_vercel/insights/script.js` script tag — no `@vercel/analytics` import needed.

### Documentation library structure

- `docs/INDEX.md` is the canonical navigation; `docs/pt-BR/INDEX.md` is the Brazilian Portuguese mirror.
- Chapters `01-16` follow the structure of `docs/07-biometric-hardening.md` exactly: quote → TL;DR → defender/attacker matrix table → 🛡️ Defender prose → ⚔️ Attacker numbered Bypass scenarios → Cross-reference → References.
- `docs/codelabs/` holds hands-on step-based labs derived from the chapters and from Jackson's wider Medium series. A `docs/codelabs/README.md` orients readers; status (authored vs scaffolded) lives in the index entries.
- pt-BR convention block at the bottom of `docs/pt-BR/INDEX.md` lists which terms stay in English (`token`, `hash`, `nonce`, `vault`, `hardening`, `auth-gated`, etc.) and which translate (`Defender → Defensor`, `Attacker → Atacante`). First occurrence of a translated term gets the English in parens once; later occurrences are PT-only.

## Repo policies

### Commit policy (hard rules from project memory)

- **Micro-commits per logical action.** One screen, one module, one doc, one config change → one commit. No batching unrelated changes.
- **NEVER mention Claude, AI, or any assistant in commit messages.** No `Co-Authored-By: Claude…` trailer, no "generated with", no AI references. Commit as Jackson — the message must read as a human engineer wrote it.
- Stage with explicit file paths (`git add path/to/file`), not `git add -A`.
- Never `--amend` commits not created in the current operation.
- Conventional Commits style: `feat(...)`, `docs(...)`, `build(...)`, `chore(...)`, `style(...)`, `fix(...)`.

### Stack preferences — do not regress

- Android networking: **Ktor client on OkHttp engine** (cert pinning + interceptors apply at the OkHttp layer). Do not introduce Retrofit.
- Android DI: **Koin** (`koin-androidx-compose` for ViewModel injection). Do not introduce Hilt.
- Android serialization: **kotlinx.serialization**. Do not introduce Moshi/Gson.
- Backend: **TypeScript + Express + disk-JSON** via `jsonStore.ts`. Do not introduce Postgres/Prisma/an ORM unless the user explicitly asks.

Pinned versions live in `gradle/libs.versions.toml` (app) and `backend/package.json` (backend) — read those when picking a dependency.

### Documentation rules

- New chapters mirror `docs/07-biometric-hardening.md` structure exactly. The numbered `Bypass N` scenarios on the attacker side are the load-bearing convention — keep them numbered and cross-reference them from defender counters.
- When a chapter or codelab lands, flip its status in `docs/INDEX.md` and `docs/pt-BR/INDEX.md`.
- pt-BR translations go in `docs/pt-BR/` with the same filenames as English. Trechos de código, paths e identifiers ficam em inglês.
- The landing page (`backend/src/web/landing.ts`) carries a `DOCS` array that mirrors what's authored; update it when a chapter lands so the public site stays accurate.

## When verifying changes end-to-end

```bash
# Backend
cd backend && npx tsc --noEmit && echo OK

# Android
./gradlew assembleDebug --no-daemon -q
```

Both must be clean before committing a feature that touches the wire.
