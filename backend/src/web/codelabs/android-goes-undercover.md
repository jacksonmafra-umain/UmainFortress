---
title: "Android goes undercover — the not-so-open-source saga"
slug: android-goes-undercover
level: beginner
estimated_minutes: 20
status: published
company: Fortress
tags:
  - ecosystem
  - policy
  - android
  - aosp
summary: >
  The slow privatisation of Android's userland — Play Services, AOSP drift, vendor forks —
  and what each shift means for security engineers. Read this to understand why the
  Android you target as a developer is *not* the Android the Open Source Project describes.
references:
  - title: "Google's Android Goes Undercover (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/googles-android-goes-undercover-the-not-so-open-source-saga-626c30a7a507
  - title: "Android Open Source Project"
    url: https://source.android.com/
  - title: "Project Treble — modularising the Android architecture"
    url: https://source.android.com/docs/core/architecture/treble
---

## Welcome to the saga

Understand the gap between "AOSP" and "Android the device-fleet" before you target the
second one with a fintech app.

When developers say "Android" they usually mean "the user-facing OS on a Pixel or a
Samsung". When the Android Open Source Project (AOSP) means "Android" it means
"the bottom 60 % of that OS — kernel, system services, Java framework". The other 40 %
is the Google Mobile Services (GMS) layer that is closed-source, distributed only to
licensed devices, and increasingly the layer most of your important security primitives
live in. This codelab walks the boundary so you know which features come from which side.

> **Why this matters.** A security architecture that assumes "AOSP" is what users run
> will miss the existence of Play Integrity, SafetyNet, Play Protect, GMS-only attestation,
> and a dozen other primitives that simply do not exist on non-Google Android.

---

## Step 1: AOSP vs Android the product

A short ladder of "what's open source":

- **AOSP base.** Open. Kernel, init, system server, framework Java API, Material library
  stubs.
- **Vendor BSP.** Mostly closed. Qualcomm / MediaTek / Samsung drivers and HALs.
- **OEM skin.** Closed. One UI (Samsung), MIUI (Xiaomi), ColorOS (Oppo). Often replaces
  large chunks of the framework UI.
- **Google Mobile Services (GMS).** Closed. Play Services, Play Store, Play Integrity,
  Maps, Firebase, Google Sign-In, FIDO2/Passkeys credential providers, Cast.
- **Optional Google apps.** Closed. Gmail, Photos, Drive, etc.

Your fintech app talks to AOSP for low-level primitives (Keystore, BiometricPrompt,
WindowManager) and to GMS for high-level primitives (Play Integrity, Play Protect,
Passkeys). Without GMS, half of the second list does not exist.

> **Why this matters.** A device without GMS is not a broken device — it is a different
> Android. Your defences must degrade gracefully on it, not crash.

---

## Step 2: How AOSP and GMS drift apart over time

Every Android major release moves capabilities from one column to the other. The
direction is almost always AOSP → GMS:

- **Pre-2017.** SafetyNet Attestation in GMS, but the same hardware Keystore primitives
  in AOSP. You could plausibly do attestation without GMS.
- **2018–2020.** SafetyNet matures, Play Integrity is announced. Keystore key-attestation
  remains in AOSP but the verification chain leans on GMS-side root certs.
- **2021–2024.** Play Integrity is the official replacement; SafetyNet is deprecated.
  Passkey support starts shipping via `androidx.credentials` backed by Google Password
  Manager (GMS).
- **2025–2026.** Play Integrity is the only supported attestation flow. The "Standard"
  request requires a Cloud project. Off-GMS devices have *no* equivalent.

The trend matters for portfolio decisions — your defence strategy is increasingly
GMS-coupled by design.

> **Why this matters.** Every year, more of "what makes Android secure" is upstream of
> Google's licensing, not upstream of AOSP. Plan accordingly.

---

## Step 3: Devices without GMS in the wild

Three categories you will encounter in support tickets:

1. **Huawei post-2019.** US export restrictions forced Huawei off GMS. HMS (Huawei Mobile
   Services) is the analogue, with different APIs and different attestation primitives.
2. **GrapheneOS, CalyxOS, LineageOS.** Privacy-focused custom ROMs. They sometimes ship
   `microG` as a GMS stub; sometimes ship nothing. Your app cannot tell at install time.
3. **Brand-new Indian / South-East Asian mid-range devices.** Some ship without Play
   Services to dodge licensing fees; users sideload F-Droid or Aurora Store.

For category 3 your app may need a Play Integrity-less verdict path that still produces
"Limited" rather than "Untrusted" — the user is not necessarily hostile, just running a
non-licensed device.

> **Why this matters.** Refusing the "no GMS" install altogether is a customer-acquisition
> decision dressed up as a security one. Make the decision deliberately.

---

## Step 4: Project Treble and the vendor-update problem

Treble (2017) split the Android system image from the vendor image so that AOSP upgrades
no longer require vendor cooperation per device. In principle this should accelerate
security patches. In practice:

- **System updates.** Reach users within months of release on Pixel; up to a year on
  flagship Samsung; sometimes never on budget OEMs.
- **Vendor patches.** Driver-level CVEs (Mali GPU, Qualcomm baseband) often go years
  unpatched on devices the OEM has stopped supporting.
- **Project Mainline.** Subset of system modules deliverable via Play Store updates,
  independent of OS update. Helps mitigate the long tail.

For your app, the takeaway is *patch-state heterogeneity is the default*. You will see
devices on Android 13 with 2023-08 security patch level still receiving Play Integrity
verdicts that say "everything is fine". The verdict is correct — that combination is
still officially supported — but it is not the version-bumping cadence Google publishes.

