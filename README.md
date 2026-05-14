# Fortress

A working Android security showcase. Half of it is a fintech app (Fortress Bank, Kotlin +
Jetpack Compose). The other half is a documentation library that tells every modern Android
security story from two sides: the defender who built the control, and the attacker who knows
where the seams are.

The pairing matters. Reading either side alone leaves the gap an attacker will walk through.
Reading them together is the only honest threat model.

---

## How to read this project

Three layers. Pick the entry point that matches what you came for.

### As an attacker

You're here for the offensive playbook — how to break what was built, what tools the wider
community is shipping, what bypass surfaces exist in real apps. Read in this order:

1. [docs/12 — APK decompiling](docs/12-decompiling.md). Start with the static-analysis kit:
   `apktool`, `jadx-gui`, smali patching, resign and reinstall. The five-step workflow that
   makes everything else possible.
2. [docs/11 — Root detection in 2026](docs/11-root-detection.md). Why every userspace check
   has been defeated by default, what actually still works.
3. [docs/13 — Play Integrity bypass](docs/13-play-integrity-bypass.md). The Magisk + Zygisk
   + Play Integrity Fix kit, decryption servers, the verdict-ladder game.
4. [docs/14 — RASP strategies](docs/14-rasp-strategies.md). In-process probes the defender
   plants, how each one falls to a Frida script.
5. [docs/15 — KernelSU on emulators](docs/15-emulator-rooting.md). Setting up a sparring
   partner on your laptop without a physical rooted phone.
6. [docs/16 — Exploiting content providers](docs/16-content-providers.md). SQL injection,
   path traversal, `drozer` automation against the IPC surface.
7. Cross-read the defender chapters as foils:
   [07 — Biometric hardening](docs/07-biometric-hardening.md),
   [08 — Network warfare](docs/08-network-warfare.md),
   [09 — Zero trust](docs/09-zero-trust.md),
   [02 — Hardware vault](docs/02-hardware-vault.md).

Every doc has a `⚔️ Attacker` half with seven to nine numbered bypass scenarios. Skim those
first if you're triaging.

### As a defender

You're here for the architecture — what to build, in what order, with what guarantees. Read
in this order:

1. [docs/01 — Stateless auth blueprint](docs/01-stateless-auth.md). JWT shape, asymmetric
   signing, refresh-token rotation, claim hygiene.
2. [docs/02 — Hardware-backed token vault](docs/02-hardware-vault.md). Where the tokens live
   at rest. AES-256-GCM, StrongBox, what the TEE buys you.
3. [docs/03 — Interceptor pattern](docs/03-interceptor-pattern.md). The single-flight refresh
   on 401, mutex-protected, replay-safe.
4. [docs/06 — Token lifecycle](docs/06-token-lifecycle.md). Rotation, revocation, reuse
   detection as the canonical fraud signal.
5. [docs/07 — Biometric hardening](docs/07-biometric-hardening.md) — the canonical sample
   for tone and depth. Read this if you read nothing else.
6. [docs/04 — Passkeys](docs/04-passkeys.md). `androidx.credentials`, WebAuthn server,
   recovery-flow attacks.
7. [docs/05 — Play Integrity](docs/05-play-integrity.md). Standard vs classic flows, server
   verification, verdict policy.
8. [docs/08 — Network warfare](docs/08-network-warfare.md). Certificate pinning, MITM defence.
9. [docs/09 — Zero trust](docs/09-zero-trust.md). Device binding, the `cnf` claim, risk
   signal blending.
10. [docs/10 — System design](docs/10-system-design.md). Staff-level architecture at 5M users
    — service split, KMS custody, the seams a defender hardens last.

### As a designer or engineer reading the app

The app is the executable accompaniment to the docs. Two paths:

- [docs/design-system.md](docs/design-system.md) — the "Vault" contract. Tokens, type scale,
  components, icon registry, light/dark, accessibility.
- [docs/INDEX.md](docs/INDEX.md) — the full table of contents.

---

## Running the demo

Two pieces: a TypeScript backend, an Android app.

### 1. Start the backend

```bash
cd backend
npm install
npm run dev
```

Listens on `http://localhost:8787`. On first start it seeds the demo user
`alice@fortress.dev / passw0rd!` and a small set of accounts, transactions and cards.

The state lives on disk under `backend/data/*.json` (gitignored). Wipe with `rm
backend/data/*.json` and re-start to get a clean slate.

### 2. Run the Android app

Open in Android Studio and run on any emulator with biometric enrolled (Extended controls →
Fingerprint). The backend URL is never hardcoded — it comes from configuration, in this order:

1. `local.properties#fortress.baseUrl` — gitignored, per-developer.
2. `-Pfortress.baseUrl=https://your-host/` — CLI override.
3. `gradle.properties#fortress.baseUrl` — committed default (Vercel).

If you want the app to hit your local Node backend, use the tunnel:

```bash
# In one shell
cd backend && npm run dev

# In another
./gradlew fortressTunnel        # ngrok http 8787, writes the public URL to local.properties
./gradlew :app:installDebug     # build picks up the ngrok URL via BuildConfig.BASE_URL

# When you're done
./gradlew fortressTunnelStop    # kills ngrok and removes the override
```

Requirement: `ngrok` in `$PATH` with a configured authtoken
(`ngrok config add-authtoken …`).

Demo login: `alice@fortress.dev` / `passw0rd!`. After the first successful login the device
generates an ECDSA P-256 keypair inside the Android Keystore, registers the public key with the
backend, and gates all subsequent step-up flows (IBAN reveal, money transfer, card PAN reveal)
behind a fresh `BiometricPrompt` signing the server's nonce.

### Dev Mode

Debug builds expose a Dev Mode screen via the Profile tab. Four toggles simulate attack
scenarios so each defence is visible in the running app:

- Simulate root / Magisk → integrity verdict goes Untrusted, sensitive ops refuse.
- Simulate MITM proxy → verdict goes Limited.
- Simulate replayed challenge → server rejects with `CHALLENGE_REJECTED`.
- Simulate Play Integrity fail → verdict goes Untrusted with a Play Integrity reason.

Release builds (`BuildConfig.ALLOW_DEV_MODE=false`) ignore the toggles.

---

## Project structure

```
.
├── app/                      Android app (Kotlin + Jetpack Compose, Vault design system)
├── backend/                  TypeScript + Express demo backend, JSON-on-disk store
├── docs/                     Dual-narrative documentation library (16 chapters)
│   ├── design-system.md      The "Vault" design contract
│   └── INDEX.md              Full table of contents
├── scripts/                  Local tunnel automation (ngrok)
└── vercel.json               Vercel deployment config for the backend
```

The runtime stack and versions live in `gradle/libs.versions.toml` (app) and
`backend/package.json` (backend). Read those, not this README, when picking a dependency.

---

## License

MIT — for educational use. The demo backend is intentionally small (no real database, no rate
limits, no replication, no HSM-backed signing). Don't lift it wholesale into production. The
threat-model notes in [docs/01-stateless-auth.md](docs/01-stateless-auth.md) and
[docs/10-system-design.md](docs/10-system-design.md) cover what the production picture should
look like.

## Credits

**Jackson Mafra** — Mobile & Security Engineer, [Umain](https://umain.com).

Synthesised from the Stackademic *Scaling Android Auth* series and a wider set of community
deep-dives on root detection, Play Integrity, RASP, KernelSU, and content-provider exploitation.
Every chapter ends with a `References` block linking the source articles.
