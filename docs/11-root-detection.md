# 11 — Root Detection Is Dead: What Actually Works in 2026

> "Checking for `su` in `$PATH` is the security equivalent of locking your front door but
> putting the key under the welcome mat — except the mat has been moved a hundred times and
> nobody bothered to tell you." — *Fortress field notes*

**TL;DR** — Userspace root detection (su-binary scan, RW path checks, busybox lookups, package
manager probes) is **defeated by default** on any device with Magisk + the right modules.
What still works in 2026: **Play Integrity** (for the device verdict), **KeyAttestation** (for
hardware-attested key generation), and **layered signal blending** (RASP + Play Integrity +
risk engine). This file walks why the old tricks died, what replaced them, and the attacker's
inventory.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Get a hardware-attested verdict that survives the userspace shenanigans | Hide root from anything the app could observe |
| **Key idea** | Move the gate from "what the app sees" to "what the TEE / Google attests" | If the gate is anything the app sees, I show it what it wants to see |
| **Worst failure** | Shipping any kind of "is it rooted?" boolean check the app trusts | A defender's check that fails open on parse errors |

---

## 🛡️ Defender — "I outsource the question to silicon"

### Why the classic checks are dead

Old root detection libraries (`RootBeer`, `SafetyNetHelper`, hand-rolled) typically did some of:

```kotlin
// 1. Look for su binaries
File("/system/bin/su").exists()
File("/system/xbin/su").exists()
File("/sbin/su").exists()

// 2. Look for root apps
packageManager.getPackageInfo("com.topjohnwu.magisk", 0)

// 3. Look for Xposed / hooks
File("/system/framework/XposedBridge.jar").exists()

// 4. Check selinux mode
Os.uname().release.contains("magisk", ignoreCase = true)

// 5. Try executing su
Runtime.getRuntime().exec("su").exitValue() != 0
```

Every single one of these is mitigated by **Magisk DenyList** (since Magisk 24.0+) or **Zygisk
modules**:

| Check | How DenyList mitigates |
|---|---|
| File existence | Magisk mounts a clean view of the filesystem for the targeted app — the su binaries don't exist *from the app's perspective* |
| Package lookup | Hide-from-target hooks `PackageManager.getPackageInfo` to throw `NameNotFoundException` |
| Property reads | `getprop ro.boot.verifiedbootstate` returns `green` instead of `orange` |
| `Runtime.exec("su")` | The su shim is gated by the DenyList; from the app's UID it's not available |

These checks don't even need a sophisticated attacker. Free Magisk modules ("Universal Safetynet
Fix", "Play Integrity Fix") flip the right switches and your detection is gone.

### What still works: hardware-attested KeyAttestation

When you generate a key in the Android Keystore, you can request an **attestation certificate**
that comes from Google's hardware root of trust. The cert chain encodes:

- `attestationVersion` (which Android KeyMaster version)
- `attestationSecurityLevel` — `Software`, `TrustedEnvironment`, or `StrongBox`
- `verifiedBootKey` — the cryptographic fingerprint of the bootloader
- `verifiedBootState` — `Verified`, `SelfSigned`, `Unverified`, `Failed`
- `verifiedBootHash`

The cert chain roots in a Google-controlled CA. **A device with an unlocked bootloader cannot
mint an attestation chain that claims `Verified` + the original boot hash** — the hardware
RoT refuses.

In Fortress, we read this during device-binding enrolment:

```kotlin
fun deviceAttestation(alias: String): AttestationVerdict {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val certChain = ks.getCertificateChain(alias) ?: return AttestationVerdict.Unknown

    val factory = CertificateFactory.getInstance("X.509")
    val attestation = factory.generateCertificate(
        certChain[0].encoded.inputStream()
    ) as X509Certificate

    // The attestation extension is OID 1.3.6.1.4.1.11129.2.1.17.
    // Parse the ASN.1 to extract verifiedBootState, attestationSecurityLevel, etc.
    val ext = attestation.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")
        ?: return AttestationVerdict.Unknown
    // ...parse ASN.1, validate chain against pinned Google root...
}
```

The **server** also receives the cert chain (or its hash) and validates against Google's
published root keys. Doing this *only* on the client is meaningless — see attacker section.

A `KeyInfo` shortcut (no cert-chain parsing) is also available for a coarser signal:

```kotlin
val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
val info = factory.getKeySpec(privateKey, KeyInfo::class.java)
when (info.securityLevel) {
    KeyProperties.SECURITY_LEVEL_STRONGBOX  -> { /* high trust */ }
    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> { /* normal trust */ }
    KeyProperties.SECURITY_LEVEL_SOFTWARE   -> { /* refuse */ }
    KeyProperties.SECURITY_LEVEL_UNKNOWN    -> { /* refuse */ }
}
```

