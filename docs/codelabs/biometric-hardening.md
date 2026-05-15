---
title: "Biometric hardening + user intent"
slug: biometric-hardening
level: advanced
estimated_minutes: 35
status: published
company: Fortress
tags:
  - biometric
  - cryptoobject
  - step-up
  - keystore
summary: >
  Bind a BiometricPrompt to a specific cryptographic operation via CryptoObject so the
  signed bytes are unforgeable proof that a real human authorised this specific action in
  this specific moment — and learn the failure modes that turn the prompt into theatre if
  you skip the binding.
references:
  - title: "Biometric hardening (Fortress doc — canonical reference for style + depth)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/07-biometric-hardening.md
  - title: "BiometricPrompt — official docs"
    url: https://developer.android.com/training/sign-in/biometric-auth
  - title: "androidx.biometric.BiometricPrompt.CryptoObject"
    url: https://developer.android.com/reference/androidx/biometric/BiometricPrompt.CryptoObject
---

## Welcome to biometric hardening

Understand the gap between "the user authenticated" and "this specific operation was
authorised", and why only the second matters in fintech.

The default `BiometricPrompt` call returns a yes/no. That is enough for unlocking a UI
state, but it is not enough to authorise a transfer. A `CryptoObject` ties the prompt to a
specific cryptographic primitive (a `Signature`, `Cipher`, or `Mac`). The OS only allows
the primitive to operate *after* a successful biometric, and the resulting bytes prove
both "user was present" and "this exact bytes were authorised". Lose either binding and
you have ceremony, not security.

> **Why this matters.** A biometric prompt without a `CryptoObject` proves nothing. An
> attacker who replays a "yes" gets the same flow. The signed bytes from a `CryptoObject`
> are non-replayable evidence of intent.

---

## Step 1: Generate a step-up signing key

EC P-256, biometric-required, invalidated on enrolment change. Same shape as the
device-binding key, different alias so the policies stay independent.

```kotlin
const val ALIAS_STEP_UP = "fortress.stepUp.v1"

fun generateStepUpKey() {
  val spec = KeyGenParameterSpec.Builder(
    ALIAS_STEP_UP,
    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
  )
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    .setInvalidatedByBiometricEnrollment(true)
    .build()
  KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    .apply { initialize(spec) }
    .generateKeyPair()
}
```

`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`: the `0` is the validity
timeout in seconds — `0` means "every cryptographic operation requires a fresh prompt".
That is the whole point.

> **Why this matters.** A non-zero timeout caches the authentication, and now the prompt
> on the previous screen authorises the next one. That is exactly the gap an attacker
> with a stolen unlocked phone walks through.

---

## Step 2: Initialise the Signature without authenticating yet

Allocate a `Signature` object and call `initSign(privateKey)`. The Keystore intercepts:
because the key requires auth, the `Signature` is in an "unusable" state until the user
passes a biometric.

```kotlin
fun prepareSignature(): Signature {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  val privateKey = ks.getKey(ALIAS_STEP_UP, null) as PrivateKey
  return Signature.getInstance("SHA256withECDSA").apply {
    initSign(privateKey) // throws UserNotAuthenticatedException if no recent auth
  }
}
```

The exception path is the happy path here. We *want* it to throw — that means the key
is correctly bound to authentication. We wrap the `Signature` in a `CryptoObject` and let
the OS unlock it.

> **Why this matters.** A `Signature` that initSigns without throwing is a `Signature`
> the OS will allow you to sign with on any thread, any time. That is the wrong state
> for step-up.

---

## Step 3: Build the BiometricPrompt with the right authenticator class

Three constants matter:

- `BIOMETRIC_STRONG` — Class 3, hardware-backed, suitable for `CryptoObject`.
- `BIOMETRIC_WEAK` — Class 2, software fallback, cannot back a `CryptoObject`.
- `DEVICE_CREDENTIAL` — PIN/pattern/password, never use as the sole authenticator for
  step-up (defeats the purpose).

```kotlin
val prompt = BiometricPrompt.PromptInfo.Builder()
  .setTitle("Confirm transfer")
  .setSubtitle(formatMoney(transfer.amount))
  .setDescription("To ${transfer.recipient}")
  .setNegativeButtonText("Cancel")
  .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
  .setConfirmationRequired(true)
  .build()
```

`setConfirmationRequired(true)` forces an explicit tap after a passive face-unlock match.
Worth the extra UX friction on irreversible actions.

