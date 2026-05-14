# 15 — Rooting the Android Studio Emulator with KernelSU (Apple Silicon)

> "A rooted emulator on your laptop is a sparring partner with no plane ticket. Use it to find
> what your app whispers about itself before an attacker does." — *Fortress field notes*

**TL;DR** — On Apple Silicon (M1 / M2 / M3 / M4), the Android Studio AVD emulator runs an ARM64
guest natively and can be rooted via **KernelSU** — a kernel-level su provider that lives in a
patched boot image. Unlike Magisk, KernelSU has minimal userspace footprint and is suited to
emulator workflows where you want a clean Frida / proxy / RASP test bench. This file walks the
setup, the workflow, and what KernelSU does (and **doesn't**) bypass on Fortress's defences.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Have a rooted dev environment to validate every defence layer fires | Skip the friction of a physical rooted phone for fuzzing |
| **Key idea** | Use KernelSU emulators to test the *attacker view* of your own app | Userspace probes are weak; kernel-level patches let me run higher privilege |
| **Worst failure** | Believing emulator success means production is safe | Forgetting that Play Integrity sees the unverified bootloader |

---

## 🛡️ Defender — "I keep a sparring emulator on my laptop"

### Why a rooted emulator beats a rooted phone for development

| Concern | Physical rooted device | Rooted emulator (KernelSU) |
|---|---|---|
| Provisioning time | Buy/unbox/unlock/flash — hours | Boot a fresh AVD — minutes |
| Reproducibility | Each device differs | Same image, same state, every time |
| Snapshot / reset | Factory reset, slow | `avdmanager` snapshot, instant |
| Cost | $$ | Free (emulator + KernelSU image) |
| Plane ticket | The CI device farm is in Texas | Your laptop |
| Faithful to real attack | High | Mid — Play Integrity behaves differently |

For Fortress's local dev: a KernelSU emulator is the **default** way to verify that:
- The cert pinner refuses a mitmproxy-installed CA.
- The biometric `CryptoObject` refuses to sign without a real biometric ceremony.
- The Play Integrity verdict on this emulator drops to `MEETS_VIRTUAL_INTEGRITY` only —
  triggering the read-only fallback in our policy.
- RASP probes fire on Frida attach.

### Quick setup (Apple Silicon)

The community-maintained [KernelSU-Next](https://kernelsu-next.github.io/webpage/) ships
prebuilt boot images for the AOSP emulator kernel.

```bash
# 1. Install the AOSP emulator image — pick a version with arm64-v8a Google APIs.
#    From Android Studio → SDK Manager → SDK Platforms → Show Package Details.
#    Pick e.g. "Android 14.0 ARM 64 v8a Google Play Intel x86_64" (yes, the label is
#    confusing; the ARM v8a image works natively on M-series).

# 2. Create the AVD.
avdmanager create avd -n fortress-rooted -k "system-images;android-34;google_apis;arm64-v8a"

# 3. Boot it once to populate /sdcard etc., then shut it down.
emulator -avd fortress-rooted -no-snapshot

# 4. Download the matching KernelSU boot.img from the KernelSU-Next releases page.
#    Match the kernel version reported by `adb shell uname -a` on the running emulator.

# 5. Reboot to fastboot and flash the patched boot.img.
adb reboot bootloader
fastboot flash boot ksu-boot-<version>.img
fastboot reboot

# 6. Install the KernelSU Manager APK on the emulator.
adb install KernelSU-Manager.apk

# 7. Open the KernelSU Manager — should show "Working" with root status green.
```

The result: a stock-looking AOSP emulator with root available via the KernelSU su shim.

### Validating Fortress on the rooted emulator

```bash
# Start the local backend.
cd backend && npm run dev &

# Tunnel it to a public URL the emulator can reach (the emulator's network is virtualised).
./gradlew fortressTunnel

# Install Fortress and launch.
./gradlew :app:installDebug
adb shell am start -n com.umain.fortress/.MainActivity
```

Expected behaviour:

| Layer | Expected on KernelSU emulator |
|---|---|
| App launch + splash | Loads, integrity probe completes |
| `IntegrityCheck.current()` (real impl) | Would return `Untrusted` if Play Integrity says virtual |
| Login | Succeeds — read-only flows are not gated on Strong |
| Biometric unlock | Requires emulator's "extended controls → fingerprint" to be configured |
| IBAN reveal step-up | Should succeed if device-binding key was enrolled at login |
| Transfer | Should succeed for low-value; gated for high-value when Strong is required |

Run mitmproxy alongside to confirm cert pinning refuses the inserted CA:

```bash
# In another terminal
mitmproxy -p 8889

# Install mitmproxy CA on the emulator via system trust store (KernelSU lets you remount /system).
# Without pinning, requests appear in mitmproxy.
# With pinning (Fortress's default), requests fail at connection setup.
```

### What KernelSU does NOT change

- **Bootloader state is unverified** to Play Integrity. `MEETS_DEVICE_INTEGRITY` and
  `MEETS_STRONG_INTEGRITY` will not be granted to a KernelSU emulator.
- **Hardware attestation** chains are software-emulated and roll up to a root that Google
  knows is virtual.
- **TEE keys** still cannot be exported. The biometric step-up flow's signed bytes are still
  unforgeable, even on a rooted emulator.

In short, KernelSU gets you to "emulator that can do filesystem operations as root, run Frida,
intercept syscalls". It doesn't get you to "emulator that passes Play Integrity Strong". The
hard floor still holds.

### When this is the right tool

- **Verifying defence-in-depth.** "Does the SecurityChip turn red when integrity says virtual?
  Does the transfer flow refuse?"
- **Running Frida attaches without a USB cable.** Great for ad-hoc probing.
- **Reproducing customer-reported issues** that only happen on rooted devices.
- **CI integration testing** — though for CI the [`fortressTunnel`](../scripts/start-local-tunnel.sh)
  + Genymotion-style hosted emulators are usually simpler than a KernelSU-patched AOSP.

### When this is the wrong tool

- **Confirming production-ready security.** A real Pixel with bootloader locked, Strong
  integrity, and a hardware secure element is the canonical test environment. The emulator can
  *trigger* defences; it can't *prove* they're correctly tied to hardware on real devices.
- **Penetration test scope.** External pentest engagements should use physical devices for
  fidelity.

---

## ⚔️ Attacker — "I use the same tool the defender does, just differently"

### Bypass 1 — Use KernelSU for free Frida + filesystem access

I install KernelSU on a real device (not an emulator), then attach Frida and run my probes.
The userspace footprint of KernelSU is smaller than Magisk's, so naive `which su` style root
detection might miss it.

**Defender counter:**
- Play Integrity sees an unlocked bootloader regardless of whether KernelSU is "hidden" in
  userspace. Strong integrity refuses.
- Modern root detection focuses on hardware attestation, not `su` binary scans (see
  [11-root-detection.md](11-root-detection.md)).

### Bypass 2 — Combine KernelSU with PIF / Zygisk fingerprint spoofing

KernelSU + a Zygisk-compatible fork + Play Integrity Fix module can sometimes get Device
verdict on a properly-locked-but-modified ROM. See [13-play-integrity-bypass.md](13-play-integrity-bypass.md).

**Defender counter:**
- Require Strong for sensitive ops. KernelSU + PIF cannot reliably forge Strong.

### Bypass 3 — Patch out the emulator-detection RASP probe

If the app uses `Build.HARDWARE == "ranchu"` as a fast-path emulator check, I edit
`/system/build.prop` (KernelSU lets me remount /system rw) to lie about hardware strings, then
run the app.

**Defender counter:**
- See [14-rasp-strategies.md](14-rasp-strategies.md). Layer probes; pair with Play Integrity
  `MEETS_VIRTUAL_INTEGRITY`, which the emulator's hardware abstraction layer reveals.

### Bypass 4 — Strace your own app for instrumentation hints

```bash
adb shell su -c "strace -p $(pidof com.umain.fortress) -e openat 2>&1" \
  | grep -iE "/proc/self/maps|/data/local/tmp"
```

I see exactly which paths your RASP probes read, so I know which paths to spoof.

**Defender counter:**
- This is the attacker's reconnaissance phase. Strace is observable from inside the process
  too (`ptrace`-attached → `TracerPid > 0`). The Debugger / Frida probes catch it.

### Bypass 5 — Long-running snapshot of an attested moment

Start the emulator clean → enrol device-binding → take an AVD snapshot. Then for each test
run, restore the snapshot — every run starts with a valid (but stale) enrolment.

**Defender counter:**
- Refresh tokens rotate; a stale snapshot's refresh token is one-use and burns on first
  refresh. The next refresh from the snapshot fails reuse-detection.
- Device-binding-key challenges are nonce-bound; replaying a snapshot's signature against a
  new nonce fails the signature check.

### Bypass 6 — Patch Frida's `/proc/self/maps` view

KernelSU + a kernel module can intercept the open(2) on `/proc/self/maps` and feed Frida's
caller a redacted view. RASP's userspace probe sees a clean map.

**Defender counter:**
- Native-code probes (NDK) that bypass the libc-level interception.
- Don't gate sensitive ops on RASP-only verdicts; require Play Integrity Strong from the TEE,
  which the kernel-level patch can't fake.

---

## Cross-reference

- **What KernelSU *can't* defeat: hardware integrity** → [05-play-integrity.md](05-play-integrity.md), [11-root-detection.md](11-root-detection.md)
- **In-process probes the rooted emulator exercises** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **Bypass module landscape** → [13-play-integrity-bypass.md](13-play-integrity-bypass.md)
- **The local tunnel that pairs with rooted-emulator testing** → [`scripts/start-local-tunnel.sh`](../scripts/start-local-tunnel.sh), [README#local-dev](../README.md)

## References

- [Rooting Android Studio Emulator with KernelSU (Apple Silicon)](https://mjais0508.medium.com/rooting-android-studio-emulator-with-kernelsu-apple-silicon-m1-m2-m3-m4-enable-root-on-google-c1b7d8417bea)
- [KernelSU-Next — Documentation](https://kernelsu-next.github.io/webpage/)
- [KernelSU upstream](https://github.com/tiann/KernelSU)
- [Android Developers — Create and manage virtual devices](https://developer.android.com/studio/run/managing-avds)
- [mitmproxy](https://mitmproxy.org/)
