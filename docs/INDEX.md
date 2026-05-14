# 📚 Fortress documentation index

Each file is a **dual narrative**: 🛡️ Defender (how I built this) alongside ⚔️ Attacker (how I'd
try to break it). The sample style is set by [07 — Biometric Hardening](07-biometric-hardening.md);
treat it as the canonical reference for depth + tone.

## Core authentication series (synthesises the Stackademic 10-part series)

| # | Topic | Status |
|---|---|---|
| 01 | [Stateless auth blueprint](01-stateless-auth.md) — JWT, scaling, key rotation | ✅ |
| 02 | [Hardware-backed token vault](02-hardware-vault.md) — Keystore + StrongBox | ✅ |
| 03 | [OkHttp interceptor pattern](03-interceptor-pattern.md) — single-flight refresh, race conditions | ✅ |
| 04 | Passkeys — `androidx.credentials`, FIDO2 server | 🚧 |
| 05 | Play Integrity — standard request, server verification | 🚧 |
| 06 | [Token lifecycle](06-token-lifecycle.md) — rotation, revocation, reuse detection | ✅ |
| 07 | [Biometric hardening + user intent](07-biometric-hardening.md) — `CryptoObject` binding | ✅ |
| 08 | [Network warfare](08-network-warfare.md) — certificate pinning, MITM defence | ✅ |
| 09 | Zero trust — device binding, risk signals | 🚧 |
| 10 | System design — staff-level architecture trade-offs | 🚧 |

## Offensive deep dives

| # | Topic | Status |
|---|---|---|
| 11 | Root detection in 2026 — what actually works | 🚧 |
| 12 | APK decompiling — the dark art | 🚧 |
| 13 | Play Integrity bypass — what's circulating in the wild | 🚧 |
| 14 | RASP strategies — runtime application self-protection | 🚧 |
| 15 | KernelSU on Android Studio emulators (Apple Silicon) | 🚧 |
| 16 | Exploiting content providers | 🚧 |

## Reading order

If you're new to mobile auth: 01 → 02 → 03 → 06 → 07 → 04 → 05 → 09 → 08 → 10.

If you came for the offence: 12 → 11 → 13 → 14 → 16 → 07 (defence) → 09 → 02.
