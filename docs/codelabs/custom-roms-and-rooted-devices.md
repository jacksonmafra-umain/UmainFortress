---
title: "Custom ROMs and rooted devices — the wild west"
slug: custom-roms-and-rooted-devices
level: intermediate
estimated_minutes: 25
status: published
company: Fortress
tags:
  - root
  - rom
  - magisk
  - kernelsu
  - policy
summary: >
  Map the modern custom-ROM and root-framework landscape — LineageOS, GrapheneOS,
  Magisk, KernelSU — what each changes about the device's security guarantees, and how
  to write a policy that refuses what is actually dangerous without locking out
  privacy-conscious users.
references:
  - title: "Custom ROMs and Rooted Devices (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/custom-roms-and-rooted-devices-the-security-wild-west-c5de72851582
  - title: "Magisk — root and module framework"
    url: https://github.com/topjohnwu/Magisk
  - title: "KernelSU — kernel-based Android root"
    url: https://kernelsu.org/
  - title: "GrapheneOS"
    url: https://grapheneos.org/
---

## Welcome to the wild west

Understand what a "custom ROM" actually is, what a "rooted device" actually is, and why
the two are not the same thing.

The Android security model assumes verified boot is enabled, the bootloader is locked,
and userspace cannot escalate. Custom ROMs and root frameworks each relax exactly one of
those three. Knowing which one matters for policy decisions.

> **Why this matters.** A blanket "no root, no custom ROM" stance refuses entire
> communities (developers, privacy advocates) without measurably improving security
> against the actual fraudsters who will spoof the verdict anyway. A nuanced policy
> keeps the legitimate users and refuses the dangerous configurations.

---

## Step 1: Three things that can be "modified"

- **Bootloader.** OEM-signed by default. Unlocking lets you flash unsigned system
  images. Bootloader state surfaces in Keystore key-attestation as `verifiedBootState`.
- **System partition.** The OS. Either the OEM image (stock), an OEM-signed update, or
  an unsigned custom ROM (LineageOS, GrapheneOS).
- **Runtime privilege.** Whether any process can elevate to root *while* the device is
  running. Magisk, KernelSU, APatch all grant this without modifying the system
  partition.

A device can have any combination — unlocked bootloader without root (typical
LineageOS), locked bootloader with root (impossible, by design), stock + Magisk (the
common attacker configuration).

> **Why this matters.** Treating "custom ROM" and "rooted" as synonyms collapses
> distinct threat models into one bucket.

---

## Step 2: Custom ROMs you will see in the wild

Three families, ordered by popularity:

1. **LineageOS.** AOSP-derived, open source, no Google services by default. Privacy and
   maintenance-friendly. Bootloader unlocked, ROM signed with `test-keys`.
2. **GrapheneOS.** Pixel-only, focused on hardening. Verified boot *re-locked* against
   user-installed AVB key. Closer to the OEM security posture than LineageOS.
3. **CalyxOS.** Pixel-only, less rigorous than GrapheneOS but with microG for GMS-stub
   compatibility.

None of the three is hostile by default. Many privacy-conscious users run them. They
typically pass first-party fraud checks because the user is the same human across
re-flashes.

> **Why this matters.** Refusing every non-OEM ROM refuses your most privacy-aware
> customers, who are also the ones most willing to pay for an actually-secure product.

---

## Step 3: Root frameworks you will see

- **Magisk.** Userspace root. Most common. Ships DenyList to hide root from apps.
- **KernelSU.** Kernel-level root. Stealthier than Magisk because the modification lives
  below userspace.
- **APatch.** Newer; combines kernel patches with userspace modules.
- **SuperUser legacy.** Pre-Magisk; rare in 2026, mostly on very old devices.

Each grants any process the ability to escalate to root. Magisk + Zygisk also intercept
specific APIs to lie about the device state — that is the bypass-tooling angle.

> **Why this matters.** The kernel-level frameworks are noticeably harder to detect
> from userspace. Defence cannot rely on Magisk-specific tells alone.

---

## Step 4: Read the verified-boot state, not "is rooted"

Keystore key-attestation includes the verified-boot state, signed by a chain of trust
the device's bootloader controls. This is the strongest signal you have.

```ts
// On the server, after verifying the attestation chain
function verdictForBootState(state: "GREEN" | "YELLOW" | "ORANGE" | "RED") {
  switch (state) {
    case "GREEN":  return "Trusted";  // OEM-signed, locked bootloader, stock or signed ROM
    case "YELLOW": return "Limited";  // user-installed AVB key (GrapheneOS-style); intentional, often safe
    case "ORANGE": return "Limited";  // unlocked bootloader, ROM unverified
    case "RED":    return "Untrusted"; // verified boot failure — refuse
  }
}
```

`GREEN` is the OEM-signed state. `YELLOW` is "the user installed a key but it is
locked-down again" — that is GrapheneOS's posture. `ORANGE` is "anyone can flash
anything" — the LineageOS-on-unlocked-Pixel posture. `RED` is "verified boot failed at
boot" — a tamper indicator.

> **Why this matters.** This is the single highest-quality signal. Most of what your
> policy decides should branch on it.

---

## Step 5: The "limited" tier — what to allow

A `Limited` verdict comes from `YELLOW` or `ORANGE`. The user is legitimate; the device
posture is weaker than the OEM default. Policy:

- **Read.** Allow — the user is signing into their own account.
- **Mutate (preferences, low-stakes).** Allow with step-up if it is the first time
  from this device.
