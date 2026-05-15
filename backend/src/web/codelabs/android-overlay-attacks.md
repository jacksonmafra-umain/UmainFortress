---
title: "Defending against Android overlay attacks"
slug: android-overlay-attacks
level: advanced
estimated_minutes: 30
status: published
company: Fortress
tags:
  - overlay
  - tapjacking
  - rasp
  - biometric
  - ghosttouch
summary: >
  Wire the four-layer overlay-attack defence into a production Android app — filterTouchesWhenObscured,
  SYSTEM_ALERT_WINDOW posture checks, accessibility-service detection, and a step-up biometric
  guard for irreversible actions. Modelled after the GhostTouch educational demo.
references:
  - title: "Android Overlay Attacks — How They Work and How to Stop Them (Jackson Mafra)"
    url: https://medium.com/@jacksonfdam/android-overlay-attacks-how-they-work-and-how-to-stop-them-f3dbea3d215f
  - title: "GhostTouch — hands-on educational demo"
    url: https://github.com/jacksonmafra-umain/GhostTouch
  - title: "View.setFilterTouchesWhenObscured — Android docs"
    url: https://developer.android.com/reference/android/view/View#setFilterTouchesWhenObscured(boolean)
  - title: "MotionEvent.FLAG_WINDOW_IS_OBSCURED"
    url: https://developer.android.com/reference/android/view/MotionEvent#FLAG_WINDOW_IS_OBSCURED
---

## Welcome to overlay attacks

Understand what an overlay attack actually is and which surfaces it targets in a fintech app.

An overlay attack is any technique that draws on top of your UI to trick the user or
intercept their input. Variants include tap-jacking (transparent windows that grab clicks),
clickjacking (legitimate-looking decoys), and full-screen impostor activities. The 2024 wave
of banking-trojan campaigns leans on overlays almost exclusively because they sidestep
runtime permissions entirely — they only need `SYSTEM_ALERT_WINDOW` or, worse, an enabled
accessibility service.

> **Why this matters.** A successful overlay on your biometric step-up screen means the
> attacker can authorise transfers while the user thinks they are confirming a notification
> dismissal.

---

## Step 1: Map the attacker's toolkit

Three categories of overlay primitive. Pick which one your screen is vulnerable to, then
defend against that one first.

1. `SYSTEM_ALERT_WINDOW` — the legacy "Draw over other apps" permission. Disappearing in
   Android 16, but still everywhere in the install base.
2. `BIND_ACCESSIBILITY_SERVICE` — accessibility services can read and inject input across
   apps. Banking trojans abuse this. There is no automatic mitigation, only detection.
3. Untrusted touches — even without either permission, any window on top of yours that
   passes through input creates an "obscured" touch the OS will tell you about *if* you
   ask.

> **Why this matters.** The mitigation differs by category. Lumping them together leaves
> gaps.

---

## Step 2: Refuse obscured touches on sensitive controls

`android:filterTouchesWhenObscured="true"` makes the OS drop any input event with
`FLAG_WINDOW_IS_OBSCURED` set. Apply it to every button that authorises a side-effecting
action — Send, Confirm, Reveal PAN, Sign Transaction.

```xml
<!-- Apply at the View level via XML… -->
<Button
  android:id="@+id/transferConfirm"
  android:filterTouchesWhenObscured="true"
  android:text="Confirm transfer" />
```

```kotlin
// …or in Compose via the underlying view modifier.
@Composable
fun ConfirmButton(onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.semantics { contentDescription = "Confirm transfer" },
    // Compose 1.6+ exposes filterTouchesWhenObscured on Modifier via interaction source;
    // until then, wrap the LocalView and call setFilterTouchesWhenObscured(true).
  ) { Text("Confirm transfer") }
}
```

> **Why this matters.** This is the cheapest line of defence and it stops the most
> common tap-jacking variant cold.

---

## Step 3: Detect the obscured flag yourself

