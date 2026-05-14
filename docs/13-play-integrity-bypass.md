# 13 — Play Integrity Bypass: What's Circulating in the Wild

> "Every wave of Play Integrity hardening triggers a wave of bypass modules. The defender's
> question isn't whether the bypass exists — it's how to spot the verdict distribution
> shifting in your telemetry before fraud does." — *Fortress field notes*

**TL;DR** — Play Integrity has been hardened, then bypassed, then re-hardened, in roughly six-
month cycles since 2022. Today (early 2026) the working bypass kit is **Magisk + Zygisk +
DenyList + Play Integrity Fix (PIF) module**. It defeats `MEETS_BASIC_INTEGRITY` and very often
`MEETS_DEVICE_INTEGRITY`. It does **not** defeat `MEETS_STRONG_INTEGRITY`. The defender's
counter-play is to require Strong for high-value actions and to watch verdict distribution per
app version / market for the telltale shift when a new bypass module ships.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Notice the bypass distribution before fraud volumes spike | Convince Google's verdict to read "stock device" on my rooted phone |
| **Key idea** | Strong integrity uses hardware-rooted attestation; module bypasses can't forge that signature | I don't need to forge — I need to get the verdict to a level your server still accepts |
| **Worst failure** | Accepting `MEETS_BASIC_INTEGRITY` for high-value ops | Hardcoded thresholds that don't survive a verdict-distribution shift |

---

## 🛡️ Defender — "I watch the histogram, I require the floor"

### The verdict ladder, again

| Verdict | Bootloader | Magisk + DenyList? | PIF module today? |
|---|---|---|---|
| `MEETS_STRONG_INTEGRITY` | Locked + verified-boot green + hardware-attested | Fails | Fails — needs hardware RoT |
| `MEETS_DEVICE_INTEGRITY` | Locked + standard Android image | Passes if DenyList covers your app | Often passes (with PIF) |
| `MEETS_BASIC_INTEGRITY` | May be unlocked | Passes if PIF spoofs Pixel build fingerprint | Reliably passes |
| `MEETS_VIRTUAL_INTEGRITY` | Emulator | n/a | n/a |
| (none) | Unlocked, no module | — | Honest verdict |

**For Fortress, the gate is**: high-value ops require `MEETS_STRONG_INTEGRITY`. That's the
floor an attacker cannot bypass with current commercial-grade modules; clearing it requires a
hardware-level attack on the device's secure element, which is out of reach of free Magisk
tooling.

### The defender's telemetry shape

What to log per request:

- `verdict.deviceRecognitionVerdict` distribution per (app version, country, device model).
- Ratio of Strong / Device / Basic / None per slice.
- 95th-percentile latency of Play Integrity token requests.
- Failure codes from `decodeIntegrityToken` (signature invalid, expired, app mismatch).

The signal of a circulating bypass module is **a sudden shift in the verdict mix** for a slice
of users. Before the shift: a country's distribution might be 70% Strong / 20% Device / 9%
Basic / 1% None. Two weeks after a new PIF module ships and gets traction: 40% Strong / 35%
Device / 22% Basic / 3% None. The "Device" bucket inflated at Strong's expense — that's the
shape of "someone published a new bypass."

Operational response:

1. **Alert** when the per-slice ratio shifts by > 2σ within 24 hours.
2. **Don't** auto-tighten thresholds during the spike — false positives spike too.
3. **Manually verify** a sample of "newly Device-only" sessions: were they previously Strong?
4. If a known module is responsible, escalate to **Play Integrity team** with the verdict
   payloads — Google ships server-side fixes within weeks.

### Hardware attestation as the unfoolable layer

`MEETS_STRONG_INTEGRITY` ultimately relies on **the device's hardware-rooted attestation
chain** (covered in [11-root-detection.md](11-root-detection.md)). The Play Integrity service
reads attestation cert chains from the device's secure element; those chains end in a key
**Google controls** at the hardware root. Magisk runs in Linux userspace; the secure element
runs below Linux. There is no software trick that gets the secure element to attest a different
boot state.

What Magisk + PIF can do is **manipulate the userspace inputs** that Play Integrity reads
alongside the attestation: build properties, device fingerprint, package list. When Google's
verdict logic weighs userspace signals heavily (as it does for `BASIC`/`DEVICE`), userspace
spoofing works. When it weighs hardware attestation heavily (as it does for `STRONG`), spoofing
doesn't reach.

### Defence in depth, even if you require Strong

Even with `STRONG` required, layer with:

- **Device binding** ([09-zero-trust.md](09-zero-trust.md)) so a Strong-integrity attacker
  device still doesn't match the user's enrolled binding.
- **Behaviour scoring** — Strong-integrity is necessary but not sufficient. A first-ever
  transaction from a brand-new Strong-integrity device should still trip the risk engine.
- **Out-of-band confirmation** — push to a known-trusted device for high-value ops.

### Don't trust the client to send "Strong"

The integrity token must be **decoded server-side**. A client that says "I have a Strong
verdict" is just text on the wire. The token is a JWT (well, JWS) signed by Google; the server
calls `playintegrity.v1.decodeIntegrityToken`, gets back the structured verdict, and **then**
decides. See [05-play-integrity.md](05-play-integrity.md).

---

## ⚔️ Attacker — "I shift the verdict by one tier and watch for unsealed doors"

### Bypass 1 — Magisk + Zygisk + DenyList (the floor)

