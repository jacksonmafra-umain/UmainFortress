---
title: "Fingerprinting Android devices"
slug: fingerprinting-android-devices
level: intermediate
estimated_minutes: 25
status: published
company: Fortress
tags:
  - fingerprinting
  - device-identity
  - fraud
  - privacy
summary: >
  Build a stable Android device fingerprint without permanent hardware identifiers —
  combining hardware/system/install signals into a probabilistic identity that survives
  reinstall and partial OS resets, while staying inside what privacy law allows.
references:
  - title: "Fingerprinting Android Devices (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/fingerprinting-android-devices-like-csi-but-for-your-app-e99a1aeff248
  - title: "Android developer policy — best practices for unique identifiers"
    url: https://developer.android.com/training/articles/user-data-ids
  - title: "Play Console policy — User Data: Identifiers"
    url: https://support.google.com/googleplay/android-developer/answer/10144311
---

## Welcome to fingerprinting

Understand what you can and cannot rely on for "is this the same device" — and how to
combine the legal signals into something useful for fraud detection.

The pre-Android-10 way to identify devices was simple: `IMEI`, `MAC`, `Serial`. All three
are restricted now, and reading any of them on modern Android either fails outright or
triggers a Play Store policy violation. The replacement is a *combination* of weaker
signals that, together, identifies a device with enough probability to drive fraud
decisions while staying within policy.

> **Why this matters.** Stop one fraudster moving across 1000 devices in your install
> base, and you stop a category of attack. Without device-stable identity, fraud loops
> on the same accounts indefinitely.

---

## Step 1: The four categories of signal

Rank them by stability and intrusiveness:

1. **Persistent app-instance identifiers.** `Installations.getId()` (Firebase), or a UUID
   you generate on first run and store in the Keystore. Stable until uninstall.
2. **Hardware / system stable.** `Build.HARDWARE`, `Build.BOARD`, `Build.MANUFACTURER`,
   `Build.MODEL`, ABI list, screen metrics. Stable across reinstalls; identical across
   millions of devices of the same model.
3. **System config.** Default locale, timezone, font scale, currency. Stable but not
   unique.
4. **Probabilistic.** Sensor calibration, audio latency, kernel boot ID. Unique but
   noisier — different boot, different value.

The right fingerprint is a *function* of category 2 + category 3 + (optionally) category
1, hashed and salted with a per-user secret.

> **Why this matters.** Any single category alone is either trivially spoofable or
> trivially reset. The combination is what carries the signal.

---

## Step 2: The legal floor — what Play Store allows

Two rules from the Play Console policy on user data identifiers:

1. **Do not associate a non-resettable hardware identifier with personal information.**
   `IMEI`, `Build.SERIAL`, `MAC` are restricted. `ANDROID_ID` is *resettable* in the
   sense that factory reset changes it.
2. **For ads or analytics use the advertising ID.** It is user-resettable and
   user-deletable. Use it only for the purposes the user consented to.

Fingerprinting for fraud detection is *permitted* under "security and fraud prevention",
but the fingerprint must not be associated with PII outside of that purpose, and you
must declare it in your data-safety section.

> **Why this matters.** A fingerprint that lands you a Play Store policy strike is
> worse than no fingerprint. Stay on the right side of the policy.

---

## Step 3: The app-instance identifier (category 1)

Generate a UUID on first run. Persist it behind the Keystore-encrypted token store so it
survives a kill-and-restart but not an uninstall.

```kotlin
class DeviceIdProvider(
  private val store: TokenStore,
) {
  suspend fun current(): String {
    val existing = store.loadDeviceId()
    if (existing != null) return existing
    val fresh = UUID.randomUUID().toString()
    store.saveDeviceId(fresh)
    return fresh
  }
}
```

This is what your server uses as the *primary* device id. Uninstall + reinstall produces
a new id — that is the right behaviour for the primary id.

> **Why this matters.** The primary device id is the simple, honest identifier. The
> fingerprint is what catches re-installation deliberately attempted to defeat it.

---

## Step 4: The hardware fingerprint (category 2)

Concatenate a small set of stable `Build` fields, hash. The output is identical for two
devices of the same model — that is acceptable. The fingerprint's job is to *cluster*,
not to *individuate*.

```kotlin
fun hardwareFingerprint(): String {
  val parts = listOf(
    Build.MANUFACTURER,
    Build.MODEL,
    Build.BRAND,
    Build.HARDWARE,
    Build.BOARD,
    Build.SUPPORTED_ABIS.joinToString(","),
  )
  val raw = parts.joinToString("|").lowercase()
  return sha256Hex(raw).take(16)
}
```

Two Pixel 7s in the same configuration share this fingerprint. That is fine — when
fraud signals concentrate on one fingerprint cluster, you investigate, you do not
auto-ban.

> **Why this matters.** Stable across reinstalls is the property that catches fraudsters
> who reset to evade a ban.

---

## Step 5: The system-config fingerprint (category 3)

Locale, timezone, font scale, screen density, color modes. Each individually has low
entropy; combined they narrow the cluster.

```kotlin
fun systemFingerprint(context: Context): String {
  val cfg = context.resources.configuration
  val parts = listOf(
    cfg.locales.toLanguageTags(),
    java.util.TimeZone.getDefault().id,
    cfg.fontScale.toString(),
    context.resources.displayMetrics.run { "${widthPixels}x${heightPixels}@${densityDpi}" },
    cfg.uiMode.toString(),
  )
  return sha256Hex(parts.joinToString("|")).take(16)
}
```

