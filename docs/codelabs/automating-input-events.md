---
title: "Automating input events on Android"
slug: automating-input-events
level: intermediate
estimated_minutes: 20
status: published
company: Fortress
tags:
  - tooling
  - input
  - automation
  - testing
  - uiautomator
summary: >
  Drive an Android device end-to-end from the shell — `adb input`, `sendevent`,
  `monkey`, UI Automator — including the security gotchas of injecting touches against
  your own app under test and against system surfaces.
references:
  - title: "Automating Input Events (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/automating-input-events-on-android-a-comprehensive-guide-c2a1927217ce
  - title: "UI Automator — official docs"
    url: https://developer.android.com/training/testing/other-components/ui-automator
  - title: "adb shell input command — reference"
    url: https://developer.android.com/tools/adb#input
---

## Welcome to scripted input

Understand the three layers of input injection on Android — `adb input`, `sendevent`,
UI Automator — and when to reach for each.

Manual QA does not scale beyond about 20 tests per release. Anything more needs scripted
input. Android exposes four mechanisms that range from "one-line shell" to "full
inspect-and-act test framework". Knowing all four is what separates a flaky test suite
from a fast one.

> **Why this matters.** Most regression suites time-out at the limits of the cheapest
> input mechanism the team knows. Knowing the others raises the ceiling by 10x.

---

## Step 1: `adb shell input` — the cheap and cheerful

For 80 % of scripted gestures, `input` is sufficient.

```bash
# Tap at coords
adb shell input tap 540 1200

# Swipe (x1 y1 x2 y2 [duration_ms])
adb shell input swipe 540 1800 540 600 300

# Type text (escaped for spaces)
adb shell input text "alice%saccount"

# Send a single keycode
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_POWER

# Hold a key (long-press)
adb shell input keyevent --longpress KEYCODE_MENU
```

`input text` URL-encodes spaces as `%s`. Newlines need `input keyevent KEYCODE_ENTER`.

> **Why this matters.** Almost every "I need to repeat this manual gesture 200 times"
> story has `adb shell input` as the right answer.

---

## Step 2: Find the right coordinates with `dumpsys`

`input tap` needs pixel coordinates. `uiautomator dump` plus a tiny parsing step gives
you the screen layout.

```bash
adb shell uiautomator dump /sdcard/dump.xml
adb pull /sdcard/dump.xml ./
adb shell rm /sdcard/dump.xml

# Now inspect locally. The XML contains every visible node with bounds="[x1,y1][x2,y2]".
grep -oE 'text="Transfer"[^/]+bounds="\[[0-9,]+\]\[[0-9,]+\]"' dump.xml
```

For one-off scripts, copy the centre of the bounds into `input tap`. For test suites,
parse `dump.xml` and compute centres programmatically.

> **Why this matters.** Hard-coding screen coordinates makes tests fragile to layout
> changes. Resolving them from `uiautomator dump` keeps the script stable across
> redesigns.

---

## Step 3: `sendevent` — low-level when `input` is not enough

`adb shell input` sends synthesised events at the framework level. Some scenarios
(testing the actual touchscreen driver, simulating multi-touch) need the kernel-level
`/dev/input/eventN` interface.

```bash
# Identify the touchscreen device
adb shell getevent -p
# add /dev/input/event2
#   name:     "ft5x06_ts"
#   ...

# Single tap at (540, 1200) via raw events
adb shell sendevent /dev/input/event2 3 57 1     # touch slot tracking id
adb shell sendevent /dev/input/event2 3 53 540   # ABS_MT_POSITION_X
adb shell sendevent /dev/input/event2 3 54 1200  # ABS_MT_POSITION_Y
adb shell sendevent /dev/input/event2 0 0 0      # SYN_REPORT
adb shell sendevent /dev/input/event2 3 57 -1    # finger lift
adb shell sendevent /dev/input/event2 0 0 0
```

Brutally low-level. Useful for testing input-validation code that sees the raw event
stream — e.g. the
[Android Overlay Attacks codelab](/codelabs/android-overlay-attacks) defences that read
`MotionEvent.flags`.

> **Why this matters.** `input` cannot test obscure multi-touch or rate scenarios.
> `sendevent` can.

---

## Step 4: `monkey` — random stress

The original Android stress tester. Useful as a pre-release smoke; useless as a
deterministic regression tool.

```bash
# 1000 random events biased toward touch
adb shell monkey -p com.umain.fortress \
  --pct-touch 70 --pct-motion 20 --pct-syskeys 0 --pct-anyevent 10 \
  -s 42 -v 1000
```

The `-s 42` is a random seed — same seed produces the same event sequence, making the
"crash on event 412" reproducible.

> **Why this matters.** `monkey` finds the unhandled-input crashes nobody planned for.
> A 2-minute monkey pass before each release catches 80 % of them.

---

## Step 5: UI Automator — when you need to inspect, not just act

UI Automator is the Android-native test framework for cross-app automation. It runs as
a privileged test process; it can see your app, the system UI, the notification shade,
the recents screen.

```kotlin
@RunWith(AndroidJUnit4::class)
class TransferFlowTest {

  private val device by lazy {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  @Test fun transferRequiresBiometric() {
    device.pressHome()
    device.executeShellCommand("am start -n com.umain.fortress/.MainActivity")
    device.wait(Until.hasObject(By.text("Send")), 5_000)
    device.findObject(By.text("Send")).click()
    device.findObject(By.text("Continue")).click()

    // BiometricPrompt is a system surface — UI Automator can dismiss it.
    device.wait(Until.hasObject(By.text("Confirm transfer")), 3_000)
    device.findObject(By.text("Cancel")).click()

    // Back in the app, refusal message should appear.
    assertNotNull(device.findObject(By.textContains("not authorised")))
  }
}
```

