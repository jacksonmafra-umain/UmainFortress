---
title: "The manufacturer's dilemma — Samsung, Huawei et al."
slug: manufacturers-dilemma
level: beginner
estimated_minutes: 20
status: published
company: Fortress
tags:
  - ecosystem
  - samsung
  - huawei
  - policy
  - oem
summary: >
  How major Android OEMs reconcile carrier requirements, regional regulation, vendor-BSP
  realities and security-update SLAs — and the practical impact on app-side defences. Read
  this if you have ever wondered why "the Samsung version" of your bug is the hardest one.
references:
  - title: "The Manufacturer's Dilemma (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/the-manufacturers-dilemma-how-samsung-huawei-and-others-handle-security-c898bdc02775
  - title: "Samsung Knox — the OEM secure platform"
    url: https://www.samsungknox.com/en
  - title: "Huawei Mobile Services (HMS) — developer documentation"
    url: https://developer.huawei.com/consumer/en/hms
---

## Welcome to the OEM problem

Understand why the Android device on the test bench is not the Android the framework docs
describe, and which OEM divergences matter for fintech.

Android documentation treats "the device" as a uniform target. Real devices ship with
OEM-modified system services, vendor-specific HALs, regional-regulator-tweaked behaviour,
and carrier-image overlays that you can only discover by holding the device and watching
your bug not reproduce. This codelab maps the territory.

> **Why this matters.** Half of your worst Android bugs will be OEM-specific. Knowing
> which OEM does which weird thing turns a week of triage into a morning.

---

## Step 1: The forces an OEM is balancing

Five pressures, often in conflict:

1. **Google licensing.** GMS access requires hitting the Android Compatibility Test
   Suite and quarterly security patch cadence. Failure means no Play Store, no
   YouTube, no Maps — commercial death.
2. **Carrier requirements.** Verizon, AT&T, Deutsche Telekom and their international
   peers each demand custom apps, lock-screen branding, and specific connectivity
   features. The bigger the carrier, the more the demand.
3. **Regional regulation.** EU GDPR, India's data localisation, China's MIIT licensing
   regime, US export rules. Each shifts what the OEM can and cannot include.
4. **Vendor BSPs.** Qualcomm, MediaTek and Samsung LSI each ship a Board Support Package
   the OEM must integrate. Driver bugs cascade into the user OS.
5. **Bill of materials.** Every cent matters. A cheaper modem chip means a different
   baseband stack means different RIL behaviour means different SMS-OTP edge cases.

Each pressure pushes the device away from the AOSP baseline. The "best" OEMs balance
the five with minimal disruption; the worst ship a recognisable Android that fails in
inventive ways.

> **Why this matters.** Knowing which pressure produced which behaviour helps you predict
> the next surprise.

---

## Step 2: Samsung — the heaviest fork that still ships GMS

Samsung's One UI is the most-shipped Android fork. The fork includes:

- **Knox.** A discrete secure boot and key-management stack. Provides additional
  attestation primitives via Knox SDK on Knox-licensed devices.
- **Samsung Pass.** A credential manager that competes with Google Password Manager.
- **Battery / background-process management.** Samsung kills background processes more
  aggressively than stock Android, breaking long-running uploads and silent push.
- **Custom SystemUI.** The recent-apps screen, notification shade, share-sheet all
  diverge from AOSP. Most of your `Intent` chooser bugs live here.
- **Edge Panel and Air Command.** Floating windows that interact with overlay-defence
  in unexpected ways.

For fintech, Knox is the headline. If your app integrates Samsung's attestation API you
get a *second* attestation primitive on top of Play Integrity, useful on devices where
Play Integrity is borderline.

> **Why this matters.** Samsung is half of your install base in most regions outside
> China. Their forks are the most likely to expose edge cases.

---

## Step 3: Huawei — the parallel-universe Android

Post-2019 US export restrictions cut Huawei off from GMS. They responded by building
Huawei Mobile Services (HMS) as a parallel stack:

- **Huawei AppGallery** in place of Play Store.
- **Push Kit** in place of FCM.
- **Map Kit, Account Kit, Wallet Kit** in place of Google equivalents.
- **Safety Detect** in place of SafetyNet / Play Integrity.