System-config does drift (user changes timezone on travel). Treat the change as a signal
to investigate (sudden timezone shift on an account is interesting), not to auto-fail.

> **Why this matters.** Most fraudsters use VPNs to spoof IP. Spoofing the on-device
> timezone is one more step they often skip.

---

## Step 6: The composite fingerprint, salted per user

Combine the three identifiers and salt with a per-user secret so the same device shows
up as a *different* fingerprint for different users. That preserves cross-account fraud
correlation server-side while not creating a globally-trackable identifier.

```kotlin
fun compositeFingerprint(userId: String, context: Context, deviceId: String): String {
  val raw = listOf(deviceId, hardwareFingerprint(), systemFingerprint(context)).joinToString("|")
  return hmacSha256Hex(key = userSalt(userId), data = raw).take(32)
}
```

Server stores the composite. Cross-account correlation queries operate on the *raw*
hardware fingerprint, not the composite — that limits exposure if the composite leaks.

> **Why this matters.** Salting is the privacy-vs-security compromise. You get the
> fraud signal without creating a tracking primitive.

---

## Step 7: Sensor-based supplements (category 4) — use sparingly

Sensor calibration is unique to a given device but noisy. Useful only as a *boost*
signal alongside the deterministic categories.

```kotlin
suspend fun gyroscopeBaseline(sensorManager: SensorManager): FloatArray? = withTimeoutOrNull(500) {
  val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return@withTimeoutOrNull null
  val samples = mutableListOf<FloatArray>()
  val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) { samples += event.values.copyOf() }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
  }
  sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_NORMAL)
  delay(300)
  sensorManager.unregisterListener(listener)
  if (samples.isEmpty()) null else FloatArray(3) { i -> samples.map { it[i] }.average().toFloat() }
}
```

Aggregate to a coarse-grained "calibration class" rather than the raw values. The raw
values change every boot; the class is stable.

> **Why this matters.** Sensor signals are battery-expensive and privacy-sensitive. Use
> them only when the deterministic categories are inconclusive.

---

## Step 8: How the server uses the fingerprint

Three patterns:

1. **Bind a token to the fingerprint.** A refresh token presented with a fingerprint
   different from the one at issuance is suspicious. Step-up or refuse.
2. **Cluster accounts.** Multiple accounts on the same hardware fingerprint cluster is a
   ring-signal. Surface for fraud review.
3. **Recover from device loss.** A user signing in from a new device with the *same*
   `Build`-based fingerprint as their previous (lost) device gets a smoother recovery
   experience.

Each pattern uses a different slice of the same data. Store both the composite and the
raw hardware fingerprint on the server, indexed separately.

> **Why this matters.** A fingerprint without server-side policy is a wasted signal.

---

## Step 9: When fingerprints fail (and they do)

Three failure modes you must plan for:

- **Two devices share a fingerprint.** Identical model, identical config. Cluster, do
  not deny.
- **Fingerprint changes for legitimate reasons.** OS update bumps `Build.HARDWARE`,
  user enables a custom font. Drift, do not lock out.
- **Fraudster runs an emulator that spoofs every field.** Cuttlefish + Magisk + a config
  generator. Fingerprint passes; integrity verdict fails. Use the *combination*.

The fingerprint is one signal in a stack. Treat it as such.

> **Why this matters.** Fingerprinting marketed as "unique device identity" loses you
> the trust to keep using it. Marketed as "fraud signal", it scales.

---

## Step 10: Document the data flow

Privacy regulators do read your in-app "What we collect" copy. List the fingerprint
inputs in plain language.

> **Stored on this device**
>
> - Encrypted device id (random UUID, regenerated on reinstall)
>
> **Sent to the server, salted with your account**
>
> - Composite fingerprint computed from device model, hardware identifiers (HARDWARE,
>   BOARD, MANUFACTURER), system configuration (locale, timezone, screen size)
>
> **Used for**
>
> - Fraud detection — recognising your device when you sign in, spotting suspicious
>   re-installations. Not used for advertising, not shared with third parties.

The transparency converts the same fingerprint into a competitive advantage. Users
notice.

> **Why this matters.** A fingerprint your users do not understand is a fingerprint the
> regulator will eventually take from you.

---

## Wrap-Up

You now have a fingerprint shape that respects the Play Store policy, salts with a
per-user secret, combines deterministic + probabilistic signals, and ships with a
server-side use plan and a transparent user-facing description.

Next mission:
- [Device Attestation 101](/codelabs/device-attestation-101) — the deterministic
  counterpart that pairs with this probabilistic signal.
- [Bulletproof Security](/codelabs/bulletproof-security) (draft) — combining both into
  a single policy primitive.
- [Privacy vs Security](/codelabs/privacy-vs-security) — the framework that holds this
  fingerprint inside the privacy gradient.

**Recap of the fingerprint stack:**

- Four signal categories: app-instance, hardware, system config, sensor-based.
- A per-user salt so the composite is not a global tracking ID.
- Three server-side uses: token binding, account clustering, device-loss recovery.
- An honest failure-mode account of where fingerprints break.
- A user-facing "What we collect" entry that turns the fingerprint into a trust artefact.
