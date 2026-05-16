---
title: "System design — staff-level architecture trade-offs"
slug: system-design
level: advanced
estimated_minutes: 40
status: published
company: Fortress
tags:
  - architecture
  - system-design
  - scale
  - kms
summary: >
  Architect a mobile auth + attestation + risk stack at 5 million users — service
  boundaries, KMS custody, regional partitioning, the seams a defender hardens last —
  and the trade-offs interviewers actually probe. The capstone for everything else in
  this catalogue.
references:
  - title: "System design (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/10-system-design.md
  - title: "Google Cloud KMS — concepts"
    url: https://cloud.google.com/kms/docs/concepts
  - title: "AWS KMS — best practices"
    url: https://docs.aws.amazon.com/kms/latest/developerguide/best-practices.html
---

## Welcome to the capstone

Understand the architectural trade-offs that hold every other Fortress codelab together
when the install base hits a few million devices. This codelab is the answer to a
typical staff-level design interview.

The earlier codelabs each described a single primitive — JWTs, the vault, the
interceptor, attestation, passkeys, the risk score. This one assembles them into one
system serving 5M MAU across multiple regions, with the right service boundaries, the
right key custody, and an honest account of what fails at that scale.

> **Why this matters.** Every primitive in this catalogue ships fine at 5,000 users.
> Several of them collapse at 5,000,000 without the work below. The collapse modes are
> what staff engineers know how to avoid.

---

## Step 1: Draw the boxes

A workable service split for 5M MAU:

```
                            ┌─────────────────────┐
            ┌───── reads ──▶│  read replica(s)    │
            │               │  ├─ accounts        │
            │               │  ├─ transactions    │
            │               │  └─ profile         │
            │               └─────────────────────┘
            │
[app] ─▶ [edge] ─▶ [api-gateway] ─▶ [identity-service] ──▶ [kms]
                            │           ├─ /auth         │
                            │           ├─ /me           │
                            │           └─ /stepup       │
                            │
                            ├─▶ [attestation-service] ─▶ [play integrity]
                            │           └─ verdict cache  └─ google service-account
                            │
                            ├─▶ [risk-service] ────────────▶ [redis cluster]
                            │           └─ score events    └─ velocity windows
                            │
                            ├─▶ [tx-service] ─writes─▶ [accounts-db (primary)]
                            │
                            └─▶ [audit-service] ─writes─▶ [audit-log (immutable)]
```

Six services, four datastores. The split is around *blast-radius* — a leak in any one
service should not compromise the others.

> **Why this matters.** A monolith at this scale is operationally viable but
> security-fragile. One bad bug, one bad commit, and the entire fleet is exposed.

---

## Step 2: Key custody — what lives in KMS

Four kinds of keys, three custody levels:

| Key | Lives in | Why |
|---|---|---|
| JWT signing key (auth) | KMS, hardware-backed | Should never touch the auth service host. Sign via KMS API. |
| TLS / pinning private keys | KMS or HSM | Same reasoning. |
| Per-user data-encryption keys | KMS-wrapped, cached in identity-service memory | Trade-off: latency-vs-blast-radius. |
| Refresh-token hashes | Application database | Hash only; nothing valuable on its own. |

KMS is the right home for anything that, if leaked, requires emergency rotation. The
key never leaves; you call KMS to sign or wrap. Latency is ~10ms — fine for tokens,
cacheable for higher-volume paths.

> **Why this matters.** Keys in your application database are keys at your
> attack-surface's level of security. KMS is one trust boundary lower.

---

## Step 3: Regional partitioning

Two reasons to shard by region:

1. **Latency.** A user in São Paulo making a transfer should not round-trip to
   Frankfurt for every signature verification.
2. **Regulation.** EU GDPR, US data-residency, India IT Rules. Some jurisdictions
   require data to remain in-country.

Shape: each region is its own deployment with its own KMS, its own primary DB, its own
read replicas. Cross-region replication for global audit and analytics only, never for
the primary read path.

```
fortress.eu-west-1 ──▶ owns eu-resident users
fortress.us-east-1 ──▶ owns us-resident users
fortress.ap-south-1 ──▶ owns in-resident users

[audit-log] is the only cross-region pipe — append-only, regulator-readable.
```