Apps targeting Huawei devices must either ship two builds (GMS and HMS), use an
abstraction layer, or accept "no Huawei users". For European fintech the answer is
usually accept; for Asia-Pacific it is two builds.

The interesting security note: Huawei's Safety Detect verdict is a *different* signal,
with different bypass patterns. If your server treats every attestation token as
equivalent, you will mis-classify Huawei traffic on day one.

> **Why this matters.** A nontrivial slice of Asian fintech traffic comes from devices
> that simply do not have Play Integrity. Plan for it.

---

## Step 4: Xiaomi, Oppo, Vivo, OnePlus — the Chinese OEMs

These OEMs ship two product lines: a "Chinese-market" variant without GMS, and a
"global" variant with it. The Chinese-market variant typically ships:

- **MIUI / ColorOS / OxygenOS / OriginOS** — heavy UI forks.
- **Vendor app stores** (Mi Store, OPPO App Market) — primary distribution.
- **Strict background-process limits**, even more aggressive than Samsung.
- **Custom keyboard** with cloud-suggest behaviour that occasionally leaks fields.

For app developers the most common pain point is the background-process death. A push
notification arrives, your service starts, and 30 seconds later the OEM scheduler kills
it. The fix is usually `WorkManager` with the OEM-specific "battery optimisation"
exception requested explicitly via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

> **Why this matters.** A push-based fraud-detection workflow that works on Pixel and
> Samsung will silently fail on a brand-new Xiaomi until you understand this.

---

## Step 5: Google Pixel — the reference but also a product

The Pixel is the AOSP reference target. It is also a commercial product with its own
forks:

- **Pixel-exclusive Tensor SoC features.** Hardware NPU access via the Edge TPU API,
  unavailable elsewhere.
- **Pixel-exclusive Magic Eraser, Now Playing, Call Screen** — closed-source but live on
  many sensitive code paths.
- **Faster security-patch cadence.** Pixel typically gets monthly patches; most other
  OEMs get quarterly at best.
- **More aggressive default privacy.** Approximate-location-only-by-default, lockdown
  mode for civil-society users.

For fintech development the Pixel is the easiest happy path. For a bug to land it has to
work on Pixel *and* the next two OEMs in your install base — that is the real bar.

> **Why this matters.** "Works on Pixel" is necessary, not sufficient. The harder
> question is "works on a 2023 Samsung A-series".

---

## Step 6: The patch-cadence reality

Three rough cohorts in 2026:

1. **Up to date (≤ 90 days old patch).** Pixel, recent Samsung flagships, recent OnePlus.
2. **Lagging (90–365 days).** Mid-range Samsung, mid-range Xiaomi, most Motorola.
3. **Unpatched (> 365 days).** Cheap budget devices past their official support window,
   user-flashed custom ROMs without builder commitment.

Your server-side policy can read the security-patch level from Play Integrity's device
verdict and act:

```ts
function policyForPatchAge(patchLevel: string): "trusted" | "limited" | "untrusted" {
  const ageDays = (Date.now() - parsePatchDate(patchLevel)) / 86_400_000;
  if (ageDays <= 90) return "trusted";
  if (ageDays <= 365) return "limited";
  return "untrusted";
}
```

Refusing every device older than 90 days breaks half your fleet. Allowing every device
indefinitely defeats the purpose. Pick a threshold and document it.

> **Why this matters.** Patch state is the single most useful signal for ambient device
> risk. Use it, but use it gently.

---

## Step 7: The carrier-image layer

In many markets the device is sold through a carrier, and the carrier overlays additional
apps and config:

- **Pre-installed apps.** Often unremovable, sometimes with elevated permissions.
- **Carrier billing.** A direct-to-bill SDK that interacts with your in-app purchase
  flow.
- **Lock-screen and dialler customisation.** Affects how OTP intents and call-state
  signals reach your app.
- **WiFi-calling and VoLTE settings.** Affects your network reachability assumptions.

You cannot test against every carrier image. You can monitor for support tickets that
correlate with a specific build fingerprint and treat that as a known-edge.

> **Why this matters.** Carrier-image bugs reproduce on one bench in your office and
> nowhere else. Telemetry that captures `Build.FINGERPRINT` is how you find them.