- **Money mutations.** Require step-up biometric. Add a frequency limit and a per-day
  cap that is lower than the `Trusted` tier.
- **PAN reveal, IBAN reveal.** Require step-up *and* surface "We notice your device is
  in a custom configuration. Confirm again to proceed."
- **Add card.** Allow with step-up.

The `Limited` tier should still be usable. If it is not, you are blanket-banning by
another name.

> **Why this matters.** A nuanced `Limited` tier is the difference between "we serve
> 95% of the market well" and "we serve 70% of the market".

---

## Step 6: The "untrusted" tier — what to refuse

An `Untrusted` verdict comes from `RED` boot state, repeated failed attestations,
positive Magisk-with-hiding clues, or Play Integrity failing in `MEETS_BASIC_INTEGRITY`.
Policy:

- **Sign-in.** Allow (you want to know who is trying).
- **Read.** Allow read-only on summary data; refuse on PII (IBAN, full card).
- **Mutations.** Refuse.
- **Sign-up.** Refuse — do not let a hostile device open new accounts.

When you refuse, surface a meaningful message: "We can't perform sensitive actions on
this device. If you believe this is a mistake, please contact support." Never the
technical reason; that just trains the next attacker.

> **Why this matters.** A refusal that names the failed check is a tutorial. A refusal
> that withholds the reason is a wall.

---

## Step 7: Detect Magisk DenyList specifically

Magisk's DenyList unmounts overlays inside the target process. The asymmetry is your
friend: outside the DenyList the device looks rooted; inside the DenyList it looks
stock. You can detect the *act of hiding*.

```kotlin
fun suspiciousMountAnomalies(): List<String> {
  val mounts = runCatching { java.io.File("/proc/self/mounts").readText() }.getOrDefault("")
  val expected = listOf("/data", "/system", "/vendor", "/product")
  val present = expected.filter { mounts.contains(it) }
  val absent = expected - present.toSet()
  return absent.map { "missing.mount.$it" }
}
```

If `/system` is not mentioned in `/proc/self/mounts` for your process — that is unusual,
and worth flagging. Stack with the other Magisk clues from the Hackers Gonna Hack
codelab.

> **Why this matters.** DenyList is increasingly well-tuned, but the *act* of hiding
> leaves traces that "real stock" never does.

---

## Step 8: The KernelSU problem

KernelSU patches the kernel. The userspace tells that work against Magisk fail against
KernelSU. Specific defences:

- Read `/proc/kallsyms` and look for symbols that should not exist on a stock kernel.
  Mostly blocked to non-root on modern Android; the *failure* to read is itself a
  signal.
- Check the kernel version string (`uname -r`). KernelSU often appends a custom suffix.
- Rely more heavily on Play Integrity's `MEETS_STRONG_INTEGRITY` — that one requires
  hardware-backed evidence that KernelSU cannot fabricate.

```kotlin
fun kernelSuffix(): String? = runCatching {
  java.io.File("/proc/version").readText().trim()
}.getOrNull()
```

> **Why this matters.** Kernel-level root will be the dominant framework by 2027.
> Userspace-only defences are losing relevance.

---

## Step 9: Develop a Dev Mode that simulates each posture

Three toggles in the Fortress demo:

```kotlin
data class CustomRomFlags(
  val verifiedBootState: BootState,         // GREEN / YELLOW / ORANGE / RED
  val rootFramework: RootFramework?,        // null / MAGISK / KERNELSU
  val magiskDenyListActive: Boolean,
)
```

Each toggle drives the integrity verdict deterministically in debug builds. QA can run
every combination of verdict tier against every flow without needing a physical rooted
device.

> **Why this matters.** Testing the `Limited` and `Untrusted` flows against real
> hardware costs hours; Dev Mode costs minutes.

---

## Step 10: Write the customer-facing policy

The decisions you make here belong in your data-safety section *and* in a public help-
desk article. Two paragraphs is enough.

> **What this means for power users**
>
> We support sign-in from any Android device — including LineageOS, GrapheneOS, and
> rooted Pixel phones. Sensitive actions (transfers, PAN reveal, IBAN reveal) require
> an additional confirmation when your device's bootloader is unlocked. Devices where
> verified boot has failed cannot perform sensitive actions; if you believe this is
> wrong, please contact support.

The article saves your support desk the same conversation 200 times. It also wins you
trust with the audience that runs custom ROMs.

> **Why this matters.** Transparency about policy is itself a security primitive. Users
> who know the rules do not invent worse workarounds.

---

## Wrap-Up

You can now classify any Android device along three axes — bootloader, system image,
runtime privilege — and write a policy that allows the privacy-conscious user while
refusing the fraudster.

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) — the Keystore primitive
  this policy depends on.
- [Hackers Gonna Hack](/codelabs/hackers-gonna-hack) — the bypasses this policy
  defends against.
- [Root Detection in 2026](/codelabs/root-detection-2026) (draft) — the userspace-level
  detection details.

**Recap of the wild-west map:**

- Three independent axes: bootloader, system image, runtime privilege.
- Three custom-ROM families and two main root frameworks worth knowing.
- Verified-boot state as the highest-quality signal, mapped to four verdict tiers.
- Concrete `Limited` and `Untrusted` policy templates.
- Magisk DenyList anomaly detection and KernelSU-specific notes.
- A Dev Mode set that exercises every posture without physical hardware.
- A public-facing policy paragraph that explains the rules without giving away the
  detection mechanics.
