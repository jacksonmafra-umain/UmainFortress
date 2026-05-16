---
title: "Cuttlefish — the Android emulator you didn't know you needed"
slug: cuttlefish
level: intermediate
estimated_minutes: 25
status: published
company: Fortress
tags:
  - tooling
  - cuttlefish
  - emulator
  - testing
summary: >
  Set up Cuttlefish (Google's cloud-friendly Android emulator) for headless CI testing
  of security-critical flows, multi-device fan-out, and side-by-side comparison with the
  standard Android Virtual Device.
references:
  - title: "Cuttlefish (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/cuttlefish-the-android-emulator-you-didnt-know-you-needed-94b86ccc23f3
  - title: "Cuttlefish — official AOSP docs"
    url: https://source.android.com/docs/setup/create/cuttlefish
  - title: "Android Test Lab on Cuttlefish — Google blog"
    url: https://android-developers.googleblog.com/
---

## Welcome to Cuttlefish

Understand what Cuttlefish is, when it beats the standard AVD, and why every fintech CI
pipeline benefits from having both.

The standard Android Virtual Device (AVD) shipped with Android Studio is a Qemu VM with
strong UI tooling and weak headless ergonomics. Cuttlefish is the AOSP-canonical
emulator: KVM-virtualised, headless-first, designed for cloud fleets, supports multi-
display configurations, accurate Vulkan, and the same kernel as a real Pixel. Knowing
both is a small superpower.

> **Why this matters.** A two-minute Cuttlefish run in CI catches the same Build-fingerprint
> bugs that took your QA bench three days to reproduce on Samsung.

---

## Step 1: When AVD wins, when Cuttlefish wins

| Need | AVD | Cuttlefish |
|---|---|---|
| Local development with UI | ✅ | ✗ |
| Headless CI | meh | ✅ |
| Accurate hardware fingerprint | ✗ (`Build.HARDWARE=ranchu`) | ✅ (`Build.HARDWARE=vsoc_arm64`) |
| Multi-display | ✗ | ✅ (up to 8) |
| Vulkan that matches a real Pixel | ✗ | ✅ |
| Cloud fan-out (100 emulators) | painful | designed for this |
| Beginner-friendly | ✅ | ⚠️ |

You usually run both. AVD for local debugging; Cuttlefish for the integration test that
must pass on the hardware fingerprint you care about.

> **Why this matters.** Picking the right emulator per phase makes the test pyramid
> work without the friction of supporting two stacks ad-hoc.

---

## Step 2: Get a Cuttlefish host

Cuttlefish needs a Linux host with KVM. Three reliable options:

- **A nested-virtualisation cloud VM.** Google Cloud `n2-standard-4` with nested VT-x
  enabled. ~€80/month if always-on; on-demand for CI is cheaper.
- **A bare-metal CI runner.** GitHub Actions self-hosted runner on a Hetzner box, AMD
  Ryzen, ~€40/month.
- **Locally on a Linux laptop.** Possible on Intel/AMD machines with KVM; not possible
  on Apple Silicon (no nested KVM).

Apple-Silicon developers run Cuttlefish remotely. That is the whole reason cloud-first
emulator design exists.

> **Why this matters.** "Just run it locally" is the most common stuck-on-Cuttlefish
> path. Provisioning a small cloud host or a CI runner is the unblock.

---

## Step 3: Install Cuttlefish

On Debian / Ubuntu:

```bash
sudo apt update
sudo apt install -y git devscripts equivs config-package-dev debhelper-compat \
                    qemu-system-x86 qemu-utils

git clone https://github.com/google/android-cuttlefish.git
cd android-cuttlefish/base
debuild -i -us -uc -b -d
sudo dpkg -i ../cuttlefish-base_*.deb ../cuttlefish-user_*.deb
sudo reboot
```

After reboot, `sudo usermod -aG kvm,cvdnetwork,render $USER`, log out and back in.
`groups` should show `kvm cvdnetwork render`.

Sanity check: `cvd --help` should print the launcher's options.

> **Why this matters.** Cuttlefish installs are scripted; getting them wrong is the
> single source of the "I cannot start an instance" support thread.

---

## Step 4: Fetch a system image

Cuttlefish runs the actual AOSP system image. Download it from Android's CI server.

```bash
# Pick a recent stable build of an aosp_cf target
mkdir -p ~/cf && cd ~/cf
curl -O https://ci.android.com/builds/submitted/<buildId>/aosp_cf_arm64_phone-userdebug/latest/aosp_cf_arm64_phone-img-<buildId>.zip
curl -O https://ci.android.com/builds/submitted/<buildId>/aosp_cf_arm64_phone-userdebug/latest/cvd-host_package.tar.gz
unzip aosp_cf_arm64_phone-img-*.zip
tar -xzf cvd-host_package.tar.gz
```

ARM hosts pull `aosp_cf_arm64_phone-img-*`; x86 hosts pull `aosp_cf_x86_64_phone-img-*`.
Build IDs roll fast; check `https://ci.android.com/builds/branches/aosp-main` for the
current set.

> **Why this matters.** Cuttlefish-the-emulator is decoupled from the OS image. You can
> run any AOSP version on the same host binary.

---

## Step 5: Launch an instance

```bash
cd ~/cf
HOME=$PWD ./bin/launch_cvd \
  --daemon \
  --display0 width=1080,height=2400,dpi=420 \
  --memory_mb=4096 \
  --cpus=4 \
  --base_instance_num=1
```

ADB connects via `127.0.0.1:6520` for instance 1, `6521` for instance 2, and so on.

```bash
adb connect 127.0.0.1:6520
adb -s 127.0.0.1:6520 shell getprop ro.product.model     # vsoc_arm64
adb -s 127.0.0.1:6520 shell getprop ro.product.brand     # google
adb -s 127.0.0.1:6520 shell getprop ro.hardware          # vsoc_arm64
```

The `ro.hardware = vsoc_arm64` is the Cuttlefish tell — that is the property your
emulator-detection code will react to. Useful for testing your `likelyEmulator()`
function ends up in the right verdict tier.

> **Why this matters.** Cuttlefish lets you test "what happens on an emulator" without
> bringing your fingerprint heuristics down on a real user.

---

## Step 6: Multi-instance fan-out

A single host can run multiple Cuttlefish instances in parallel — typically 4 to 8
depending on RAM.

```bash
for i in 1 2 3 4; do
  HOME=$PWD ./bin/launch_cvd --daemon --base_instance_num=$i \
    --memory_mb=3072 --cpus=2
done

# All four show up on consecutive ports
adb devices
# 127.0.0.1:6520 device
# 127.0.0.1:6521 device
# 127.0.0.1:6522 device
# 127.0.0.1:6523 device
```

Pair with a test runner that targets each device by serial. Five parallel instances cuts
a five-minute serial test pass to one minute.

> **Why this matters.** Real device farms are expensive and slow. Cuttlefish fan-out is
> the budget alternative that scales when your test suite grows past 30 minutes.

---

## Step 7: Headless CI integration

Cuttlefish runs as a daemon; CI captures its stdout via `adb logcat -d` after the test.
A GitHub-Actions-style step:

```yaml
- name: Launch Cuttlefish
  run: |
    cd $HOME/cf
    HOME=$PWD ./bin/launch_cvd --daemon --memory_mb=3072
    adb wait-for-device
    adb shell input keyevent 82                # wake up

- name: Install + run instrumented tests
  run: |
    ./gradlew :app:installDebugAndroidTest :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.size=small

- name: Capture logcat on failure
  if: failure()
  run: adb logcat -d > logcat.txt

- name: Stop Cuttlefish
  if: always()
  run: cd $HOME/cf && HOME=$PWD ./bin/stop_cvd
```

> **Why this matters.** Most "instrumented tests are flaky in CI" stories trace to the
> AVD's headless mode. Cuttlefish is designed for headless from day one.

---

## Step 8: Snapshot + restore

Cuttlefish supports save/restore via `snapshot_util_cvd`. Boot once, snapshot the
post-onboarding state, then every test starts from that snapshot in under five seconds.

```bash
HOME=$PWD ./bin/snapshot_util_cvd --subcmd=snapshot_take \
  --snapshot_path=$HOME/cf/snapshots/post-onboarding

# Later, instead of cold-boot:
HOME=$PWD ./bin/launch_cvd \
  --snapshot_path=$HOME/cf/snapshots/post-onboarding \
  --resume \
  --daemon
```

A 90-second boot becomes a 4-second resume. CI test passes go from 12 minutes to 6.

> **Why this matters.** Cold boot is the slowest step in any emulator pipeline.
> Snapshots remove it.

---

## Step 9: Run Play Integrity against Cuttlefish — and see it fail

Cuttlefish does not carry GMS by default. The Play Integrity verdict will be
`UNEVALUATED` / failed. That is the *correct* behaviour — emulators should not pass
hardware attestation.

For your test harness this means:

- Cuttlefish tests for `Trusted` verdict paths: skip them; they cannot pass on
  Cuttlefish without spoofing.
- Cuttlefish tests for `Limited` / `Untrusted` paths: ideal. The emulator gives you the
  unhappy-path side of the verdict for free.

```kotlin
@Test
fun untrustedDeviceCannotTransfer() {
  // Cuttlefish reports a fingerprint that triggers Limited/Untrusted.
  // The Transfer screen should refuse.
  composeRule.onNodeWithText("Confirm transfer").assertDoesNotExist()
}
```

> **Why this matters.** Half your security test suite is "what if the device fails
> attestation?". Cuttlefish is the world's cheapest device-that-fails-attestation.

---

## Step 10: When not to use Cuttlefish

- **UI animation polish.** AVD or a real device gives more accurate frame timing.
- **Sensor-heavy flows.** Cuttlefish's sensor mocks are limited. Real device or
  Firebase Test Lab.
- **Vendor-specific OEM testing.** Cuttlefish is AOSP; it does not have One UI, MIUI,
  or HMS. Real device or vendor test lab.
- **Bluetooth, NFC, real cellular.** Cuttlefish is virtual; these are stubs.

Cuttlefish is the *integration* layer of your test pyramid. It does not replace devices
on shelf.

> **Why this matters.** The wrong tool for the wrong test produces false confidence.

---

## Wrap-Up

You can now install Cuttlefish, run multiple instances in parallel, wire it into CI,
snapshot the boot state, and use the emulator's known-failing attestation behaviour as
a feature.

Next mission:
- [AVDs Beyond the Obvious](/codelabs/avds-beyond-the-obvious) — when AVD is the right
  call.
- [Automating Input Events](/codelabs/automating-input-events) — the scripted-UI side of
  any emulator-driven test.
- [Hackers Gonna Hack](/codelabs/hackers-gonna-hack) — the attacker's view of how
  emulator detection should land.

**Recap of your Cuttlefish stack:**

- Installed on a Linux host with KVM.
- Tracking AOSP build IDs for the system image, decoupled from the host binary.
- Single-instance launch + multi-instance fan-out for parallel tests.
- Snapshot/restore to cut CI boot time by 90 %.
- Headless integration with GitHub Actions or any other CI.
- Knowing what *not* to test on Cuttlefish: animation timing, OEM skins, real radios.
