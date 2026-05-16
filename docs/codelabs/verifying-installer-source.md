---
title: "Verifying installer source"
slug: verifying-installer-source
level: intermediate
estimated_minutes: 20
status: published
company: Fortress
tags:
  - installer
  - anti-sideload
  - policy
  - distribution
summary: >
  Read at runtime whether the user installed your APK from Play, F-Droid, a vendor
  store, or via direct sideload — and decide what each combination is allowed to do.
  With the Play Integrity verdict as the second leg of the same question.
references:
  - title: "Verifying Installer Source (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/enhancing-android-app-security-verifying-installer-source-and-more-466d9240a605
  - title: "InstallSourceInfo — official docs"
    url: https://developer.android.com/reference/android/content/pm/InstallSourceInfo
  - title: "Play Integrity API — appRecognitionVerdict"
    url: https://developer.android.com/google/play/integrity/verdicts
---

## Welcome to installer verification

Understand the difference between "your app on this device" and "your app installed from
a channel you trust" — and learn how to ask the OS which one you have.

A user can install your app from any of: Play Store, Samsung Galaxy Store, Huawei
AppGallery, F-Droid, Aurora Store, a direct APK download in a browser, or `adb install`
from a developer machine. Each implies a different threat model. Reading the installer
package name at runtime gives you the data; the policy is yours to design.

> **Why this matters.** A sideloaded copy of your APK might be a developer's local
> build, a privacy-conscious user, or a repackaged-and-resigned copy with the auth
> flow surgically removed. Treat them differently.

---

## Step 1: Read the install source on the device

`PackageManager.getInstallSourceInfo()` (API 30+) returns the package that installed
your app. Cache the result; it does not change at runtime.

```kotlin
data class InstallerInfo(
  val installingPackageName: String?,
  val originatingPackageName: String?,
  val installSourceClass: InstallSourceClass,
)

enum class InstallSourceClass { PlaySafe, VendorStore, FDroid, Sideload, Adb, Unknown }

fun installerInfo(context: Context): InstallerInfo {
  val info = context.packageManager.getInstallSourceInfo(context.packageName)
  val installer = info.installingPackageName
  val originating = info.originatingPackageName
  return InstallerInfo(installer, originating, classify(installer))
}

private fun classify(pkg: String?): InstallSourceClass = when (pkg) {
  null                                  -> InstallSourceClass.Adb
  "com.android.vending"                 -> InstallSourceClass.PlaySafe
  "com.android.packageinstaller",
  "com.google.android.packageinstaller" -> InstallSourceClass.Sideload
  "com.samsung.android.app.samsungapps" -> InstallSourceClass.VendorStore
  "com.huawei.appmarket"                -> InstallSourceClass.VendorStore
  "org.fdroid.fdroid"                   -> InstallSourceClass.FDroid
  "com.aurora.store"                    -> InstallSourceClass.FDroid // close cousin
  else                                  -> InstallSourceClass.Unknown
}
```

The `Adb` classification is the developer / CI path. The `Sideload` classification is
"the user used the system package installer to install a downloaded APK".

> **Why this matters.** This single call gives you 80% of the channel information
> server-side policy needs.

---

## Step 2: The Play Integrity `appRecognitionVerdict`

Server-side Play Integrity returns a verdict on whether the running binary matches the
APK Google has on file from your developer account.

Three values:

- `PLAY_RECOGNIZED` — installed from Play, unmodified.
- `UNRECOGNIZED_VERSION` — installed via Play but a *version* Google does not know
  about (downgraded? a different track?).
- `UNEVALUATED` — not enough information (off-Play install, or Play Integrity has
  failed).

```ts
function policyForApp(appVerdict: string) {
  switch (appVerdict) {
    case "PLAY_RECOGNIZED": return "trusted";
    case "UNRECOGNIZED_VERSION": return "limited";
    case "UNEVALUATED": return "off-play";
  }
}
```

`PLAY_RECOGNIZED` is the strongest claim: this exact APK, in this exact version, signed
with your release key. Pair with the on-device installer info.

> **Why this matters.** The `appRecognitionVerdict` cannot be spoofed from userspace —
> it is computed in Google's verifier from a signed token. Trust it.

