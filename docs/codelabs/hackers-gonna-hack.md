---
title: "Hackers gonna hack — bypass techniques and counters"
slug: hackers-gonna-hack
level: intermediate
estimated_minutes: 25
status: published
company: Fortress
tags:
  - bypass
  - frida
  - magisk
  - rasp
  - playintegrity
summary: >
  A field guide to the most common mobile-security bypasses (Frida hooks, Magisk
  DenyList, Play Integrity Fix, repackaging, debugger attach, SSL kill) and the layered
  defences that raise the attacker's cost from minutes to days.
references:
  - title: "Hackers Gonna Hack (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/hackers-gonna-hack-common-bypass-techniques-and-how-to-fight-back-43eb21e1c8f0
  - title: "Frida — dynamic instrumentation toolkit"
    url: https://frida.re/
  - title: "Magisk — root + module system"
    url: https://github.com/topjohnwu/Magisk
---

## Welcome to the bypass catalogue

Understand the six families of bypass a mobile attacker reaches for, in order of
frequency. Each section names the technique, the tell, and the defensive counter.

If you only defend against the threats you have seen on bug reports, you are defending
against last year's catalogue. The categories below cover almost every published
incident from 2020 to 2026.

> **Why this matters.** Defences cluster. One Frida hook bypasses three of your eight
> controls at once. Knowing the catalogue picks out which controls share a failure mode.

---

## Step 1: Frida hooks — runtime instrumentation

Frida injects a JavaScript-VM into the target process and rewrites Java/Kotlin/native
methods on the fly. Common uses:

- Hook `KeyguardManager.isKeyguardSecure()` to always return true.
- Hook `BiometricManager.canAuthenticate()` to always return SUCCESS.
- Hook `Signature.verify()` to always return true.
- Hook the root-detection helper your app calls and short-circuit it.

```javascript
// frida script — bypass a "isDeviceRooted" check
Java.perform(() => {
  const Detector = Java.use("com.umain.fortress.security.RootDetector");
  Detector.isDeviceRooted.implementation = function () { return false; };
});
```

The tell: the app behaves as if every security check trivially passed. Network traffic
proceeds, biometric prompts succeed without UI, integrity verdicts say Trusted on a
visibly rooted device.

> **Why this matters.** Any check that *only* runs in-app is bypassable in five lines of
> Frida. Critical checks must reach the server.

---

## Step 2: Counter to Frida — multi-layer attestation

Three counters, stacked. The attacker has to bypass all three.

1. **Server-side verdict.** Play Integrity verdict computed at Google's side, not the
   device. Frida cannot hook Google's servers.
2. **Keystore signature.** The device-binding key signs a server-issued challenge inside
   a `CryptoObject`. The Keystore lives in the TEE — Frida runs in userland, cannot
   reach it.
3. **Frida-specific detection.** Scan for frida-server's port (27042), for the
   `frida-agent` mapping in `/proc/self/maps`, for the `gum-js-loop` thread name.

```kotlin
fun likelyFridaPresent(): Boolean {
  val maps = runCatching { java.io.File("/proc/self/maps").readText() }.getOrDefault("")
  if (maps.contains("frida-agent") || maps.contains("gum-js-loop")) return true
  val threadCount = Thread.getAllStackTraces().keys
    .any { it.name == "gmain" || it.name == "gum-js-loop" }
  return threadCount
}
```

Frida-detection is itself bypassable (Frida can hook the detector). The point is to make
the bypass *cumulative* — every layer takes a separate hook.

> **Why this matters.** The goal is not to stop Frida. The goal is to make stopping
> Frida the precondition for everything else.

---

## Step 3: Magisk and DenyList — hidden root

Magisk grants root + ships a "DenyList" feature that hides root from chosen apps.
DenyList unmounts `/system` overlays inside the target process, removes Magisk's
properties from `getprop`, and intercepts common root-tells.

Tell: `Build.TAGS == "release-keys"`, `/system/bin/su` does not exist, but the device
absolutely is rooted. Naïve checks all pass.

Detection counters:

