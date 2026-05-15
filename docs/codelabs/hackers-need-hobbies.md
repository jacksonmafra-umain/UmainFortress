---
title: "Mobile security — because hackers need hobbies too"
slug: hackers-need-hobbies
level: beginner
estimated_minutes: 20
status: published
company: Fortress
tags:
  - fundamentals
  - threat-modelling
  - owasp
summary: >
  A short opinionated tour of the mobile-security landscape — who attacks mobile apps,
  why, what they want, and the minimum defences a 2026 release should ship with. Read
  this before you read any other Fortress codelab.
references:
  - title: "Mobile Security — Hackers Need Hobbies Too (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/mobile-security-because-hackers-need-hobbies-too-6b84b0ab52d8
  - title: "OWASP Mobile Top 10 — official site"
    url: https://owasp.org/www-project-mobile-top-10/
  - title: "Verizon DBIR — Mobile chapter, current year"
    url: https://www.verizon.com/business/resources/reports/dbir/
---

## Welcome to the landscape

Understand who attacks mobile apps and why, before reading anything about how to defend.

Mobile-security writing tends to jump straight to controls — Keystore, Play Integrity,
certificate pinning — without grounding the reader in what they are defending against and
who is doing the attacking. The result is a list of "best practices" that feel like dogma.
This codelab fixes the order. We start with the attacker landscape, walk through what they
want, and arrive at the minimum defence set on the other side.

> **Why this matters.** Defences without a threat model are theatre. Threat models without
> defences are essays. The two only become useful when they meet in the middle.

---

## Step 1: Who actually attacks your app?

Four broad categories. The right defences depend on which categories you take seriously.

1. **Curious / opportunistic.** Researchers, students, the bored. They reverse APKs they
   downloaded. Low effort, high publicity if they find something cheap.
2. **Fraudsters.** Out for direct financial gain. They want stolen sessions, replayed
   transactions, money mules. Almost all banking-app attacks are this category.
3. **Organised crime.** Banking trojans, accessibility-abusing overlays, SIM-swap
   coordination, romance scams paired with mobile remote-access. Run as a business.
4. **Nation-state.** Targeted spyware (Pegasus, Predator). You probably do not defend
   against this, but the controls that do help everyone — sandboxing, ephemeral data,
   minimal attack surface.

A fintech app should design for categories 2 and 3. Category 1 will find things; that is a
feature of the open-source dependency tree. Category 4 is a recovery problem, not a
prevention problem.

> **Why this matters.** Each category has different motivation, budget, and persistence.
> Stacking defences in priority order beats spreading effort uniformly.

---

## Step 2: What attackers actually want

Three primary objectives in mobile-finance attacks:

1. **Take over the account.** Steal a session, register a new device, change recovery
   contact, drain.
2. **Forge a transaction.** Authorise a transfer the legitimate user did not approve, via
   an overlay or accessibility hijack at the moment of step-up.
3. **Exfiltrate data.** Scrape contacts, screenshots, OTPs from notifications, full
   transaction history. Often the precursor to (1) or (2) on a different platform.

Everything below is in service of preventing one of these three outcomes. When a control
does not map to one of them, ask why it exists.

> **Why this matters.** Defence backlogs grow without bound if every "interesting" idea
> earns a slot. Anchoring controls to one of three concrete attacker goals keeps the
> backlog honest.

---

## Step 3: The minimum defence set

Six controls a 2026 banking app should ship with on day one. Anything missing here is a
choice you should be able to defend.

1. **Server-side authorisation.** Every protected route re-checks identity. The client
   only displays. (See [Stateless Auth](/codelabs/stateless-auth-blueprint).)
2. **Tokens behind the Keystore.** Refresh tokens encrypted at rest with a TEE-backed AES
   key. (See [Hardware Vault](/codelabs/hardware-vault).)
3. **Certificate pinning.** SHA-256 SPKI hashes for the API host, refused at both the
   platform and the OkHttp layer. (See [Network Warfare](/codelabs/network-warfare).)
4. **Biometric step-up for irreversible actions.** Transfer, IBAN reveal, card-PAN reveal
   gated by a `CryptoObject`-bound signature. (See
   [Biometric Hardening](/codelabs/biometric-hardening).)
5. **Device attestation.** Keystore signature + Play Integrity for the server to
   distinguish *known device* from *unknown device*. (See
   [Device Attestation 101](/codelabs/device-attestation-101).)
6. **Overlay defence.** `filterTouchesWhenObscured` on every irreversible button plus an
   accessibility-service allow-list. (See
   [Overlay Attacks](/codelabs/android-overlay-attacks).)

A 2026 release missing any of these is shipping in a pre-2020 threat model.

> **Why this matters.** Most security incidents in production banking apps trace to a
> missing item on this list, not to a novel zero-day.

---

## Step 4: The attacker toolkit

A short tour of what the attackers themselves run. Knowing the tools narrows the surface
you need to harden.

- `apktool` and `jadx` — static decompilation. Every APK is a zip; either tool will read
  yours in seconds.
- `frida` — runtime instrumentation. Hooks any Java/Kotlin/native method on a rooted
  device or an emulator. Used to bypass jailbreak checks, dump tokens, replay flows.
- `mitmproxy` and `Burp` — TLS interception. Defeats anything without certificate pinning.
- `Magisk` and `Magisk Hide` plus `Zygisk` modules — root, with the rooted-state hidden
  from app-side checks.
