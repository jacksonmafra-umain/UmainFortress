---
title: "AVDs beyond the obvious"
slug: avds-beyond-the-obvious
level: intermediate
estimated_minutes: 20
status: published
company: Fortress
tags:
  - tooling
  - avd
  - emulator
  - testing
summary: >
  Power-user moves with Android Virtual Devices — snapshots, custom system images,
  sensor injection, Telnet console, hardware profile editing — that let an engineer
  test scenarios most teams cannot reproduce.
references:
  - title: "Exploring AVDs beyond emulators (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/exploring-android-virtual-devices-avds-more-than-just-emulators-d93a0450ce53
  - title: "Android emulator — command-line options"
    url: https://developer.android.com/studio/run/emulator-commandline
  - title: "Android emulator console (Telnet) reference"
    url: https://developer.android.com/studio/run/emulator-console
---

## Welcome to the AVD power tour

Understand the AVD past "click Run". Snapshots, sensor injection, scripted location,
custom hardware profiles, and the Telnet console — together they make AVD the right
tool for 80 % of mobile QA scenarios.

The standard AVD interaction is "click play in Android Studio, wait, watch app boot".
That ergonomic is fine for happy-path development. The features below are why senior
engineers still reach for AVD over a real device in many specific cases.

> **Why this matters.** A team that knows AVD's full feature set debugs scenarios in
> minutes that other teams treat as "buy a second device".

---

## Step 1: Boot from cold, snapshot post-onboarding

The first 30 seconds of an AVD are the slowest. Snapshot the moment your app reaches
its post-onboarding home screen and every subsequent boot becomes a one-second resume.

```bash
# Launch an AVD with snapshot saving on quit
emulator -avd Pixel7_API_34 -no-boot-anim -gpu host -no-audio

# In Android Studio Device Manager → ⋮ → "Wipe data" / "Take snapshot"
# CLI alternative:
adb -s emulator-5554 emu avd snapshot save post-onboarding
```

To resume from a named snapshot:

```bash
emulator -avd Pixel7_API_34 -snapshot post-onboarding
```

> **Why this matters.** Most "test the second screen of the app" workflows cold-boot
> the emulator every time. Snapshots cut a 90-second cycle to 5.

---

## Step 2: The Telnet console

Every running AVD exposes a Telnet console on port 5554 (or the port shown in its
title bar). The auth token is in `~/.emulator_console_auth_token`.

```bash
nc localhost 5554
Android Console: type 'help' for a list of commands
OK
auth $(cat ~/.emulator_console_auth_token)
OK

# Useful commands:
geo fix -122.084 37.422              # set GPS location
gsm signal-profile 4                  # full bars
gsm signal-profile 0                  # no signal — test offline UX
network speed lte                     # simulate LTE bandwidth
power capacity 12                     # 12% battery
power ac off                          # unplug
sms send +1234567890 "OTP: 421337"    # synthetic SMS for OTP flows
quit
```

`telnet localhost 5554` works too if you have it. `nc` is the modern minimum.

> **Why this matters.** Every "what does the app do when the network drops mid-transfer"
> question becomes a five-second console command instead of a real-device choreography.

---

## Step 3: Sensor injection from the console

Beyond network and power, the emulator console accepts sensor readings.

```bash
# In an active console session:
sensor set acceleration 0:9.81:0     # device flat, facing up
sensor set acceleration 0:0:9.81     # device flat, facing forward
sensor set magnetic-field 25:0:-15   # synthetic compass
sensor set orientation 90:0:0        # 90° rotation around the z-axis

# Read what's currently set
sensor get acceleration
```