If a Keystore claims `TRUSTED_ENVIRONMENT` but the bootloader is unlocked, the chain validation
at the server catches the lie. The local `securityLevel` query is for fast-fail UX; the server
chain check is the authority.

### What still works: Play Integrity

Covered in detail in [05-play-integrity.md](05-play-integrity.md). The summary in this context:

- **`MEETS_STRONG_INTEGRITY`** → hardware-attested boot, no known root
- **`MEETS_DEVICE_INTEGRITY`** → bootloader locked, standard Android
- **`MEETS_BASIC_INTEGRITY`** only → user has rooted the device (bootloader unlocked, or
  systemless root that *Google has flagged*), or running a custom ROM
- **`MEETS_VIRTUAL_INTEGRITY`** → emulator

Combine with KeyAttestation: `MEETS_STRONG_INTEGRITY` + `StrongBox` security level is roughly
"unmodified, hardware-rooted, full hardware vault". That's the baseline for high-value actions.

### What still works: RASP heuristics (layered with the above)

Run-time checks the *attacker* can hook, *if they have already injected something into the
process*. Useful as a tripwire — if these fire, you assume the process is compromised:

| Tripwire | Detects |
|---|---|
| `Debug.isDebuggerConnected()` | Active JDWP debugger |
| `/proc/self/status` → `TracerPid` ≠ 0 | ptrace attached (Frida, etc.) |
| `/proc/self/maps` contains `frida-agent.so`, `xposed`, `riru` | Library injection |
| `Build.TAGS` contains `test-keys` | Custom ROM |
| `Build.FINGERPRINT.startsWith("generic")` | Emulator (sometimes — modern emulators lie) |
| `pm list packages` contains known Magisk apps (best-effort) | User-installed root manager |

None of these are reliable on their own. As a **last line** before a sensitive operation —
running them and combining the result with Play Integrity + KeyAttestation gives a more honest
picture than any one signal alone.

### The blended verdict

```
verdict = combine(
  playIntegrity = standardRequest(),     // Google's view
  keyAttestation = attestationChain(),   // hardware's view (Google CA chain)
  raspSignals    = runtimeProbes(),      // process-internal view
  riskEngine     = serverDecide(...)     // server-side aggregation
)
```

Mapped to UX:

- All four "clean" → full functionality
- `keyAttestation` says StrongBox but `playIntegrity` is `BASIC` → trust the hardware, suspect
  Play Integrity is being suppressed; degrade to read-only
- `playIntegrity` says `STRONG` but RASP detects a hooked process → trust your own eyes; refuse
- Any signal hard-fails → refuse all sensitive ops, surface a clear message

### Fortress's implementation, today

[`IntegrityCheck`](../app/src/main/kotlin/com/umain/fortress/security/IntegrityCheck.kt) is
a stub returning `Trusted`. The wire-up for Play Integrity and KeyAttestation chain extraction
is staged for the next pass. The architecture is ready — the consumer side ([`SecurityChip`](../app/src/main/kotlin/com/umain/fortress/ui/components/SecurityChip.kt)
and [`DashboardViewModel`](../app/src/main/kotlin/com/umain/fortress/ui/screens/dashboard/DashboardViewModel.kt))
already handles the three-state verdict. Plugging in real signals replaces the stub without
touching the rest of the app.

---

## ⚔️ Attacker — "I hide what the app could see, and I read the wind"

### Bypass 1 — Magisk DenyList + Play Integrity Fix

Standard kit, free, takes five minutes. Install Magisk, enable Zygisk, install Play Integrity
Fix module, add Fortress to DenyList. Every userspace root detection check passes. Play Integrity
may also return `MEETS_DEVICE_INTEGRITY` if the fix module is current — Google patches in
waves and the fix modules ship updates the same week.

**Counter:**
- Require `MEETS_STRONG_INTEGRITY` for high-value ops. Strong integrity requires the bootloader
  to be locked and verified-boot to be `green` — Magisk cannot bypass this because the verdict
  is signed by the device's hardware-rooted attestation, not by something Magisk can touch.
- KeyAttestation chain validation server-side. The `verifiedBootState` in the attestation
  extension is signed by Google's hardware root — un-spoofable in userspace.

### Bypass 2 — Bootloader-locked custom ROM with leaked Google keys

A few times in history, Google's signing keys for specific device families have leaked (Pixel 6
DAR keys, etc.). A custom ROM signed with the leaked keys can present as "Verified" boot state
on those devices, and `MEETS_DEVICE_INTEGRITY` passes.

**Counter:**
- Google revokes the leaked keys. Server-side chain validation checks against the current
  Google JWKS / KeyAttestation trust roots, and revoked certs are rejected.
