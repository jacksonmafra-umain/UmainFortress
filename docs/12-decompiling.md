# 12 — APK Decompiling: The Dark Art

> "Your APK is a public document the moment Play Store hands it to a device. Everything inside
> it — Kotlin classes, resources, native libraries, your dev's nervous code comments — is one
> `unzip` and one `jadx-gui` away from being read aloud at a conference." — *Fortress field notes*

**TL;DR** — Treat your APK as **public**. Every constant, every endpoint, every detection
heuristic ends up in someone's IDE. The defender's job is to (a) **shrink** what's there
(R8 minification + resource shrinking), (b) **obfuscate** what survives (R8's name mangling,
selectively applied), and (c) **layer** behind it — because no obfuscation makes the static
analysis hard enough to matter against a motivated attacker. The real gates live server-side.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Make the static analysis slow enough to push attackers toward easier targets | Read the app's mind to find unprotected gates |
| **Key idea** | Everything in the APK is public; the obfuscation just buys time | Decompile, search for `Auth`, `verify`, `pin` — start there |
| **Worst failure** | Trusting that R8 obfuscation "hides" anything | Disabled R8 + plaintext keys in `BuildConfig` |

---

## 🛡️ Defender — "I shrink, I obfuscate, I trust nothing of mine"

### What's actually in your APK

```
app-release.apk
├─ AndroidManifest.xml         ← decompiled to plain XML
├─ classes.dex                 ← Kotlin/Java bytecode (jadx → ~Kotlin source)
├─ classes2.dex, …             ← multidex
├─ resources.arsc              ← string resources
├─ res/                        ← XML layouts, drawables
├─ lib/{arm64-v8a,…}/*.so      ← native libs (your NDK code + transitive deps)
├─ assets/                     ← unencrypted, anyone can read
└─ META-INF/                   ← signature, certificates
```

The attacker runs `apktool d app-release.apk` and gets a working source tree in 30 seconds. If
your code does anything *expressible* — checks a string, reads a property, calls an API — the
attacker is reading the same code you wrote.

### R8 — what it does and what it doesn't

R8 (Android's default since AGP 3.4) does three things on `release` builds:

1. **Minification** — strips unused classes, methods, fields. Reduces APK size 30-50%.
2. **Obfuscation** — renames classes/methods/fields to `a, b, c, …` to make decompiled code
   harder to read.
3. **Optimisation** — inlines small methods, constant-folds, dead-code-eliminates.

What R8 **does not** do:

- It doesn't *hide* logic. The renamed `a.b(c)` is just as functional as `AuthRepository.login(email)`.
- It doesn't obfuscate strings (unless you add a string-encryption pass like StringObfuscator
  manually). Endpoints, key aliases, error messages — all readable.
- It doesn't change semantic behaviour. A determined reverse engineer follows the data flow.

For Fortress the goal is: **make `jadx-gui` take 30 minutes to find the security gate instead of
30 seconds**. The thirty minutes is enough time for the attacker to either give up or be caught
by other layers (Play Integrity ratcheting, RASP signalling, fraud telemetry).

### A reasonable R8 config

```
# proguard-rules.pro

# Keep entry points (Application, MainActivity) so the manifest still resolves them.
-keep class com.umain.fortress.FortressApplication { *; }
-keep class com.umain.fortress.MainActivity { *; }

# kotlinx.serialization needs reflection on the generated descriptors.
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,allowobfuscation class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor uses reflection in places; keep the engine factories.
-keep class io.ktor.client.engine.** { *; }

# DTOs are serialized — names matter on the wire.
-keep class com.umain.fortress.network.dto.** { *; }

# Don't strip code coverage of debug logs in release if you ever need stack traces.
# Comment this in only if you specifically need fully obfuscated stack traces from Crashlytics.
# -dontobfuscate
```

The Fortress demo APK is a debug build — no minification. **Production release** would enable
R8 in [`app/build.gradle.kts`](../app/build.gradle.kts):

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

### Don't put secrets in `BuildConfig` thinking it's safe

A common mistake: `BuildConfig.API_KEY = "sk-fortress-prod-abc123…"`. R8 will not obfuscate
this string. `jadx-gui` shows it in the first second.

Fortress's `BuildConfig.BASE_URL` is intentionally **not** a secret — it's the public URL
clients hit. The signing-key material lives in the KMS server-side; the device-binding private
key lives in the TEE. No constants in the APK are secret.

### Mapping files: friend and enemy

R8 produces a `mapping.txt` mapping `a.b.C` back to `com.umain.fortress.NetworkLayer`. Keep
this mapping for:

