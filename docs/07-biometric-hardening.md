# 07 — Biometric Hardening & User Intent

> "A fingerprint isn't a password. It's a permission slip the OS hands back to your process — and a permission slip can be forged, replayed, or signed by the wrong office." — *Fortress field notes*

**TL;DR** — `BiometricPrompt` returning `SUCCESS` proves *something biometric happened on this device a moment ago*. It does **not** prove that the user authorized **this specific action** on **this app's behalf**, unless you bind the prompt to a `CryptoObject` whose key is gated by `setUserAuthenticationRequired(true)`. Everything else is theater.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Cryptographically bind a user's biometric to a specific action | Make the app *believe* a biometric happened, without one |
| **Key idea** | If `signature.sign(challenge)` returns bytes, a real biometric *just* happened in StrongBox/TEE | If the app only checks the `AuthenticationResult` boolean, I can patch the boolean |
| **Worst failure** | Calling `BiometricPrompt` without a `CryptoObject` and trusting the callback | Frida hook returning `AuthenticationResult.SUCCESS` from userspace |

---

## 🛡️ Defender — "I treat the biometric as a key, not a switch"

### The mental model

Biometric authentication on Android is a **policy on a key**, not a check on the user. When you create a key in the Android Keystore with:

```kotlin
KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    .setInvalidatedByBiometricEnrollment(true)
    .setIsStrongBoxBacked(true) // best-effort
    .build()
```

…the **TEE/StrongBox itself** refuses to perform `sign()` unless a biometric ceremony has succeeded in the last *N* seconds. The OS, not your app, enforces the gate. Your app can be patched, hooked, or replayed — but the cryptographic operation cannot complete without a real, recent, strong-class biometric.

### The signing dance

Step-up flow for "Transfer 1000 EUR to IBAN XX":

```kotlin
// 1. Backend issues a per-action challenge (nonce + action hash + expiry)
val challenge: ByteArray = api.requestStepUpChallenge(action = TRANSFER, payload = transferDto)

// 2. Initialize a Signature with the auth-gated key — this prepares the operation
//    but DOES NOT yet sign. The TEE is waiting for the biometric.
val signature = Signature.getInstance("SHA256withECDSA").apply {
    initSign(keystore.getEntry(KEY_ALIAS, null).let { (it as PrivateKeyEntry).privateKey })
}

// 3. Wrap the Signature in a CryptoObject and present BiometricPrompt
val cryptoObject = BiometricPrompt.CryptoObject(signature)
BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        // 4. The Signature inside result is now AUTHORIZED for ONE sign() call.
        val sig = result.cryptoObject!!.signature!!
        sig.update(challenge)
        val signedChallenge = sig.sign()
        // 5. Send signed challenge to backend. Backend verifies with the public key
        //    it stored at enrollment. If verification passes, the action is committed.
        api.commitTransfer(transferDto, signedChallenge)
    }
}).authenticate(promptInfo, cryptoObject)
```

The key insight: **the signed bytes are the proof**. Even if every line of Kotlin is hostile, the bytes can only come into existence if the TEE saw a real biometric.

### Five non-negotiables