The biometric-cancel branch is what makes UI Automator distinctive. Espresso runs
inside your app and cannot interact with `BiometricPrompt`.

> **Why this matters.** Every step-up-flow test requires interacting with system UI.
> UI Automator is the way.

---

## Step 6: `adb shell input` from instrumentation

You can shell out from inside an instrumented test. Useful when UI Automator's API does
not cover the exact thing you need.

```kotlin
fun shell(command: String): String {
  val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
  return proc.inputStream.bufferedReader().readText()
}

@Test fun rotateDeviceMidTransfer() {
  // … reach the review screen …
  shell("settings put system user_rotation 1")  // landscape
  // … assert state is preserved …
  shell("settings put system user_rotation 0")  // portrait
}
```

This works even on releases, useful for screenshot-diff regression flows where
orientation matters.

> **Why this matters.** Drift between "test framework" and "shell" wastes time. Knowing
> both interlock keeps tests in one file.

---

## Step 7: Synthetic SMS for OTP flows

Real SMS is unreliable for CI. The emulator console accepts synthetic SMS, which the
SMS Retriever API on Android picks up just like a real one.

```bash
adb emu sms send +15551234567 "Your Fortress code is 421337"
```

Pair with `adb shell input keyevent KEYCODE_NOTIFICATION` to expand the shade if the
test needs to see the notification UI.

> **Why this matters.** OTP-flow tests on real devices require either a real SIM or a
> mocked SMS retriever. The emulator console removes the need for either.

---

## Step 8: Stress-test concurrent gesture pairs

Some bugs only surface under fast, overlapping input. Two `adb input` calls in
parallel from a script:

```bash
( adb shell input tap 540 1200 & ) ; adb shell input swipe 100 1800 900 1800 200
wait
```

The bash subshell + `wait` gives you the cheapest available "two gestures in close
succession". Not as fast as real multi-touch, but enough to find the obvious
re-entrancy bugs in your UI state machine.

> **Why this matters.** Single-gesture-only scripts find single-gesture bugs. Pairs
> find concurrency bugs.

---

## Step 9: Security gotchas

Scripted input is privileged. Three things to know:

1. **`adb` is not on a real user device.** Tests that pass via `adb input` will not
   reproduce on a user without USB debugging — make sure you are testing the user
   path, not the developer path.
2. **Touches over a `FLAG_SECURE` window** still get through if the source is
   `adb input` (it is a synthesised event from the system, not an overlay). That is
   intended — your tests still work. But it means `FLAG_SECURE` is not enforcement
   against scripted touches.
3. **`BiometricPrompt` does not accept synthetic touches.** The fingerprint sensor is a
   trusted-input device. You cannot script biometric success from `adb`. Use the
   emulator's UI to "press" the fingerprint or wire a Dev Mode toggle that simulates
   it.

> **Why this matters.** Knowing the limits of scripted input prevents test results
> that "pass on the bench, fail in production".

---

## Step 10: A small reusable test-driver kit

Pull the patterns together into a tiny helper your test suite shares.

```kotlin
class TestDriver(private val device: UiDevice) {
  fun openFortress() = device.executeShellCommand("am start -n com.umain.fortress/.MainActivity")
  fun back() = device.pressBack()
  fun home() = device.pressHome()
  fun clear() = device.executeShellCommand("pm clear com.umain.fortress")
  fun grant(perm: String) =
    device.executeShellCommand("pm grant com.umain.fortress $perm")
  fun rotate(orientation: Int) = device.executeShellCommand("settings put system user_rotation $orientation")
  fun pressFingerprint() = device.executeShellCommand("input keyevent --longpress KEYCODE_FINGERPRINT")
}
```

`KEYCODE_FINGERPRINT` is the emulator-only synthetic press; real devices ignore it. On
real devices, your Dev Mode toggle is the equivalent.

> **Why this matters.** Three lines of duplicated `executeShellCommand` across 30 tests
> becomes one helper class. Maintenance budget collapses.

---

## Wrap-Up

You can now drive any Android device from the shell or from an instrumented test —
high-level via `adb input`, low-level via `sendevent`, stress-style via `monkey`,
inspect-and-act via UI Automator — and you know the limits of each.

Next mission:
- [Android CLI Tools](/codelabs/android-cli-tools) — the wider toolkit context.
- [Cuttlefish](/codelabs/cuttlefish) — the CI-grade emulator the scripted-input toolkit
  feeds into.
- [AVDs Beyond the Obvious](/codelabs/avds-beyond-the-obvious) — the AVD-specific
  features that pair with scripted input.

**Recap of the scripted-input ladder:**

- `adb shell input` for 80 % of scripted gestures.
- `uiautomator dump` to resolve stable coordinates from the layout.
- `sendevent` for raw-driver-level multi-touch and rate control.
- `monkey` for pre-release random-stress sweeps.
- UI Automator for cross-app inspection and system-UI interaction.
- Synthetic SMS via the emulator console for OTP flows.
- Three security gotchas — `adb`-only paths, `FLAG_SECURE` semantics, `BiometricPrompt`
  trust boundaries.
- A small reusable `TestDriver` to keep the suite consistent.