> **Why this matters.** Defending only against the latest OS is defending against a
> fraction of your install base.

---

## Step 5: SELinux, the policy nobody reads

AOSP ships with SELinux in enforcing mode. Every app is a separate domain; every IPC is
labelled. This is the policy that prevents one app from reading another app's data
directory, even with root *unless the root framework explicitly relaxes the policy*.

Magisk and KernelSU both maintain "enforcing mode" by default for compatibility, but
ship hooks that let users grant individual apps the ability to escape the sandbox.

```bash
# On a rooted device, see which apps Magisk has authorised
adb shell su -c "magisk --denylist ls"
# Output: a list of packages denylisted from Magisk's root capabilities
```

For your app, the SELinux policy is invisible until something goes wrong. When it does
(usually a sandboxed file-access denial that an OEM tightened) the symptom is a
`Permission denied` you cannot grant from the manifest.

> **Why this matters.** SELinux denials look like bugs and get reported as bugs.
> Recognising the shape saves hours of triage.

---

## Step 6: The Play Store as a privileged distribution channel

Three things Play Store does that no other distribution channel does:

1. **Code signing chain of trust.** Play App Signing means the signing key never leaves
   Google. The developer signs uploads with an upload key; Google re-signs with the
   release key. A leaked upload key cannot directly ship a malicious update.
2. **Play Protect runtime scanning.** Submits app behaviour samples to Google's malware
   classifier. Detects known-malicious apps even when sideloaded from another channel.
3. **Play Integrity verdict.** "Installed from Play" is one of the inputs to the standard
   verdict. Sideloaded copies are flagged.

Sideload distribution (F-Droid, Aurora, direct APK) loses all three. For a fintech app
that is usually unacceptable; for a privacy app, acceptable; for a developer-tool app,
sometimes preferred.

> **Why this matters.** "We are on Play" is itself a security primitive. Choosing to also
> distribute via F-Droid changes the verdict your server expects.

---

## Step 7: The user-built / OEM-built distinction

Two kinds of Android you ship onto:

1. **OEM-built.** Pixel, Galaxy, Xiaomi, Oppo. Signed system image, locked bootloader,
   verified boot active. Verified boot status surfaces in Keystore key-attestation as
   `verifiedBootState: GREEN`.
2. **User-built / user-relocked.** Custom ROMs, often signed with `test-keys` or with a
   user-installed AVB key. Verified boot status comes back `YELLOW` (user has installed a
   key) or `ORANGE` (unlocked bootloader).

Server-side policy can read the attestation verdict and act:

```ts
function policyForVerifiedBoot(state: "GREEN" | "YELLOW" | "ORANGE" | "RED") {
  switch (state) {
    case "GREEN": return "trusted";
    case "YELLOW": return "limited"; // user knows what they're doing, often legitimate
    case "ORANGE": return "limited"; // unlocked bootloader, weaker assurance
    case "RED": return "untrusted";  // verified boot failure, refuse
  }
}
```

Refusing every non-`GREEN` device is sometimes correct (regulated banking), often wrong
(consumer fintech, where the customer-acquisition cost of a "refused" decision is high).

> **Why this matters.** A blanket "no custom ROMs" stance loses real users. A nuanced
> "GREEN runs full, YELLOW runs read-only" stance keeps them.

---

## Step 8: Crystal ball — what is shifting in 2026

Three trends worth watching:

1. **Passkey adoption hits a tipping point.** When iOS, Android, and the desktop browsers
   all support credential-manager passkeys consistently, the password-with-2FA flow
   becomes the legacy path.
2. **Play Integrity gets stricter.** Each release tightens the verdict; old apps that
   relied on liberal verdicts will start failing without warning.
3. **AVB / verified-boot becomes mandatory.** OEMs that ship with unlocked bootloaders by
   default will lose access to Google services. The grey market shrinks.

Your codebase should be ready for all three by 2027. If it is not, plan now.

> **Why this matters.** "Plan for the next three years" is the unfashionable security
> work. It pays back in the year you are the only team without an emergency.

---

## Step 9: A short reading list

To go deeper:

- [Source Android](https://source.android.com/) — AOSP documentation, treat as the spec.
- [Android Security Center](https://security.googleblog.com/) — Google's security
  engineering blog.
- [Magisk repo](https://github.com/topjohnwu/Magisk) — to understand how root *actually*
  hides itself in 2026.
- [GrapheneOS docs](https://grapheneos.org/) — the most rigorous of the privacy-ROM
  conversations.

Each is a 1–2 hour investment. Most security engineers benefit from at least Source
Android and the Security Center blog.

> **Why this matters.** Reading widely is how you stop being surprised. Surprises are
> what break production at 3am.

---

## Wrap-Up

You can now name the difference between AOSP and the device-fleet, between Treble and
Mainline, and between the three categories of off-GMS device you will see in support.

Next mission: read [The Manufacturer's Dilemma](/codelabs/manufacturers-dilemma) to
understand the *commercial* pressures that drive the technical decisions you just learned
about, then jump to the [OWASP Mobile Top 10](/codelabs/mobile-top-10) for the canonical
attacker / defender split that lives inside this ecosystem.

**Recap of what you just internalised:**

- AOSP, vendor BSP, OEM skin, GMS — five distinct layers, one device.
- The decade-long migration of attestation from AOSP to GMS.
- The three off-GMS device categories and how to plan for them.
- Treble, Project Mainline, the patch-cadence reality.
- SELinux, Play Store privileges, verified-boot states, and what each implies for policy.
- Three 2026 trends that will shape the next two years: passkeys, stricter Play Integrity,
  AVB-mandatory baselines.