- **De-obfuscating stack traces** in Crashlytics / Sentry. Without it, every crash report is
  noise.
- **Bug bounty / triage** — when a researcher reports a vulnerability, you need to find the
  code they're talking about.

Keep it **out** of:

- Public CI artifacts.
- The APK itself.
- Anywhere accessible to an attacker.

Upload `mapping.txt` to Play Console (it auto-deobfuscates Crashlytics) and store a copy in
your secrets manager. Tag mappings with the build's version code so you can de-obfuscate a
two-year-old stack trace.

### What an attacker harvests, and what to do about each

| Found in APK | Defender response |
|---|---|
| API endpoints in code | These are public; HTTPS + cert pinning is what protects them, not secrecy. |
| Key aliases ("fortress.vault.tokens") | These are non-sensitive; the *keys* are in the TEE. |
| Cert pin SPKI hashes | Should be in code; this is by design. |
| Integrity check thresholds ("score < 30 allow") | Move thresholds server-side. The client just renders the verdict. |
| Risk scoring math | Same — server-side. Client never knows the policy. |
| Debug logs / asserts | Strip in release. R8 with `Timber` strips `Timber.d` calls when configured. |
| Test credentials in resources | Audit your `BuildConfig`; never ship dev creds. |
| Backend admin endpoints | The mobile app should not know about admin routes. Period. |

### App signing — the v1/v2/v3/v4 story

| Scheme | What it signs | Threat it stops |
|---|---|---|
| v1 (JAR signing) | Each entry, by file path | Modifying one file in the APK |
| v2 (whole-file APK signing block) | The entire APK contents | Tampering anywhere |
| v3 (key rotation support) | Same as v2 + a lineage proof | Rotating your signing key without breaking existing installs |
| v4 (incremental file signing) | Per-block hashes | Faster install for streaming installs (Android 11+) |

Always sign with **v2 + v3** at minimum. v3's key lineage means you can rotate your signing key
in the future without losing the ability to ship updates to existing users.

For Play Store: enroll in **Play App Signing** so Google holds the canonical signing key. Your
upload key is per-developer; Google signs the final binary. Then your local signing key never
needs to be the long-term anchor.

### Detecting tampering at runtime — the layer R8 can't provide

```kotlin
fun isOurSignature(context: Context): Boolean {
    val signers = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        .signingInfo
        ?.apkContentsSigners
        ?: return false
    val expected = "AB:CD:EF:…(Play Console SHA-256)"
    val actual = signers.first().toByteArray().let { sha256(it).toHex() }
    return actual.equals(expected, ignoreCase = true)
}
```

Call this on app launch; if it returns false, refuse to start the network layer. Combine with
Play Integrity's `appIntegrity.certificateSha256Digest` for the server-side equivalent.

### Native libraries

If you ship a `.so` (NDK code), it's still in the APK and a determined attacker can run
`objdump` / `Ghidra` / `IDA Pro` on it. Native isn't a magic obfuscator — it's a different
language that takes longer to read but is no more secret. Don't put logic there expecting it
to be hidden; do put logic there if you have a *performance* reason and you want a slightly
higher reverse-engineering cost as a side effect.

---

## ⚔️ Attacker — "I read your mind in five steps"

### Bypass 1 — `apktool d app-release.apk`

```
$ apktool d app-release.apk -o fortress/
I: Using Apktool 2.10.0 …
I: Loading resource table …
I: Decoding AndroidManifest.xml …
I: Loading resource table from file: /apktool/framework/1.apk
I: Decoding file-resources …
I: Decoding values */* XMLs …
I: Baksmaling classes.dex …
I: Baksmaling classes2.dex …
I: Copying assets and libs …
$ ls fortress/smali/
com/  io/  kotlin/  androidx/  …
```

Now I have smali — readable Dalvik assembly. Search for `BiometricPrompt`, `cipher`,
`Authorization` to find the security paths.

### Bypass 2 — `jadx-gui` for higher-level reading

```
$ jadx-gui app-release.apk
```

The classes panel shows the de-obfuscated tree as recovered Java. Even with R8 obfuscation, the
*shape* of the code is preserved. I search for the strings I know must exist: HTTP paths,
prompt titles, deny-list reasons. Each search lands me in the relevant code.

For Kotlin specifically, `jadx` decompiles to readable Java that re-mimics Kotlin's coroutine
state machines fairly well — you can follow the `suspend` flow.

### Bypass 3 — Static-string grep

```
$ jadx --output-dir src app-release.apk
$ grep -r "rooted\|magisk\|integrity\|isJailbroken\|BiometricPrompt" src/
src/.../security/RootDetector.java:    boolean rooted = checkSu();
src/.../auth/StepUpAuthenticator.java:    BiometricPrompt prompt = …
```

