---
title: "Bulletproof security — attestation × fingerprinting"
slug: bulletproof-security
level: advanced
estimated_minutes: 35
status: published
company: Fortress
tags:
  - attestation
  - fingerprinting
  - fraud
  - policy
summary: >
  Combine device attestation (the deterministic signal) and device fingerprinting (the
  probabilistic signal) into a single trust verdict the backend can act on, and design a
  verdict policy that scales across millions of devices without locking out legitimate
  edge cases.
references:
  - title: "Bulletproof Security — combining attestation + fingerprinting (Jackson Mafra)"
    url: https://medium.com/@jacksonfdam/building-a-bulletproof-security-system-combining-attestation-and-fingerprinting-2f4d65c02128
  - title: "Device Attestation 101 (this catalogue)"
    url: /codelabs/device-attestation-101
  - title: "Fingerprinting Android Devices (this catalogue)"
    url: /codelabs/fingerprinting-android-devices
---

## Welcome to the fused verdict

Understand why neither attestation alone nor fingerprinting alone produces a usable
signal at scale, and how to combine them into something a server can act on.

Attestation is *deterministic* — it asks the device "are you cryptographically a real
Android device?" and gets a yes/no/maybe. Fingerprinting is *probabilistic* — it asks
"are you the same device that signed in last time?" and gets a confidence score. The
two answer different questions; neither one alone is enough. The fused verdict is what
millions-of-devices fraud detection actually runs on.

> **Why this matters.** Attestation catches the "this is not a real device" case.
> Fingerprinting catches the "this is a real device but not the same one as before"
> case. Combined, they catch both.

---

## Step 1: The two questions in detail

Attestation answers: *Is this device a legitimate, unmodified Android device with the
expected security properties?* It uses cryptographic chains rooted in Google or in the
device's TEE. The answer is high-quality but coarse — Trusted / Limited / Untrusted.
See [Device Attestation 101](/codelabs/device-attestation-101) and
[Play Integrity](/codelabs/play-integrity).

Fingerprinting answers: *Is this the same device that signed in last time?* It combines
hardware identifiers, system configuration, and persistent app-instance data into a
hash that should be stable across reinstalls but vary across devices. See
[Fingerprinting Android Devices](/codelabs/fingerprinting-android-devices).

Each signal alone has well-documented bypasses. Combined, the attacker has to defeat
both, on the same device, simultaneously.

> **Why this matters.** Defence-in-depth only works when the layers are independent.
> Attestation and fingerprinting are independent: defeating one does not defeat the
> other.

---

## Step 2: The matrix — four buckets

|                          | Fingerprint matches | Fingerprint mismatches |
|--------------------------|--------------------|-------------------------|
| **Attestation Trusted**  | ✅ Trusted          | ⚠️ Limited — new device  |
| **Attestation Limited**  | ⚠️ Limited          | 🚨 Suspicious — review   |
| **Attestation Untrusted**| 🚨 Untrusted        | 🚨 Untrusted             |
| **No fingerprint yet**   | ⚠️ Limited          | n/a (first install)      |

Reading the corners:

- Top-left: known device, hardware looks real → full trust.
- Top-right: new device, hardware real → likely device-replacement, friction.
- Middle-right: hardware borderline + new device → likely fraudster, review.
- Bottom row: hardware fails attestation → refuse irrespective of fingerprint.

> **Why this matters.** The matrix is short enough to memorise and concrete enough to
> code from. Every other decision in this codelab walks the matrix.

---

## Step 3: Per-action thresholds on top of the matrix

The matrix produces a verdict; the policy is per-action.

