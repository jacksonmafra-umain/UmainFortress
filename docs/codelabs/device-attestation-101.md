---
title: "Device Attestation 101"
slug: device-attestation-101
level: intermediate
estimated_minutes: 35
status: published
company: Fortress
tags:
  - attestation
  - keystore
  - play-integrity
  - device-binding
summary: >
  Build a minimal device-attestation flow end to end — generate a key inside the Android
  Keystore, register the public part with a server, ask the server for a challenge, sign it
  inside a BiometricPrompt CryptoObject, verify the signature server-side, and decide what
  to do with a "yes / maybe / no" verdict.
references:
  - title: "Device Attestation 101 (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/device-attestation-101-making-sure-your-users-arent-evil-robots-75928cc1bd0c
  - title: "Building a Bulletproof Security System — attestation + fingerprinting"
    url: https://medium.com/@jacksonfdam/building-a-bulletproof-security-system-combining-attestation-and-fingerprinting-2f4d65c02128
  - title: "Trust No One — why device verification matters"
    url: https://medium.com/@jacksonfdam/trust-no-one-why-your-android-app-needs-to-verify-devices-1228f186a941
  - title: "Android Keystore — official docs"
    url: https://developer.android.com/training/articles/keystore
---

## Welcome to attestation

Understand the difference between authenticating a *user* and attesting a *device*, and why
both matter.

Authentication tells your backend who is at the other end of the wire. Attestation tells
your backend *what* is at the other end of the wire. They are independent — a valid user on
a hostile device is still a hostile session. The simplest attestation primitive Android
gives us is a Keystore-resident key the device can prove possession of by signing a
challenge. We are going to build that flow from scratch.

> **Why this matters.** Without device-side attestation, an attacker who replays a stolen
> session token from a different device looks identical to the legitimate user. With it,
> the server can refuse step-up actions from any device it has not seen before.

---

## Step 1: Generate a Keystore-resident key

Create an EC P-256 keypair that lives inside the Android Keystore. The private key never
leaves the secure element; only signatures come out.

The flags here are deliberate. `setUserAuthenticationRequired(true)` binds the key to a
biometric / device credential — every signature requires a fresh user gesture. `setInvalidatedByBiometricEnrollment(true)` invalidates the key if the user enrols a new
fingerprint, which is the cheapest mitigation for "attacker enrolled their own biometric".

```kotlin
const val ALIAS_DEVICE_BINDING = "fortress.deviceBinding.v1"

fun generateDeviceBindingKey() {
  val spec = KeyGenParameterSpec.Builder(
    ALIAS_DEVICE_BINDING,
    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
  )
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    .setInvalidatedByBiometricEnrollment(true)
    .build()
  val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
  kpg.initialize(spec)
  kpg.generateKeyPair()
}
```

> **Why this matters.** The key cannot be exfiltrated even with root, because it lives in
> the TEE (or StrongBox). An attacker who clones your app data still cannot sign on your
> behalf without unlocking the original device.

---

## Step 2: Export the public key for registration

The server needs the public key in a portable format to verify future signatures. SPKI
(`X.509 SubjectPublicKeyInfo`) is the standard wire format; base64 it and `POST` to your
enrolment endpoint.

```kotlin
fun publicKeySpkiB64(alias: String): String {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  val cert = ks.getCertificate(alias) ?: error("missing key for $alias")
  return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
}
```

Send it once, on first launch after sign-in. The server stores `(userId, deviceId, publicKeySpkiB64)` and that record becomes the device identity for that user.

> **Why this matters.** A backend that cannot recognise your device cannot make trust
> decisions about it. The enrolment step is small but it is the foundation of everything
> that follows.

---

## Step 3: Server stores the binding

On the backend, persist the SPKI public key against `(userId, deviceId)`. Treat the binding
as durable but rotatable — a device that loses its key (Keystore wipe, factory reset)
re-enrols transparently.