1. **Always use `CryptoObject`.** Plain `BiometricPrompt.authenticate(promptInfo)` (no crypto object) is a UX widget, not a security boundary. The callback can be forged.
2. **Require `BIOMETRIC_STRONG`.** Class 3 (Strong) is the only tier suitable for cryptographic operations. `BIOMETRIC_WEAK` (face on most devices that didn't pass the spoof tests) cannot be bound to keys.
3. **`setInvalidatedByBiometricEnrollment(true)`.** When the user adds a new fingerprint, the key is destroyed. Without this, an attacker who briefly gets the device PIN can enroll their own finger and use the existing key forever.
4. **Per-action challenges.** Re-using a single "I am the user" assertion across multiple actions = replayable. Bind each high-risk action to a server-issued nonce + action hash.
5. **Server-side public key registry.** The backend stores the public key per (user, device, key alias). It verifies signatures. The phone proves nothing on its own — the server is the source of truth.

### Step-up scenarios in Fortress Bank

| Action | Gate |
|---|---|
| Open app | Cached session token + integrity check |
| View balance | Session token alone |
| Reveal IBAN / full PAN | `BiometricPrompt` + `CryptoObject` (sign challenge: `reveal:<account_id>:<nonce>`) |
| Transfer ≤ €100 | `BiometricPrompt` + `CryptoObject` (sign challenge with transfer payload hash) |
| Transfer > €1000 or new payee | Above + risk-engine green light + 24h device trust age |
| Change security settings | Above + password re-entry |

### Implementation: file map in this repo

- [`android/app/src/main/kotlin/com/umain/fortress/security/BiometricKeyStore.kt`](../android/app/src/main/kotlin/com/umain/fortress/security/BiometricKeyStore.kt) — key generation / retrieval with the auth-gated spec above
- [`android/app/src/main/kotlin/com/umain/fortress/security/StepUpAuthenticator.kt`](../android/app/src/main/kotlin/com/umain/fortress/security/StepUpAuthenticator.kt) — the dance, wrapped in a suspending function
- [`android/app/src/main/kotlin/com/umain/fortress/ui/components/StepUpSheet.kt`](../android/app/src/main/kotlin/com/umain/fortress/ui/components/StepUpSheet.kt) — Compose bottom sheet
- [`backend/src/routes/stepup.ts`](../backend/src/routes/stepup.ts) — challenge issuance + signature verification

---

## ⚔️ Attacker — "I bypass the prompt entirely"

### How I'd come at it

The biometric prompt is just Android IPC and a callback. If you only check the callback's boolean, I will hand you a forged boolean.

### Bypass 1 — Frida hook on the callback (no crypto object)

If you wrote this:

```kotlin
BiometricPrompt(activity, executor, object : AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: AuthenticationResult) {
        proceedWithTransfer() // ❌ trusts the boolean
    }
}).authenticate(promptInfo)
```

…then on a rooted device with Frida, my script is two lines:

```javascript
Java.perform(() => {
    const AC = Java.use("androidx.biometric.BiometricPrompt$AuthenticationCallback");
    AC.onAuthenticationSucceeded.implementation = function (result) {
        // I never touched the sensor. The prompt may not even have shown.
        this.onAuthenticationSucceeded(result);
    };
});
```

**Counter:** use `CryptoObject`. My hook can call your callback all day; without the TEE-signed bytes, your server rejects the request.

### Bypass 2 — Replay a previous signed challenge

If the server reuses challenges, or doesn't bind the challenge to the action payload, I capture a signed "approve transfer of €10 to Bob" and replay it as "approve transfer of €10,000 to me."

**Counter:** challenge = `HMAC(server_secret, user_id || action || canonical_payload || nonce || expiry)`. Server verifies it generated this *exact* challenge for *this exact* payload, then burns the nonce.

### Bypass 3 — Enrolment hijack

I get 30 seconds with the unlocked phone. I add my fingerprint via Settings. Now every existing biometric-gated key in your app accepts my finger forever.

**Counter:** `setInvalidatedByBiometricEnrollment(true)` — adding a new biometric nukes the key. Force re-enrollment of the app's signing key, which requires the *current* user's existing biometric.

### Bypass 4 — Class 2 (Weak) face unlock

On many OEM devices, face unlock is **Class 2 (Weak)**. You cannot bind a key to it — Keystore will throw. If you fell back to "any biometric works", I 3D-print a face from a LinkedIn photo and walk in.

**Counter:** `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`. If only Weak is available, force password fallback or refuse the operation.

### Bypass 5 — `setUserAuthenticationValidityDurationSeconds` time-based keys

If the key spec uses time-based validity (`setUserAuthenticationParameters(30, AUTH_BIOMETRIC_STRONG)`), then after a successful biometric, the key is usable from any code path for 30 seconds — no further prompt needed. I trigger your legitimate biometric flow (e.g., "reveal balance"), then immediately fire a different code path that uses the same key for a transfer. The TEE says "yes, biometric was recent" and signs.

**Counter:** use **per-operation** auth (`setUserAuthenticationParameters(0, ...)`). The key is good for *one* operation, not *N seconds of operations*.

### Bypass 6 — Snapshot the key alias and import on another device

Tried and failed for me — the Android Keystore key is non-exportable, hardware-bound. Listed only for completeness; this is the part the OS *actually* gets right.

### What I look for in your APK

Decompile (see [12-decompiling.md](12-decompiling.md)) and grep for:

```bash
# Bad sign — direct callback trust
grep -r "onAuthenticationSucceeded" → check for any branch that doesn't reach signature.sign()

# Bad sign — no CryptoObject
grep -r "BiometricPrompt" | grep -v "CryptoObject"

# Bad sign — long auth validity windows
grep -r "setUserAuthenticationValidityDurationSeconds"
grep -r "setUserAuthenticationParameters" → second arg should be 0 for per-op
```

If I see any of those, I write the Frida hook and stop reading.

---

## Cross-reference

- **Storage of the auth-gated key** → [02-hardware-vault.md](02-hardware-vault.md)
- **Server-side challenge issuance** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Risk engine that decides *when* to step up** → [09-zero-trust.md](09-zero-trust.md)
- **Frida / hooking countermeasures** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **What happens on rooted devices** → [11-root-detection.md](11-root-detection.md)

## References

- [Part 7 — Identity Check: Biometric Hardening & User Intent](https://blog.stackademic.com/part-7-identity-check-biometric-hardening-user-intent-4eb927397e8b)
- [Android Developers — Use a cryptographic solution for sensitive information](https://developer.android.com/training/sign-in/biometric-auth#crypto)
- [AOSP — Biometric authentication classes (Strong/Weak/Convenience)](https://source.android.com/docs/security/features/biometric)