```ts
type Outcome = "allow" | "stepUp" | "stepUpStrong" | "deny";
type CellVerdict = "Trusted" | "Limited" | "Suspicious" | "Untrusted";

const ACTION_FLOOR: Record<Action, CellVerdict> = {
  read:         "Limited",
  mutate:       "Limited",
  irreversible: "Trusted",
  lifecycle:    "Trusted",
};

function outcomeFor(action: Action, cell: CellVerdict): Outcome {
  const rank: Record<CellVerdict, number> = { Trusted: 3, Limited: 2, Suspicious: 1, Untrusted: 0 };
  if (rank[cell] === 0) return "deny";
  if (rank[cell] >= rank[ACTION_FLOOR[action]]) return "allow";
  return action === "lifecycle" ? "stepUpStrong" : "stepUp";
}
```

A read on a Limited cell is allowed. A transfer on the same cell asks for biometric.
A password change on any cell below Trusted goes to strong step-up (out-of-band email
or SMS).

> **Why this matters.** A blanket policy is too coarse. The matrix × action grid is the
> shape that ships.

---

## Step 4: Wire the inputs

Three independent inputs feed the matrix:

```ts
async function fusedVerdict(ctx: RequestContext): Promise<CellVerdict> {
  // 1. Attestation — server-side decode of Play Integrity token and Keystore chain.
  const attestation = await attestationVerdict(ctx);

  // 2. Fingerprint — composite computed client-side, salted, sent to server.
  const fp = ctx.compositeFingerprint;
  const knownFp = await deviceFingerprints.find({ userId: ctx.userId, fp });

  // 3. Map into a CellVerdict.
  if (attestation === "Untrusted") return "Untrusted";
  if (attestation === "Trusted" && knownFp) return "Trusted";
  if (attestation === "Trusted" && !knownFp) return "Limited";
  if (attestation === "Limited" && knownFp) return "Limited";
  return "Suspicious";
}
```

Persist the fingerprint on first sight, after the user has stepped up successfully —
that becomes the baseline for the next session.

> **Why this matters.** A fingerprint store that grows only after successful step-up
> resists fingerprint poisoning. The attacker cannot pre-register their own
> fingerprint by trying once.

---

## Step 5: Edge cases — new device, OS upgrade, fingerprint drift

Three legitimate cases produce `Suspicious` if naïvely handled:

1. **User got a new phone.** Different hardware → different fingerprint. Real human.
2. **OS upgrade.** `Build.HARDWARE` sometimes changes between OS major versions.
3. **User changed system config.** Timezone on travel, font scale for accessibility.

Mitigations:

- For (1): allow step-up to enrol the new fingerprint. Surface "Sign in to this new
  device" UX explicitly.
- For (2): re-fetch the hardware fingerprint on every cold start; refresh the server
  copy when the user successfully signs in.
- For (3): split the composite — *hardware* part is stable, *system-config* part is
  expected to drift. Match on hardware alone for the "is same device" question; system
  drift is informational, not blocking.

> **Why this matters.** Cases (1)-(3) are common. A policy that locks out the user in
> any of them is a policy that loses users.

---

## Step 6: The Trusted-fingerprint hijack scenario

What if an attacker spoofs the fingerprint? They can fake every `Build.*` field if they
control the device. The mitigation:

1. **Attestation veto.** Hardware fingerprint match without a passing attestation is
   *not* Trusted. The matrix already encodes this.
2. **Fingerprint salt.** The composite is salted with a per-user secret. Steal one
   user's fingerprint, you cannot port it to a different account.
3. **Velocity check.** Two simultaneous sessions from the same fingerprint on different
   IPs is suspicious regardless of the matrix.

The matrix + salt + velocity stack is what makes the fused verdict robust against
fingerprint spoofing alone.

> **Why this matters.** A single defence is brittle. The fusion is what survives
> determined adversaries.

---

## Step 7: Operate on the verdict — telemetry, alerts, reviews

Four events. Aggregate.

```ts
type FusedEvent =
  | { kind: "fused.cell"; userId: string; cell: CellVerdict; attestation: string; knownFp: boolean }
  | { kind: "fused.escalation"; userId: string; from: CellVerdict; to: CellVerdict; reason: string }
  | { kind: "fused.review"; userId: string; cell: CellVerdict; action: Action; outcome: Outcome }
  | { kind: "fused.policy.shadow"; userId: string; live: Outcome; shadow: Outcome };
```