```ts
router.post("/me/device-binding", requireAuth, async (req, res) => {
  const { deviceId, publicKeySpkiB64 } = req.body as {
    deviceId: string; publicKeySpkiB64: string;
  };
  const userId = req.claims!.sub;
  await deviceBindings.upsert({
    id: `${userId}:${deviceId}`,
    userId, deviceId, publicKeySpkiB64,
    createdAtEpochMs: Date.now(),
    updatedAtEpochMs: Date.now(),
  }, "id");
  res.json({ ok: true });
});
```

> **Why this matters.** The server now has a verifier — it can challenge any future
> request from this `(userId, deviceId)` pair and prove cryptographically that the original
> device made the request.

---

## Step 4: Server issues a fresh challenge

Whenever the client wants to do something sensitive, the server hands out a single-use,
short-lived nonce (16+ bytes from a CSPRNG). The challenge is tied to the action ("transfer
$500 to IBAN X") via a `payloadDigest` so a signed challenge for one action cannot be
replayed onto another.

```ts
router.post("/stepup/challenge", requireAuth, async (req, res) => {
  const action = String(req.body.action);
  const payload = req.body.payload ?? {};
  const nonce = crypto.randomBytes(32);
  const payloadDigest = crypto
    .createHash("sha256")
    .update(JSON.stringify(payload))
    .digest();
  const challenge = {
    id: crypto.randomUUID(),
    userId: req.claims!.sub,
    nonceB64: nonce.toString("base64url"),
    action,
    payloadDigestB64: payloadDigest.toString("base64url"),
    expiresAtEpochMs: Date.now() + 2 * 60_000,
    consumed: false,
  };
  await challenges.upsert(challenge, "id");
  res.json({ nonceB64: challenge.nonceB64, payloadDigestB64: challenge.payloadDigestB64 });
});
```

> **Why this matters.** Without a fresh server-generated nonce the client could replay an
> old signature. Without binding the nonce to the payload an attacker could MITM the
> signature onto a different transfer.

---

## Step 5: Client signs inside a BiometricPrompt

The signature happens inside a `BiometricPrompt` so the OS proves a *real human* authorised
this *specific signature*. The `CryptoObject` ties the prompt to the Keystore key — there
is no "authorise then sign later" gap.

```kotlin
suspend fun signChallenge(
  activity: FragmentActivity,
  alias: String,
  challenge: ByteArray,
  prompt: BiometricPrompt.PromptInfo,
): ByteArray = suspendCancellableCoroutine { cont ->
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  val key = ks.getKey(alias, null) as PrivateKey
  val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key) }

  val cb = object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      val sig = result.cryptoObject!!.signature!!
      sig.update(challenge)
      cont.resume(sig.sign()) {}
    }
    override fun onAuthenticationError(code: Int, msg: CharSequence) {
      cont.resumeWithException(StepUpError(code, msg.toString()))
    }
  }
  BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb)
    .authenticate(prompt, BiometricPrompt.CryptoObject(signature))
}
```

> **Why this matters.** The `BiometricPrompt` is what makes user-presence a cryptographic
> primitive — not a UX nicety. The signed bytes prove a fingerprint touched the sensor in
> the same second the signature was produced.

---

## Step 6: Server verifies the signature

Decode the nonce, fetch the stored public key, and verify with `SHA256withECDSA`. Consume
the challenge on success so it cannot be reused.

```ts
router.post("/stepup/verify", requireAuth, async (req, res) => {
  const { nonceB64, signatureB64, deviceId } = req.body as Record<string, string>;
  const userId = req.claims!.sub;
  const challenge = await challenges.find(
    (c) => c.userId === userId && c.nonceB64 === nonceB64 && !c.consumed
       && c.expiresAtEpochMs > Date.now(),
  );
  if (!challenge) return res.status(400).json({ code: "CHALLENGE_REJECTED" });

  const binding = await deviceBindings.find(
    (b) => b.userId === userId && b.deviceId === deviceId,
  );
  if (!binding) return res.status(400).json({ code: "DEVICE_NOT_BOUND" });

  const verifier = crypto.createVerify("SHA256");
  verifier.update(Buffer.from(challenge.nonceB64, "base64url"));
  const ok = verifier.verify(
    { key: Buffer.from(binding.publicKeySpkiB64, "base64"), format: "der", type: "spki" },
    Buffer.from(signatureB64, "base64"),
  );
  if (!ok) return res.status(400).json({ code: "SIGNATURE_INVALID" });

  await challenges.upsert({ ...challenge, consumed: true }, "id");
  res.json({ ok: true });
});
```

> **Why this matters.** This is where attestation pays off. The server has now proved that
> the request came from the original device and from a present user, both bound together
> in the same signature.

---

## Step 7: Add Play Integrity for at-rest assurance

Keystore attestation tells you the *device* is real. Play Integrity tells you the *app
binary* is real and the *device environment* is intact. Layer the two — never rely on one
alone.

```kotlin
val integrityManager = IntegrityManagerFactory.createStandard(context)
val req = StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
  .setCloudProjectNumber(BuildConfig.PLAY_INTEGRITY_PROJECT)
  .build()
val provider = integrityManager.prepareIntegrityToken(req).await()

val token = provider.request(
  StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
    .setRequestHash(nonceHex)
    .build(),
).await().token()
```

Send the token to your server, decrypt it on the backend with the Play Integrity service
account, and read the verdict. Verdict policy belongs on the server, not the device.

> **Why this matters.** Without Play Integrity, an attacker can hand-roll the entire
> attestation flow on a rooted device — they own the Keystore implementation. Play
> Integrity is what makes the Keystore signature meaningful.

---

## Step 8: Decide what to do with the verdict

Three buckets: Trusted (continue), Limited (continue with friction), Untrusted (refuse).
Wire the verdict into your app via a single `IntegrityVerdict` sealed class and let every
screen react accordingly.

```kotlin
sealed class IntegrityVerdict {
  data object Trusted : IntegrityVerdict()
  data class Limited(val reasons: List<String>) : IntegrityVerdict()
  data class Untrusted(val reasons: List<String>) : IntegrityVerdict()
}

class TransferGuard(private val verdict: IntegrityVerdict) {
  fun allowTransfer(amountMinor: Long): Decision = when (verdict) {
    IntegrityVerdict.Trusted -> Decision.Allow
    is IntegrityVerdict.Limited -> if (amountMinor < 50_00L) Decision.Allow
                                   else Decision.RequireStepUp
    is IntegrityVerdict.Untrusted -> Decision.Block
  }
}
```

> **Why this matters.** Blocking everything at the first sign of trouble is a great way to
> lose customers on legitimately broken devices. A staged policy — friction for Limited,
> refusal for Untrusted — keeps support tickets bounded.

---

## Step 9: Test the failure modes

Before shipping, force every branch through Dev Mode toggles. The Fortress demo includes
`Simulate root / Magisk`, `Simulate MITM proxy`, `Simulate replayed challenge` and
`Simulate Play Integrity fail`. If any verdict produces a different UX than you expected,
fix that before a hostile device finds it.

```kotlin
// app/.../devmode/DevModeStore.kt
data class DevModeFlags(
  val simulateRoot: Boolean = false,
  val simulateMitm: Boolean = false,
  val simulateReplay: Boolean = false,
  val simulateIntegrityFail: Boolean = false,
)
```

> **Why this matters.** Production is the wrong place to discover that your "Untrusted"
> branch silently crashes the dashboard. Toggle each scenario at least once before signing
> the release.

---

## Wrap-Up

You just built a working attestation stack: Keystore-resident keys, biometric-bound
signatures, server-side verification, and a Play Integrity overlay.

Next mission: layer fingerprinting on top — see the Bulletproof Security article in the
references for the long form, then come back for the Overlay Attacks codelab to harden the
biometric prompt itself.

**Recap of what you just built:**

- An EC P-256 keypair inside the Android Keystore, bound to user authentication.
- A registration flow that puts the public key on the server.
- A challenge / signature / verify loop that ties user presence to a specific server-issued
  nonce and payload.
- A Play Integrity overlay for at-rest device-environment assurance.
- A three-bucket verdict policy the rest of the app can branch on.
