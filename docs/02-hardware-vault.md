# 02 — The Android Vault

> "Software-only encryption of credentials is just obfuscation with extra steps. The TEE turns
> theatre into a perimeter." — *Fortress field notes*

**TL;DR** — The session token pair (access + refresh) is encrypted at rest with an **AES-256-GCM**
key that lives inside the Android Keystore — ideally StrongBox-backed. The key bytes never leave
the secure element. Even with root, `adb backup`, or a forensic image, an attacker gets opaque
ciphertext. This file walks the threat model, the key spec, the StrongBox-vs-TEE trade-off, and
how the same key dies cleanly when the user wipes biometrics.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Make the at-rest copy of tokens unrecoverable without the TEE | Lift the tokens off the device by any means |
| **Key idea** | The TEE wraps the key in hardware; userspace only orchestrates encrypt/decrypt | If I can spin up another process and call the same Keystore alias, the OS does the work for me |
| **Worst failure** | Storing tokens in plain `SharedPreferences` | App sandbox compromise lets me call your `Cipher` from a hostile process under your UID |

---

## 🛡️ Defender — "I treat the disk as adversarial"

### Where the tokens live in this repo

[`TokenStore`](../app/src/main/java/com/umain/fortress/security/TokenStore.kt) is the only
class that touches the encrypted blob:

```
plaintext JSON Session  ──►  AES-256-GCM (Keystore alias "fortress.vault.tokens")  ──►  VaultBlob(iv, ct)
                                                                                            │
                                                                                            └──►  Base64 in DataStore
```

DataStore is treated as a glorified file — opaque without the key. Nothing in the app reads or
writes session tokens through any other path.

### The key spec, line by line

[`KeystoreVault`](../app/src/main/java/com/umain/fortress/security/KeystoreVault.kt) builds the
key with this spec:

```kotlin
KeyGenParameterSpec.Builder(
    alias,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setRandomizedEncryptionRequired(true)
    .setIsStrongBoxBacked(true)   // best-effort
```

| Flag | Why |
|---|---|
| `PURPOSE_ENCRYPT or PURPOSE_DECRYPT` | This key cannot sign, derive other keys, or be exported. |
| `BLOCK_MODE_GCM` | Authenticated encryption — tampering with the ciphertext fails decryption (vs CBC, where padding-oracle attacks are a thing). |
| `ENCRYPTION_PADDING_NONE` | GCM doesn't need padding; if you set one, Keystore rejects the key. |
| `setKeySize(256)` | Symmetric crypto is cheap; pick the larger of the two standard sizes. |
| `setRandomizedEncryptionRequired(true)` | Forces the Keystore to refuse static IVs. The GCM IV is provider-generated, so this is belt-and-braces. |
| `setIsStrongBoxBacked(true)` | Requests the key live in a discrete secure element (Titan M / equivalent). Falls back silently on older devices via the try/catch in [`KeystoreVault`](../app/src/main/java/com/umain/fortress/security/KeystoreVault.kt). |

What this key is **not**:

- Not biometric-gated — it would force a fingerprint for every read of the access token, which
  would break the network layer. Biometric gating is reserved for **action-authorisation** keys
  (see [`BiometricKeyStore`](../app/src/main/java/com/umain/fortress/security/BiometricKeyStore.kt)
  and [07-biometric-hardening.md](07-biometric-hardening.md)).
- Not user-authentication-required for the same reason.

### TEE vs StrongBox

| | TEE (TrustZone) | StrongBox (Titan M / Pixel etc.) |
|---|---|---|
| Implementation | A separate, trusted execution environment inside the main SoC | A physically discrete secure element on its own bus |
| Threat model | Compromised Linux kernel cannot extract the key | Compromised TEE (e.g. via SoC-level exploits) cannot extract the key |
| Performance | Fast — shared silicon | Slower — IPC across hardware bus |
| Availability | Effectively universal on modern devices | Pixel 3+, Samsung S20+, some others |

This repo requests StrongBox and falls back to TEE silently. In production you'd add telemetry
to know which devices fell back — the threat profile is different and the risk engine may want
to weigh it ([09-zero-trust.md](09-zero-trust.md)).

### Key lifecycle

The key is created lazily on first encrypt. It is destroyed by:

- `KeystoreVault.invalidate(alias)` — explicit, called on logout to make leftover ciphertext
  unrecoverable.
- App data clear / app uninstall — the OS reclaims the key.
- Factory reset.

What does **not** invalidate it:

- Adding or removing fingerprints (this is intentional — at-rest keys should outlive biometric
  re-enrolment). Action-auth keys use `setInvalidatedByBiometricEnrollment(true)` instead.

