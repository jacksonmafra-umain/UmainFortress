---
title: "Play Integrity — standard request and server verification"
slug: play-integrity
level: advanced
estimated_minutes: 30
status: published
company: Fortress
tags:
  - play-integrity
  - attestation
  - policy
summary: >
  Send a Play Integrity standard request, decrypt the verdict on the server, read every
  field that matters, and pick a policy that does not lock out legitimate-but-unusual
  devices. Includes the standard-vs-classic decision, request-hash binding, error budget.
references:
  - title: "Play Integrity (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/05-play-integrity.md
  - title: "Play Integrity API — official docs"
    url: https://developer.android.com/google/play/integrity
  - title: "Play Integrity verdicts reference"
    url: https://developer.android.com/google/play/integrity/verdicts
---

## Welcome to Play Integrity

Understand what Play Integrity claims, what it cannot claim, and how to extract the
useful signal from its sometimes-misunderstood verdict structure.

Play Integrity is Google's attestation service: a token computed at Google's verifier
side that tells you "this is a real Android device running an unmodified copy of your
app installed via Play". It superseded SafetyNet in 2024 and is now the default
hardware-attestation primitive for any fintech-grade Android app. This codelab walks the
full client + server integration.

> **Why this matters.** Without Play Integrity, your only attestation is Keystore
> key-attestation — strong on its own, but bypassable when the entire device is
> emulated. Play Integrity is the second leg that catches the emulator case.

---

## Step 1: Standard vs classic — pick the right shape

Two request shapes. Each has a use.

- **Classic request.** One call → one token. No warm-up. Latency is hundreds of
  milliseconds. Quota-limited. Used for low-volume "verify this user once an hour"
  checks.
- **Standard request.** Two phases — *prepare* once at app start, *request* per
  action. Warm prepares give sub-100ms request latency. Designed for high-volume
  per-action attestation. Recommended for fintech.

Use Standard unless you have a specific reason not to. The Fortress demo uses Standard
for every step-up.

> **Why this matters.** A 500ms attestation on the critical path of a transfer is bad
> UX. Standard makes the attestation effectively free at request time.

---

## Step 2: Prepare a Standard token provider

`StandardIntegrityManager.prepareIntegrityToken()` warms up the cryptographic state
once. The returned provider is reused for every per-action request.

```kotlin
class PlayIntegrityProbe(
  context: Context,
  private val cloudProjectNumber: Long,
) {
  private val manager = IntegrityManagerFactory.createStandard(context)
  private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

  suspend fun prepare(): Unit = suspendCoroutine { cont ->
    val req = StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
      .setCloudProjectNumber(cloudProjectNumber)
      .build()
    manager.prepareIntegrityToken(req)
      .addOnSuccessListener { provider = it; cont.resume(Unit) }
      .addOnFailureListener { cont.resumeWithException(it) }
  }

  suspend fun token(requestHash: String): String = suspendCoroutine { cont ->
    val p = provider ?: error("call prepare() first")
    val req = StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
      .setRequestHash(requestHash)
      .build()
    p.request(req)
      .addOnSuccessListener { cont.resume(it.token()) }
      .addOnFailureListener { cont.resumeWithException(it) }
  }
}
```

Call `prepare()` once at app launch (in Application's `onCreate` or via Koin
single-instance). Call `token(...)` for each protected action.

> **Why this matters.** Cold prepare + request adds 800ms on the slow path. Warm prepare
> + request is 60ms.

---

## Step 3: Bind the token to the request via requestHash

`setRequestHash(...)` ties the token to specific bytes. The server later computes the
same hash from the request payload and compares. If they disagree, the token has been
replayed onto a different action.

```kotlin
suspend fun stepUpTransfer(transfer: Transfer): String {
  val canonical = Json.encodeToString(transfer)
  val hashBytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
  val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
  return integrity.token(hashHex)
}
```

The hash should cover *everything the server will act on*: amount, recipient, currency.
Anything you do not hash, an attacker can change.