- `fused.cell` baselines the population distribution. Shifts across hours indicate
  campaigns.
- `fused.escalation` traces a user's verdict over time. A device that drops from
  Trusted to Suspicious in one session is a fraud lead.
- `fused.review` is the helpdesk's evidence package — what was the verdict at the time
  of the failed action.
- `fused.policy.shadow` runs new policy candidates without acting.

> **Why this matters.** A verdict that is never reviewed is a verdict that drifts.
> Telemetry keeps the policy honest.

---

## Step 8: Performance budget

The fused verdict runs on every authenticated request. Budget:

- Attestation decode: ~30ms (with Play Integrity service-side cache).
- Fingerprint lookup: ~5ms (indexed by user × fp).
- Velocity check: ~5ms (Redis-sliding-window or equivalent).
- Cell computation: <1ms.

Forty milliseconds at p95. Below the human-perception threshold; safe to run inline.
Above 100ms it becomes a UX problem; pre-fetch and cache aggressively in that case.

> **Why this matters.** A defence that adds 300ms to every request is a defence the
> product team will quietly disable. Stay inside the latency budget.

---

## Step 9: Privacy posture

The fused verdict collects more identifying data than either signal alone. Documentation
matters. The fingerprint section of the Security Center copy should be the longest one:

```text
What we collect for fraud detection
  ✓ Device hardware identifiers (manufacturer, model, board, ABI)
  ✓ System configuration (locale, timezone, screen size)
  ✓ Attestation tokens from Google Play Integrity
  ✓ Telemetry of which actions were attempted from which device

What we do not collect
  ✗ Contact list, photos, location, microphone, camera
  ✗ Cross-app browsing data
  ✗ Advertising or analytics beyond what is needed for fraud
```

The honesty is itself a defence. Users who understand what is collected do not invent
worse workarounds.

> **Why this matters.** A fused verdict that ships without a clear privacy story is a
> regulator-fine waiting for an audit.

---

## Step 10: Rollout choreography

Bulletproof security is a *large* change. Roll it out in five stages, each at least a
week long:

1. **Shadow stage.** Compute the fused verdict alongside whatever you ship today. Take
   no action. Compare outcomes.
2. **Canary on reads.** Apply the verdict to read actions only. Watch the support
   tickets.
3. **Canary on low-stakes mutations.** Add preference toggles, cosmetic mutations.
4. **All actions, low percentage.** 1 % of users see the full policy.
5. **Full rollout.** 100 % of users.

Each stage has a documented kill-switch back to the previous stage. The kill-switch is
the only thing that matters at 3am.

> **Why this matters.** The biggest risk of zero-trust is rolling it out too fast. The
> five-stage choreography manages that risk.

---

## Wrap-Up

You now have a complete fused-verdict stack — deterministic attestation + probabilistic
fingerprinting fused into a four-quadrant CellVerdict, per-action policy on top, edge-
case handling for legitimate new-device and config-drift situations, fingerprint-
hijack mitigations, telemetry, performance budget, privacy story, and a safe rollout
choreography.

Next mission:
- [System Design](/codelabs/system-design) — staff-level architecture for these
  controls at scale.
- [Zero Trust](/codelabs/zero-trust) — the policy framework this verdict feeds into.
- [Passkeys](/codelabs/passkeys) — the credential layer that completes the picture.

**Recap of the bulletproof stack:**

- Attestation answers "is the device real"; fingerprinting answers "is it the same".
- A 4-cell matrix on top of the two yields Trusted / Limited / Suspicious / Untrusted.
- A per-action floor table maps cells to allow / stepUp / stepUpStrong / deny.
- Three independent inputs (Play Integrity, Keystore chain, composite fingerprint).
- Mitigations for fingerprint-hijack via attestation veto + salt + velocity.
- Four telemetry events for verdict patterns and policy candidates.
- A 40ms p95 latency budget that keeps the verdict inline.
- A five-stage rollout choreography with documented kill-switches.