Region resolution at login: edge layer routes to the user's home region by `sub`.
Cross-region users (rare, but real — diplomats, expats) get a small router service that
brokers between regions for them.

> **Why this matters.** A single global region works at 5M users. At 50M it breaks.
> Build the regional split before you need it.

---

## Step 4: The hot path — what runs on every request

For 5M MAU you expect ~50k QPS peak. Every protected request runs:

1. JWT verify — ~0.1ms with a cached JWKS.
2. Device-binding lookup — ~5ms.
3. Risk-score composition — ~10ms (with cached inputs).
4. Authorisation check — ~1ms (server-side).
5. Business logic — varies.

The first four total ~16ms before your business logic runs. Budget: 50ms total at p95.
That leaves 34ms for business logic, which is enough for almost everything except large
report generation. Pre-cache or pre-render the heavy reports.

> **Why this matters.** Hot-path latency is the difference between a snappy app and a
> sluggish one. Every millisecond on the auth layer is one less millisecond for the
> feature.

---

## Step 5: The cold paths — failure modes

Six things go wrong at scale. Plan each.

1. **Play Integrity quota exceeded.** Standard requests have a per-app daily quota
   limit. At 50k QPS you may exhaust it. Negotiate a quota uplift with Google; cache
   verdicts for the lifetime of the prepared token; degrade to "Limited" on quota miss.
2. **KMS throttling.** Most KMS regions cap at ~3k signing-ops/sec by default. Cache
   the most recent JWT signing material (the JWK) at JWKS endpoint; ask for limit
   increases proactively.
3. **Refresh-token-reuse storm.** A leak across a cohort produces thousands of reuse
   events in minutes. Auto-revoke the families, page humans only when the reuse rate
   exceeds a threshold.
4. **Attestation chain expiry.** Google rotates the root CA on the order of years. Pin
   loosely; allow multiple roots; track the rotation calendar.
5. **DB primary failure.** Multi-AZ replication for the auth DB. Read replicas in
   every region. Manual failover with a documented procedure.
6. **Regional outage.** Each region must be able to refuse logins gracefully — the
   client falls back to a cached-credentials read-only mode and surfaces "service
   degraded" UX.

> **Why this matters.** Production at scale is a series of cold-path tests run
> against you by reality. Pre-plan each one or improvise it under fire.

---

## Step 6: Data residency choices

Three data shapes, three residency choices:

| Data | Where it lives | Why |
|---|---|---|
| PII (name, email, phone) | User's region only | GDPR / similar |
| Transactions | User's region; archive cross-region | Compliance for the live data; centralised for analytics on aged data |
| Audit log | Append-only, cross-region, write-once | Regulators ask for global audit; immutability is the property |
| Device bindings | User's region only | Tied to PII |
| Encrypted blobs (tokens) | Anywhere | Plaintext useless without per-user KMS key |

The audit log is the one exception to "user data stays in-region". Make it append-only,
sign-and-seal the entries, and the regulator's question is answered without breaking
the residency story.

> **Why this matters.** Residency is the question you cannot retrofit. Schema decisions
> made in year one become regulatory immovables in year three.

---

## Step 7: Observability stack

The minimum at 5M MAU:

- **Distributed tracing.** OpenTelemetry across every service. Sample 1 % of all
  requests, 100 % of errors. Trace IDs flow from edge to KMS.
- **Metrics.** Per-service, per-region, per-action. Critical: refresh-reuse rate,
  attestation verdict distribution, biometric step-up success rate.
- **Logs.** Structured JSON. Mandatory PII-redaction at the application layer. Search-
  indexed by user-fingerprint (not user-id) so a single user's incident is still
  diagnosable without their PII leaving the auth boundary.
- **Alerts.** Five top-level alarms: refresh-reuse-spike, attestation-verdict-failure-
  spike, KMS-signing-failure-spike, error-rate-spike-per-region, latency-p99-breach.

Everything else is derived. Resist the urge to alert on every dashboard.

> **Why this matters.** Five well-tuned alarms are operable. Fifty are noise. The first
> incident at scale teaches the team which fifteen alerts are theatre.

