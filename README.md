# 🏰 fortress-android

A modern Android security showcase, built as a working fintech app ("Fortress Bank") with a
**dual narrative** running through every topic: the 🛡️ Defender's implementation alongside the
⚔️ Attacker's tactics. Each markdown file in [`docs/`](docs/) tells both sides.

| Layer | Stack |
|---|---|
| App | Kotlin 2.2 · Jetpack Compose · Material 3 · Ktor (OkHttp engine) · Koin · kotlinx.serialization · DataStore · BiometricPrompt + CryptoObject · Android Keystore (StrongBox where available) · Play Integrity · Tink |
| Backend | TypeScript · Node 22 · Express 4 · `jose` (HS256 JWT) · `argon2` (password hashing) · JSON on disk |
| Docs | 16 markdown files under [`docs/`](docs/), each with 🛡️/⚔️ pair |

## Project structure

```
.
├── app/                 # Android app (Kotlin + Compose)
├── backend/             # Node + Express + TypeScript demo backend
└── docs/                # Dual-narrative documentation library
```

## Running the demo

### 1. Start the backend

```bash
cd backend
npm install
npm run dev      # listens on http://localhost:8787 — seeds alice@fortress.dev / passw0rd!
```

### 2. Run the Android app

Open the project in Android Studio (Hedgehog or newer recommended) and run on an emulator.

The backend URL is **never hardcoded** — it always comes from configuration. Resolution order
(highest priority first):

1. `local.properties#fortress.baseUrl` — gitignored, per-developer. Written automatically by
   `./gradlew fortressTunnel` (see below).
2. `-Pfortress.baseUrl=https://your-host/` — one-shot CLI override.
3. `gradle.properties#fortress.baseUrl` — committed default, pointing at
   `https://umain-fortress.vercel.app/`.

**Local dev against the Node backend.** Start the backend (`cd backend && npm run dev`), then in
another shell run:

```bash
./gradlew fortressTunnel        # spawns ngrok http 8787, writes the public URL to local.properties
./gradlew :app:installDebug     # build picks up the ngrok URL via BuildConfig.BASE_URL
# …when you're done:
./gradlew fortressTunnelStop    # kills ngrok and removes the override → next build uses Vercel
```

Requirements: `ngrok` in `$PATH` with a configured authtoken
(`ngrok config add-authtoken …`). The local backend port comes from
`gradle.properties#fortress.localBackendPort` (defaults to 8787).

Demo login: `alice@fortress.dev` / `passw0rd!`. After first login the app stores a refresh token
encrypted via the Android Keystore vault and gates the next session unlock behind a biometric.

## What's wired today (vertical slice)

- Splash → integrity probe → branches to Login or Biometric Unlock
- Email + password login against the backend, with at-rest encrypted token storage
- Biometric unlock that signs a fresh random challenge inside a `BiometricPrompt` `CryptoObject`
- Authenticated `/me/dashboard` call with single-flight 401 → refresh → replay (mutex-protected)
- Material 3 fintech theme (Midnight / Emerald / Violet) with money-mono typography
- Backend with rotating refresh tokens, atomic JSON store, argon2id password hashing

## Roadmap (next passes)

| Feature | Status |
|---|---|
| Onboarding, Accounts, Account detail, Transfer, Cards, Security Center, Dev Mode | 🚧 |
| Passkey enrolment + sign-in via `androidx.credentials` | 🚧 |
| Play Integrity standard request + server verdict verification | 🚧 |
| RASP probes (Frida / debugger / root) + Dev Mode simulation hooks | 🚧 |
| Production certificate pinning + dynamic pin distribution | 🚧 |
| Step-up biometric on Transfer / IBAN reveal flows | 🚧 |

See [`docs/`](docs/) for the design and threat-modelling rationale behind each.

## Documentation index

See [`docs/INDEX.md`](docs/INDEX.md) for the full table of contents.

## Credits

**Author**: **Jackson Mafra** — Mobile & Security Engineer, [Umain](https://umain.com).

Inspired by and synthesising the Stackademic *Scaling Android Auth* series and the wider Android
security community. Full reference list inside each doc file.

## License

MIT — for educational use. Do not lift the demo backend wholesale into production; see the
threat-model notes in [`docs/01-stateless-auth.md`](docs/01-stateless-auth.md) and
[`docs/06-token-lifecycle.md`](docs/06-token-lifecycle.md) first.