Belt-and-braces: also inspect `MotionEvent.flags & FLAG_WINDOW_IS_OBSCURED` (and
`FLAG_WINDOW_IS_PARTIALLY_OBSCURED` since Android N) on every important touch handler.
Refuse the gesture and surface a security toast.

```kotlin
class GuardedButton @JvmOverloads constructor(
  ctx: Context, attrs: AttributeSet? = null,
) : AppCompatButton(ctx, attrs) {
  override fun onTouchEvent(event: MotionEvent): Boolean {
    val obscured = (event.flags and (
      MotionEvent.FLAG_WINDOW_IS_OBSCURED or
      MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
    )) != 0
    if (obscured) {
      Toast.makeText(context, "Touch blocked — overlay detected", Toast.LENGTH_SHORT).show()
      return true
    }
    return super.onTouchEvent(event)
  }
}
```

> **Why this matters.** The XML attribute relies on the system actually setting the flag.
> Checking it manually means a buggy or modified ROM cannot silently strip the protection.

---

## Step 4: Probe Settings.canDrawOverlays before sensitive flows

Before any flow that mints money or reveals secrets, check whether *any* app has
`SYSTEM_ALERT_WINDOW`. If something can draw, raise the friction — require step-up
biometric, dim the screen, or refuse outright.

```kotlin
class OverlayPosture(private val ctx: Context, private val pm: PackageManager) {
  fun foreignOverlayCapable(): List<String> {
    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    return packages.mapNotNull { info ->
      val granted = (info.requestedPermissionsFlags ?: IntArray(0))
        .zip(info.requestedPermissions ?: emptyArray())
        .any { (flags, name) ->
          name == Manifest.permission.SYSTEM_ALERT_WINDOW &&
          (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }
      if (granted && info.packageName != ctx.packageName) info.packageName else null
    }
  }
}
```

> **Why this matters.** "Can someone draw on top of me right now?" is the security
> question; the OS only tells you when they do. Checking the population in advance lets
> you escalate UX before the attack happens.

---

## Step 5: Detect hostile accessibility services

The deeper attack: an enabled accessibility service can read and inject input across
apps. Enumerate enabled services and check the list against a known-good allow-list (TalkBack,
Voice Access, Switch Access, vendor-shipped services). Anything not on the list is suspect.

```kotlin
class AccessibilityPosture(private val ctx: Context) {
  private val allow = setOf(
    "com.google.android.marvin.talkback",
    "com.android.switchaccess",
    "com.google.android.apps.accessibility.voiceaccess",
  )
  fun unknownEnabledServices(): List<String> {
    val raw = Settings.Secure.getString(
      ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return emptyList()
    return raw.split(":")
      .map { it.substringBefore("/") }
      .filter { it.isNotBlank() && it !in allow }
  }
}
```

> **Why this matters.** Banking trojans almost always enable an accessibility service
> first — it gives them read access to PIN entries, copy-paste content, and the ability
> to dismiss security dialogs automatically. Detection is the only meaningful response.

---

## Step 6: Wire the posture into the integrity verdict

Both signals — `foreignOverlayCapable()` and `unknownEnabledServices()` — fold into the
device verdict alongside root and Play Integrity. If either fires, drop the verdict to
`Limited` and let the rest of the app react.

```kotlin
class IntegrityCheck(
  private val overlay: OverlayPosture,
  private val accessibility: AccessibilityPosture,
  private val playIntegrity: PlayIntegrityProbe,
) {
  suspend fun current(): IntegrityVerdict {
    val reasons = buildList<String> {
      addAll(overlay.foreignOverlayCapable().map { "Overlay-capable app: $it" })
      addAll(accessibility.unknownEnabledServices().map { "Unknown a11y service: $it" })
      addAll(playIntegrity.recentFailures())
    }
    return when {
      reasons.isEmpty() -> IntegrityVerdict.Trusted
      reasons.any { it.startsWith("Play Integrity:") } -> IntegrityVerdict.Untrusted(reasons)
      else -> IntegrityVerdict.Limited(reasons)
    }
  }
}
```