> **Why this matters.** Without request-hash binding, a Play Integrity token from a
> low-risk action (read your own balance) can be replayed onto a high-risk action
> (transfer to attacker's IBAN). The hash is the cryptographic guarantee that the token
> belongs to *this* call.

---

## Step 4: Receive the token on the server

The client passes the opaque token as an `X-Play-Integrity-Token` header (or in the
body). The server hands it to Google's `playintegrity` API to decrypt.

```ts
import { google } from "googleapis";

const playintegrity = google.playintegrity({
  version: "v1",
  auth: await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/playintegrity"],
  }),
});

async function decodeIntegrityToken(token: string, packageName: string) {
  const res = await playintegrity.v1.decodeIntegrityToken({
    packageName,
    requestBody: { integrityToken: token },
  });
  return res.data.tokenPayloadExternal;
}
```

The decoded payload is a structured JSON object. Each field deserves its own policy
slot.

> **Why this matters.** The decode call is the trust hand-off — Google's verifier
> tells you what the device actually is. Everything downstream is policy.

---

## Step 5: Read the verdict fields that matter

Five sub-verdicts. Each is independent.

```ts
interface PlayIntegrityVerdict {
  requestDetails: { requestPackageName: string; requestHash?: string; nonce?: string };
  appIntegrity: {
    appRecognitionVerdict: "PLAY_RECOGNIZED" | "UNRECOGNIZED_VERSION" | "UNEVALUATED";
    packageName: string;
    certificateSha256Digest: string[];
    versionCode?: string;
  };
  deviceIntegrity: {
    deviceRecognitionVerdict: Array<
      "MEETS_DEVICE_INTEGRITY"
      | "MEETS_BASIC_INTEGRITY"
      | "MEETS_STRONG_INTEGRITY"
      | "MEETS_VIRTUAL_INTEGRITY"
    >;
    recentDeviceActivity?: { deviceActivityLevel: string };
  };
  accountDetails?: { appLicensingVerdict: "LICENSED" | "UNLICENSED" };
  environmentDetails?: { playProtectVerdict?: string; appAccessRiskVerdict?: object };
}
```

The most important field is `deviceRecognitionVerdict`. `MEETS_STRONG_INTEGRITY` means
hardware-backed attestation passed. `MEETS_DEVICE_INTEGRITY` means basic Play-store-grade
device, no strong hardware proof. `MEETS_BASIC_INTEGRITY` means "passes loose checks"
and is the easiest to spoof. `MEETS_VIRTUAL_INTEGRITY` covers ChromeOS / Android-in-VM.

> **Why this matters.** A policy that accepts `MEETS_BASIC_INTEGRITY` blindly opens the
> door to spoofers. A policy that demands `MEETS_STRONG_INTEGRITY` from every device
> excludes large fractions of the install base. Pick the bar per action.

---

## Step 6: Verify the requestHash echo

Re-compute the hash from the request payload, compare against
`requestDetails.requestHash`. Mismatch → reject.

```ts
function verifyRequestHash(verdict: PlayIntegrityVerdict, payload: unknown): boolean {
  const canonical = JSON.stringify(payload);
  const expected = crypto.createHash("sha256").update(canonical).digest("hex");
  return verdict.requestDetails.requestHash === expected;
}

if (!verifyRequestHash(verdict, req.body)) {
  return res.status(400).json({ code: "REQUEST_HASH_MISMATCH" });
}
```

This is the single check that catches token replay onto a different action. Skipping it
makes the rest of the verdict almost worthless.

> **Why this matters.** A token without a hash binding is a bearer token. Hashing the
> request couples it to one call and one call only.

---

## Step 7: Translate the verdict into a policy outcome

A small function from verdict-bag → action-outcome. Encode policy in code, not in
prose.

```ts
type Outcome = "Trusted" | "Limited" | "Untrusted";

function applyPolicy(verdict: PlayIntegrityVerdict): { outcome: Outcome; reasons: string[] } {
  const reasons: string[] = [];

  // App side
  if (verdict.appIntegrity.appRecognitionVerdict !== "PLAY_RECOGNIZED") {
    reasons.push("app:" + verdict.appIntegrity.appRecognitionVerdict);
  }
  // Device side
  const dr = new Set(verdict.deviceIntegrity.deviceRecognitionVerdict);
  if (!dr.has("MEETS_DEVICE_INTEGRITY") && !dr.has("MEETS_STRONG_INTEGRITY")) {
    reasons.push("device:not-meets-integrity");
  }
  // Licensing
  if (verdict.accountDetails?.appLicensingVerdict !== "LICENSED") {
    reasons.push("account:unlicensed");
  }

  const outcome: Outcome =
    reasons.length === 0 ? "Trusted"
      : reasons.some(r => r.startsWith("device:")) ? "Untrusted"
      : "Limited";
  return { outcome, reasons };
}
```

Document the table in a Markdown file the security team owns; the function above is
the executable equivalent.

> **Why this matters.** Policy expressed as code is policy that ships. Policy in
> Confluence is policy that drifts.

---

## Step 8: Handle the failure modes — quota, network, decoder errors

Three categories you must plan for:

1. **Quota exceeded** (HTTP 429). Standard requests have a per-app daily quota. A spike
   can blow it. Cache verdicts client-side for the prepared provider's TTL and degrade
   to the most-recent verdict on quota errors.
2. **Network failure** (timeout). Treat as "verdict unknown". For high-risk actions
   refuse; for reads continue.
3. **Decoder error** (Google's verifier rejected the token). Almost always indicates a
   tampered/spoofed token. Log and refuse.

```ts
async function safeDecode(token: string, pkg: string) {
  try {
    return { ok: true as const, verdict: await decodeIntegrityToken(token, pkg) };
  } catch (err: any) {
    if (err.code === 429) return { ok: false as const, reason: "QUOTA_EXCEEDED" };
    if (err.code === 400) return { ok: false as const, reason: "DECODER_REJECTED" };
    return { ok: false as const, reason: "NETWORK_ERROR" };
  }
}
```

> **Why this matters.** Treating every Play Integrity failure as "definitely untrusted"
> over-rejects in outages. Treating every failure as "trust" defeats the control.

---

## Step 9: Telemetry on every verdict

Six events. The shape mirrors the verdict structure.

```ts
type IntegrityEvent =
  | { kind: "integrity.token.requested"; userId: string; action: string }
  | { kind: "integrity.verdict.trusted"; userId: string; action: string }
  | { kind: "integrity.verdict.limited"; userId: string; reasons: string[] }
  | { kind: "integrity.verdict.untrusted"; userId: string; reasons: string[] }
  | { kind: "integrity.hash.mismatch"; userId: string; action: string }
  | { kind: "integrity.api.failed"; reason: "QUOTA_EXCEEDED" | "DECODER_REJECTED" | "NETWORK_ERROR" };
```

Aggregate by `reasons[]`. A spike in `device:not-meets-integrity` across many users in
one hour is an emulator-farm fraud event in progress; in one user it is one rooted
phone.

> **Why this matters.** The verdict is only one of many signals. Watching the
> *patterns* in the verdicts is where the fraud detection lives.

---

## Step 10: Dev Mode — simulate every verdict bucket

Five toggles in the Fortress Dev Mode panel let QA reproduce every branch without a
real rooted phone, an emulator, or a developer account in trouble.

```kotlin
sealed class SimulatedIntegrity {
  data object Trusted : SimulatedIntegrity()
  data class Limited(val reasons: List<String>) : SimulatedIntegrity()
  data class Untrusted(val reasons: List<String>) : SimulatedIntegrity()
  data object QuotaExceeded : SimulatedIntegrity()
  data object DecoderRejected : SimulatedIntegrity()
}
```

The integrity probe consults Dev Mode in debug builds and short-circuits the real
network call. Release builds ignore the flag entirely.

> **Why this matters.** Recovery-path coverage is the most important part of a security
> rollout. Without Dev Mode toggles those paths ship untested.

---

## Wrap-Up

You can now integrate Play Integrity end-to-end — Standard prepare + per-request
hash binding, server-side decode, verdict-to-policy translation, failure-mode triage,
telemetry on every branch, and Dev Mode simulations for QA.

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) — the Keystore primitive
  that pairs with Play Integrity to form a complete attestation stack.
- [Bulletproof Security](/codelabs/bulletproof-security) — combining the two with
  fingerprinting into a single verdict.
- [Zero Trust](/codelabs/zero-trust) — the policy framework Play Integrity sits inside.

**Recap of the Play Integrity stack:**

- Standard request shape with warm prepare + per-action request.
- `setRequestHash` binding the token to specific payload bytes.
- Server-side decode via Google's `playintegrity.v1` API.
- Five verdict sub-fields, four device-integrity bands.
- Hash echo verification on every decoded verdict.
- A verdict-to-outcome function with explicit reason strings.
- Three failure modes (quota, network, decoder) with distinct policy each.
- Six telemetry events aggregated by reason string.
- A five-state Dev Mode toggle suite for QA reproducibility.