---

## Step 8: Knox, Honor, Realme — the cousins

A few smaller forks worth knowing about:

- **Samsung Knox** — a security-product family that overlaps with stock Android. Knox
  Vault, Knox Configure, Knox Manage. If your enterprise customers ask "is your app
  Knox compatible?", the answer is normally "yes, via standard Android APIs; Knox does
  not require app-side adoption". If they ask for Knox SDK integration specifically,
  that is a contract conversation, not a release.
- **Honor.** Spun out of Huawei in 2020. Now ships GMS again. Most code paths look like
  Huawei devices but with Google services restored.
- **Realme.** Oppo's budget brand. Behaviour matches Oppo except for branding.
- **Nothing.** Carl Pei's startup. Stock-Android-ish with a custom launcher and a
  unique LED interaction layer.

None of these require dedicated codepaths. They do appear in your telemetry, and knowing
the family tree saves you classifying them as "weird unknown OEM".

> **Why this matters.** Half the OEMs in your support backlog are corporate splits of
> each other. The family tree predicts the bug class.

---

## Step 9: How to test against a fleet you do not own

Three options, in order of cost:

1. **Cloud device farms.** Firebase Test Lab, BrowserStack, AWS Device Farm. Real
   devices, real OSes, real OEM skins. Best for pre-release smoke. Expensive for daily
   CI.
2. **Cuttlefish.** Google's cloud-friendly Cuttlefish emulator can simulate a variety of
   form factors and OS versions. See the
   [Cuttlefish codelab](/codelabs/cuttlefish) for the long form.
3. **Buy a representative bench.** A current-gen Pixel, a current-gen Samsung mid-range,
   one Xiaomi, one Motorola. That bench plus emulators covers 90 % of your real fleet
   for under €1,500 / year.

A small physical bench beats a large emulator-only stack. Real-OEM skins surface bugs
no emulator reproduces.

> **Why this matters.** OEM bugs are exactly the bugs your customers report. Reproducing
> them locally is the difference between a hotfix in a day and a hotfix in a week.

---

## Step 10: A short telemetry kit for OEM forensics

Capture these fields with every telemetry event. None are PII; all are useful.

```kotlin
data class DeviceFingerprint(
  val manufacturer: String,   // Build.MANUFACTURER — "samsung", "xiaomi"
  val model: String,          // Build.MODEL — "SM-G990B", "Pixel 7"
  val brand: String,          // Build.BRAND — "samsung", "google"
  val sdkInt: Int,            // Build.VERSION.SDK_INT — 33, 34
  val release: String,        // Build.VERSION.RELEASE — "13", "14"
  val securityPatch: String,  // Build.VERSION.SECURITY_PATCH — "2024-09-01"
  val fingerprint: String,    // Build.FINGERPRINT — full build identifier
  val isCarrierImage: Boolean // simple heuristic on TelephonyManager
)
```

Aggregate by manufacturer + model + securityPatch on the backend and you have an instant
view of "which fleet just hit this bug".

> **Why this matters.** A single bug in your telemetry stream often correlates to one
> manufacturer + one patch level. Capture both and you find it in minutes, not days.

---

## Wrap-Up

You can now read an Android support ticket, look at the device fingerprint, and predict
which family of bugs to suspect — Samsung's overzealous battery saver, Xiaomi's
background-kill, Huawei's HMS substitution, or a stock-AOSP-on-old-Pixel quirk.

Next mission: combine this with the
[Android Goes Undercover codelab](/codelabs/android-goes-undercover) to understand the
*ecosystem* pressures that drive these OEM decisions, then read
[OWASP Mobile Top 10](/codelabs/mobile-top-10) for the controls that hold across every
fork.

**Recap of what you just learned:**

- Five forces every OEM is balancing — Google, carrier, regulator, vendor BSP, BoM.
- Samsung's fork is heaviest among GMS-licensed devices; Huawei is the parallel-universe
  alternative.
- The Chinese OEMs ship two product lines with different background-process behaviour.
- Three patch-cadence cohorts and a server-side policy template for each.
- The carrier-image layer and why some bugs reproduce on exactly one bench.
- A device-fingerprint telemetry kit that turns mystery reports into actionable patterns.
