---
title: "Android command-line tools — a guide for the terminally confused"
slug: android-cli-tools
level: beginner
estimated_minutes: 25
status: published
company: Fortress
tags:
  - tooling
  - adb
  - cli
  - sdkmanager
  - bundletool
summary: >
  A working tour of the Android command-line tools every mobile engineer should be
  comfortable with — `adb`, `aapt2`, `apksigner`, `bundletool`, `sdkmanager`,
  `avdmanager` — with copy-pasteable recipes. Take this codelab once and you will reach
  for the terminal forever after.
references:
  - title: "Android Command-Line Tools (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/android-command-line-tools-a-guide-for-the-terminally-confused-d5367df1b3c6
  - title: "adb — official docs"
    url: https://developer.android.com/tools/adb
  - title: "bundletool — official docs"
    url: https://developer.android.com/tools/bundletool
---

## Welcome to the terminal

Understand why every senior Android engineer drops to the shell at least once a day, and
which six tools deserve a permanent spot in your muscle memory.

Android Studio is excellent at the 80 % path. The other 20 % — flashing an APK to a
specific device, signing a build manually, dumping a system service, profiling a
release-mode binary — is faster and more reliable from the terminal. This codelab is the
copy-paste recipe book for that 20 %.

> **Why this matters.** A team that only knows the IDE hits a glass ceiling. The same
> team after a CLI tour fixes bugs in minutes that used to take hours.

---

## Step 1: Find the SDK and put its binaries on PATH

The SDK ships with a set of binaries the IDE hides from you. Put them on PATH and stop
launching Android Studio for shell tasks.

```bash
# macOS / Linux — append to ~/.zshrc or ~/.bashrc
export ANDROID_HOME="$HOME/Library/Android/sdk"   # adjust for your OS
export PATH="$PATH:$ANDROID_HOME/platform-tools"   # adb, fastboot
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin" # sdkmanager, avdmanager
export PATH="$PATH:$ANDROID_HOME/build-tools/34.0.0" # aapt2, apksigner, zipalign
```

```bash
# Sanity check
adb version
aapt2 version
apksigner --version
sdkmanager --version
```

If any of those error, your SDK install is incomplete. Run `sdkmanager --list` to see
what packages are missing.

> **Why this matters.** Most "Android CLI" questions on Stack Overflow are actually
> PATH questions. Fix it once.

---

## Step 2: `adb` — the daily driver

The single tool you will use most. Memorise these eight commands.

```bash
adb devices                              # list attached devices and emulators
adb -s <serial> shell                    # shell into a specific device
adb install -r path/to/app-debug.apk     # install (or reinstall) an APK
adb uninstall com.umain.fortress         # uninstall
adb logcat -s "Fortress:V"               # filtered logcat, V = verbose level
adb shell am start -n com.umain.fortress/.MainActivity   # launch an Activity
adb shell pm clear com.umain.fortress    # clear app data
adb pull /sdcard/Download/file.json ./   # copy file off device
```

If you only learn one tool this week, learn `adb logcat` with filters — it is the
debugger you can run without an IDE.

> **Why this matters.** `adb` is the muscle memory that separates a junior from a senior.
> The recipe table above is 90 % of daily use.

---

## Step 3: Connect to a device over WiFi

USB-C cables fray. WiFi `adb` is stable, especially for long debug sessions.

```bash
# One-time setup, with the device on USB
adb tcpip 5555
adb shell ip route | awk '{print $9}'    # learn the device's IP, e.g. 192.168.1.42
adb disconnect
adb connect 192.168.1.42:5555

# From Android 11+ the official wireless-debug flow uses pairing codes instead:
adb pair 192.168.1.42:37123              # enter pairing code shown in Developer Options
adb connect 192.168.1.42:37125           # then connect for daily use
```

Plug the cable back in only to reset if the device drops off.

> **Why this matters.** Long-running stress tests, all-day debugging, or development on a
> phone in another room — wireless `adb` makes those normal.

---

## Step 4: `aapt2` — inspect an APK

Sometimes you need to know exactly what an APK contains without unzipping it.

```bash
aapt2 dump badging app-release.apk | head -20
# package: name='com.umain.fortress' versionCode='1' versionName='1.0'
# sdkVersion:'33'
# targetSdkVersion:'36'
# application-label:'Fortress'
# uses-permission: name='android.permission.INTERNET'
# ...

aapt2 dump permissions app-release.apk
aapt2 dump xmltree app-release.apk --file AndroidManifest.xml
aapt2 dump resources app-release.apk | grep -i color
```

The XML-tree dump is especially useful for `AndroidManifest.xml` audits — every
`android:exported`, every intent filter, every component is visible.

> **Why this matters.** The first step of any third-party APK review is "what does the
> manifest say?". `aapt2 dump xmltree` is the answer.

---

## Step 5: `apksigner` — verify and re-sign

After every release build, verify the signature is what you expect.

```bash
# Verify signature
apksigner verify --print-certs --verbose app-release.apk

# Sign an unsigned APK with a v2/v3 scheme
apksigner sign \
  --ks ~/keystores/fortress.jks --ks-key-alias fortress \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  app-release-unsigned.apk
```

