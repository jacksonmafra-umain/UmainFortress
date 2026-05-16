---
title: "Root detection in 2026 — what actually works"
slug: root-detection-2026
level: advanced
estimated_minutes: 25
status: published
company: Fortress
tags:
  - root
  - magisk
  - kernelsu
  - rasp
  - detection
summary: >
  Why every userspace root-detection check has been defeated by default in 2026, what
  still produces a useful signal, and how to combine the survivors into a verdict the
  rest of the app can trust. Specific notes for Magisk + Zygisk, KernelSU, APatch.
references:
  - title: "Root detection in 2026 (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/11-root-detection.md
  - title: "Magisk module ecosystem — what gets injected"
    url: https://github.com/Magisk-Modules-Repo
  - title: "RootBeer (defunct) — why the classic detector failed"
    url: https://github.com/scottyab/rootbeer
---

## Welcome to the 2026 root-detection reality

Understand which root-detection techniques still produce a useful signal and which were
defeated years ago.

Every blog post titled "Detecting Root on Android" written before 2022 is now actively
harmful — they recommend checks that Magisk + Zygisk modules silence by default. The
2026 landscape is narrower. This codelab maps what still works and how to combine the
working signals into a verdict your app can act on.

> **Why this matters.** Half the team's "root detection" code is theatre. The other
> half is fragile but real. Knowing which is which is what separates ceremony from
> defence.

---

## Step 1: What the classic detectors checked (and why they broke)

The 2018-era checklist:

1. `Build.TAGS == "test-keys"` — broken: Magisk re-spoofs.
2. `/system/bin/su` exists — broken: Magisk DenyList unmounts the overlay inside your
   process.
3. `RootBeer` library — broken: every check it makes is hooked by the
   `MagiskHide-RootBeer` module.
4. `Settings.Secure.ADB_ENABLED == 1` — broken: legitimate developers leave it on; many
   power users do too. Never strong signal.
5. `pm list packages | grep -i superuser` — broken: Magisk renames itself.

If your detector is one of these, it has been bypassed since 2022. Refactor.

> **Why this matters.** Detectors that fire only on the naïve attacker are a noisy
> distraction. The signal-to-noise of the classic checks is now near zero.

---

## Step 2: What still works — positive clues

Three kinds of positive evidence are still useful in 2026:

1. **Filesystem anomalies that hiding modules forget to mask.** Magisk leaves
   `/proc/mounts` entries, kernel module names, magic file paths — modules patch most of
   them but not all.
2. **Behavioural anomalies.** A process whose `/proc/self/maps` lists more libraries
   than a stock app should is suspicious. Frida-agent specifically shows up here.
3. **Cryptographic anomalies.** A device-binding key whose attestation chain does not
   root in the Google Attestation Root CA cannot have been issued by a real Keystore.

The deeper you go, the harder the hiding gets, but none of these is bulletproof on its
own. Combine.

> **Why this matters.** The hiding modules are exhaustive but not infinite. The
> attacker who installs *every* hiding tool is rare; the attacker who installs *one*
> is common.

---

## Step 3: Filesystem anomalies — what to check

A focused list. Each check is one line.

```kotlin
class FilesystemSignals(private val proc: ProcReader = ProcReader.System) {

  fun magiskOverlayMissing(): Boolean {
    val mounts = proc.read("/proc/self/mounts")
    val expected = listOf("/data", "/system", "/vendor", "/product")
    return expected.any { !mounts.contains(it) }
  }

  fun magiskModulesPresent(): Boolean {
    val maps = proc.read("/proc/self/maps")
    return listOf(
      "libmagisk.so", "libzygisk.so", "magisk-bootlog",
      "frida-agent", "frida-gum",
    ).any { maps.contains(it) }
  }

  fun kernelSuMarker(): Boolean {
    val version = proc.read("/proc/version")
    val tellTales = listOf("KSU", "KernelSU", "GKI+")
    return tellTales.any { version.contains(it) }
  }

  fun magiskBinaryPresent(): Boolean {
    val candidates = listOf(
      "/sbin/.magisk", "/sbin/su", "/system/xbin/su",
      "/data/adb/magisk", "/data/adb/ksu", "/data/adb/apatch",
    )
    return candidates.any { runCatching { java.io.File(it).exists() }.getOrDefault(false) }
  }
}
```

Each method is a fingerprint. Modules patch them but rarely all of them at once. The
signal is the *count*, not any single method.

> **Why this matters.** A single fail is interesting. Two fails is suggestive. Three is
> conclusive.

