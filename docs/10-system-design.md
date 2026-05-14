# 10 — System Design: Staff-Level Architecture

> "Auth at a thousand users is a library. At a million users it's a database. At ten million
> it's a distributed system. The interview question is whether you noticed when the regime
> changed." — *Fortress field notes*

**TL;DR** — Designing Fortress for 5M users requires drawing three lines: the **issuer** (mints
tokens, owns the signing key, write-heavy), the **verifier fleet** (read-only, horizontally
scaled, owns the JWKS cache), and the **risk engine** (consumes events, decides whether a
specific action is OK *right now*). This file walks the architecture, the data stores behind
each line, the failure modes between them, and the attacker's seam-picking strategy.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Make every component independently scalable and independently auditable | Find the cross-component trust assumption that breaks first |
| **Key idea** | Stateless verification at the edge; stateful enforcement at the centre | Eventual consistency = a race window I can exploit |
| **Worst failure** | One service that needs to know everything | A "trusted internal" call path with no auth between services |

---

## 🛡️ Defender — "I draw three lines and I defend each"

### The high-level shape

```
                        ┌──────────────────────────────┐
       ┌──────────────► │   Risk Engine                │ ◄──── events bus
       │                │   (per-action policy)        │
       │                └──────────────────────────────┘
       │                                ▲
       │                                │ "may I let action X happen?"
       │                                │
   ┌───┴────────────────────────────────┴──────┐
   │     API Gateway (TLS termination,         │
   │     request shape validation, rate limit) │
   └───┬────────────────────────────┬──────────┘
       │                            │
       ▼                            ▼
  ┌─────────────────┐         ┌────────────────────────┐
  │ Verifier fleet  │         │ Issuer service          │
  │ (stateless JWT) │         │ (mint, refresh, revoke, │
  │                 │         │  device-binding)        │
  └─────────────────┘         └────────────────────────┘
       │                            │
       │ async events               │ writes
       │                            ▼
       │                       ┌──────────────────┐
       │                       │ Refresh-token    │
       │                       │ store (sharded)  │
       │                       └──────────────────┘
       │
       ▼
  ┌─────────────────┐
  │ Event bus       │ ────► Audit log, fraud, telemetry
  └─────────────────┘
```

Three independently scalable services + one async fabric.

### Service decomposition rationale

**API Gateway**
- TLS termination, request shape validation, request size limits, **per-IP and per-userId rate limits**.
- Owns the cert pinning story from the client's perspective (clients pin the gateway, not the upstream).
- Stateless. Horizontal scale via load balancer.

**Verifier fleet**
- Decodes the JWT bearer token, validates `iss/aud/exp/iat/nbf/cnf` against in-memory JWKS.
- For DPoP / proof-of-possession, validates the per-request signature against the public key
  named by `cnf.jkt`.
- **Reads no database in the hot path.** Maximum throughput; this is where 99% of requests live.
- Fetches JWKS from the issuer's `/.well-known/jwks.json` with a TTL cache + jittered refresh.

**Issuer service**
- Login, refresh, logout, device-binding enrol, revocation.
- The only service with write access to the refresh-token store.
- Owns the JWT signing key — itself stored in a KMS / HSM, never on disk in plaintext.
- Lower throughput than the verifier fleet (orders of magnitude). Vertical scale + small horizontal.

**Risk engine**
- Consumes events: login attempts, refreshes, transfers, device-binding registrations, geo
  changes, RASP signals.
- Maintains a per-user risk score, decays over time.
- Exposes a synchronous `POST /risk/decide` endpoint: given `{userId, deviceId, action, payload}`,
  returns `{decision, requiredFactors, reasonCodes}` in ≤ 50 ms.
- For high-frequency reads, write-through cache (Redis) backed by a columnar store for analytics.

**Event bus**
- Append-only stream of every auth-relevant event. Kafka in big-scale shops, Kinesis on AWS,
  Pub/Sub on GCP, Redis Streams in lighter setups.
- Drives the audit log (compliance), the fraud ML pipeline, and the risk engine's state.

### Data stores

| Store | Workload | Pattern |
|---|---|---|
| Refresh tokens | Write-heavy (every login + every refresh), point lookup by token-hash | Sharded by `userId` — Postgres + read replicas, or DynamoDB with `userId` partition key |
| Device bindings | Low-write, point lookup by `(userId, deviceId)` | Same store as refresh tokens, different table |
| Risk state | Read-heavy via cache, write on every event | Redis (hot) + columnar store (analytics) |
| Audit log | Append-only, no updates | Columnar / S3 + Athena |
| Step-up challenge state | Short-lived (≤60 s), point lookup by nonce | Redis with TTL eviction |

