---
title: OWASP Mobile Top 10 for humans
slug: mobile-top-10
level: beginner
estimated_minutes: 25
status: published
company: Fortress
tags:
  - owasp
  - mobile-security
  - threat-modelling
  - fundamentals
summary: >
  The 2024 OWASP Mobile Top 10, rewritten in plain English with the smallest possible code
  example for each risk. You leave knowing which weaknesses an attacker scans for first and
  which controls every mobile app should ship by default.
references:
  - title: "OWASP Mobile Top 10 — official site"
    url: https://owasp.org/www-project-mobile-top-10/
  - title: "So Your Mobile App Is a Security Dumpster Fire (Jackson Mafra)"
    url: https://medium.com/@jacksonfdam/so-your-mobile-app-is-a-security-dumpster-fire-owasp-mobile-top-10-for-normal-humans-ddf1ae85f61d
  - title: "Mobile Security — Because Hackers Need Hobbies Too (Jackson Mafra)"
    url: https://medium.com/@jacksonfdam/mobile-security-because-hackers-need-hobbies-too-6b84b0ab52d8
---

## Welcome to the Mobile Top 10

Understand what the OWASP Mobile Top 10 is and why every Android engineer should be able to
recite it from memory.

OWASP keeps a ranked list of the most exploited weaknesses in mobile apps. The list refreshes
every few years; the 2024 edition is the current one. It exists because the same handful of
mistakes are what attackers actually find — pretty UI does not stop a binary patcher.

> **Why this matters.** The Top 10 is not theoretical. Every public penetration report in the
> last decade names at least one of these as the breach root cause. If your app fails three
> of them you are already in trouble.

---

## Step 1: M1 — Improper Credential Usage

Hardcoded API keys, tokens checked into Git, secrets baked into resource XML.

Open Android Studio and grep your own app for `"sk-"`, `"AIza"`, `"AKIA"` and `Bearer `. The
goal is not to be perfect — the goal is to find what is *already there* before someone with a
binary editor does. Move every leaked secret behind a server call or a build-time vault and
rotate the leaked value.

```kotlin
// Bad — the entire APK is now a leak waiting to happen.
private const val ANALYTICS_KEY = "AKIA1234567890ABCDEF"

// Better — fetched once at sign-in, rotated server-side, never persisted.
class AnalyticsKeyProvider(private val api: ConfigApi) {
  suspend fun current(): String = api.config().analyticsKey
}
```

> **Why this matters.** APKs are zip files. `apktool d app.apk` plus `grep` is a fifteen
> second attack with a hundred percent success rate on hardcoded secrets.

---

## Step 2: M2 — Inadequate Supply Chain Security

Untrusted libraries, malicious SDKs, build tools you never audited.

Every dependency you add gets the same runtime privileges as your app. Pin versions, enable
Gradle dependency verification, and review what each library actually does on startup. The
2018 event-stream incident shipped a coin-stealer to millions of apps because one transitive
dep flipped maintainers.

```kotlin
// gradle/verification-metadata.xml — opt in to dependency verification.
// ./gradlew --write-verification-metadata sha256
```

> **Why this matters.** A supply chain compromise gives the attacker everything you have.
> They run as you, signed as you, with all of your permissions.

---

## Step 3: M3 — Insecure Authentication / Authorisation

Client-decided permissions, JWTs verified only on the device, "is admin" booleans the user
can flip in shared preferences.

Authorisation lives on the server. Always. The client may *display* a different UI for an
admin, but every protected action must be re-checked at the API. The Fortress demo backend
re-validates the `sub` claim on every `/me/*` endpoint for exactly this reason.

```kotlin
// Bad — local flag, attacker flips it.
val isAdmin = sharedPrefs.getBoolean("is_admin", false)

// Good — claim comes from a JWT the server signs and verifies; client only displays.
val isAdminFromToken: Boolean
  get() = decodedAccessToken.claims["role"] == "admin"
```

> **Why this matters.** If the client decides what is allowed, the attacker decides what is
> allowed.

---

## Step 4: M4 — Insufficient Input/Output Validation

WebViews that load untrusted HTML, deep links that pass raw path segments to a SQL `LIKE`,
intents that dispatch on a string from another app.

Treat every input from outside the app boundary as hostile, including intents. If you let
`Intent.getStringExtra("path")` flow into a `File(...)`, you just shipped a path-traversal
primitive. Use a content provider with a fixed set of `Uri` matches and validate inputs
against an allow-list, not a deny-list.

```kotlin
// Bad
val file = File(intent.getStringExtra("path")!!) // attacker passes "../../shared_prefs/auth.xml"

// Good
val allowed = setOf("invoice", "statement", "receipt")
val kind = intent.getStringExtra("kind")
require(kind in allowed) { "rejecting unknown kind=$kind" }
```

> **Why this matters.** Path traversal, SQL injection, JavaScript-bridge injection and
> deep-link abuse all live here. Most of them are one-line fixes once you find them.

---

## Step 5: M5 — Insecure Communication