### Why IV next to ciphertext is fine

GCM IVs only need to be **unique** per (key, IV) pair — not secret. We store them base64-encoded
next to the ciphertext: `<iv-b64>.<ciphertext-b64>`. The provider generates the IV; we never
reuse one. If we *did* reuse one, an attacker observing two ciphertexts could XOR-cancel and
recover plaintext differences — but the OS-provided cipher is the only path that initialises a
fresh IV per call.

### Manifest hardening that supports this

[`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml):

```xml
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:networkSecurityConfig="@xml/network_security_config"
```

- `allowBackup="false"` — `adb backup -shared` cannot exfiltrate `data/data/.../files`,
  including the DataStore blob.
- `dataExtractionRules` — opt out of device-transfer copies for sensitive paths.
- `networkSecurityConfig` — cleartext disabled outside dev loopback. See [08-network-warfare.md](08-network-warfare.md).

---

## ⚔️ Attacker — "I never see the key, but maybe I don't have to"

### Bypass 1 — Pull the key bytes

Standard answer for unprotected keys, fails here. Keystore exports nothing for symmetric keys
created with `PURPOSE_ENCRYPT/DECRYPT`. `keystore-encryption-key` from a non-hardware-backed
store would let me copy bytes; the hardware-backed one does not.

**Counter:** keep `setIsStrongBoxBacked` (or accept TEE fallback) and never set
`setUserAuthenticationRequired(false)` + `setUserPresenceRequired(false)` while also exposing
the key for export. Symmetric Keystore keys have no `KeyStore.Entry.getKey()` path that returns
raw bytes.

### Bypass 2 — Call your Cipher from another process under your UID

If I get code execution inside the app sandbox (malicious SDK pulled into the build, Frida
attached to the process, dynamic code loaded), the OS happily lets me call `Cipher.getInstance(...)`
and decrypt the blob with your Keystore alias. No raw key needed.

**Counter:**
- Code-execution-inside-the-process is the high bar. Reduce attack surface:
  - SDK hygiene (no opaque blobs from random vendors).
  - `setReleaseDebuggable` off in release.
  - Runtime hooking detection — see [14-rasp-strategies.md](14-rasp-strategies.md).
  - Play Integrity verdict required for sensitive operations — see [05-play-integrity.md](05-play-integrity.md).
- For ultra-sensitive operations, layer in a biometric-gated key so a hooked process still
  cannot mint signatures without showing the prompt — see [07-biometric-hardening.md](07-biometric-hardening.md).

### Bypass 3 — `adb backup` the device

Goal: pull `data/data/com.umain.fortress/files/datastore/fortress_tokens.preferences_pb` and
parse it. Without the Keystore key it's ciphertext.

**Counter:**
- `allowBackup="false"` — and verify it sticks: the merged manifest is what the OS sees, not
  your source.
- Even with the backup, the resulting blob is unusable without the on-device key.

### Bypass 4 — Forensic image after factory reset / root

Same as above — without the in-TEE key, the blob is opaque. On factory reset the key is
destroyed; on a root-after-the-fact the key has never left the TEE.

### Bypass 5 — Side-channel timing

Repeated GCM operations have timing characteristics. Practical attacks on hardware-backed AES
keys require physical access and lab equipment well beyond the threat model of a financial app
on consumer phones.

**Counter:** out of scope for this app's threat model. If you're defending against a nation-state
with lab access, you have a bigger conversation than this doc.

### Bypass 6 — Replace the key alias

If I get write access to the Keystore (e.g. via a misconfigured Smart Lock backup that imported
my key), I can replace your alias. Your decrypt now produces garbage; you fail loudly and force
re-login. Not a token-theft attack, but a denial-of-service vector.

**Counter:**
- Keystore key replacement requires the same UID — practically same-process compromise — and
  alias collision. Highly unlikely in shipped apps.
- Detect: on decrypt failure, surface as `SignedOut` cleanly and re-issue. Don't try to "recover"
  the old data; refusal is the correct behaviour.

---

## Cross-reference

- **What tokens get stored in here** → [01-stateless-auth.md](01-stateless-auth.md)
- **A different key spec for action authorisation** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Backups, manifest, and lateral exposure** → [16-content-providers.md](16-content-providers.md)

## References

- [Part 2 — The Android Vault: Hardware-Backed Token Storage](https://blog.stackademic.com/part-2-the-android-vault-hardware-backed-token-storage-a8beec566d81)
- [Android Developers — Use the Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [AOSP — Hardware-backed Keystore](https://source.android.com/docs/security/features/keystore)
- [Android Developers — StrongBox](https://developer.android.com/privacy-and-security/keystore#StrongBox)