For step-counter and pedometer flows, the AVD Extended Controls (the `…` in the
emulator's side panel) has a visual "virtual sensors" tool — same primitives,
clickable UI.

> **Why this matters.** Apps that gate flows on motion (fraud-check while-walking-only,
> step-counter loyalty) cannot test without sensor injection. The console is how.

---

## Step 4: Define a custom hardware profile

The default Pixel profiles are good. Custom profiles let you simulate the long tail —
budget-tier devices with low RAM, small screens, no fingerprint sensor.

`~/.android/avd/<NAME>.avd/config.ini` is the editable file. Useful fields:

```ini
hw.lcd.density=160                     # downscale to a tiny-density device
hw.ramSize=2048                        # simulate 2 GB device
hw.cpu.ncore=2                         # dual-core
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect       # software GPU — slow but predictable
hw.sensors.proximity=no                # no proximity sensor
hw.keyboard=no                         # virtual keyboard only
hw.dPad=no
hw.battery=yes
```

Reboot the AVD after editing. The new constraints apply on next launch.

> **Why this matters.** Code that runs fine on Pixel 8 sometimes crashes on a 2 GB
> Moto E. Profiles let you find out without buying the Moto E.

---

## Step 5: Custom system images

`sdkmanager` ships a catalogue. Useful filters:

- `system-images;android-34;default;arm64-v8a` — bare AOSP, no Play.
- `system-images;android-34;google_apis;arm64-v8a` — has Play Services but no Play Store.
- `system-images;android-34;google_apis_playstore;arm64-v8a` — has Play Store. Use for
  most fintech testing.
- `system-images;android-34;android-tv;arm64-v8a` — Android TV.
- `system-images;android-34;android-automotive-playstore;arm64-v8a` — Android Auto.

For Play Integrity testing, the `google_apis_playstore` images are mandatory. The
`google_apis` images have Play Services but not the Play *Store*, which means Play
Integrity's app verdict is `UNEVALUATED`.

```bash
sdkmanager --list | grep system-images | head -30
sdkmanager "system-images;android-34;google_apis_playstore;arm64-v8a"
```

> **Why this matters.** Wrong system image = wrong test environment. Pick the image
> that matches the scenario you mean to exercise.

---

## Step 6: Headless mode

`emulator -no-window` runs without UI. Pair with `-no-audio -no-boot-anim` for a faster
CI boot. ADB still connects normally; everything below the UI works.

```bash
emulator -avd Pixel7_API_34 \
  -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect \
  -no-snapshot-load \
  &
adb wait-for-device
```

For accurate animation timing testing, leave the window on. For functional tests, kill
the window.

> **Why this matters.** Headless AVD on CI is usable but never as good as Cuttlefish.
> If your CI test suite gets above 30 minutes, see the
> [Cuttlefish codelab](/codelabs/cuttlefish).

---

## Step 7: Multiple AVDs in parallel

ADB serials are derived from console ports. AVD #1 is `emulator-5554`, #2 is
`emulator-5556`, and so on.

```bash
emulator -avd Pixel7_API_34 -port 5554 &
emulator -avd Pixel7_API_30 -port 5556 &
emulator -avd Pixel7_API_27 -port 5558 &

adb devices
# emulator-5554 device
# emulator-5556 device
# emulator-5558 device

adb -s emulator-5554 install app-debug.apk
adb -s emulator-5556 install app-debug.apk
adb -s emulator-5558 install app-debug.apk
```

Three concurrent AVDs covers the API 27 / 30 / 34 grid most teams care about. Memory
budget: ~3 GB each.

> **Why this matters.** Most "this crashes on API 28 only" bugs surface only when you
> run all the AVDs side by side.

---

## Step 8: Force a specific GPS path via console + KML

Beyond a one-shot `geo fix`, you can replay a KML route file:

```bash
# Convert a GPX from your run-tracking app to KML
gpsbabel -i gpx -f route.gpx -o kml -F route.kml

# In the emulator console:
geo nmea $(cat route.nmea)            # raw NMEA, or
# Use Android Studio's Extended Controls → Location → "Routes" → import KML
```

Useful for testing flows gated on motion ("transfer rejected because device has not
moved in 5 minutes" — a real fraud signal).

> **Why this matters.** Anything geofenced or movement-gated is impossible to test
> rigorously without scripted location.

---

## Step 9: Crash recovery and corruption fixes

AVDs sometimes won't boot — half a Linux distribution lives in the snapshot file. Three
recipes:

```bash
# 1. Wipe + cold-boot
emulator -avd Pixel7_API_34 -wipe-data -no-snapshot-load

# 2. Delete the AVD's data and re-create the profile
rm -rf ~/.android/avd/Pixel7_API_34.avd/userdata*
rm -rf ~/.android/avd/Pixel7_API_34.avd/snapshots

# 3. Nuke the whole AVD and re-create from CLI
avdmanager delete avd -n Pixel7_API_34
avdmanager create avd -n Pixel7_API_34 \
  -k "system-images;android-34;google_apis_playstore;arm64-v8a" \
  --device "pixel_7"
```

Apple Silicon-specific: if your AVD boots to a black screen, the host GPU mode might
disagree. Force `-gpu swiftshader_indirect` and confirm the GPU layer is the issue.

> **Why this matters.** A broken AVD eats hours. The recovery recipes are the
> difference between "back in business" and "wait for IT".

---

## Step 10: AVD vs Cuttlefish — pick the right one

| Scenario | Use |
|---|---|
| Local dev, you want to see the screen | AVD |
| CI integration tests | Cuttlefish, fallback to AVD headless |
| Custom hardware profile (low-RAM device) | AVD |
| Multi-display, accurate Pixel hardware fingerprint | Cuttlefish |
| Sensor injection from console | AVD (richer console support) |
| Snapshot-fast resume | Both — equivalent |
| Apple Silicon laptop | AVD locally, Cuttlefish remote |
| Battery / network throttling | AVD console wins |

Most teams want *both* on the bench. AVD for everyday, Cuttlefish for CI scale-out.

> **Why this matters.** Picking the right emulator per phase removes the friction that
> makes the test pyramid wobble.

---

## Wrap-Up

You can now use AVD to its full depth — Telnet console for network/GPS/sensors, custom
hardware profiles for low-tier device simulation, snapshots for fast cycles, multiple
parallel AVDs for API-grid coverage, and a clean recovery recipe when things break.

Next mission:
- [Cuttlefish](/codelabs/cuttlefish) — the heavy-duty CI complement.
- [Automating Input Events](/codelabs/automating-input-events) — script the UI you just
  booted.
- [Android CLI Tools](/codelabs/android-cli-tools) — the broader toolkit.

**Recap of the AVD power kit:**

- Snapshots cut cold-boot time from 90s to 5s.
- Telnet console controls network, power, GPS, SMS, and sensors.
- `config.ini` edits unlock arbitrary hardware profiles.
- System-image choice determines what Play surface you test against.
- Headless mode + serial-keyed `adb -s` enable parallel AVD test runs.
- A short recovery recipe when an AVD won't boot.
- A practical AVD-vs-Cuttlefish decision matrix.