```kotlin
fun magiskClues(): List<String> {
  val clues = mutableListOf<String>()

  // 1. Anomalous mount layout — Magisk usually leaves nodev / overlay traces.
  val mounts = runCatching { java.io.File("/proc/self/mounts").readText() }.getOrDefault("")
  if (mounts.contains("magisk") || mounts.contains("KSU")) clues += "mount.magisk"

  // 2. SU package detector — looks for the Magisk Manager package by signature, not name.
  val pm = context.packageManager
  val known = listOf("com.topjohnwu.magisk", "io.github.huskydg.magisk", "me.weishu.kernelsu")
  if (known.any { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }) clues += "package.magisk"

  // 3. Service Manager — Magisk's binder service is named "magisk".
  val sm = Class.forName("android.os.ServiceManager")
  val list = sm.getMethod("listServices").invoke(null) as Array<String>
  if (list.any { it.contains("magisk") }) clues += "service.magisk"

  return clues
}
```

> **Why this matters.** Magisk on the wild is *common* and *not always hostile* —
> developers use it. The clue list lets your verdict policy distinguish Magisk-with-
> DenyList-hiding-everything (suspicious) from Magisk-doing-nothing (background fact).

---

## Step 4: Play Integrity Fix — re-spoofing the verdict

Magisk modules like Play Integrity Fix and TrickyStore re-spoof the device fingerprint
and the Keystore root certificate so Play Integrity emits a `MEETS_DEVICE_INTEGRITY`
verdict from a rooted device.

Counter: rely on the `MEETS_STRONG_INTEGRITY` verdict where supported, not the basic one.
StrongIntegrity requires hardware-backed key-attestation from a Google-rooted hardware
chain — substantially harder to spoof than the userspace fingerprint check.

```kotlin
fun policyForVerdict(verdict: IntegrityResponse): IntegrityVerdict {
  val labels = verdict.deviceIntegrity.deviceRecognitionVerdict
  return when {
    "MEETS_STRONG_INTEGRITY" in labels -> IntegrityVerdict.Trusted
    "MEETS_DEVICE_INTEGRITY" in labels -> IntegrityVerdict.Limited(listOf("Basic device integrity only"))
    else -> IntegrityVerdict.Untrusted(listOf("Play Integrity failed"))
  }
}
```

> **Why this matters.** The basic verdict is increasingly easy to defeat. The strong
> verdict raises the cost back to hardware-attack territory.

---

## Step 5: APK repackaging — modifying the app you wrote

The attacker decompiles your APK, edits the smali to remove a check, re-signs with their
own key, and reinstalls. Your code now does whatever they want.

Tell: `Build.getSerialNumber()` returns garbage, the install certificate does not match
your signing key, the package signature SHA-256 is wrong.

Counter: check the *signature*, not the package name.

```kotlin
fun signatureFingerprint(context: Context): String? {
  val pm = context.packageManager
  val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
  val sigs = info.signingInfo?.apkContentsSigners ?: return null
  if (sigs.isEmpty()) return null
  val md = java.security.MessageDigest.getInstance("SHA-256")
  return md.digest(sigs[0].toByteArray()).joinToString("") { "%02x".format(it) }
}

const val EXPECTED_SIG = "5b8a3e6f...your real signature..."

fun isTamperedBuild(context: Context): Boolean = signatureFingerprint(context) != EXPECTED_SIG
```

Pair with Play Integrity's `appRecognitionVerdict` — Google verifies the install came
from Play and matches the signing key Google has on file.

> **Why this matters.** A repackaged build is a different app. Your secrets, your auth
> flows, your trust — none of them carry across.

---

## Step 6: SSL kill switches — disabling cert pinning

`SSL Kill Switch 3`, `Justtrustme`, `Frida` hooks on `okhttp3.CertificatePinner.check` all
take the pin verification out of the picture. The Burp / mitmproxy traffic then flows
unimpeded.

Counters:

1. **Network Security Config** (platform-level pin). Cannot be bypassed by hooking
   OkHttp — the OS short-circuits the TLS handshake before OkHttp runs.
2. **Pin verification on a non-default code path.** Verify pins again before sending
   sensitive requests, not just in the interceptor stack.
3. **Server-side fingerprint mismatch detection.** When pin enforcement is bypassed,
   request shape sometimes diverges (TLS fingerprint, header order). Telemetry on that.

> **Why this matters.** Defeating one SSL kill switch defeats all the standard OkHttp
> defences at once. Platform-level pinning closes the gap.

---

## Step 7: Debugger attach

`adb shell setprop` + `ptrace` attach (or `jdwp` for Java-level debugging) on a build
that ships with `android:debuggable="true"` or has flags relaxed by Frida.