---

## Step 8: Release engineering for the security stack

Three principles:

1. **Each service deploys independently.** Auth, attestation, risk, tx — none should
   be coupled at deploy time. Schemas evolve with backwards-compatible migrations.
2. **Canary every change.** 1 % → 10 % → 50 % → 100 %. Roll back is faster than roll
   forward.
3. **Two-version overlap during rotation.** A new JWT signing key lives alongside the
   old for at least 24 hours. A new device-binding policy runs in shadow before
   enforcement.

```yaml
# Hypothetical Argo Rollouts canary for identity-service
strategy:
  canary:
    steps:
      - setWeight: 1
      - pause: { duration: 30m }
      - setWeight: 10
      - pause: { duration: 1h }
      - setWeight: 50
      - pause: { duration: 4h }
      - setWeight: 100
```

The pause durations are policy. Security-critical changes get the longest pauses; the
team needs time to react if telemetry says "back out".

> **Why this matters.** Big-bang security rollouts cause big-bang security incidents.
> Canaries reduce blast-radius proportional to the percentage.

---

## Step 9: What you do not build yourself

A short list of must-not-DIY at this scale:

- **KMS.** Google Cloud KMS, AWS KMS, Azure Key Vault. Audited, attested, FIPS-validated.
- **Play Integrity / iOS App Attest.** OS-vendor primitives — your own attempts cannot
  match.
- **WebAuthn server.** Use SimpleWebAuthn, Hanko, or a managed offering — passkeys'
  spec is detailed and easy to misimplement.
- **Cryptography primitives.** Use the platform AEAD wrappers and signed-JWT libraries.
  Tink, libsodium, jose. Never write the primitives yourself.
- **TLS termination.** Cloud load balancer or NGINX. Do not run a Java TLS terminator.
- **Time.** NTP via the cloud provider. Drift kills signatures.

Buy or import; do not write.

> **Why this matters.** Every line of crypto you write yourself is a CVE you sign your
> name to. Use the audited libraries.

---

## Step 10: The interview answer

When asked "design a mobile auth system at scale", walk this order:

1. Clarify scale: 5M MAU, three regions, fintech compliance.
2. Draw six services + four datastores (Step 1).
3. Name KMS for sensitive keys (Step 2).
4. Sketch regional partitioning + residency (Steps 3, 6).
5. Walk the hot path latency budget (Step 4).
6. Name six cold-path failures with mitigations (Step 5).
7. Name the observability stack (Step 7).
8. Talk through release engineering — canaries + two-version overlap (Step 8).
9. Name what you do not build yourself (Step 9).
10. Note the trade-offs you are *not* solving here — fraud / KYC / payment-rail
    integration. Each is its own design.

Forty minutes if you go through every step. The interview-correct answer is that all
ten of them exist; depth on any one is bonus.

> **Why this matters.** The shape of the answer is what staff interviews probe. Depth
> is icing.

---

## Wrap-Up

You now have the architecture that holds every other Fortress codelab together at
real-product scale: six services with KMS custody, regional partitioning by user,
50ms p95 hot-path budget, six cold-path failure plans, append-only cross-region audit,
a five-alert observability minimum, and a canary discipline that scales the security
posture without big-bang rollouts.

Next mission:
- Re-read [Stateless Auth Blueprint](/codelabs/stateless-auth-blueprint),
  [Zero Trust](/codelabs/zero-trust), and
  [Bulletproof Security](/codelabs/bulletproof-security) — they are the three pieces
  this architecture composes.
- Build the first cold-path runbook (Step 5) as a real document, even if no incident
  has happened yet.

**Recap of the staff-level shape:**

- Six services, four datastores, blast-radius-driven boundaries.
- KMS for every key whose leak would require emergency rotation.
- Regional deployments with audit-only cross-region replication.
- 50ms p95 latency budget on the hot path, 16ms of which is auth scaffolding.
- Six pre-planned cold paths with documented mitigations.
- Data-residency choices that survive into year three.
- Five-alert observability minimum; everything else derived.
- Canary releases with two-version overlap during key/policy rotations.
- A list of primitives you buy rather than build.
- A 10-step interview-shaped narrative that names each.
