# 16 — Exploiting Content Providers

> "A `ContentProvider` is a publicly-callable database with no front desk. The defender's job
> is to remember it exists at all." — *Fortress field notes*

**TL;DR** — `ContentProvider` is Android's IPC mechanism for sharing structured data between
apps. It's also the most over-exposed IPC surface in the ecosystem: defaults bias toward
"exported" until SDK 17, and even after that, well-intentioned `<grant-uri-permission>` blocks
and `openFile`-style paths leak. The attacker's playbook is to enumerate exported providers,
probe for SQL injection in `query`, path traversal in `openFile`, and authority confusion in
`grant-uri-permission`. The defender's response: **don't export**, and if you must, **validate
every argument as if it were a remote API request — because it is**.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Treat every exported provider call as untrusted input | Enumerate providers, find the one without an auth permission, query it |
| **Key idea** | `android:exported="false"` by default; permissions on the rest | If `pm dump` reveals it and `content query` returns rows, it's mine |
| **Worst failure** | An exported provider with concatenated SQL in its WHERE clause | An exported provider that opens user files via path traversal |

---

## 🛡️ Defender — "If I don't need to export it, I don't"

### The default has improved, but is still load-bearing

Since Android 4.2 (API 17), `android:exported` defaults to **false** when the provider has no
intent filter. Older code, third-party libraries, and copy-pasted SDK integrations frequently
ignore this and set `android:exported="true"` "just in case", leaving the door open.

```xml
<!-- Bad — every app on the device can call this -->
<provider
    android:name=".SettingsProvider"
    android:authorities="com.umain.fortress.settings"
    android:exported="true" />

<!-- Better — only callers holding our custom permission -->
<provider
    android:name=".SettingsProvider"
    android:authorities="com.umain.fortress.settings"
    android:exported="true"
    android:permission="com.umain.fortress.permission.READ_SETTINGS" />

<!-- Best — not exported at all (the only sane default for app-private state) -->
<provider
    android:name=".SettingsProvider"
    android:authorities="com.umain.fortress.settings"
    android:exported="false" />
```

Fortress's manifest exports **zero** providers. The session state lives in DataStore (private
to the app sandbox), the encrypted token vault lives in the Keystore (kernel-mediated). There
is no IPC surface for an attacker to enumerate.

### If you must export, validate as if it's a public API

```kotlin
class SettingsProvider : ContentProvider() {

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        // 1. Match the URI exactly to a known table — refuse anything else.
        val table = MATCHER.match(uri)
        if (table == UriMatcher.NO_MATCH) return null

        // 2. Refuse caller-supplied selections; use a fixed projection / WHERE.
        //    If the caller can vary the WHERE clause, you have SQL injection by design.
        val fixedSelection = when (table) {
            TABLE_USER_PROFILE -> "user_id = ?"
            else -> return null
        }

        // 3. Bound the projection — never echo the caller's column list.
        val safeProjection = ALLOWED_USER_PROFILE_COLUMNS

        // 4. Authorise. If we used a permission, we have UID-level auth via the platform;
        //    add finer-grained checks for sensitive rows.
        val callingUid = Binder.getCallingUid()
        if (!isAllowedToReadProfile(callingUid)) return null

        return db.query(USER_PROFILE_TABLE, safeProjection, fixedSelection,
            selectionArgs?.take(1)?.toTypedArray(), null, null, sortOrder)
    }
}
```

The three things that go wrong otherwise:

1. **`selection`/`selectionArgs` from the caller** get string-concatenated into the WHERE
   clause. SQL injection. Always use `?` placeholders, and **don't let the caller dictate the
   shape of the WHERE clause** — predefine it.
2. **`projection` from the caller** leaks rows the caller wasn't supposed to see. Always
   project to a fixed allowlist.
3. **`uri` lookups** without a `UriMatcher` accept any path the caller passes. `Uri` parsing
   has historically had surprises with `..` segments, double-encoded chars, and trailing slashes.

### `openFile` and the path-traversal trap

