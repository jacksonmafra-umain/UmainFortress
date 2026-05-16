---
title: "Trust no one — why device verification matters"
slug: trust-no-one
level: intermediate
estimated_minutes: 20
status: published
company: Fortress
tags:
  - zero-trust
  - policy
  - device-binding
summary: >
  The threat model that drives zero-trust mobile architectures, and the smallest set of
  controls that make "the device is what it says it is" a defensible claim — without
  collapsing into "lock everything down for everyone".
references:
  - title: "Trust No One — why your Android app needs to verify devices (Jackson Mafra)"
    url: https://medium.com/@jacksonfdam/trust-no-one-why-your-android-app-needs-to-verify-devices-1228f186a941
  - title: "NIST SP 800-207 — Zero Trust Architecture"
    url: https://csrc.nist.gov/publications/detail/sp/800-207/final
  - title: "OWASP MASVS — Mobile Application Security Verification Standard"
    url: https://mas.owasp.org/MASVS/
---

## Welcome to zero trust

Understand why "the device asked nicely" stopped being a security model in 2015 and what
replaced it.

The pre-zero-trust model was simple: a successful login meant the user was who they said
they were. Everything downstream of login assumed the session was honest. That model
worked when phones were single-user, single-network, and updated regularly. None of
those three are reliably true any more. Zero trust replaces "logged in once" with
"prove who you are *and what you are running* on every sensitive action".

> **Why this matters.** A leaked refresh token from one device shipped to a hostile
> device still passes login. Zero trust is what prevents that token from authorising
> anything meaningful.

---

## Step 1: The five zero-trust principles, applied to mobile

NIST SP 800-207 lists seven principles. Five apply directly to mobile:

1. **Assume breach.** Every device might be compromised; every network might be hostile.
2. **Verify explicitly.** Authenticate every request, not just the session.
3. **Least privilege.** Each token gets the smallest scope that does the job.
4. **Continuous verification.** Re-verify whenever the risk score shifts.
5. **Inspect and log everything.** Telemetry is part of the control.

A 2026 banking app should be able to point at concrete code that implements each one.

> **Why this matters.** The principles are the slogans. The next nine steps are the code.

---

## Step 2: "Verify explicitly" — every protected route

Every API path under `/me/*` re-verifies identity from the access-token's `sub` claim.
The client cannot pass a user-id; the server reads it from the verified token.

```ts
router.get("/me/dashboard", requireAuth, async (req, res) => {
  const userId = req.claims!.sub;            // never trust req.body.userId
  const accounts = await accountsForUser(userId);
  const transactions = await recentTxFor(userId);
  res.json({ accounts, transactions });
});
```

If your server ever pulls the user id out of the request body, the next attacker reads
the rest of your customers' data with one line of curl.

> **Why this matters.** Server-side authorisation is the one rule that has no exceptions.

---

## Step 3: "Assume breach" — device-bind every refresh

The refresh token is bound to a specific device's Keystore key via the `cnf` (confirmation)
claim. A copy of the refresh token used from a different device fails the binding check.

```ts
// On refresh: verify the access-token's cnf.kid matches a registered device binding.
if (record.cnf?.kid && record.cnf.kid !== currentDeviceBinding.kid) {
  return res.status(401).json({ code: "DEVICE_BINDING_MISMATCH" });
}
```

See the Device Attestation 101 codelab for the binding-enrolment flow. The point here:
once enrolled, the token is no longer a bearer credential — it requires the device to
be present and able to sign.

> **Why this matters.** This single change converts the most-stolen credential in mobile
> fintech (the refresh token) into something useless to the thief.

---

## Step 4: "Least privilege" — minimal token scope

Access tokens carry no permissions. Authorisation is a server-side query at request time.
The token names the user; the server decides what the user is allowed to do.

```ts
// Bad — scopes baked into the token, cached for 10 minutes.
{ "sub": "u123", "scope": "balance:read transfer:write reveal:pan" }

// Good — token only names the user. Permissions evaluated per request.
{ "sub": "u123", "aud": "fortress.api", "exp": 1715772900 }
```

Roles change. Risk scores change. A token that bakes permissions in is a token that
caches stale authority.

> **Why this matters.** Privilege escalation is the most common post-compromise move.
> Cached scopes hand the attacker time they would otherwise have to bypass for.

---

## Step 5: "Continuous verification" — score every action

A risk score is computed per request from a small bag of signals:

- Device-binding signature valid? +1 to trust.
- Play Integrity verdict `STRONG`? +1.
- IP reputation clean? +1.
- Velocity (this device's recent activity) within bounds? +1.
- Action is read-only? +1; mutating? 0; irreversible? -1.

Policy then maps the score to an outcome:

```ts
function allow(action: Action, score: number): Decision {
  if (action.kind === "read") return score >= 1 ? "allow" : "stepUp";
  if (action.kind === "mutate") return score >= 2 ? "allow" : "stepUp";
  if (action.kind === "irreversible") return score >= 4 ? "allow" : "stepUp";
  return "deny";
}
```

The numbers are illustrative. The shape — different bars per action — is the point.

> **Why this matters.** Treating every action with the same scrutiny is either too much
> friction for reads or too little for transfers. Per-action policy fixes both.

---

## Step 6: "Inspect and log everything" — telemetry as control

Five events that every zero-trust mobile stack must emit:

```ts
type ZeroTrustEvent =
  | { kind: "auth.token.refreshed"; userId: string; family: string }
  | { kind: "auth.token.reused";    userId: string; family: string }
  | { kind: "device.binding.miss";  userId: string; expectedKid: string; gotKid: string }
  | { kind: "integrity.verdict";    userId: string; verdict: "Trusted"|"Limited"|"Untrusted" }
  | { kind: "stepup.denied";        userId: string; action: string; reason: string };
```

Each is structurally identical; aggregation discovers the patterns. A spike in
`device.binding.miss` for one user is a single leak; a spike across the population is a
backend incident.

> **Why this matters.** Defences without observability are theatre. The events above are
> the proof the defences are running.

---

## Step 7: The risk-signal stack on the device side

A small bag of locally-evaluated signals the device sends to the backend in a header
or signed payload:

```kotlin
data class DeviceRiskSignals(
  val rooted: Boolean,
  val emulator: Boolean,
  val debuggerAttached: Boolean,
  val overlayCapableApps: Int,
  val unknownAccessibilityServices: Int,
  val playIntegrityRecent: PlayIntegritySummary?,
  val osPatchAgeDays: Int,
)
```

The server treats the bag as advisory: trust the patterns of values, not any single
field (Frida hooks each in isolation).

> **Why this matters.** A signal you cannot verify is still useful in aggregate. The
> server compares the bag against what it has seen before for this user; deviation is
> the alarm.

---

## Step 8: Where zero trust meets UX

A pure zero-trust policy is unusable. Every action would require step-up; users would
quit. The trade-offs:

- **Reads.** Almost free. Trusted verdict + valid token = serve.
- **Cosmetic mutations** (toggle a preference). Free with a verified session.
- **Money mutations under €100.** Optional step-up, frequency-based ("once per day").
- **Money mutations over €100, PAN reveal, IBAN reveal.** Always step-up.
- **Device-binding rotation, password change.** Always step-up *and* require email
  confirmation.

The numbers are policy. The shape — frictionless reads, graduated friction for writes —
is the universally correct answer.

> **Why this matters.** Zero trust without a UX gradient is a vendor demo, not a product.

---

## Step 9: Recovery flows are the weak side

The hardest place to enforce zero trust is the *recovery* surface:

- "I forgot my password" — must let in someone who, by definition, cannot prove they are
  the user from authenticated state.
- "I lost my phone" — must let someone register a new device-binding key without the
  old device available.
- "My account was compromised, lock it" — must accept the request from a possibly
  hostile source.

The standard answer: a second factor outside the mobile channel. Email + SMS is weak.
Email + KYC step is stronger. Email + identity-document re-verify is strongest.

> **Why this matters.** Most account takeovers in 2026 go through recovery flows, not
> auth flows. The recovery surface is where the lock-down is hardest *and* most needed.

---

## Step 10: The minimum viable zero trust

If you must ship in two weeks, the smallest version of zero trust that buys real
security:

1. Server-side authorisation on every `/me/*` route. (Same day.)
2. JWT with `iss`, `aud`, `exp`, `sub`. No baked scopes. (Same day.)
3. Refresh-token rotation with reuse detection. (Two days.)
4. Device-binding signature on irreversible actions. (One week.)
5. Telemetry on the five event kinds in Step 6. (Half a day with a vendor SaaS.)

That is the floor. Everything above this codelab — risk scoring, continuous verification,
patch-age policy, signal bags — is *additive*. Build the floor first.

> **Why this matters.** Perfect zero trust is a year of engineering. Eighty-percent zero
> trust is two weeks. Ship two weeks first.

---

## Wrap-Up

You can now defend "we do zero trust" in a design review with concrete code citations,
distinguish principles from slogans, and stage the rollout from "ship in two weeks" to
"the full posture by Q4".

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) — the strongest single
  primitive in this stack.
- [Token Lifecycle](/codelabs/token-lifecycle) — refresh-rotation + reuse detection in
  full.
- [System Design](/codelabs/system-design) (draft) — staff-level architecture for these
  controls at scale.

**Recap of the floor:**

- Five zero-trust principles mapped to mobile: assume breach, verify explicitly, least
  privilege, continuous verification, inspect everything.
- Per-request server authorisation; never trust client identity.
- Device-bound refresh tokens via the `cnf` claim.
- Identity-only access tokens, permissions evaluated server-side per request.
- A risk-score-driven policy that graduates friction by action class.
- A five-event telemetry baseline that proves the controls run.
- A two-week minimum viable rollout plan.
