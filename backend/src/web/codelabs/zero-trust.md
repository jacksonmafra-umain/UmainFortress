---
title: "Zero trust — device binding and risk signals"
slug: zero-trust
level: advanced
estimated_minutes: 30
status: published
company: Fortress
tags:
  - zero-trust
  - cnf
  - device-binding
  - risk
summary: >
  Bind every token to a specific device's Keystore key via the `cnf` (confirmation)
  claim, blend a stack of risk signals into a per-action score, and let policy decide
  what each score is allowed to do. The advanced policy-level follow-up to the
  Trust-No-One codelab.
references:
  - title: "Zero trust (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/09-zero-trust.md
  - title: "RFC 7800 — Proof-of-Possession Key Semantics for JWTs (cnf claim)"
    url: https://datatracker.ietf.org/doc/html/rfc7800
  - title: "Trust No One — beginner intro (this catalogue)"
    url: /codelabs/trust-no-one
---

## Welcome to the deep end of zero trust

Understand how to turn the zero-trust *principles* (Trust-No-One codelab) into a
running policy stack that scores every action against a typed risk score and acts on it.

This codelab assumes you have already read the Trust-No-One codelab and have a server
implementing stateless auth + refresh-token rotation. We now wire those into a typed
risk score, bind every token to a specific device via the `cnf` (confirmation) claim,
and define a per-action policy that turns the score into Allow / StepUp / Deny.

> **Why this matters.** Zero trust without typed risk scoring is "ask the user nicely".
> The score is what makes the architecture defensible against a determined attacker.

---

## Step 1: The `cnf` claim — proof-of-possession at the token layer

RFC 7800 defines the `cnf` (confirmation) claim. The simplest form names a key by `kid`:

```json
{
  "iss": "fortress.auth",
  "sub": "u_abc123",
  "aud": "fortress.api",
  "exp": 1715772900,
  "iat": 1715772300,
  "cnf": { "kid": "dev-binding-spki-7f3a..." }
}
```

The `kid` is the SHA-256 hash of the device's SPKI public key. The server stores the
SPKI itself in the device-binding table; on every protected request the server requires
a fresh signature over a server-issued nonce, verified against that public key.

> **Why this matters.** Without `cnf`, your access token is bearer. With `cnf`, every
> use requires *possession of the private key* — which lives in the Keystore on the
> device that enrolled.

---

## Step 2: Enrol the device key

A one-time enrolment per (user, device). The device generates the keypair, sends the
SPKI public part to the server, the server records it.

```kotlin
suspend fun enrolDevice(userId: String) {
  generateDeviceBindingKey(ALIAS_DEVICE_BINDING) // see Device Attestation 101
  val spkiB64 = publicKeySpkiB64(ALIAS_DEVICE_BINDING)
  val attestation = keystoreAttestationChainB64(ALIAS_DEVICE_BINDING)

  authApi.enrolDevice(
    EnrolRequest(
      deviceId = deviceIdProvider.current(),
      publicKeySpkiB64 = spkiB64,
      attestationChainB64 = attestation,
    )
  )
}
```