```kotlin
// DANGEROUS
override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val name = uri.lastPathSegment  // attacker controls this
    val file = File(context!!.filesDir, name)
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
}

// If the attacker passes a URI ending in "../../../../data/data/com.umain.fortress/databases/main.db",
// the resulting File is YOUR private database. The Provider has just exfiltrated it.
```

The defender's mitigation:

```kotlin
override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val expectedBase = File(context!!.filesDir, "public_exports").canonicalFile
    val requestedRelative = uri.lastPathSegment ?: return null
    val resolved = File(expectedBase, requestedRelative).canonicalFile
    // The canonical resolved path must still be a *child* of expectedBase.
    if (!resolved.path.startsWith(expectedBase.path + File.separator)) return null
    return ParcelFileDescriptor.open(resolved, ParcelFileDescriptor.parseMode(mode))
}
```

`canonicalFile` resolves `..` and symlinks. The startsWith check confirms the result is still
inside the expected directory.

### Permission models

Three layers, weakest to strongest:

1. **`android:exported="true"` only** — every app on the device can call.
2. **`android:permission="…"`** — only apps with the declared permission. Granular but still
   coarse (one permission for the whole provider).
3. **Per-row / per-URI permissions** via `<grant-uri-permission>` and `FLAG_GRANT_READ_URI_PERMISSION`
   — caller-app gets temporary, scoped access (the model FileProvider uses).

For Fortress's hypothetical "share a transaction as a PDF" feature: never expose the file via
an exported provider. Use **FileProvider** + `FLAG_GRANT_READ_URI_PERMISSION` to grant a
specific URI to a specific other app for a single intent, then revoke.

### Discovering your own exposure

```bash
# Every provider exported by every app on the device.
adb shell pm list packages -f | cut -d= -f2 | while read pkg; do
  adb shell dumpsys package "$pkg" | grep -A 1 'Provider'
done

# Or for a single app:
adb shell dumpsys package com.umain.fortress | grep -A 6 'Provider:'
```

Run this against your own release builds as a CI check. If a provider you didn't intend to
export shows up — fail the build.

### The path-traversal grep

```bash
# In CI, fail if openFile takes uri.lastPathSegment without canonicalisation.
grep -nR --include='*.kt' 'uri.lastPathSegment' app/src/ \
  | grep -iv 'canonicalFile\|startsWith'
```

Crude but catches the most common shape of the bug.

### Logging IPC entry points

Log every `query`, `insert`, `update`, `delete`, `openFile` call with `Binder.getCallingUid()`,
the URI, and the count of returned rows. In production, the logs go to your analytics; an
anomalous spike of provider hits from a single UID is fraud signal.

---

## ⚔️ Attacker — "I enumerate, I probe, I exfiltrate"

### Bypass 1 — Enumerate exported providers

```bash
# On the target device (mine, with the victim app installed):
$ adb shell dumpsys package com.victim.bank | grep -A 6 'Provider:'

  Provider{abc123 com.victim.bank/.AccountsProvider}
    authority=com.victim.bank.accounts
    exported=true
    ...
```

Now I have the authority and the surface. Time to probe.

### Bypass 2 — `content query` against the discovered authority

```bash
$ adb shell content query --uri content://com.victim.bank.accounts/profile
Row: 0 _id=1, balance=12453.00, email=alice@victim.bank, full_iban=SE45...
```

If the provider is exported with no permission, **I just dumped the user's profile from a
malicious app installed alongside it**. No root needed.

**Defender counter:** the whole point of "don't export" or "require permission".

### Bypass 3 — SQL injection in selection

```bash
$ adb shell content query --uri content://com.victim.bank.accounts/profile \
    --where "user_id = '1' OR 1=1--"
```

If the provider does `db.query(table, projection, "user_id = " + selection, …)`, the `--`
comments out the rest and `1=1` matches every row.

**Defender counter:** parameterised queries with `selectionArgs`, fixed `selection` strings,
never concatenate caller input.

### Bypass 4 — Projection-based leak

```bash
$ adb shell content query --uri content://com.victim.bank.accounts/profile \
    --projection "password_hash"
```