> **Why this matters.** A face-unlock match alone is not user intent. The explicit
> confirmation tap is the difference between "authenticated" and "authorised".

---

## Step 4: Show the prompt with the CryptoObject

`authenticate(prompt, cryptoObject)`. The OS pops the bottom sheet; on success it gives
you back the same `CryptoObject` with the `Signature` now unlocked.

```kotlin
suspend fun signChallenge(
  activity: FragmentActivity,
  challenge: ByteArray,
  prompt: BiometricPrompt.PromptInfo,
): ByteArray = suspendCancellableCoroutine { cont ->
  val signature = prepareSignature()
  val cb = object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      val sig = result.cryptoObject?.signature ?: run {
        cont.resumeWithException(StepUpError(ERR_NO_CRYPTO, "Crypto-less success"))
        return
      }
      try {
        sig.update(challenge)
        cont.resume(sig.sign()) {}
      } catch (e: Exception) {
        cont.resumeWithException(StepUpError(ERR_SIGN_FAILED, e.message ?: "sign failed"))
      }
    }
    override fun onAuthenticationError(code: Int, msg: CharSequence) {
      cont.resumeWithException(StepUpError(code, msg.toString()))
    }
    override fun onAuthenticationFailed() = Unit // retry, not fatal
  }
  BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb)
    .authenticate(prompt, BiometricPrompt.CryptoObject(signature))
}
```

The "no `cryptoObject` on success" branch is rare but real — guard against it. The
"`onAuthenticationFailed`" callback is the "bad fingerprint, try again" path; do not
treat it as terminal.

> **Why this matters.** The callback contract is asymmetric. `succeeded` is a yes,
> `error` is a no, `failed` is a try-again. Treating `failed` as terminal turns a
> three-attempt UX into a one-attempt UX users will hate.

---

## Step 5: Bind the signature to the payload

The challenge bytes are *not* just the server-issued nonce. They are
`SHA-256(canonical(payload))` — the digest of the exact request the user is authorising.
A signature on a generic "step-up me" challenge is replayable to any other request.

```kotlin
fun signableChallenge(nonceB64: String, payload: TransferPayload): ByteArray {
  val canonical = Json.encodeToString(payload)
  val payloadDigest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
  val nonce = Base64.decode(nonceB64, Base64.NO_WRAP)
  return ByteBuffer.allocate(nonce.size + payloadDigest.size)
    .put(nonce).put(payloadDigest).array()
}
```

Server-side verification mirrors: re-compute `SHA-256(canonical(payload))`, prepend the
nonce, verify against the device-binding public key.

> **Why this matters.** A signature without a payload binding is reusable. An attacker
> who can MITM (or just steal a signature from a previous flow) can apply it to a
> different transfer.

---

## Step 6: Handle the lockout and enrolment-change paths

`BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` returns a status code. Five outcomes
matter:

- `BIOMETRIC_SUCCESS` — proceed.
- `BIOMETRIC_ERROR_HW_UNAVAILABLE` — fall back to step-up via password (or refuse, depending
  on policy).
- `BIOMETRIC_ERROR_NONE_ENROLLED` — direct the user to system Settings to enrol.
- `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` — the device needs an OS patch. Refuse high-
  stakes actions until updated.
- `BIOMETRIC_ERROR_LOCKOUT` / `BIOMETRIC_ERROR_LOCKOUT_PERMANENT` — too many failed
  attempts. UX-wise, surface a meaningful message; security-wise, the lockout itself is
  the defence and you do not need to add more.

```kotlin
fun preflight(context: Context): BiometricStatus {
  val bm = BiometricManager.from(context)
  return when (bm.canAuthenticate(BIOMETRIC_STRONG)) {
    BIOMETRIC_SUCCESS -> BiometricStatus.Ready
    BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.Enrol
    BIOMETRIC_ERROR_LOCKOUT, BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> BiometricStatus.LockedOut
    BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.UpdateOs
    else -> BiometricStatus.Unavailable
  }
}
```

> **Why this matters.** Most production crashes in biometric flows come from skipping the
> preflight and trying to authenticate on a device that has no enrolled biometric. The
> prompt fails with `ERROR_NO_BIOMETRICS` and the user has no clue what to do.

---

## Step 7: Tie the prompt to the action explicitly in copy

The strings shown in the prompt are themselves a security boundary. The user reads them
and consents. "Confirm" is meaningless. "Confirm transfer of $500 to Jerry Helfer" is
not. The OS displays them verbatim; a hostile overlay attack cannot rewrite them.