- `Play Integrity Fix` modules — re-spoof the device fingerprint to satisfy Play
  Integrity from a rooted phone.
- Cheap Android-on-PC VMs and `Cuttlefish` — when an actual rooted device is too risky to
  carry, the attacker uses cloud emulators.

Three of those — `frida`, `mitmproxy`, `apktool` — are also legitimate defender tools.

> **Why this matters.** The defender's toolkit and the attacker's toolkit overlap almost
> entirely. The skill is which side you point them at.

---

## Step 5: The OWASP Mobile Top 10 in a single line each

A telegraphic recap. Read the full
[OWASP Mobile Top 10 codelab](/codelabs/mobile-top-10) for examples.

- **M1 Improper Credential Usage** — secrets shipped in the APK.
- **M2 Inadequate Supply Chain Security** — untrusted libraries.
- **M3 Insecure Authentication / Authorisation** — client-side permission checks.
- **M4 Insufficient Input / Output Validation** — untrusted intents, deep links, WebViews.
- **M5 Insecure Communication** — plain HTTP or no pinning.
- **M6 Inadequate Privacy Controls** — PII in logs, screenshots, backup.
- **M7 Insufficient Binary Protections** — no R8, no anti-tamper, no integrity check.
- **M8 Security Misconfiguration** — exported components, debuggable, lax FileProvider.
- **M9 Insecure Data Storage** — plaintext SharedPreferences.
- **M10 Insufficient Cryptography** — custom crypto, MD5, ECB, reused IVs.

Every item maps to one of the three attacker objectives in Step 2. Walk the list at least
once per release.

> **Why this matters.** Memorise the list. An auditor will rate you on it. So will the
> attacker — they just use different paperwork.

---

## Step 6: Where defences fail in practice

Three patterns that are universal across mobile-security incidents:

1. **The fourth bypass nobody planned for.** Defences are designed to handle attacks the
   designer imagined. The bypass that wins is the one nobody imagined.
2. **The compromise in the release pipeline, not the app.** A signing key on a CI worker,
   a token in a build log, a vendor SDK with a malicious update. The app is hardened; the
   build is not.
3. **Recovery flows.** Reset-password, sign-out-everywhere, restore-from-backup. The
   wrap-around paths that everyone forgets to test.

Build in monitoring (telemetry on pin failures, integrity verdicts, refresh-reuse), not
just controls. Surprise is the enemy.

> **Why this matters.** Defence in depth assumes the layers fail one at a time. Telemetry
> is what tells you which layer is currently failing.

---

## Step 7: What good looks like in 2026

Five non-negotiables for a fintech mobile release:

1. **Minimum SDK 33+.** Older versions expose attack surfaces the new ones removed.
2. **No exported components.** Exception: launcher activity. Every other component
   declares `android:exported="false"`.
3. **A working Dev Mode toggle for each defence.** The Fortress demo ships four (simulate
   root, MITM, replayed challenge, integrity fail). Without these, recovery paths are
   untested.
4. **Telemetry on every defence boundary.** Pin failure, refresh-reuse, attestation
   verdict, biometric-prompt error code. Aggregated server-side.
5. **A documented incident playbook.** Three paragraphs is enough. "How to rotate a
   compromised pin." "How to revoke a leaked refresh-token family." "How to push a
   minimum-version cutoff." When you need them, you need them quickly.

> **Why this matters.** Mature security is operational, not structural. The control list
> is the easy part; the running-it-on-Saturday-at-3am part is what separates teams.

---

## Step 8: Read these next

This codelab is the foreword to a larger arc. The reading order:

1. [OWASP Mobile Top 10 for humans](/codelabs/mobile-top-10) — the breadth.
2. [Stateless Auth Blueprint](/codelabs/stateless-auth-blueprint) — the server side of
   token life.
3. [Hardware Vault](/codelabs/hardware-vault) — the client side of token life.
4. [Interceptor Pattern](/codelabs/interceptor-pattern) — the network glue.
5. [Network Warfare](/codelabs/network-warfare) — TLS, pinning, MITM defence.
6. [Device Attestation 101](/codelabs/device-attestation-101) — Keystore + Play Integrity.
7. [Biometric Hardening](/codelabs/biometric-hardening) — CryptoObject-bound step-up.
8. [Overlay Attacks](/codelabs/android-overlay-attacks) — the cherry on top.

Eight codelabs, roughly four hours total. That is the entry-level defender's curriculum
for a 2026 Android fintech engineer.

> **Why this matters.** The full arc reads as one story. Skipping a step leaves a gap an
> attacker walks through.

---

## Wrap-Up

You now have a mental map of the mobile-security landscape — who attacks, what they want,
which defences map to which goals, and where defences typically fail.

Next mission: open the [OWASP Mobile Top 10 codelab](/codelabs/mobile-top-10). It is the
next narrowing step in the funnel from "the attacker landscape" to "what you actually
ship on Monday".

**Recap of what you just internalised:**

- Four attacker categories, sized by motivation and budget.
- Three attacker objectives: account takeover, transaction forgery, data exfiltration.
- A six-item minimum defence set, every item mapped to a deeper codelab.
- The five tools the attacker reaches for and the overlap with the defender's kit.
- Three patterns where defences fail in practice — designed-against, build-pipeline,
  recovery flows.
- Five non-negotiables for a 2026 mobile-fintech release.
