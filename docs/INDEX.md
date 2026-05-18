# 📚 Fortress documentation index

> 🇧🇷 **Versão em português brasileiro:** [`pt-BR/INDEX.md`](pt-BR/INDEX.md) — tradução completa (17/17 arquivos).

Each file is a **dual narrative**: 🛡️ Defender (how I built this) alongside ⚔️ Attacker (how I'd
try to break it). The sample style is set by [07 — Biometric Hardening](07-biometric-hardening.md);
treat it as the canonical reference for depth + tone.

## Core authentication series (synthesises the Stackademic 10-part series)

| # | Topic | Status |
|---|---|---|
| 01 | [Stateless auth blueprint](01-stateless-auth.md) — JWT, scaling, key rotation | ✅ |
| 02 | [Hardware-backed token vault](02-hardware-vault.md) — Keystore + StrongBox | ✅ |
| 03 | [OkHttp interceptor pattern](03-interceptor-pattern.md) — single-flight refresh, race conditions | ✅ |
| 04 | [Passkeys](04-passkeys.md) — `androidx.credentials`, FIDO2 server | ✅ |
| 05 | [Play Integrity](05-play-integrity.md) — standard request, server verification | ✅ |
| 06 | [Token lifecycle](06-token-lifecycle.md) — rotation, revocation, reuse detection | ✅ |
| 07 | [Biometric hardening + user intent](07-biometric-hardening.md) — `CryptoObject` binding | ✅ |
| 08 | [Network warfare](08-network-warfare.md) — certificate pinning, MITM defence | ✅ |
| 09 | [Zero trust](09-zero-trust.md) — device binding, risk signals | ✅ |
| 10 | [System design](10-system-design.md) — staff-level architecture trade-offs | ✅ |

## App design

| Topic | Status |
|---|---|
| [Design system — the "Vault" palette](design-system.md) — colour tokens, type scale, components, icons, light/dark, accessibility | ✅ |

## Hands-on codelabs

Step-based learning surface rendered on the live site at
[`/codelabs`](https://umain-fortress.vercel.app/codelabs); markdown sources under
[`docs/codelabs/`](codelabs/README.md). **All 28 codelabs are fully authored** — covering the
full spectrum from beginner tooling to advanced attestation and offensive bypasses:

| # | Codelab | Level | Status |
|---|---|---|---|
| 1 | [OWASP Mobile Top 10 for humans](codelabs/mobile-top-10.md) | Beginner | ✅ |
| 2 | [Stateless auth blueprint](codelabs/stateless-auth-blueprint.md) | Intermediate | ✅ |
| 3 | [Hardware-backed token vault](codelabs/hardware-vault.md) | Intermediate | ✅ |
| 4 | [OkHttp interceptor pattern](codelabs/interceptor-pattern.md) | Intermediate | ✅ |
| 5 | [Network warfare + cert pinning](codelabs/network-warfare.md) | Intermediate | ✅ |
| 6 | [Device Attestation 101](codelabs/device-attestation-101.md) | Intermediate | ✅ |
| 7 | [Biometric hardening + user intent](codelabs/biometric-hardening.md) | Advanced | ✅ |
| 8 | [Defending against Android overlay attacks](codelabs/android-overlay-attacks.md) | Advanced | ✅ |
| 9 | [Privacy vs Security](codelabs/privacy-vs-security.md) | Beginner | ✅ |
| 10 | [Fingerprinting Android Devices](codelabs/fingerprinting-android-devices.md) | Intermediate | ✅ |
| 11 | [Bulletproof Security](codelabs/bulletproof-security.md) | Advanced | ✅ |
| 12 | [Passkeys](codelabs/passkeys.md) | Advanced | ✅ |
| 13 | [Play Integrity](codelabs/play-integrity.md) | Advanced | ✅ |
| 14 | [Zero Trust](codelabs/zero-trust.md) | Advanced | ✅ |
| 15 | [Cuttlefish emulators](codelabs/cuttlefish.md) | Intermediate | ✅ |
| 16 | [AVDs Beyond the Obvious](codelabs/avds-beyond-the-obvious.md) | Intermediate | ✅ |
| 17 | [Root detection in 2026](codelabs/root-detection-2026.md) | Advanced | ✅ |
| 18 | [System design](codelabs/system-design.md) | Advanced | ✅ |
| 19 | [Android CLI tools](codelabs/android-cli-tools.md) | Beginner | ✅ |
| 20 | [Android goes undercover](codelabs/android-goes-undercover.md) | Beginner | ✅ |
| 21 | [Manufacturers' dilemma](codelabs/manufacturers-dilemma.md) | Beginner | ✅ |
| 22 | [Hackers need hobbies](codelabs/hackers-need-hobbies.md) | Beginner | ✅ |
| 23 | [Hackers gonna hack](codelabs/hackers-gonna-hack.md) | Intermediate | ✅ |
| 24 | [Trust no one](codelabs/trust-no-one.md) | Intermediate | ✅ |
| 25 | [Custom ROMs and rooted devices](codelabs/custom-roms-and-rooted-devices.md) | Intermediate | ✅ |
| 26 | [Automating input events](codelabs/automating-input-events.md) | Intermediate | ✅ |
| 27 | [Verifying installer source](codelabs/verifying-installer-source.md) | Intermediate | ✅ |
| 28 | [Token lifecycle](codelabs/token-lifecycle.md) | Intermediate | ✅ |

See [`docs/codelabs/README.md`](codelabs/README.md) for the full navigation.

## Offensive deep dives

| # | Topic | Status |
|---|---|---|
| 11 | [Root detection in 2026](11-root-detection.md) — what actually works | ✅ |
| 12 | [APK decompiling](12-decompiling.md) — the dark art | ✅ |
| 13 | [Play Integrity bypass](13-play-integrity-bypass.md) — what's circulating in the wild | ✅ |
| 14 | [RASP strategies](14-rasp-strategies.md) — runtime application self-protection | ✅ |
| 15 | [KernelSU on Android emulators](15-emulator-rooting.md) (Apple Silicon) | ✅ |
| 16 | [Exploiting content providers](16-content-providers.md) | ✅ |

## Reading order

If you're new to mobile auth: 01 → 02 → 03 → 06 → 07 → 04 → 05 → 09 → 08 → 10.

If you came for the offence: 12 → 11 → 13 → 14 → 16 → 07 (defence) → 09 → 02.