```kotlin
val prompt = BiometricPrompt.PromptInfo.Builder()
  .setTitle("Confirm transfer")
  .setSubtitle(formatMoney(transfer.amount))
  .setDescription("To ${transfer.recipient.name}\nIBAN ${transfer.recipient.iban}")
  .setNegativeButtonText("Cancel")
  .setAllowedAuthenticators(BIOMETRIC_STRONG)
  .setConfirmationRequired(true)
  .build()
```

Always render the exact amount, the exact recipient, and the action verb. The user is
authenticating *with the OS*, not with your app — your app's overlay cannot lie at this
point.

> **Why this matters.** The prompt copy is the only piece of UI on the screen the
> overlay-attack codelab promises is uncompromised. Use that real estate.

---

## Step 8: Server-side verification mirrors the client

Decode the nonce, re-compute the payload digest, prepend, verify with the registered
public SPKI. Reject any of:

1. Signature does not verify.
2. Nonce already consumed.
3. Nonce expired.
4. Payload digest does not match what the client claimed in the request body.

```ts
const expected = Buffer.concat([nonce, payloadDigest]);
const verifier = crypto.createVerify("SHA256");
verifier.update(expected);
const ok = verifier.verify(
  { key: Buffer.from(binding.publicKeySpkiB64, "base64"), format: "der", type: "spki" },
  Buffer.from(signatureB64, "base64"),
);
if (!ok) return res.status(400).json({ code: "SIGNATURE_INVALID" });
```

Persist the consumed nonce so a replay returns `NONCE_REUSED`. The challenge row goes
from `consumed = false` to `consumed = true` atomically.

> **Why this matters.** Without consume-on-verify, a captured `(nonce, signature)` pair
> is replayable until expiry. Five minutes of replay window is plenty for an attacker.

---

## Step 9: Add the Dev Mode replay simulation

Force the failure with a Dev Mode toggle so QA can prove the path works.

```kotlin
class SimulatedReplayInterceptor(private val devMode: DevModeStore) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val req = chain.request()
    val isVerify = req.url.pathSegments.lastOrNull() == "verify"
    val replay = runBlocking { devMode.flags().first().simulateReplay }
    if (isVerify && replay) {
      return Response.Builder()
        .request(req).protocol(Protocol.HTTP_1_1).code(400)
        .message("Simulated replay")
        .body("{\"code\":\"CHALLENGE_REJECTED\"}".toResponseBody(JSON))
        .build()
    }
    return chain.proceed(req)
  }
}
```

The UI sees the same `CHALLENGE_REJECTED` code the real server emits, so the recovery
path can be exercised in QA without a real attacker.

> **Why this matters.** Recovery paths in security flows are almost never tested in
> production until the day they have to be. A Dev Mode toggle is the cheapest way to
> verify them.

---

## Step 10: Audit your own copy and assertions

Before shipping a biometric flow, walk it once with an auditor's checklist:

1. Is every `CryptoObject` initialised with a key that has
   `setUserAuthenticationRequired(true)`?
2. Are the timeouts on those keys `0`?
3. Is `setInvalidatedByBiometricEnrollment(true)` set?
4. Does the prompt copy name the action, the amount, and the counterparty?
5. Does the server verify nonce + payload digest, not just the nonce?
6. Does the server consume the nonce atomically with verification?
7. Is there a Dev Mode toggle that proves the replay-rejected path?

Seven yes answers means you have done the work.

> **Why this matters.** Reviewing your own code with someone else's eyes is the only way
> the obvious mistakes get caught. The checklist is short on purpose.

---

## Wrap-Up

Biometric step-up is no longer ceremony — every prompt produces a signature that proves
which action was authorised, by whom, in this exact moment, against a server-issued nonce
that is consumed on use.

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) for the device-binding key
  that pairs with this signature.
- [Android Overlay Attacks](/codelabs/android-overlay-attacks) for the defences that
  protect the prompt itself.

**Recap of what you just built:**

- A biometric-bound EC P-256 step-up key with a zero auth timeout.
- A `CryptoObject`-wrapped `Signature` that the OS unlocks only after biometric.
- Payload-bound challenge bytes (`nonce + SHA-256(payload)`).
- Preflight handling of every enrolment / lockout / update-required state.
- Prompt copy that names the action, amount, and counterparty unambiguously.
- A Dev Mode replay simulation that exercises the rejection path.