Tell: `Debug.isDebuggerConnected()` returns true, or `/proc/self/status` has a non-zero
`TracerPid`. Either is reason to refuse step-up.

```kotlin
fun debuggerAttached(): Boolean {
  if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
  val status = runCatching { java.io.File("/proc/self/status").readText() }.getOrDefault("")
  val tracer = status.lineSequence()
    .firstOrNull { it.startsWith("TracerPid:") }
    ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
  return tracer != 0
}
```

Refuse: do not crash, do not silently degrade. Surface the security message and refuse
to proceed with the action.

> **Why this matters.** A debugger attaches in seconds and dumps memory in minutes. Your
> step-up flow cannot run while one is connected.

---

## Step 8: Emulator and cloud-VM detection

`Cuttlefish`, `Genymotion`, `BlueStacks` — emulators legitimate for development, also
the host of choice for fraud rings that need to run 1000 sessions from one machine.

Tells: ABI is `x86_64` (most real devices are `arm64-v8a`), build fingerprint contains
`generic`/`emu64`/`bluestacks`, sensors (gyroscope, accelerometer) return defaults or
nothing.

```kotlin
fun likelyEmulator(): Boolean {
  val tells = listOf(
    Build.FINGERPRINT.contains("generic"),
    Build.FINGERPRINT.contains("emu64"),
    Build.MODEL.contains("sdk_gphone", ignoreCase = true),
    Build.BRAND.startsWith("generic"),
    Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu"),
    Build.PRODUCT.startsWith("sdk"),
    "x86" in Build.SUPPORTED_ABIS.joinToString(),
  )
  return tells.count { it } >= 2
}
```

Emulator is not always hostile (your QA team runs them). The verdict policy decides what
to do: `Limited` for sandboxed actions, `Trusted` only with additional positive signals
(device-binding key already enrolled, no other risk signals).

> **Why this matters.** Emulator detection is a soft signal. Treating it as hard refuses
> half of your engineering team's debug sessions.

---

## Step 9: Composition — the layered cost model

A bypass on one control is cheap. A bypass on N independent controls is exponentially
costly. The math:

| Layer | Hours to bypass (skilled) |
|---|---|
| One in-app root check | 0.5 |
| Five in-app root checks calling the same helper | 0.5 (one hook covers them all) |
| Five in-app root checks + server-side Play Integrity verdict | 8 |
| Above + Keystore-bound device binding | 24 |
| Above + biometric-bound CryptoObject step-up | 72 |
| Above + network-security-config pinning | 80 |

Numbers are rough, but the slope is the point. Independent layers add hours; redundant
ones do not.

> **Why this matters.** Defence-in-depth only depths if the layers are independent. Five
> versions of the same check is one check.

---

## Step 10: The bypass dev mode

Every defence needs a Dev Mode toggle that simulates a successful bypass, so QA can
verify the recovery path without an actual attacker.

```kotlin
sealed class SimulatedBypass {
  data object Root : SimulatedBypass()
  data object Mitm : SimulatedBypass()
  data object Replay : SimulatedBypass()
  data object IntegrityFail : SimulatedBypass()
  data object DebuggerAttached : SimulatedBypass()
  data object Emulator : SimulatedBypass()
}
```

Wire each into the verdict path with an `if BuildConfig.ALLOW_DEV_MODE && state.flag`.
The release build ignores the flags entirely.

> **Why this matters.** Untested recovery paths fail in production. A Dev Mode toggle
> is the cheapest way to test them.

---

## Wrap-Up

You can now name the six common bypass families, the tells each leaves, the counters
that raise their cost, and how to compose the counters into a layered ladder.

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) — the strongest single
  counter, repeated for emphasis.
- [Android Overlay Attacks](/codelabs/android-overlay-attacks) — the seventh family,
  treated separately because it does not require root.
- [Custom ROMs and Rooted Devices](/codelabs/custom-roms-and-rooted-devices) — the
  landscape these bypasses live in.

**Recap of the catalogue:**

- Frida — runtime instrumentation; defeat with layered attestation.
- Magisk + DenyList — hidden root; defeat with positive clue-collection.
- Play Integrity Fix — verdict spoofing; defeat with `MEETS_STRONG_INTEGRITY`.
- APK repackaging — code substitution; defeat with signature pinning + Play app verdict.
- SSL kill — pinning bypass; defeat with Network Security Config + non-default verify.
- Debugger attach + emulator — process tampering; refuse step-up, telemetry-tag.
