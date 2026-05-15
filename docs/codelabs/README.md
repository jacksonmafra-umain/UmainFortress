# 🧪 Fortress codelabs

Hands-on, step-based walk-throughs of the topics the rest of this repository documents.
Each codelab ships as a single Markdown file with **YAML frontmatter** + a sequence of
**step blocks** separated by horizontal rules (`---`). The same files render on the live
site at [`/codelabs`](https://umain-fortress.vercel.app/codelabs).

## Status

**28 codelabs total**: 8 fully authored (✅), 20 scaffolded as drafts (🚧) with reference
links to their Medium long-form. The published set tells one coherent story end-to-end —
the *defender's core arc* — and is the recommended reading order.

### Published — defender's core arc

| # | Codelab | Level | Time | Steps |
|---|---|---|---|---|
| 1 | [OWASP Mobile Top 10 for humans](mobile-top-10.md) | Beginner | 25 min | 12 |
| 2 | [Stateless auth blueprint](stateless-auth-blueprint.md) | Intermediate | 30 min | 12 |
| 3 | [Hardware-backed token vault](hardware-vault.md) | Intermediate | 30 min | 12 |
| 4 | [OkHttp interceptor pattern — single-flight refresh](interceptor-pattern.md) | Intermediate | 30 min | 12 |
| 5 | [Network warfare — certificate pinning + MITM defence](network-warfare.md) | Intermediate | 30 min | 12 |
| 6 | [Device Attestation 101](device-attestation-101.md) | Intermediate | 35 min | 11 |
| 7 | [Biometric hardening + user intent](biometric-hardening.md) | Advanced | 35 min | 12 |
| 8 | [Defending against Android overlay attacks](android-overlay-attacks.md) | Advanced | 30 min | 11 |

### Drafts — scheduled

Each draft is a frontmatter-only stub linking to the canonical Medium article (or Fortress
doc) for the long-form. They show up in the live `/codelabs` library with a `draft` chip;
filter by status to hide them.

Beginner: `privacy-vs-security`, `hackers-need-hobbies`, `android-cli-tools`,
`android-goes-undercover`, `manufacturers-dilemma`. Intermediate: `hackers-gonna-hack`,
`fingerprinting-android-devices`, `trust-no-one`, `custom-roms-and-rooted-devices`,
`cuttlefish`, `avds-beyond-the-obvious`, `automating-input-events`,
`verifying-installer-source`, `token-lifecycle`. Advanced: `bulletproof-security`,
`passkeys`, `play-integrity`, `root-detection-2026`, `system-design`, `zero-trust`.

## File layout

```
docs/codelabs/
├── README.md                          ← this file
├── mobile-top-10.md                   ← Beginner — OWASP Mobile Top 10 for humans
├── stateless-auth-blueprint.md        ← Intermediate — JWT, refresh, rotation
├── hardware-vault.md                  ← Intermediate — Keystore-sealed tokens
├── interceptor-pattern.md             ← Intermediate — single-flight 401 → refresh
├── network-warfare.md                 ← Intermediate — cert pinning, MITM defence
├── device-attestation-101.md          ← Intermediate — attestation deep dive
├── biometric-hardening.md             ← Advanced — CryptoObject, payload-bound step-up
├── android-overlay-attacks.md         ← Advanced — overlay attack defence
└── …                                  ← 20 more drafts, see Status table above
```

## Frontmatter contract

```yaml
---
title: Device Attestation 101
slug: device-attestation-101
level: intermediate         # beginner | intermediate | advanced
estimated_minutes: 20
status: published           # published | draft
company: Fortress           # short label shown on the library card
tags:                       # filterable, free-form
  - attestation
  - device-integrity
  - keystore
summary: >
  Build a minimal device-attestation flow end to end — key generation, signed
  challenge, server-side verification, verdict policy.
references:
  - title: "Device Attestation 101 (Medium)"
    url: https://medium.com/@jacksonfdam/device-attestation-101-making-sure-your-users-arent-evil-robots-75928cc1bd0c
---
```

## Step blocks

Steps are introduced by a markdown header that starts with `## Step ` (or `## Wrap-Up`
for the final step) and are separated by horizontal rules. The loader splits on those
boundaries; everything between two `---` lines belongs to the current step.

```markdown
## Welcome to <topic>

Short paragraph describing what the codelab covers and who it's for.

> **Why this matters.** One-sentence stakes statement.

---

## Step 1: <verb> <noun>

The first hands-on step. Include code blocks where they help.

```kotlin
fun example() = "use real, runnable snippets where possible"
```

---

## Wrap-Up

Recap + suggested next codelab.
```

## Authoring rules

- Keep step titles short and imperative ("Add Koin to the radar", not "We will add Koin").
- Lead each step with a one-sentence description, then the body.
- One actionable code block per step is ideal; two is the max.
- Use a blockquote (`>`) for "Why this matters" callouts — they get pulled into a green
  side panel in the viewer.
- End the codelab with a `## Wrap-Up` step that lists what the reader just built and points
  at the next mission.

## Build pipeline

`docs/codelabs/` is the **canonical** location. The Express server reads from
`backend/src/web/codelabs/` at runtime; the `npm run sync-codelabs` step (called by both
`dev` and `build`) copies the canonical files into the backend tree so Vercel's
`{web,ui}/**` `includeFiles` glob picks them up automatically.