### JWT key custody

- Private key for signing: in a **KMS / HSM**. The issuer service has IAM permission to *sign*
  but cannot read the bytes. Rotation is automated; signing operations carry `kid`.
- Public JWKS: served by the issuer at `/.well-known/jwks.json`, cached by every verifier with
  ≤ 5 min TTL. Rotation is dual-published: new key shows up in JWKS days before it's used to
  sign, so verifiers have already cached it when the first signed token arrives.
- Revocation of a `kid`: remove from JWKS, plus a deny-list cache in verifiers. Worst case
  recovery time: cache TTL + deny-list propagation delay.

### Refresh-token storage trade-offs

| Store | Pros | Cons |
|---|---|---|
| **Postgres** | Transactional rotation, easy reuse detection, mature operationally | Vertical scale ceiling; sharding becomes work past 50M tokens |
| **DynamoDB / Bigtable** | Horizontal forever, predictable latency | Eventual consistency on secondary indexes — easy to misuse for reuse-detection |
| **Redis (volatile)** | Tiny latency | Lose state on failover → mass refresh failures unless replicated |
| **Cassandra** | Multi-region writes | Counter-intuitive query model; lightweight transactions are heavy |

Fortress's bet: Postgres with `(user_id, hash)` partitioning until ~10M users, then move the
hot path (token lookup) to DynamoDB while keeping audit history in Postgres.

### Risk engine, in 50 ms

Each `POST /risk/decide` consults:

- **Hot path** (Redis): per-user current score, last seen geolocation, recent failure count.
- **Cold path** (only if hot signals are missing): batch lookup of device binding age,
  historical action patterns.

Decision tree (illustrative):

```
if score > 90:                       refuse + alert
elif score > 60:                     require step-up + cooldown
elif score > 30 and action sensitive: require step-up
elif action sensitive:               require biometric step-up only
else:                                allow
```

The risk engine **never blocks** the request synchronously — it returns a verdict; the action
service enforces. This means the risk engine can fail open or closed depending on the policy:
the issuer can decide "if risk engine is unreachable, refuse all step-ups" (closed) or "allow
read-only, refuse mutations" (graceful degradation).

### Multi-region

| Concern | Approach |
|---|---|
| Reads (verifier fleet) | Stateless — region-local serving from region-local JWKS cache |
| Writes (refresh, device-binding) | Single-leader per shard, region-pinning by `userId` hash |
| Risk engine | Per-region instance reading events from region-local stream; cross-region replication for the score is eventual (and acknowledged as such — see attacker side) |
| Audit log | Append-only, eventually replicated; compliance-grade replication SLAs |

The hard part is the user who moves regions. Fortress's strategy: a `home_region` per user, and
cross-region requests are proxied to home — adds latency but preserves write consistency.

### Observability — the must-haves

- **Per-user latency histogram** at each service boundary, p50/p95/p99.
- **Verdict telemetry**: distribution of Play Integrity verdicts per app version per market.
- **Refresh-reuse signals**: count per minute; this is your primary fraud canary.
- **Cross-service latency**: API gateway → verifier → issuer → risk engine — one trace, one
  trace-id propagated.
- **Rotation health**: how many tokens are signed by `kid` X, by `kid` Y? Used for safe rotation.

### Cost shape

At 5M MAU with the typical fintech mix (one login a day, ten API calls per session, one
transfer a week):

- ~ 5M logins/day → 60/s peak → fits one issuer node.
- ~ 500M API calls/day → 6 000/s peak → 10-20 verifier nodes.
- ~ 5M refreshes/day → again on the issuer.
- ~ 700K transfers/week → step-up infrastructure handles ~1/s peak.

The bottleneck is **never compute**. It's the refresh-token store under failure recovery (when
every device re-logs after an outage). Capacity-plan for the recovery thundering herd, not the
steady state.

---

## ⚔️ Attacker — "I look for the seams"

### Bypass 1 — Trust between services

Internal service calls between API gateway and verifier, or between verifier and risk engine,
often skip auth ("we're inside the VPC; it's fine"). I get RCE on one service, I now have
implicit trust to call any other.

**Counter:**
- mTLS between every internal service.
- Service identity tokens (SPIFFE / workload identity) on every call.
- Zero-trust networking: no implicit trust based on network location.

### Bypass 2 — JWKS cache poisoning

If a verifier caches JWKS over plain HTTP, I MITM the JWKS endpoint and inject my key. My tokens
now verify.

**Counter:**
- JWKS over HTTPS with mTLS to the issuer.
- Pin the JWKS endpoint cert (verifier-side equivalent of mobile cert pinning).
- Sign the JWKS itself with a long-lived offline key, so even a successful MITM of the endpoint
  doesn't help me — verifier checks the outer signature.