---

## Step 4: Behavioural anomalies — what stock Android does not do

The shape of a stock app process is stable. Deviations indicate something else is
running inside.

```kotlin
class BehaviouralSignals {

  fun unusualThreadNames(): Int {
    val anomalous = setOf("gmain", "gum-js-loop", "linjector", "frida")
    return Thread.getAllStackTraces().keys.count { it.name in anomalous }
  }

  fun anomalousMapEntries(): Int {
    val maps = runCatching { java.io.File("/proc/self/maps").readText() }.getOrDefault("")
    val suspicious = listOf(
      "frida-agent",
      "memfd:",                   // anonymous memfd, sometimes used to inject .so
      "libsubstrate",             // older instrumentation library
      "DexFile",
    )
    return suspicious.count { maps.contains(it) }
  }

  fun tracerPidNonZero(): Boolean {
    val status = runCatching { java.io.File("/proc/self/status").readText() }.getOrDefault("")
    val pid = status.lineSequence()
      .firstOrNull { it.startsWith("TracerPid:") }
      ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
    return pid != 0
  }
}
```

`TracerPid != 0` is a debugger attach. `gum-js-loop` is a Frida thread. `memfd:`
mappings are sometimes used to inject without leaving a path on disk.

> **Why this matters.** Behavioural anomalies catch the run-time tools. Filesystem
> anomalies catch the install-time tools. Both layers add cost to a bypass.

---

## Step 5: Cryptographic anomalies — Keystore key-attestation

The strongest signal. Generate a key with `attestKey: true` and ship the attestation
chain to the server. The server verifies that the chain roots in Google's Attestation
Root CA and reads off the device's *real* state.

```kotlin
fun keystoreAttestationChain(alias: String): List<ByteArray> {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  val chain = ks.getCertificateChain(alias) ?: return emptyList()
  return chain.map { it.encoded }
}
```

```ts
import { X509Certificate, verify } from "node:crypto";

const GOOGLE_ROOT_CERT = readFileSync("google-attestation-root.pem", "utf8");

function verifyChainRootsInGoogle(chain: Buffer[]): boolean {
  const certs = chain.map(b => new X509Certificate(b));
  // Walk: leaf signs intermediate, intermediate signs root. Last cert should match Google.
  const last = certs[certs.length - 1];
  return last.toString() === GOOGLE_ROOT_CERT.trim();
}
```

If the chain does not root in Google, the key was generated by a fake Keystore. That is
a definitive root + spoof signal. No legitimate device produces this.

> **Why this matters.** Magisk can fake the userspace, but it cannot forge a
> Google-signed attestation chain. That is the single strongest signal you have.

---

## Step 6: Verdict policy — combine the signals

Each signal contributes to a score. Thresholds map to verdicts.

```kotlin
data class RootSignals(
  val filesystem: FilesystemSignals,
  val behavioural: BehaviouralSignals,
)

fun rootVerdict(s: RootSignals, attestationOk: Boolean?): IntegrityVerdict {
  var score = 0
  val reasons = mutableListOf<String>()

  if (s.filesystem.magiskOverlayMissing())  { score++; reasons += "fs.mount.missing" }
  if (s.filesystem.magiskModulesPresent())  { score += 2; reasons += "fs.module.present" }
  if (s.filesystem.kernelSuMarker())        { score += 2; reasons += "fs.kernelsu" }
  if (s.filesystem.magiskBinaryPresent())   { score += 2; reasons += "fs.binary" }

  if (s.behavioural.unusualThreadNames() > 0) { score++; reasons += "beh.thread" }
  if (s.behavioural.anomalousMapEntries() > 0){ score++; reasons += "beh.map" }
  if (s.behavioural.tracerPidNonZero())       { score += 2; reasons += "beh.tracer" }

  if (attestationOk == false)               { score += 4; reasons += "att.chain-fail" }

  return when {
    score >= 4 -> IntegrityVerdict.Untrusted(reasons)
    score >= 2 -> IntegrityVerdict.Limited(reasons)
    else       -> IntegrityVerdict.Trusted
  }
}
```

The attestation result is the heaviest signal. Filesystem and behavioural anomalies
add up but are individually weaker.

> **Why this matters.** A weighted score keeps a single false-positive from refusing a
> legitimate user; the same score concentrates evidence across multiple signals into a
> firm refusal.

---

## Step 7: Refuse, do not crash

When the verdict drops to `Untrusted`, the UX is "refuse to perform the action, message
the user, hand off to support" — never crash, never silently degrade.