If the provider passes `projection` through unchanged, the caller chose the column. Bonus:
some providers return `null` for unknown projections — the attacker iterates the schema by
trying projections until they get a non-null response.

**Defender counter:** fixed projection per route. Treat the caller's projection as a *request*
to be validated against a route-specific allowlist.

### Bypass 5 — Path traversal via `openFile`

```bash
$ adb shell content read --uri "content://com.victim.bank.exports/files/../../databases/main.db"
$ # Or via Java code from my malicious app:
val pfd = contentResolver.openFileDescriptor(
    Uri.parse("content://com.victim.bank.exports/files/../../databases/main.db"),
    "r",
)
val stream = FileInputStream(pfd.fileDescriptor)
// Read the victim's private DB, send to my server.
```

The provider's `openFile` resolves `files/../../databases/main.db` relative to `filesDir`,
ends up at `data/data/com.victim.bank/databases/main.db`, opens it. The pfd flies across the
Binder boundary; my app reads the database that should never have left the sandbox.

**Defender counter:** the `canonicalFile + startsWith` check shown above.

### Bypass 6 — URI authority confusion

If the provider declares `authority="com.victim.bank.exports"` but the routing code looks at
`uri.path` only (ignoring authority), I send `content://com.victim.bank.exports/exports/../../sensitive/file`
— the authority check passes, the path traversal works.

**Defender counter:** validate the *whole* URI structurally, not by string pattern. Use
`UriMatcher` exclusively.

### Bypass 7 — Race the permission grant

`FLAG_GRANT_READ_URI_PERMISSION` is supposed to be temporary. If the issuer doesn't revoke,
I keep the permission across re-installs of my malicious app — once granted, the AOSP permission
table persists until revoked or the issuing app is uninstalled.

**Defender counter:** call `revokeUriPermission` when the grant is no longer needed. Tie the
lifetime of the grant to a specific intent dispatch, not to the lifetime of the issuing
session.

### Bypass 8 — Use a non-exported provider from a same-process attacker

If I have code in your process (Frida, malicious SDK), `android:exported="false"` doesn't
matter — same-process callers don't need the IPC layer. Provider methods are just Kotlin
methods I can call.

**Defender counter:**
- Same answer as the RASP / decompile chapters: don't have code in your process. If you do
  have it, the provider isn't your weakest link anyway.
- Sensitive data in `ContentProvider` is fundamentally weaker than sensitive data in a
  TEE-bound Keystore key. Use the Keystore for actual secrets; use providers only for shareable
  data.

### Bypass 9 — Drozer / automated provider fuzzing

`drozer` (formerly Mercury) automates the above:

```bash
$ drozer console connect
dz> run app.provider.info -a com.victim.bank
dz> run app.provider.query content://com.victim.bank.accounts/profile
dz> run scanner.provider.injection -a com.victim.bank
dz> run scanner.provider.traversal -a com.victim.bank
```

Twenty minutes, no skill required.

**Defender counter:** the same CI checks that catch hand-written misuse. Run drozer's scanners
in your own CI against release candidates.

---

## Cross-reference

- **Don't put secrets in providers — use the TEE** → [02-hardware-vault.md](02-hardware-vault.md)
- **APK-level enumeration the attacker uses to find your providers** → [12-decompiling.md](12-decompiling.md)
- **In-process attackers that bypass provider permissions entirely** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **App-side hygiene that complements provider hardening** → `AndroidManifest.xml`: `android:allowBackup="false"`, `android:dataExtractionRules`

## References

- [Exploiting Content Providers in Android Applications](https://redfoxsecurity.medium.com/exploiting-content-providers-in-android-applications-a75cbda2a5c7)
- [Android Developers — ContentProvider](https://developer.android.com/guide/topics/providers/content-providers)
- [Android Developers — UriMatcher](https://developer.android.com/reference/android/content/UriMatcher)
- [Android Developers — FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [OWASP MASVS — MSTG-PLATFORM-4 / MSTG-PLATFORM-5](https://mas.owasp.org/MASVS/)
- [Drozer security assessment framework](https://github.com/WithSecureLabs/drozer)