### Bypass 3 — Race the risk engine on eventual consistency

Cross-region replication of the risk score is eventually consistent. If a region's view of the
user is "score 20, all good" while the home region has "score 95, fraud" because the latest
events haven't replicated, I can momentarily exploit the gap by routing my requests through the
optimistic region.

**Counter:**
- Hot-path **decision** is local to each region — fast. But it consults the home region for
  high-value actions (transfer ≥ €1000). A bit of latency, no race window.
- Or: graceful degradation — if the home region is unreachable from this region, refuse the
  sensitive action rather than fall back to optimism.

### Bypass 4 — Refresh-store sharding seam

If shards are by `userId` hash, and I can force the user-record to be queried via a different
key (e.g. email), my request hits a different shard with potentially stale state.

**Counter:**
- Single canonical lookup path per record. No secondary index that can desync from the primary.
- All shard routing happens behind the issuer's API; clients never see shard boundaries.

### Bypass 5 — Audit-log gap to game fraud

If the audit log lags real-time, a high-velocity attack burst can happen before the fraud
detector even sees the first event. By the time the system reacts, my session has moved on.

**Counter:**
- Velocity-based protections at the issuer itself, not just downstream fraud.
- Rate limits on `/auth/login` per IP, per user, per device.
- "Cooldown" on per-token rotation cadence — refresh cannot happen more than N times in M minutes.

### Bypass 6 — Service version skew

During rollout, half the verifier fleet runs v1.4 (knows about `cnf`), half runs v1.3 (ignores
it). I route my requests to v1.3 nodes via session-affinity manipulation.

**Counter:**
- Feature flags gated by issuer policy, not verifier version. If `cnf` is required on a token,
  v1.3 verifiers refuse the *whole* token because they don't understand `cnf` — fail-closed.
- Forced fleet-wide deploys for security-critical features; no opt-in version skew windows.

### Bypass 7 — Step-up challenge store under TTL pressure

The step-up challenge store is Redis with TTL eviction (60 s). Under memory pressure, Redis
might evict before TTL. A pre-issued challenge "becomes invalid" not because consumed but
because vanished — verify can't detect this is the user's legitimate challenge vs replay attempt
just looks like "challenge not found". If the server fails open ("if not found, refuse with
'try again'"), my replay attempts are indistinguishable from legitimate retries.

**Counter:**
- Size the store for headroom (peak active challenges × 10).
- Use persistent storage with eager expiry rather than TTL-based eviction.
- Telemetry on "challenge not found at verify" — a spike is either Redis pressure or attack.

### Bypass 8 — KMS quota exhaustion

If the issuer signs with KMS (every token sign = one KMS call), and KMS has a per-second quota,
I can DoS the issuer by causing the legit traffic to exhaust the quota.

**Counter:**
- Sign locally with a CKM-style data key derived from the KMS key, refreshed every N minutes.
  The KMS is consulted for the *data key*, not every token. Smith called this "envelope signing".
- Multi-region KMS for redundancy.
- Front the issuer with a per-IP rate limit so I can't drive the issuer's KMS load from outside.

### Bypass 9 — Internal lateral movement via shared secrets

If the issuer and the verifier share an HS256 secret in env vars (Bypass 1 of [01-stateless-auth.md](01-stateless-auth.md)),
and the verifier is exposed to a wider network than the issuer, compromising a single verifier
node grants me the signing key.

**Counter:**
- Asymmetric (RS256/ES256). Verifiers never hold material that can mint tokens. Compromising a
  verifier gives an attacker nothing they couldn't already do with the public JWKS.

---

## Cross-reference

- **The token shape that flows through this architecture** → [01-stateless-auth.md](01-stateless-auth.md)
- **Refresh-token lifecycle these stores manage** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Device-binding cnf claim** → [09-zero-trust.md](09-zero-trust.md)
- **Risk-signal sources** → [05-play-integrity.md](05-play-integrity.md), [09-zero-trust.md](09-zero-trust.md)
- **Verifier-side cert pinning** → [08-network-warfare.md](08-network-warfare.md) (the principle is the same)

## References

- [Part 10 — The Staff Interview: System Design Mastery](https://blog.stackademic.com/part-10-the-staff-interview-system-design-mastery-d758710ac7a4)
- [RFC 7517 — JSON Web Key (JWK) / JWKS](https://datatracker.ietf.org/doc/html/rfc7517)
- [RFC 9449 — DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [Google SRE Workbook — Hierarchy of Service Reliability](https://sre.google/workbook/reliable-product-launches/)
- [SPIFFE — Secure Production Identity Framework for Everyone](https://spiffe.io/)