For Play App Signing the release key never leaves Google; you only sign the *upload*
key locally. `apksigner verify` confirms the final on-Play APK has the keys you expect.

> **Why this matters.** Signature mismatches are the single most common "won't install"
> error in CI pipelines. The verify step takes a second and saves an hour.

---

## Step 6: `bundletool` — work with App Bundles

`.aab` is the upload format; the device installs split APKs derived from it.
`bundletool` is the official converter.

```bash
# Build a device-specific APK set
bundletool build-apks \
  --bundle=app-release.aab \
  --output=app-release.apks \
  --ks=~/keystores/fortress.jks \
  --ks-key-alias=fortress \
  --connected-device

# Install the right slice on the connected device
bundletool install-apks --apks=app-release.apks
```

`bundletool extract-apks` lets you pull a single-device APK for a specific device model
and OS, useful for repro-on-bench bug hunts.

> **Why this matters.** "It works in debug but fails in release on one device" is almost
> always an App Bundle splitting issue. `bundletool` is how you debug it.

---

## Step 7: `sdkmanager` and `avdmanager` — manage emulators from CLI

Skip Android Studio's AVD Manager — the CLI is faster and scriptable.

```bash
# List system images
sdkmanager --list | grep system-images

# Install a system image
sdkmanager "system-images;android-34;google_apis_playstore;arm64-v8a"

# Create an AVD
avdmanager create avd \
  -n FortressTestPixel7 \
  -k "system-images;android-34;google_apis_playstore;arm64-v8a" \
  --device "pixel_7"

# Start it
$ANDROID_HOME/emulator/emulator @FortressTestPixel7 -no-snapshot-load
```

CI pipelines that test on multiple OS versions live and die by this script.

> **Why this matters.** Click-create AVDs do not survive a new laptop; scripted AVDs do.

---

## Step 8: `adb shell` — the inside-the-device toolbox

Once you have an `adb shell`, a smaller toolbox lives inside the device.

```bash
adb shell

# Inside the shell:
pm list packages -3                       # third-party (user-installed) apps
pm dump com.umain.fortress | head -40     # everything the package manager knows
am force-stop com.umain.fortress          # kill the process
am start -n com.umain.fortress/.MainActivity
dumpsys activity activities                # what's on the back stack
dumpsys battery                            # battery state, useful for testing low-power
input keyevent KEYCODE_HOME                # synthetic key press
input tap 540 1200                         # synthetic tap at screen coords
service list                               # all system services
```

`dumpsys` alone has thirty subcommands. `dumpsys window` for window manager, `dumpsys
package` for package state, `dumpsys netstats` for network usage per app, and so on.

> **Why this matters.** Every Android subsystem has a `dumpsys` interface. Knowing the
> shape collapses debugging time.

---

## Step 9: Capture screenshots and screen recordings

A surprisingly common need. Both flow through `adb shell`.

```bash
# Single screenshot
adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png ./
adb shell rm /sdcard/shot.png

# Screen recording, up to 3 minutes
adb shell screenrecord --bit-rate 6M /sdcard/clip.mp4
# Stop with Ctrl-C, then:
adb pull /sdcard/clip.mp4 ./
adb shell rm /sdcard/clip.mp4
```

Combine `screencap` with `input keyevent` to script entire visual regression flows.

> **Why this matters.** Bug reports without screenshots are guesswork. Bug reports with
> a 30-second clip are reproducible.

---

## Step 10: `monkey` — the dumb stress test

The original Android stress tester. Generates random events until your app crashes.

```bash
# Send 500 random events to the Fortress package
adb shell monkey -p com.umain.fortress -v 500

# More targeted: heavy on motion events, light on system keys
adb shell monkey -p com.umain.fortress \
  --pct-touch 60 --pct-motion 30 --pct-syskeys 0 --pct-anyevent 10 \
  -v 2000
```

Two minutes of `monkey` will find any crash that lurks at the unfortunate-input edges.
Run it before every release.

> **Why this matters.** Most app crashes come from input the developer never tested.
> `monkey` is the cheapest way to find them.

---

## Wrap-Up

You now have a working command-line toolkit: PATH set, `adb` reflexes installed, APK
introspection via `aapt2`, signing via `apksigner`, bundles via `bundletool`, emulator
control via `sdkmanager` + `avdmanager`, and `adb shell` for the inside-the-device
inspection.

Next mission: read [Cuttlefish](/codelabs/cuttlefish) for Google's cloud-friendly
emulator (when local AVDs are too constrained), then
[Automating Input Events](/codelabs/automating-input-events) for the scripted-UI side of
`adb input`.

**Recap of what you just installed in muscle memory:**

- PATH augmented with `platform-tools`, `cmdline-tools`, `build-tools`.
- Eight `adb` commands that cover daily use.
- Wireless `adb` setup with the Android 11+ pairing flow.
- `aapt2 dump xmltree` for manifest audits.
- `apksigner verify` after every release build.
- `bundletool build-apks --connected-device` for App Bundle debugging.
- `sdkmanager` + `avdmanager` for scriptable emulators.
- `dumpsys`, `pm`, `am`, `input` as the inside-the-shell tools.
- `monkey` for two-minute pre-release stress checks.
