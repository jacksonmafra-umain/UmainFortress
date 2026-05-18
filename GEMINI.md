# Fortress — Project Instructions

This document provides foundational mandates, architectural invariants, and development workflows for the **Fortress** project. It is committed to the repository to ensure all contributors (human or AI) adhere to the established standards.

## Project Overview

**Fortress** is a dual-narrative Android security showcase. It consists of:
- **Fortress Bank App (`app/`):** A working fintech prototype built with Kotlin and Jetpack Compose.
- **Security Documentation Library (`docs/`):** A 16-chapter deep-dive into Android security, telling stories from both the **🛡️ Defender** (architectural controls) and **⚔️ Attacker** (bypass scenarios) perspectives.
- **Demo Backend (`backend/`):** A TypeScript/Express server with JSON-on-disk persistence, deployed as a serverless function on Vercel.

## Core Architectural Invariants

### 1. Unified Security Contract: Step-up Signing
Sensitive actions (IBAN reveal, card PAN reveal, money transfer) must follow the **Challenge-Response** flow:
1. **Client requests a challenge** from the server.
2. **Server generates a nonce**, persists it with a TTL, and returns it.
3. **Client signs the nonce** using a hardware-bound ECDSA P-256 key (`BiometricKeyStore.ALIAS_DEVICE_BINDING`) gated by `BiometricPrompt`.
4. **Server verifies the signature** against the registered public key for that user/device and executes the action if valid.

### 2. Configuration & `fortress.baseUrl`
The backend URL is never hardcoded. `app/build.gradle.kts` resolves it with the following priority:
1. `local.properties#fortress.baseUrl` (Per-developer, gitignored, upserted by `./gradlew fortressTunnel`).
2. `-Pfortress.baseUrl=...` (CLI override).
3. `gradle.properties#fortress.baseUrl` (Committed default for Vercel production).

### 3. Vault Design System
- **Colors:** Defined exclusively in `app/.../ui/theme/Color.kt`. Use `FortressTheme.colors` for custom tokens.
- **Icons:** Route all icons through `FortressIcons` in `ui/icons/FortressIcons.kt`.
- **Typography:** Use `MoneyText` for currency rendering; do not manually concatenate units.
- **Previews:** Every preview must support light and dark modes via `PreviewSurface`.

### 4. Dev Mode & Integrity Stub
In debug builds, the **Integrity Center** reads from `DevModeStore` to simulate attack states (Root, MITM, Replay, Integrity Fail). This allows testing of UI reactions without needing a real rooted device. Release builds ignore these toggles.

## Tech Stack Preferences

- **Android:**
    - **Networking:** Ktor client on OkHttp engine (for cert pinning/interceptors).
    - **DI:** Koin (`koin-androidx-compose`).
    - **Serialization:** kotlinx.serialization.
    - **UI:** Jetpack Compose.
- **Backend:**
    - **Runtime:** Node.js (TypeScript).
    - **Framework:** Express.
    - **Database:** Lightweight JSON-on-disk via `jsonStore.ts`.

## Development Workflows

### Building & Running

#### Backend
```bash
cd backend
npm install
npm run dev      # Starts on http://localhost:8787
npm run typecheck # Required before committing TS changes
```

#### Android
```bash
# To point the app at your local backend:
./gradlew fortressTunnel      # Spawns ngrok and updates local.properties

# Standard build commands:
./gradlew assembleDebug       # Build debug APK
./gradlew :app:installDebug   # Install on emulator
./gradlew :app:testDebugUnitTest # Run unit tests
```

### Documentation Standards
- **Mirroring:** New chapters must mirror the structure of `docs/07-biometric-hardening.md`.
- **Dual-Narrative:** Always include both 🛡️ Defender and ⚔️ Attacker sections.
- **Localization:** Brazilian Portuguese translations live in `docs/pt-BR/` with mirrored filenames.

### Commit Policy
- **Micro-commits:** One logical change per commit (e.g., one screen, one module, one doc).
- **Conventional Commits:** Use `feat:`, `fix:`, `docs:`, `build:`, `style:`, `chore:`.
- **Anonymity:** **NEVER** mention AI assistants, Claude, or LLMs in commit messages. Messages must read as if written by a human engineer (Jackson).
- **Staging:** Use explicit file paths (`git add path/to/file`); avoid `git add .` or `git add -A`.

## Project Structure

- `app/`: Android application source code.
- `backend/`: Demo backend source code.
- `docs/`: Documentation library, design system, and codelabs.
- `docs/pt-BR/`: Brazilian Portuguese translations of documentation.
- `scripts/`: Automation scripts for local tunnels and build tasks.
- `vercel.json`: Deployment configuration for the backend.