---

## Step 3: Cross-check on-device installer vs Play Integrity

The two answers should agree on a Play install. When they disagree, that is the signal.

```ts
type Channel = "Play" | "Vendor" | "FDroid" | "Sideload" | "Adb" | "Unknown";
type AppVerdict = "PLAY_RECOGNIZED" | "UNRECOGNIZED_VERSION" | "UNEVALUATED";

function deriveChannelVerdict(
  installer: Channel,
  appVerdict: AppVerdict,
): "trusted" | "limited" | "suspicious" | "untrusted" {
  if (installer === "Play" && appVerdict === "PLAY_RECOGNIZED") return "trusted";
  if (installer === "Play" && appVerdict !== "PLAY_RECOGNIZED") return "suspicious";
  if (installer === "Vendor" || installer === "FDroid") return "limited";
  if (installer === "Sideload") return "limited";
  if (installer === "Adb") return "limited";
  return "untrusted";
}
```

The interesting cell: installer says Play, Play Integrity disagrees. That is "the
attacker spoofed `getInstallSourceInfo`'s answer but cannot spoof Google's verifier".
Treat as suspicious.

> **Why this matters.** Two independent signals catch the case where only one was
> compromised. That is the entire defence-in-depth principle in one query.

---

## Step 4: Decide what each channel can do

Sample policy. Numbers are illustrative; the shape is the point.

| Channel × Verdict | Sign-in | Read | Mutations < €100 | Mutations ≥ €100 | PAN reveal | Sign-up |
|---|---|---|---|---|---|---|
| Play / RECOGNIZED | ✅ | ✅ | ✅ | step-up | step-up | ✅ |
| Play / UNRECOG | ✅ | ✅ | step-up | refuse | refuse | refuse |
| Vendor / UNEVAL | ✅ | ✅ | step-up | step-up | step-up | ✅ |
| F-Droid / UNEVAL | ✅ | ✅ | step-up | refuse | refuse | refuse |
| Sideload / UNEVAL | ✅ | ✅ | refuse | refuse | refuse | refuse |
| adb / UNEVAL | ✅ | ✅ | refuse | refuse | refuse | refuse |

Two principles in the table: (1) reads are almost always allowed (privacy is more
costly than denying someone access to their own data), (2) sign-up is the surface that
needs to be hardest, because a fresh account on a hostile channel is the canonical
fraudster move.

> **Why this matters.** The grid is a single artefact you can put in a release-review
> ticket. It survives whiteboard sessions in a way prose does not.

---

## Step 5: Surface the policy to the user

A user who installed your app via F-Droid deserves to know why "Transfer €500" asks for
extra confirmation. The Security Center screen should explain.

```text
This device installed Fortress from F-Droid. That's fine for everyday use; we
require a fingerprint for any transfer over €100 from non-Play installs because we
can't verify the install came directly from us.
```

The transparency wins trust with the audience that *chose* a non-Play install. Hiding
the policy implies you are doing something furtive.

> **Why this matters.** Off-Play users are often your most engaged customers. Treat the
> policy as a conversation, not a barrier.

---

## Step 6: Handle the `Adb` case explicitly

Developer-installed builds are the source of internal testing. They will hit your
production backend (in QA) and your staging backend (always). Two options:

1. **Hard-code an allow-list in debug builds.** `BuildConfig.DEBUG` + `Adb` installer →
   skip the channel-verdict policy.
2. **Server-side flag per user email.** Internal-testing accounts bypass the policy via
   an admin flag.

The second is more flexible (works for external QA). The first is fastest to ship.

```kotlin
fun isInternalDebugInstall(context: Context): Boolean {
  return BuildConfig.DEBUG && installerInfo(context).installSourceClass == InstallSourceClass.Adb
}
```

> **Why this matters.** Without an `Adb` carve-out, every developer build hits the
> `Untrusted` branch and the team cannot test. With it, the policy still applies to the
> release binaries that go out the door.

---

## Step 7: Watch for the changing-installer case

The installer info is set at install time. A user who *updates* the app from a
different source can carry a stale `installingPackageName`. The `originatingPackageName`
captures the *most recent* origin in this case — useful for re-classification.