> **Why this matters.** The whole point of a sealed verdict class is so the policy is
> centralised. Every new attack surface adds reasons; no UI screen needs to know about
> overlay attacks specifically.

---

## Step 7: Step-up biometric for irreversible actions

For anything truly costly — money transfer, PAN reveal, IBAN reveal — require a fresh
`BiometricPrompt` signature against a server-issued challenge. The signature happens
inside a `CryptoObject` so it cannot be replayed and is bound to a real human gesture in
the moment. Overlay attacks fall apart here because the prompt is owned by the OS, not
the app's window.

```kotlin
suspend fun confirmTransfer(activity: FragmentActivity, transfer: Transfer): TransferResult {
  if (verdict is IntegrityVerdict.Untrusted) return TransferResult.Refused(verdict.reasons)
  val challenge = stepUpApi.requestTransferChallenge(transfer)
  val signature = stepUpAuthenticator.signChallenge(
    activity = activity,
    alias = BiometricKeyStore.ALIAS_DEVICE_BINDING,
    challenge = Base64.decode(challenge.nonceB64, Base64.NO_WRAP),
    prompt = transferPromptInfo(transfer),
  )
  return stepUpApi.verifyTransfer(challenge, signature)
}
```

> **Why this matters.** Even if the attacker has obtained tap-and-confirm primitives, they
> cannot synthesise a signature. The step-up biometric is the floor of the defence — if
> nothing else worked, this still does.

---

## Step 8: Add a runtime probe for hostile windows

A small `RaspProbe` watching `WindowManager` events spots a window that appears with
`TYPE_APPLICATION_OVERLAY` or `TYPE_SYSTEM_ALERT` during a sensitive screen. Pause input,
log to telemetry, return to the home of the app. Crashing is appropriate.

```kotlin
class OverlayWatchdog(private val window: Window) {
  fun arm(onHostile: (String) -> Unit) {
    window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
      val obscured = window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
      if (obscured > 0 && System.currentTimeMillis() - openedAt < 1500) {
        onHostile("Window obscured during step-up")
      }
    }
  }
}
```

> **Why this matters.** Defence in depth. Each layer the attacker has to peel forces them
> to write more bespoke code; the cost of a successful attack scales superlinearly with
> the number of layers.

---

## Step 9: Test against the GhostTouch demo

Run the [GhostTouch](https://github.com/jacksonmafra-umain/GhostTouch) educational demo on
the same device as your app. Confirm every defence fires — touches are dropped, the
verdict downgrades, the step-up still requires biometric. If any layer is silent, that is
the layer that will fail in production.

```text
$ adb install -r ghosttouch-demo.apk
$ adb shell am start -n dev.ghost.touch/.MainActivity
# In the demo, enable "Overlay capable" and "Accessibility service" toggles.
# Open Fortress → Transfer → confirm.
# Expected: Toast "Touch blocked", verdict chip turns amber, transfer blocks.
```

> **Why this matters.** Untested defences are theatre. Every layer needs an executable
> proof — GhostTouch is exactly that, locally and only for awareness.

---

## Wrap-Up

You now own a four-layer overlay defence: `filterTouchesWhenObscured`, runtime touch
inspection, install-time overlay/accessibility posture, and a step-up biometric that is
infeasible to bypass with overlays alone.

Next mission: read the
[Bulletproof Security article](https://medium.com/@jacksonfdam/building-a-bulletproof-security-system-combining-attestation-and-fingerprinting-2f4d65c02128)
to combine this with device fingerprinting, then look at the upcoming RASP strategies
codelab for runtime probes against Frida, debuggers and Magisk.

**Recap of what you just shipped:**

- `filterTouchesWhenObscured` and a hand-rolled `FLAG_WINDOW_IS_OBSCURED` check on every
  irreversible button.
- An install-time check for overlay-capable apps that downgrades the integrity verdict.
- A runtime check for unknown enabled accessibility services with an allow-list of vendor
  defaults.
- A step-up biometric guard backed by a Keystore signature on a server-issued nonce.
- An end-to-end runnable test against the GhostTouch educational demo.