- Maintain a denylist of known-compromised device families if the revocation timeline is slow.

### Bypass 3 — Memory hooking after KeyAttestation has succeeded

Even if the attestation chain at enrolment is genuine, once the process runs I can Frida-hook
the API calls *after* attestation. The local `KeyInfo.securityLevel` query returns whatever
the hook says.

**Counter:**
- Server validation is the truth — local checks are diagnostic.
- The TEE-bound private key still cannot be exported, so even with hooked metadata I can't
  sign on another device.
- RASP probes detect Frida in the process and refuse to continue.

### Bypass 4 — Run the app on a real, attested device under my control

Buy a Pixel 8, leave it stock, enrol Fortress as a victim user. Now I have a real attested
device. Use it to issue and complete step-up flows on behalf of the victim's session.

This is the same shape as Bypass 3 from [05-play-integrity.md](05-play-integrity.md). It's
expensive (real device, real account, real network footprint) and high-friction (manual or
limited-throughput).

**Counter:**
- Device binding ([09-zero-trust.md](09-zero-trust.md)) — the device with my real bootloader is
  not the victim's enrolled device. A new (userId, deviceId) binding triggers OOB confirmation.
- Risk engine: a "previously dormant user, new device, immediate transfer" pattern is a strong
  signal.

### Bypass 5 — Patch the APK to skip the check entirely

I decompile the APK ([12-decompiling.md](12-decompiling.md)), find the integrity-check call,
no-op it, recompile, resign with my own cert, install.

**Counter:**
- Play Integrity will detect the modified APK (`appIntegrity.appRecognitionVerdict !=
  PLAY_RECOGNIZED`) and the cert digest mismatch.
- Server-side actions verify the integrity verdict before high-value ops — patching the client
  doesn't help once the server is the gate.

### Bypass 6 — Race the local check vs the server check

If the app gates UI on a *local* integrity verdict (the stub) and the server isn't actually
consulted, I just need to defeat the local check (Frida hook the result). The server-side
check is the only one that matters.

**Counter:**
- Never gate sensitive operations on client-side integrity alone. The client renders the
  verdict for *user information*; the server enforces it for *authorisation*.

### Bypass 7 — Emulator with `MEETS_VIRTUAL_INTEGRITY`

Modern emulators (Genymotion with Play Services, AOSP emulator with Google APIs) pass virtual
integrity. If the server accepts that for sensitive ops, I get my fuzzing playground.

**Counter:**
- `MEETS_VIRTUAL_INTEGRITY` alone authorises read-only operations and dev workflows. For
  transfers / IBAN reveal, require `MEETS_DEVICE_INTEGRITY` or stronger.
- Emulator usage is fine for QA — flag with a developer-mode banner, telemetry distinguishes
  "internal QA" emulators (known IPs/projects) from external emulator traffic.

### Bypass 8 — KernelSU on Android Studio emulators (Apple Silicon)

KernelSU brings root to AOSP emulators on M1/M2/M3/M4 Macs without Magisk's overhead. The
device looks otherwise stock to userspace.

See [15-emulator-rooting.md](15-emulator-rooting.md) for the full procedure. KernelSU still
fails Play Integrity strong/device verdicts because the bootloader on a KernelSU emulator is
unverified — but a quick userspace root check sees nothing wrong.

**Counter:**
- Same as Bypass 7. The emulator path needs `MEETS_DEVICE_INTEGRITY` for sensitive ops, which
  KernelSU emulators don't meet.

---

## Cross-reference

- **The Google-side attestation** → [05-play-integrity.md](05-play-integrity.md)
- **Where the attestation chain is generated and consumed** → [02-hardware-vault.md](02-hardware-vault.md), [09-zero-trust.md](09-zero-trust.md)
- **Anti-Frida / process-internal detection** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **What KernelSU does on emulators** → [15-emulator-rooting.md](15-emulator-rooting.md)
- **APK patching that bypasses local checks** → [12-decompiling.md](12-decompiling.md)
- **The bypass landscape against Play Integrity itself** → [13-play-integrity-bypass.md](13-play-integrity-bypass.md)

## References

- [Root Detection Is Dead: What Actually Works in Android 2026](https://levelup.gitconnected.com/root-detection-is-dead-what-actually-works-in-android-2026-b7f801e50531)
- [Android Developers — Verifying hardware-backed key pairs with KeyAttestation](https://developer.android.com/privacy-and-security/security-key-attestation)
- [Android KeyAttestation ASN.1 schema](https://developer.android.com/privacy-and-security/security-key-attestation#schema)
- [AOSP — Verified Boot](https://source.android.com/docs/security/features/verifiedboot)
- [Magisk DenyList docs](https://topjohnwu.github.io/Magisk/denylist.html)
