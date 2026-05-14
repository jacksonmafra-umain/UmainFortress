# 14 — RASP: Runtime Application Self-Protection

> "Static analysis tells the attacker where to look. RASP tells you when they're looking back."
> — *Fortress field notes*

**TL;DR** — Runtime Application Self-Protection (RASP) is the layer of **in-process checks**
that fire while your app runs and signal when its own runtime has been compromised — Frida
attached, debugger attached, hooking framework loaded, emulator detected. RASP cannot prevent
a sophisticated attacker once they have code in the process; it can **alert** the server, force
the session into a tighter mode, and refuse the most sensitive actions. This file walks the
probe palette, how to combine them, and how the attacker disarms each one.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Trip a tripwire when the process is hosted by a hostile runtime | Patch out the tripwires so my hooks run silently |
| **Key idea** | A bag of cheap probes, sampled, with the verdict sent to the server | Whatever check the app does, I can disable from inside the process |
| **Worst failure** | Trusting a single in-process boolean to gate sensitive actions | Naive checks that fail open on exception (catch-all → "all good") |

---

## 🛡️ Defender — "I plant tripwires, I report tripped wires"

### The probe palette

```kotlin
data class RaspProbes(
    val frida: FridaProbe.Result,
    val debugger: DebuggerProbe.Result,
    val hooking: HookingProbe.Result,
    val emulator: EmulatorProbe.Result,
    val codeIntegrity: CodeIntegrityProbe.Result,
)
```

Each probe returns `Clean | Suspect(reasons: List<String>) | Tripped(reasons)`. The collector
aggregates and sends to the server via a `POST /me/rasp/snapshot`. The **server** decides what
to do — never the client.

### Frida detection

Common Frida indicators visible inside the process:

```kotlin
object FridaProbe {
    fun run(): Result {
        val reasons = mutableListOf<String>()

        // 1. Process maps — Frida injects libfrida-agent.so or frida-gadget
        runCatching {
            File("/proc/self/maps").readLines().forEach { line ->
                if (line.contains("frida") || line.contains("gum-js-loop")) {
                    reasons += "Process map contains Frida agent"
                    return@forEach
                }
            }
        }

        // 2. Named pipes — Frida server creates pipes under /data/local/tmp
        runCatching {
            File("/data/local/tmp").listFiles()?.forEach { f ->
                if (f.name.startsWith("frida-")) reasons += "Frida named pipe at ${f.name}"
            }
        }

        // 3. TCP port 27042 (default frida-server) — open ports listing
        runCatching {
            File("/proc/net/tcp").readLines().forEach { line ->
                // local_address format: "0100007F:69A2" → 127.0.0.1:27042 in hex
                if (line.contains(":69A2")) reasons += "Default Frida port 27042 open"
            }
        }

        // 4. /proc/self/status TracerPid — Frida and gdb both ptrace
        runCatching {
            File("/proc/self/status").readLines().firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(":")?.trim()?.toIntOrNull()?.let {
                    if (it != 0) reasons += "TracerPid=$it (ptrace attached)"
                }
        }

        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

Each individual probe is bypassable — but stacking three or four raises the cost of bypassing
to "Frida-with-anti-detection script that targets your specific probes", which means you've
moved the attacker out of the "free script kit" tier.

### Debugger detection

```kotlin
object DebuggerProbe {
    fun run(context: Context): Result {
        val reasons = mutableListOf<String>()
        if (Debug.isDebuggerConnected()) reasons += "JDWP debugger connected"
        if (Debug.waitingForDebugger()) reasons += "Waiting for debugger"
        // ApplicationInfo.FLAG_DEBUGGABLE should be off in release.
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            reasons += "App is debuggable (release build should not be)"
        }
        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

### Hooking framework detection

Xposed, LSPosed, Riru, EdXposed leave artefacts:

```kotlin
val hookingPaths = listOf(
    "/system/framework/XposedBridge.jar",
    "/system/lib/libxposed_art.so",
    "/system/lib64/libxposed_art.so",
    "/system/etc/init.d/00xposed",
)
val knownHookingPackages = setOf(
    "de.robv.android.xposed.installer",
    "io.github.lsposed.manager",
    "io.va.exposed",
)
```

Detect either via filesystem presence or `PackageManager.getInstalledPackages` (with the caveat
that Magisk DenyList hides packages — see [11-root-detection.md](11-root-detection.md)).

### Emulator detection

Real device features that emulators frequently miss:

```kotlin
object EmulatorProbe {
    fun run(context: Context): Result {
        val reasons = mutableListOf<String>()
        if (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("sdk_gphone")) {
            reasons += "Build fingerprint looks emulator-like"
        }
        if (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")) {
            reasons += "Emulator hardware string"
        }
        // Modern emulators with Play Services may have a fake-but-plausible sensor list,
        // so absent-sensor checks are unreliable. Build properties are more honest.
        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

Be careful here: development emulators ship with full Play Services and pass many of these
naive checks. Emulator detection is most useful when **combined** with the Play Integrity
`MEETS_VIRTUAL_INTEGRITY` verdict — when both fire, you're confident.

### Code integrity

Detect tampering with the APK itself:

```kotlin
object CodeIntegrityProbe {
    fun run(context: Context): Result {
        val signers = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
        }.getOrNull() ?: return Result.Tripped(listOf("Cannot read signing info"))