```kotlin
fun isUpdateOrigin(context: Context): Boolean {
  val info = context.packageManager.getInstallSourceInfo(context.packageName)
  return info.originatingPackageName != null
    && info.originatingPackageName != info.installingPackageName
}
```

A user who installed from Play and updated via a sideloaded APK *should* show
`installingPackageName == "com.android.vending"` and a non-null `originatingPackageName`
pointing at the package installer. That mismatch is policy-relevant.

> **Why this matters.** Initial-install metadata can mislead months later. Re-check
> the originating package on each app launch.

---

## Step 8: Server-side validation cannot be skipped

The on-device `installerInfo` is *advisory* — a determined attacker can hook
`getInstallSourceInfo` and return any value. Authoritative answers live server-side via
Play Integrity. Pattern:

1. Client reports its self-reported channel + signed Play Integrity token on every
   sensitive action.
2. Server verifies the Play Integrity token, derives the authoritative app verdict.
3. Server compares the self-report to the authoritative answer. Disagreement → log.

```ts
async function authoritativeChannel(req: Request): Promise<Channel> {
  const token = req.headers["x-play-integrity-token"];
  const verdict = await verifyPlayIntegrity(token);
  return verdict.appRecognitionVerdict === "PLAY_RECOGNIZED" ? "Play" : "OffPlay";
}
```

When the server's view disagrees with the client's claim, you have evidence of
tampering with the on-device check.

> **Why this matters.** Trusting only the device is trusting the attacker.

---

## Step 9: Plan for distribution growth

If you ship via Play today and want to add F-Droid tomorrow, three things change:

1. The build that goes to F-Droid is signed with a *different* key. Play Integrity will
   not recognise it. Your channel-verdict policy must allow it explicitly via the
   `FDroid` branch.
2. Updates flow via F-Droid's signing key chain, not yours. You lose some control over
   roll-outs.
3. Your data-safety section in Play has to declare the F-Droid build does not exist in
   the Play world, with its own privacy commitments.

For an Enterprise APK (sideloaded by a corporate MDM), similar reasoning applies. Plan
the channel policy *before* you commit to a second distribution channel.

> **Why this matters.** "We'll add F-Droid later" is a decision that changes the
> security model. Make it deliberately.

---

## Step 10: Telemetry on every channel decision

Five events. Aggregate. Watch for shifts.

```ts
type ChannelEvent =
  | { kind: "channel.classified"; channel: Channel; verdict: AppVerdict; userId: string }
  | { kind: "channel.mismatch"; installerClaim: Channel; serverDerived: Channel; userId: string }
  | { kind: "channel.blocked"; channel: Channel; action: string; userId: string }
  | { kind: "channel.stepup.required"; channel: Channel; action: string; userId: string }
  | { kind: "channel.policy.changed"; from: string; to: string };
```

A spike of `channel.mismatch` in a single hour is almost certainly a fraudster running a
fleet of repackaged-and-installed-from-Play-looking copies. Spike-of-the-day pattern
matching catches this in real-time.

> **Why this matters.** Telemetry without alerts is data. Telemetry with alerts is
> control.

---

## Wrap-Up

You can now classify every install in your fleet by source, cross-check the
self-reported channel against Play Integrity's authoritative verdict, and apply
graduated policy that respects both legitimate non-Play users and fraudster realities.

Next mission:
- [Play Integrity](/codelabs/play-integrity) (draft) — the deep dive on the verdict
  this codelab consumes.
- [Custom ROMs and Rooted Devices](/codelabs/custom-roms-and-rooted-devices) — the
  device-side analogue of channel policy.
- [Hackers Gonna Hack](/codelabs/hackers-gonna-hack) — repackaging is one of the six
  bypasses listed there.

**Recap of the channel-verification kit:**

- `getInstallSourceInfo()` for on-device classification into six channels.
- Play Integrity's `appRecognitionVerdict` for the authoritative second leg.
- A per-channel policy grid covering sign-in, reads, mutations, and sign-up.
- An explicit `Adb` carve-out for internal QA.
- `originatingPackageName` for catching change-of-channel updates.
- Telemetry-as-alarm on installer/verdict mismatches.