```
1. Install Magisk via custom recovery (TWRP or boot.img patch).
2. Enable Zygisk in Magisk settings.
3. Install the "Play Integrity Fix" module — current canonical fork.
4. Open Magisk → DenyList → add the target app (`com.umain.fortress`).
5. Reboot.
6. Verify: "Play Integrity API Checker" app shows MEETS_DEVICE_INTEGRITY (sometimes STRONG).
7. Open the target app.
```

If the target accepts `MEETS_DEVICE_INTEGRITY` for sensitive operations, I'm in.

**Defender counter:**
- Require `MEETS_STRONG_INTEGRITY` for state-mutating ops. Reduces what I can do but doesn't
  refuse my session.
- For *signing in* and *reading state*, a Device verdict is a reasonable floor; the harm I
  can do without Strong is bounded by step-up gating.

### Bypass 2 — PIF module fingerprint spoofing

The PIF module works by injecting a fake `Build.FINGERPRINT` + `ro.build.fingerprint` that
matches a Pixel build whose signing keys Google has **not** revoked. The Play Integrity service
sees a "known good" Pixel fingerprint and grants `MEETS_DEVICE_INTEGRITY` (sometimes briefly
`MEETS_STRONG_INTEGRITY` until Google patches that specific fingerprint).

The chase: PIF maintainers track which Pixel fingerprints Google hasn't revoked yet and update
the module. Google revokes; the module updates again. Cycle is ~weeks.

**Defender counter:**
- Watch the **verdict.deviceIntegrity** distribution per app version. A new "Strong drop +
  Device rise" shape correlates with a new PIF release.
- Telemetry on `appIntegrity.certificateSha256Digest` — verify it matches *your* release cert.
  PIF can't fake this without resigning the APK.

### Bypass 3 — Decryption / proxy servers

Online services accept a target app's nonce, run the Play Integrity flow on **their** real,
attested device, and return the resulting token. The token is genuine — it's a Strong verdict
from a real Pixel — just not from the device asking for it.

Effective against any defence that doesn't bind the token to the requesting client.

**Defender counter:**
- Bind the integrity token to a `requestHash` derived from the device-binding public key and
  the action. The proxy can't generate a token for *my* hash because they don't have my key.
- Cert pinning + DPoP (proof of possession) on every request makes "swap in a stolen token"
  hard, because the request still has to be signed by the device the token was minted for.

### Bypass 4 — Leaked Google signing keys (rare)

A few times in history, OEM signing keys have leaked (Pixel 6, certain Samsung firmware). A
custom ROM signed with the leaked keys signals "Verified" boot state to Play Integrity.
Google revokes the keys; until the revocation propagates, the ROM passes Strong.

**Defender counter:**
- The revocation is the answer. Verify against the current JWKS / KeyAttestation trust roots,
  which excludes revoked keys.
- If you have visibility into specific compromised key fingerprints, deny-list them at the
  application layer too.

### Bypass 5 — `attestationVersion` downgrade

Old devices report a lower `attestationVersion` in the KeyAttestation chain. If your server
accepts low versions for sensitive ops, an attacker with a vulnerable old device gets a Strong
verdict from cooperative-but-weak hardware.

**Defender counter:**
- Set a minimum `attestationVersion` floor (e.g. ≥ 200, KeyMaster 4.0+) for sensitive ops.
- Older devices: refuse sensitive ops politely, point user at "upgrade your phone" docs.

### Bypass 6 — Bootloader-locked but custom-key ROM

Some OEMs allow users to lock the bootloader with their **own** signing key (e.g. via fastboot
flash key). Play Integrity then sees "bootloader locked + verified boot" and may grant Strong
on the first generation of these devices, before Google's logic learns to distinguish "OEM key"
from "user key".

**Defender counter:**
- KeyAttestation chain validation: the user-installed root key produces a chain that doesn't
  validate against Google's hardware roots. The server-side check catches it.
- Telemetry: a Pixel claiming "Locked + non-Google bootkey" is anomalous and should be flagged.

### Bypass 7 — Caching a Strong verdict and replaying

If your server caches verdicts for too long (say 24 hours), I get a clean Strong verdict at
midnight, then run my attacks the rest of the day under the cached blessing.

**Defender counter:**
- Cache window ≤ 5 min for sensitive ops, single-use binding for transfers.
- For read-only ops: longer caching is OK but always bind to the device.

### Bypass 8 — Race the freshness window with parallel requests

If the integrity check is per-session (not per-action), I fire 1 000 transfer requests in the
60 seconds my token is fresh, before the next check would catch me.

**Defender counter:**
- Per-sensitive-action token binding via `requestHash`. The token issued for "reveal account
  X" doesn't verify on "transfer €1000 to Y".
- Rate limit on sensitive endpoints — at the application layer, regardless of integrity.

---

## Cross-reference

- **The Play Integrity verdicts and server flow** → [05-play-integrity.md](05-play-integrity.md)
- **What replaces root detection in 2026** → [11-root-detection.md](11-root-detection.md)
- **Why device binding closes residual gaps** → [09-zero-trust.md](09-zero-trust.md)
- **Frida / RASP layer detecting in-process tamper** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **The emulator-rooting angle** → [15-emulator-rooting.md](15-emulator-rooting.md)

## References

- [How Attackers Bypass Play Integrity API in the Wild](https://medium.com/@vaibhav.shakya786/how-attackers-bypass-play-integrity-api-in-the-wild-f1091aea36e9)
- [Google Play Integrity Verdicts reference](https://developer.android.com/google/play/integrity/verdicts)
- [topjohnwu/Magisk DenyList](https://topjohnwu.github.io/Magisk/denylist.html)
- [Play Integrity Fix module (community fork)](https://github.com/chiteroman/PlayIntegrityFix)