Within 30 seconds of decompiling I have the locations of every security check the app
performs locally. From there I either:

- **Patch the check** with smali edits + apktool b + sign (see Bypass 5).
- **Hook the function** with Frida (see [14-rasp-strategies.md](14-rasp-strategies.md)).

### Bypass 4 — Resource harvesting

`strings.xml`, drawable filenames, layout XML — all unobfuscated. Often reveals:

- Internal feature flags not stripped in release.
- Test mode strings ("DEMO BUILD — DO NOT SHIP").
- Hidden admin URL fragments (you'd hope not, but).

```
$ grep -r "demo\|test\|admin\|debug" fortress/res/values/
```

### Bypass 5 — Patch + repackage + resign

```
$ # Patch the smali
$ sed -i 's/iput-boolean v0, p0, .*->rooted:Z/iput-boolean v1, p0, …->rooted:Z/' \
  fortress/smali/com/umain/fortress/security/RootDetector.smali

$ # Rebuild
$ apktool b fortress -o patched.apk

$ # Sign with my own key
$ keytool -genkey -keystore my.keystore -alias attacker -keyalg RSA -keysize 2048 -validity 365
$ apksigner sign --ks my.keystore --ks-key-alias attacker patched.apk

$ adb install -r patched.apk
```

The app installs, runs, and never sees its own root check fail. **From my perspective the app
is patched in five minutes.**

**Defender counters:**
- Server-side integrity (Play Integrity verifies the certificate digest — my patched APK's
  signature mismatch is detected).
- Self-signature check at runtime (per the "Detecting tampering at runtime" snippet above).
- Anti-tampering library combining: signature check, code-integrity check, `installer
  package` check (only `com.android.vending` should have installed your app), Frida detection.

### Bypass 6 — Mapping leak

If your `mapping.txt` ends up on a public CI artifact, GitHub release page, or accidentally
shipped in the APK's `assets/`, all R8 obfuscation work is undone. I get the real class names.

**Defender counter:** treat `mapping.txt` like a credential. Don't put it where your build's
artifacts are public.

### Bypass 7 — Reading native libs

```
$ readelf -a lib/arm64-v8a/libnative-auth.so
$ objdump -dM intel lib/arm64-v8a/libnative-auth.so | less
```

Ghidra / IDA Pro decompile native libs back to C-ish source. Slower than Kotlin decompile but
not magic.

**Defender counter:** don't expect native to hide logic. If you really need higher cost,
look at commercial obfuscators (DexGuard, Promon Shield) — they raise the price meaningfully
for one to two release cycles, then bypasses circulate.

### Bypass 8 — `strings(1)` for the lazy

```
$ strings app-release.apk | grep -iE "api[._-]?key|secret|token|password"
```

Catches everything from accidentally-shipped `AWS_SECRET` to internal-comment leftover
("TODO REMOVE BEFORE PROD"). 30 seconds, no decompile needed.

**Defender counter:** lint-check release builds in CI for suspicious string patterns. Fail the
build, not the demo.

### Bypass 9 — Diff against the public source

If your app open-sources a *portion* of itself (an SDK you maintain, a Compose-extras module),
I can diff against the open source to find the proprietary bits — those are where your
business logic lives, and that's what I want.

**Defender counter:** open-source the *capabilities*, not the *application code*. A reusable
network library is fine to publish; the actual auth flow in your app isn't.

---

## Cross-reference

- **Server-side gates that survive APK patching** → [01-stateless-auth.md](01-stateless-auth.md), [05-play-integrity.md](05-play-integrity.md), [09-zero-trust.md](09-zero-trust.md)
- **Runtime anti-hooking / anti-tampering** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **What the attacker also wants from the wire** → [08-network-warfare.md](08-network-warfare.md)
- **Root + hooks at the process level** → [11-root-detection.md](11-root-detection.md)
- **Content providers as a side-channel into the APK** → [16-content-providers.md](16-content-providers.md)

## References

- [From APK to Source Code: The Dark Art of App Decompiling (2025)](https://medium.com/@vaibhav.shakya786/from-apk-to-source-code-the-dark-art-of-app-decompiling-explained-2025-edition-7f28fc2dee0f)
- [jadx — Dex to Java decompiler](https://github.com/skylot/jadx)
- [Apktool](https://apktool.org/)
- [Android Developers — R8 shrink, obfuscate and optimize](https://developer.android.com/build/shrink-code)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [APK Signature Scheme v2/v3/v4](https://source.android.com/docs/security/features/apksigning)