        val installer = context.packageManager.getInstallSourceInfo(context.packageName)
            .installingPackageName
        val reasons = mutableListOf<String>()
        if (installer != "com.android.vending") reasons += "Installer is $installer (not Play)"
        val sha = signers.first().toByteArray().sha256().toHex()
        if (sha != EXPECTED_RELEASE_SIGNER_SHA256) reasons += "Signer fingerprint mismatch"
        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

### Composing the verdict

Run probes on a low-frequency cadence (app launch + every 5 minutes + on every sensitive
operation). Aggregate to a `RaspVerdict`:

```kotlin
sealed class RaspVerdict {
    data object Clean : RaspVerdict()
    data class Suspect(val reasons: List<String>) : RaspVerdict()    // continue, but tighten
    data class Tripped(val reasons: List<String>) : RaspVerdict()    // refuse sensitive ops
}
```

Wire into [`IntegrityCheck`](../app/src/main/kotlin/com/umain/fortress/security/IntegrityCheck.kt)
alongside Play Integrity and the device-binding signal, so the existing `SecurityChip` and risk
engine consume RASP transparently. The Fortress demo's `DevModeStore` simulates the same
verdict-shape externally (see [docs/13-play-integrity-bypass.md](13-play-integrity-bypass.md))
so the rest of the app can be exercised without a real Frida attach.

### What to do when RASP trips

- **Suspect**: send telemetry to the server with the reasons. Don't tell the user yet — keep
  the attacker uncertain about what tripped them.
- **Tripped**: refuse sensitive operations (transfers, reveals), force step-up biometric on
  read-only ops. Show a generic "We couldn't verify this device — try again later" — not the
  reason. **Don't print which probe fired** — that hands the attacker the next thing to patch.
- **Persistently Tripped** (3+ events in a session): server invalidates the session, forces
  re-login, marks the device-binding for review.

### What RASP cannot do

- **Stop** a determined attacker. If they have code in your process, they can disable your
  probes. RASP is a tripwire, not a wall.
- **Replace** server-side enforcement. Sensitive operations *must* require server-attested
  proof of the device's integrity (Play Integrity standard token bound to the request). RASP
  is auxiliary.
- **Run forever**. Probe cadence should be sampled, not continuous — running on every Compose
  recomposition is a battery sink and a noisy telemetry stream.

---

## ⚔️ Attacker — "I disarm your tripwires while staying in your process"

### Bypass 1 — Hook the probe itself

The most direct: my Frida script `Java.use("com.umain.fortress.security.FridaProbe").run.implementation = function() { return Result.Clean }`. The probe returns Clean regardless of reality.

**Defender counter:**
- Cross-check probe output across multiple call sites. If the static-call site reports Clean
  but a dynamic-reflection call to the same probe reports Tripped, the static path was hooked.
- Compute probe results from server-side hints too (Play Integrity says virtual, RASP says
  clean → contradiction).
- Sign the probe result, send to server, server decides. The hooked probe still leaves Play
  Integrity / device-binding-signature inconsistencies the server can spot.

### Bypass 2 — Patch the APK to no-op the probes

Decompile → smali edit → resign. The probe never runs.

**Defender counter:**
- Code integrity probe catches the resign (signing cert mismatch).
- Play Integrity's `appIntegrity.appRecognitionVerdict` rejects unrecognised binaries.
- Server-side: refuse any session from a binary whose cert digest doesn't match the registered
  release cert.

### Bypass 3 — Frida stalker / anti-anti-frida

Anti-detection Frida scripts that proactively spoof `/proc/self/maps`, hook the `File.readLines`
call site, or NX the probe code. The arms race is real and the toolchain is mature.

**Defender counter:**
- Multiple probes — anti-detection scripts usually target *known* probe signatures. Custom
  inline probes (not from open-source RASP libraries) buy you signature variance.
- Native probes: implement the readline-and-grep in C++, where Frida hooking is harder (still
  possible, but more friction).
- Frequency randomisation: don't probe at exactly the same offsets each session.

### Bypass 4 — Targeted hooking framework that hides itself

LSPosed in module mode can selectively hook the target without leaving usual filesystem
artefacts. If your RASP only checks `/system/framework/XposedBridge.jar`, you don't see LSPosed.

**Defender counter:**
- Multiple indicators: process memory map + class loading order + reflection on ClassLoader
  parent chain (Xposed mangles the parent ClassLoader).
- Combine with Play Integrity; Xposed-modded ROMs typically fail Strong.

### Bypass 5 — Emulator with hardware-faked properties

Modern Android Studio emulators (and Genymotion) ship with `Build.HARDWARE = ranchu` but you
can edit the emulator's config to lie about hardware strings. Suddenly my emulator looks like a
Pixel.

**Defender counter:**
- Play Integrity `MEETS_VIRTUAL_INTEGRITY` is the canonical answer — Google's verdict service
  knows the emulator's secure element shape, regardless of userspace lies.
- Combine probe outputs: emulator + missing accelerometer + no telephony stack is still a
  strong signal even if `Build.HARDWARE` is fake.

### Bypass 6 — Trigger RASP fail-open path

If your probe code has a top-level `try/catch (Throwable) { return Result.Clean }`, all I need
is to throw inside the probe and your defender sees "clean".

**Defender counter:**
- Probe failures default to **Suspect**, not Clean. An exception in a security check is itself
  a signal.
- Telemetry on probe exception types and frequencies — a spike of `SecurityException` from
  `File.readLines` is a fingerprint of a hooking attempt.

### Bypass 7 — Disable RASP at the dependency-injection level

If RASP is a Koin/Hilt-bound class, my Frida hook intercepts the DI container and replaces the
RASP instance with a stub that always returns Clean.

**Defender counter:**
- The RASP result that reaches the server should be signed with the device-binding key (TEE).
  My stub can return Clean, but my forged signature won't verify server-side.
- Server requires fresh RASP attestation on every sensitive action.

### Bypass 8 — Run my exploit in a child process

Some RASP only checks the main process. If I can fork or spawn a sandboxed Frida instance with
its own pid namespace, the main process's `/proc/self/maps` looks clean.

**Defender counter:**
- This is harder for the attacker than it sounds on Android (no plain fork from app context),
  but worth defending: probe the **whole UID** when listing processes (`pm list packages` to
  enumerate apps under your UID, `ps` to check unexpected children).

---

## Cross-reference

- **The Play Integrity signal that complements RASP** → [05-play-integrity.md](05-play-integrity.md), [13-play-integrity-bypass.md](13-play-integrity-bypass.md)
- **Why root detection sits in the same boat** → [11-root-detection.md](11-root-detection.md)
- **What the attacker has to do to even get in process** → [12-decompiling.md](12-decompiling.md)
- **The TEE-bound signature RASP results should be wrapped in** → [09-zero-trust.md](09-zero-trust.md)
- **Dev Mode simulating these RASP verdicts** → [`DevModeScreen`](../app/src/main/kotlin/com/umain/fortress/ui/screens/devmode/DevModeScreen.kt)

## References

- [Exploring Android Protections — RASP Times](https://medium.com/@justmobilesec/exploring-android-protections-on-rasp-times-3d140e8df115)
- [Frida — Dynamic instrumentation toolkit](https://frida.re/)
- [LSPosed — A modern Xposed framework](https://github.com/LSPosed/LSPosed)
- [Android Developers — Debug.isDebuggerConnected](https://developer.android.com/reference/android/os/Debug#isDebuggerConnected())
- [OWASP MASVS — MSTG-RESILIENCE-1..13](https://mas.owasp.org/MASVS/)