```kotlin
fun guard(action: Action): Decision {
  return when (val v = currentVerdict()) {
    IntegrityVerdict.Trusted -> Decision.Allow
    is IntegrityVerdict.Limited -> Decision.StepUp
    is IntegrityVerdict.Untrusted -> Decision.RefuseWithReason(
      userMessage = "We can't perform this action on this device. Contact support if you believe this is a mistake.",
      telemetryReasons = v.reasons,
    )
  }
}
```

The user-facing message stays generic; the support-side telemetry carries the specific
reason codes. Helpdesk uses the codes to triage.

> **Why this matters.** Crashing on a rooted device hides the policy from the user. A
> message + a help-link converts the failure into a recoverable conversation.

---

## Step 8: What about hiding modules?

The `MagiskHide`, `Shamiko`, `ZygiskNext` family of modules try to defeat detection by
patching every API your detector calls. Three mitigations:

1. **Read filesystem directly via syscalls when possible.** Java `File.exists()` is
   easily hooked; `open()` via `posix.system()` is harder.
2. **Compare what the device says about itself against what the server-side Play
   Integrity verdict says.** Mismatch is direct evidence of userspace hiding.
3. **Move the most critical check server-side.** Send the raw evidence (attestation
   chain, request hash) and verify on the server where the hiding modules cannot reach.

The third mitigation is the only durable one. Treat userspace detection as a *secondary*
signal, never the only one.

> **Why this matters.** Userspace hiding is an arms race the attacker wins by default
> in the long run. Server-side verification is the arms race the defender wins.

---

## Step 9: Privacy concerns — what your detection collects

Root detection collects host information. The privacy implications:

- `/proc/self/maps` includes library names. Some libraries reveal which other apps the
  user has installed. Limit the reporting.
- `/proc/version` includes the kernel build, which can identify a specific OEM /
  device family. That is okay (you need this signal) but document it.
- The attestation chain itself contains a device-public identifier that, salted, becomes
  a fingerprint. See the
  [Fingerprinting Android Devices codelab](/codelabs/fingerprinting-android-devices).

```ts
// On the server, never store raw library names. Salt + hash.
const moduleFingerprint = crypto.createHmac("sha256", USER_SALT)
  .update(rawLibrariesList.join("|"))
  .digest("hex")
  .slice(0, 16);
```

Document the collection in the "What we collect" Security Center screen, plain language,
no obfuscation.

> **Why this matters.** Root detection that leaks PII is worse than no detection — it
> exchanges security for liability.

---

## Step 10: Test the detection ladder against real adversaries

If you do not test against actual rooted devices with hiding modules installed, you do
not know whether your detection works. Two cheap options:

1. **Buy a second-hand Pixel.** Flash it with Magisk + Shamiko + the latest Play
   Integrity Fix modules. Cost: under €200. Runs in your office.
2. **Cuttlefish + Magisk.** Boot Cuttlefish, root via `adb shell magisk --install-image`,
   add modules. See the [Cuttlefish codelab](/codelabs/cuttlefish).

Run the Fortress demo on the rooted bench. Observe which detections fire, which do not,
which give false positives on stock builds, which give false negatives on rooted
builds. Adjust thresholds. Repeat every six months as the hiding modules evolve.

> **Why this matters.** Detection thresholds drift. Without periodic re-tuning, the
> detector that worked at launch is wrong by the second year.

---

## Wrap-Up

You can now explain why classic root detection broke, build a 2026-relevant detector
that combines filesystem, behavioural, and cryptographic signals, weight them into a
defensible verdict, and refuse cleanly without breaking the user experience.

Next mission:
- [Hackers Gonna Hack](/codelabs/hackers-gonna-hack) — the bypasses your detector
  fights.
- [Custom ROMs and Rooted Devices](/codelabs/custom-roms-and-rooted-devices) — the
  legitimate-user side of the policy.
- [Bulletproof Security](/codelabs/bulletproof-security) — combining root detection
  with attestation and fingerprinting.

**Recap of the detection ladder:**

- Five classic checks that no longer work in 2026.
- Three categories of signal that still do — filesystem, behavioural, cryptographic.
- A focused list of filesystem and behavioural probes.
- Keystore key-attestation chain verification as the strongest single signal.
- A weighted verdict function that turns the bag into Trusted / Limited / Untrusted.
- A refuse-not-crash UX.
- Privacy hygiene on the collected evidence.
- A real-rooted-device test bench refreshed every six months.