Send the *attestation chain* alongside, so the server can verify the key originated from
a real hardware Keystore (the chain roots in Google's Attestation Root CA). This is the
moment your server learns to trust a specific device.

> **Why this matters.** A device-binding without attestation can be forged by an
> attacker who spoofs the Keystore. The chain check turns "we trust this device"
> into "we have cryptographic proof this device is real".

---

## Step 3: Bind every refresh to the device

When the refresh endpoint mints a new pair, the access token carries `cnf.kid = SHA-256(SPKI)`.
The refresh token carries the same id in its server-side record.

```ts
async function mintForDevice(userId: string, deviceId: string) {
  const binding = await deviceBindings.findOne({ userId, deviceId });
  if (!binding) throw new Error("DEVICE_NOT_BOUND");
  const kid = sha256Hex(binding.publicKeySpkiB64).slice(0, 32);

  const access = await new SignJWT({ cnf: { kid } })
    .setProtectedHeader({ alg: "ES256", kid: ACTIVE_KEY_ID })
    .setIssuer("fortress.auth").setSubject(userId).setAudience("fortress.api")
    .setIssuedAt().setExpirationTime("10m").setJti(uuid()).sign(privateKey);

  const refresh = await mintRefreshToken(userId, deviceId, kid);
  return { access, refresh };
}
```

A refresh token presented with a `cnf.kid` that does not match the registered binding
for that user-device pair fails the binding check. Steal the token, present it from a
different device, you get `DEVICE_BINDING_MISMATCH`.

> **Why this matters.** This is the single line of defence that prevents stolen
> refresh tokens from being usable elsewhere. Everything else in this codelab is
> supplementary.

---

## Step 4: Demand a fresh signature on every sensitive action

For irreversible actions, the server requests a *signed* nonce. The signature must be
produced *now*, via a BiometricPrompt-bound `CryptoObject`. See
[Biometric Hardening](/codelabs/biometric-hardening).

```ts
router.post("/stepup/verify", requireAuth, async (req, res) => {
  const { nonceB64, signatureB64, payloadDigestB64 } = req.body;
  const userId = req.claims.sub;
  const kid = req.claims.cnf?.kid;
  if (!kid) return res.status(401).json({ code: "MISSING_CNF" });

  const binding = await deviceBindings.findOne({ userId, kid });
  if (!binding) return res.status(401).json({ code: "DEVICE_BINDING_MISMATCH" });

  const ok = verifyEcdsaSpki(binding.publicKeySpkiB64, signatureB64, Buffer.concat([
    Buffer.from(nonceB64, "base64"),
    Buffer.from(payloadDigestB64, "base64"),
  ]));
  if (!ok) return res.status(401).json({ code: "SIGNATURE_INVALID" });
  res.json({ ok: true });
});
```

Every irreversible action is signed by a private key that requires a real human's
biometric at signing time. That is the floor.

> **Why this matters.** Even a token compromise plus a device fingerprint match plus a
> session takeover plus a network MITM still cannot synthesise a fresh `CryptoObject`
> signature. The floor holds.

---

## Step 5: Compose the risk score

Twelve typed signals fold into one `score: int`. The signals are independent; the score
is the sum.

```ts
interface RiskInputs {
  cnfPresent: boolean;             // +2 if cnf matches binding, 0 otherwise
  attestationVerdict: "Trusted" | "Limited" | "Untrusted";  // +3 / +1 / -3
  playIntegrityStrong: boolean;    // +2 if MEETS_STRONG_INTEGRITY
  installerClass: "Play" | "Vendor" | "FDroid" | "Sideload" | "Adb"; // +2 / 0 / 0 / -1 / -1
  fingerprintMatches: boolean;     // +1 if known fingerprint, -1 if first seen
  osPatchAgeDays: number;          // +1 if <=90, 0 if <=365, -1 otherwise
  ipReputationOk: boolean;         // +1 / -2
  velocityHealthy: boolean;        // +1 / -2 (e.g. <3 sensitive actions in 60s)
  refreshReuseSeen: boolean;       // -5 if seen for this family in last 24h
  overlayCapableApps: number;      // 0 / -1 / -2 depending on count
  unknownA11yServices: number;     // 0 / -2 / -3
  recentSecurityIncident: boolean; // -3 if user account flagged
}
```

The vector of coefficients is policy. Document it in code so it can be reviewed and
rolled back as one change.

> **Why this matters.** A scoring system in code is reviewable. The same logic in
> prose drifts within months.

---

## Step 6: Map score to outcome per action class

Three action classes, four outcomes. Different floors per class.

```ts
type Action = "read" | "mutate" | "irreversible" | "lifecycle";
type Outcome = "allow" | "stepUp" | "stepUpStrong" | "deny";

function policyFor(action: Action, score: number): Outcome {
  if (score <= -5) return "deny";
  switch (action) {
    case "read":         return score >= 0 ? "allow" : "stepUp";
    case "mutate":       return score >= 2 ? "allow" : "stepUp";
    case "irreversible": return score >= 4 ? "allow" : score >= 1 ? "stepUp" : "stepUpStrong";
    case "lifecycle":    return "stepUpStrong"; // password change, device add — always
  }
}
```

`stepUp` is a BiometricPrompt-bound signature. `stepUpStrong` adds out-of-band
verification (email confirmation or SMS-OTP). `lifecycle` is *always* strong — these
are the recovery surfaces and the most-attacked paths.

> **Why this matters.** A flat policy that treats every action the same either annoys
> read-heavy users or undersecures money-mover users. The per-class floor fixes both.

---

## Step 7: Inputs that the device provides — and how to verify them

Some of the 12 inputs come from the device. The server cannot blindly trust them.

| Input | Source | Server-side check |
|---|---|---|
| `cnfPresent` | Token field | Verified — token is signed by your auth service. |
| `attestationVerdict` | Computed locally | Re-evaluated server-side from the chain. |
| `playIntegrityStrong` | Play Integrity token | Re-decoded server-side via Google. |
| `installerClass` | Device API | Cross-checked against Play Integrity `appRecognitionVerdict`. |
| `fingerprintMatches` | Composite device fingerprint | Server-side comparison against stored. |
| `osPatchAgeDays` | `Build.VERSION.SECURITY_PATCH` | Trust-but-verify: Play Integrity carries the same field signed. |
| `ipReputationOk` | Server-side | Server-side only. |
| `velocityHealthy` | Server-side | Server-side only. |
| `refreshReuseSeen` | Server-side | Server-side only. |
| `overlayCapableApps` | Device-side | Advisory; if user-reported value disagrees with seen behaviour, log. |
| `unknownA11yServices` | Device-side | Advisory; same. |
| `recentSecurityIncident` | Server-side | Server-side only. |

> **Why this matters.** Half the inputs are server-derived (and trustworthy); half are
> client-reported. Knowing which is which keeps the policy honest.

---

## Step 8: Telemetry — every score, every decision

Three events.

```ts
type ZeroTrustEvent =
  | { kind: "zt.score"; userId: string; action: Action; score: number; inputs: Record<string, unknown> }
  | { kind: "zt.outcome"; userId: string; action: Action; outcome: Outcome; reason: string[] }
  | { kind: "zt.policy.change"; oldShape: string; newShape: string; actor: string };
```

Aggregate `zt.score` by user; a drift in the average score is a fraud-pattern signal.
Aggregate `zt.outcome` by action × outcome; a spike in `deny` for one specific action
means a campaign in progress.

> **Why this matters.** Risk-based policy demands operational telemetry. Without it
> the policy is invisible the day it is wrong.

---

## Step 9: Roll out a policy change safely

Policy changes are dangerous: a bad threshold can lock out legitimate users. Three-step
rollout:

1. **Shadow mode.** Compute the new policy outcome alongside the live policy. Log both;
   take no action on the shadow. Compare distributions for a week.
2. **Canary tier.** Enable the new policy for 1 % of users (`hash(userId) % 100 == 0`).
   Track support-ticket rate per tier.
3. **Full rollout.** Move to 100 %. Keep a kill-switch (config flag) that reverts to
   the previous policy.

Document the rollback procedure with the rollout. The rollback is the procedure that
matters most.

> **Why this matters.** Risk policy is the single highest-leverage change in your
> system. Treat its rollouts like database migrations.

---

## Step 10: The continuous-verification loop

The score does not freeze at login. Re-evaluate on:

- Every `/me/*` request (cheap, just re-compose the score from cached inputs).
- Every action class change (read → mutate triggers a re-score).
- Every elapsed-time bucket (re-score every 15 minutes regardless of activity, so the
  score reacts to changing IP reputation and recent-incident state).
- Every push of a new policy version (shadow-evaluated as in Step 9).

```ts
async function continuousVerification(ctx: RequestContext) {
  const inputs = await collectInputs(ctx);
  const score = computeScore(inputs);
  await telemetry.ztScore({ userId: ctx.userId, action: ctx.action, score, inputs });
  return policyFor(ctx.action, score);
}
```

A request that was Allow ten minutes ago can become StepUp the next time, because IP
changed or a velocity spike happened. The user does not notice unless action class
escalates.

> **Why this matters.** A session is not a binary; it is a continuous evaluation.
> Modelling it that way produces a system that catches fraud as it develops, not after.

---

## Wrap-Up

You can now implement the full zero-trust stack — device-bound tokens via `cnf`,
attestation-rooted enrolment, biometric-bound step-up on irreversible actions, a typed
12-signal risk score, per-action-class policy with four outcomes, continuous
re-evaluation, and the operational rollout discipline that lets you change the policy
safely.

Next mission:
- [Bulletproof Security](/codelabs/bulletproof-security) — fuses attestation,
  fingerprinting, and the risk score into a single verdict.
- [System Design](/codelabs/system-design) — the architecture and scale story.
- [Passkeys](/codelabs/passkeys) — replaces passwords entirely in the lifecycle path.

**Recap of the zero-trust stack:**

- `cnf.kid` proof-of-possession at the token layer.
- Device-binding enrolment that ships the Keystore attestation chain.
- Biometric-bound `CryptoObject` step-up for every irreversible action.
- A 12-signal risk-score vector with each coefficient policy-typed.
- A per-action-class outcome table (allow / stepUp / stepUpStrong / deny).
- A clean server-side / client-side trust split for each input.
- Three telemetry events for monitoring scores, outcomes, and policy changes.
- A three-stage policy-rollout procedure with documented rollback.
- Continuous re-evaluation on every request, action change, and elapsed-time bucket.