Plain HTTP, missing certificate pinning, accepting any TLS certificate the OS trusts.

By default, an Android app trusts every certificate the device trusts. That includes any CA
a corporate MDM or a custom ROM installs. Pin to your real keys; in 2026 that means hashing
the **subject public key**, not the leaf certificate.

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
  <domain-includeSubdomains="true">
    <domain>api.fortress.dev</domain>
    <pin-set expiration="2026-12-31">
      <pin digest="SHA-256">7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y=</pin>
    </pin-set>
  </domain-includeSubdomains>
</network-security-config>
```

> **Why this matters.** A man-in-the-middle proxy turns your app inside out. Cert pinning
> removes nine out of ten common interception attacks.

---

## Step 6: M6 — Inadequate Privacy Controls

Logging PII to logcat, persisting raw documents in `MediaStore.Downloads`, screenshots of
sensitive screens.

Add `FLAG_SECURE` to anything that shows balances, identity documents or step-up screens.
Strip PII from logs in production builds. The privacy story matters legally too — GDPR fines
have hit nine figures for far less.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                  WindowManager.LayoutParams.FLAG_SECURE)
}
```

> **Why this matters.** The screenshot in a phishing victim's photo gallery is the same data
> that took weeks of engineering to lock down server-side.

---

## Step 7: M7 — Insufficient Binary Protections

No code obfuscation, no integrity check, no anti-debug, no anti-frida. Every Java method
name still readable in `jadx`.

Enable R8 with full mode for release builds. Add Play Integrity for at-rest device assurance.
Layer in lightweight runtime application self-protection (RASP) probes for debugger, Frida
and root indicators. You will not stop a determined reverse engineer — you will raise their
cost from minutes to days.

```kotlin
// build.gradle.kts (app)
buildTypes {
  release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  }
}
```

> **Why this matters.** Defence in depth. Each layer the attacker has to peel is another
> hour they spend somewhere other than your codebase.

---

## Step 8: M8 — Security Misconfiguration

Exported activities you forgot, debug flags shipped to production, file providers granting
URI permissions to the world.

Audit `AndroidManifest.xml` line by line. `android:exported="true"` should only ever appear
on intent-filter activities. Remove `android:debuggable` from production. Constrain
`grantUriPermissions` and signature-protect content providers.

```xml
<provider
  android:name=".attachments.AttachmentProvider"
  android:authorities="${applicationId}.attachments"
  android:exported="false"
  android:grantUriPermissions="true">
  <meta-data
    android:name="android.support.FILE_PROVIDER_PATHS"
    android:resource="@xml/file_paths" />
</provider>
```

> **Why this matters.** Most "real" mobile breaches start with a misconfigured manifest, not
> a zero-day. Read your own manifest carefully.

---

## Step 9: M9 — Insecure Data Storage

Plain SharedPreferences, room databases without encryption, files dropped in
`getExternalFilesDir()` that the OS will happily back up.

Anything that names a token, a session ID, a refresh token, a PAN or biometric template must
live behind the Android Keystore. EncryptedSharedPreferences is acceptable for low-stakes
data; for high stakes use an AES-256-GCM key bound to user authentication. See the
Fortress hardware-vault codelab for the long form.

```kotlin
// Use EncryptedSharedPreferences from androidx.security.crypto
val masterKey = MasterKey.Builder(context)
  .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
  .build()
val prefs = EncryptedSharedPreferences.create(
  context, "fortress-prefs", masterKey,
  EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
  EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)
```

> **Why this matters.** An at-rest token leak compromises every account in your backend, not
> just the one phone that lost it.

---

## Step 10: M10 — Insufficient Cryptography

Custom crypto, MD5 for passwords, electronic codebook mode for anything, IVs reused across
messages, keys generated with `Random`.

Use the platform's vetted primitives. AES-256-GCM for symmetric encryption. ECDSA P-256 or
Ed25519 for signatures. Argon2id for passwords. Never invent or "improve" cryptographic
constructions. The Tink library wraps all of this with sane defaults; reach for it before
you write a single `Cipher.getInstance` call.

```kotlin
// Tink AEAD — the right way to encrypt arbitrary bytes.
AeadConfig.register()
val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
val aead = handle.getPrimitive(Aead::class.java)
val ciphertext = aead.encrypt("hello".toByteArray(), associatedData)
```

> **Why this matters.** Bad crypto is worse than no crypto — it tells users they are safe
> when they are not. Use libraries that experts maintain.

---

## Wrap-Up

You can now name every entry in the OWASP Mobile Top 10 and identify which the codebase you
left this morning would fail.

Next mission: deepen one of these into a real defence layer. The
[Device Attestation 101](/codelabs/device-attestation-101) codelab is a natural follow-up if
M3 or M7 stood out to you.

**Recap of what you just internalised:**

- M1 leaks live in your APK; grep your own build first.
- Authorisation is a server problem. Always.
- Pin to subject public keys, not certificates.
- R8 + Play Integrity + a RASP probe is the minimum binary-protection ladder.
- Tink and the Android Keystore exist so you do not have to write crypto by hand.
