---
title: "Privacy vs security — the user-trust tightrope"
slug: privacy-vs-security
level: beginner
estimated_minutes: 20
status: published
company: Fortress
tags:
  - privacy
  - ux
  - policy
  - telemetry
summary: >
  Where privacy and security agree, where they collide, and the design moves that keep
  both intact in a fintech UI. With concrete examples from the Fortress app — logging,
  screenshot protection, error copy, telemetry payloads.
references:
  - title: "Privacy vs Security — walking the tightrope (Jackson Mafra, Medium)"
    url: https://medium.com/@jacksonfdam/privacy-vs-security-walking-the-tightrope-of-user-trust-c29e69199191
  - title: "Android — Privacy and security overview"
    url: https://source.android.com/docs/security/overview
  - title: "GDPR Art. 5 — Principles relating to processing of personal data"
    url: https://gdpr-info.eu/art-5-gdpr/
---

## Welcome to the tightrope

Understand the cases where a "more secure" decision is *also* a "less private" decision,
and learn the design moves that keep both axes high.

Privacy and security are often described as the same goal. They are not. Security keeps
the wrong people out; privacy decides what data exists in the first place. When they
agree (encrypted at rest, no unnecessary collection) the design is easy. When they
disagree — fraud detection that needs device fingerprints, debug logs that contain PII,
recovery flows that leak account existence — the design is hard. We will walk every
collision the Fortress app has to handle.

> **Why this matters.** A team that conflates the two ships fingerprinting in the name of
> "security" and ten million euros in GDPR fines in the same release.

---

## Step 1: Define both axes precisely

- **Security** = control over who can access an asset. Confidentiality, integrity,
  availability.
- **Privacy** = control over what data exists *about* a user. Minimisation,
  purpose-limitation, retention.

Two distinct levers. An app can be highly secure and highly privacy-invasive. (Think:
adtech with rigorous TLS.) An app can be highly private and weakly secure. (Think: a
naïvely encrypted note-taker with a hardcoded key.) The goal is to score well on both.

> **Why this matters.** Conflation makes both decisions worse. Treat them as orthogonal,
> design for both, then surface the trade-offs explicitly.

---

## Step 2: Where they agree (the easy cases)

Most security decisions are also privacy wins:

- **Encrypt data at rest.** Less likely to leak, also less recoverable in an exfiltration.
- **Encrypt data in transit.** Less likely to be tampered with, also less likely to be
  observed by ISPs / corporate MDM.
- **Minimum scoped permissions.** Less data flowing to your app, less surface for misuse.
- **Short data retention.** Less to leak in a breach, less to subpoena.

If a team is debating one of these, the debate is usually about engineering cost — not
about whether it is the right call.

> **Why this matters.** Most of the work is uncontroversial. The disagreements are at the
> margin; do not let them dominate the conversation.

---

## Step 3: Where they collide (the hard cases)

Five concrete collisions. Every fintech app has to take a position on each.

1. **Device fingerprinting for fraud detection.** Identifies devices uniquely — useful
   for stopping account takeover. Also a persistent identifier, which is exactly what
   privacy laws regulate.
2. **Telemetry that helps debug security incidents.** Refresh-reuse logs help spot
   compromised tokens. They also record session activity tightly enough to count as user
   behaviour.
3. **Logging error stacks.** Helpful for triage. Often contains PII (IBANs in the
   stacktrace, user IDs in URLs).
4. **Account-enumeration via login errors.** "Wrong password" leaks that the email is a
   real account. "Wrong credentials" hides it but degrades UX.
5. **Crash reports.** Useful for stability. The crash payload often contains the screen
   the user was looking at, which can include balance / recipient.

There is no universal right answer. The right answer is *take a deliberate position per
item and document why*.

> **Why this matters.** A team that has not picked a position will accidentally do the
> worse thing on each axis.

---

## Step 4: Decide what to log, write a logging policy

Three rules that resolve most of the disagreements:

1. **Never log raw PII.** Names, emails, IBANs, PANs, addresses. Hash them or store an
   opaque ID.
2. **Never log secrets.** Tokens, signed challenges, keys. Log a fingerprint hash if
   correlation is needed.
3. **Never log full request / response bodies.** Log the route, the status, the latency.
   Log the body only with explicit opt-in for debugging, and never in release builds.

```kotlin
// Bad
Timber.i("Login attempt for $email password=$password")

// Good
Timber.i("auth.login.start subjectFingerprint=${email.fingerprint()} attempt=$attempt")
```

A fingerprint function is just a per-user salt + SHA-256, truncated to 8 hex characters.
Enough to correlate without enabling tracking.

> **Why this matters.** Most production PII leaks come from log lines, not databases.
> Cleaning them at the source is cheaper than redacting them downstream.

---

## Step 5: Lock down the screenshot surface

Sensitive screens — balance, account detail, IBAN reveal, card PAN, transfer review —
should set `FLAG_SECURE`. The OS will block the recent-apps preview, in-app screenshots,
and screen mirroring.

```kotlin
class TransferActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(
      WindowManager.LayoutParams.FLAG_SECURE,
      WindowManager.LayoutParams.FLAG_SECURE,
    )
  }
}
```

Apply per-Activity, not globally — users still expect to screenshot transactions for
their own records, and blanket `FLAG_SECURE` breaks accessibility tooling.

> **Why this matters.** The default Android behaviour is "everything screenshottable".
> A balance in a recent-apps thumbnail is a balance leaked to anyone who picks up the
> phone.

---

## Step 6: Disable backups for sensitive data

`android:allowBackup="true"` ships every SharedPreference and DataStore to cloud backup,
where it lives at the user's Google account's security level (which is rarely as strong
as the device). For an app with encrypted tokens, restoring on a new device that has not
re-enrolled gives the attacker a key-less ciphertext, which is harmless — but for a less
careful app, restoring is restoring usable credentials.

```xml
<application
  android:allowBackup="false"
  android:dataExtractionRules="@xml/data_extraction_rules"
  ...>
</application>
```

```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
  <cloud-backup>
    <exclude domain="sharedpref" path="fortress-secrets.xml" />
    <exclude domain="sharedpref" path="auth-tokens.xml" />
  </cloud-backup>
  <device-transfer>
    <exclude domain="sharedpref" path="fortress-secrets.xml" />
  </device-transfer>
</data-extraction-rules>
```

Prefer the rules file over a blanket `allowBackup="false"` — the latter blocks legitimate
device migration UX, the former carves out only the sensitive parts.

> **Why this matters.** Cloud backup is a privacy-fine surface (data crosses the user's
> intent) and a security surface (credentials end up in a vendor cloud).

---

## Step 7: Make error copy unambiguous *without* leaking enumeration

The "wrong email vs wrong password" debate is misframed. The right copy is "Couldn't sign
in. Check your email and password." — and the server still treats both cases as 401 with
identical timing. The client says nothing useful to an enumerator; the legitimate user
still understands the next move.

```kotlin
// Server response
res.status(401).json({ code: "INVALID_CREDENTIALS" })

// Client display
when (result.code) {
  "INVALID_CREDENTIALS" -> "Couldn't sign in. Check your email and password."
  "ACCOUNT_LOCKED"      -> "Too many attempts. Try again in 30 minutes."
  else                  -> "Couldn't sign in. Try again."
}
```

The lockout message *does* differ. Account lockout is itself a piece of attacker
information, but the UX value is high enough that a rate-limited lockout-notice is the
right call.

> **Why this matters.** "Wrong password" tells the attacker the email exists. That is
> half of the credentials they will phish next. Be unambiguous about what to do, not
> about which input was wrong.

---

## Step 8: Decide telemetry by purpose, not by convenience

Every telemetry event answers "what data, for what purpose, retained how long?". If you
cannot answer all three on the spot, the event does not ship.

```kotlin
data class TelemetryEvent(
  val name: String,                      // e.g. "auth.refresh.failed"
  val purpose: TelemetryPurpose,         // Security, Reliability, Product
  val retainDays: Int,                   // hard cap, enforced server-side
  val attributes: Map<String, String>,   // PII-free
)
```

Three purposes only. *Product* events have the shortest retention. *Security* events have
the longest (regulators ask). *Reliability* sits in the middle. A central catalogue of
events with all three columns is the easiest way to keep yourself honest.

> **Why this matters.** Telemetry sprawl is the most common privacy-debt accumulator on
> mobile. A formal catalogue makes the trade-offs visible.

---

## Step 9: Build the "What we collect" screen

Users do not read privacy policies. They do open the Security Center on the off chance
something is wrong. Use that screen to list, in plain language, the data your app stores
on this device and the data it sends to the server.

```text
Stored on this device
  ✓ Encrypted refresh token (deleted on sign-out)
  ✓ Cached account list (last sync 18:42)
  ✓ App preferences (theme, language)

Sent to the server
  ✓ Email + password on sign-in
  ✓ Device ID (random, regenerable)
  ✓ App version + Android OS version
  ✗ Contact list, photos, location — we do not collect these
```

The honesty buys disproportionate trust. The negatives ("we do not collect …") matter as
much as the positives.

> **Why this matters.** Privacy policies are legalese; this screen is plain. The plain
> version is what users actually read.

---

## Step 10: Walk the trade-off matrix once per release

Print this table, fill it in, file it in the release ticket.

| Decision | Security gain | Privacy cost | Chosen | Why |
|---|---|---|---|---|
| Device fingerprinting | High (fraud) | Medium (persistent ID) | Yes, salted | Salted hash, not raw ID |
| Refresh-reuse logging | High (compromise alert) | Medium (session graph) | Yes | 30-day retention, fingerprinted |
| `FLAG_SECURE` on Balance | High (shoulder surfing) | Low | Yes | n/a |
| Crash report payloads | Low | High | No | Disabled body capture in release |
| `allowBackup` | Low | Medium | Mixed | Exclude tokens via rules file |

Whoever reads the ticket in six months has a record of why the design landed where it
did. That is the bar.

> **Why this matters.** Decisions made implicitly become rumours. Decisions documented at
> the time become a defensible record.

---

## Wrap-Up

You now have a concrete framework for picking the privacy-vs-security position on every
recurring fintech-mobile decision — logs, screenshots, backups, error copy, telemetry,
user-facing transparency.

Next mission: walk the [Hardware Vault](/codelabs/hardware-vault) codelab. The vault is
the place where the privacy and security stories agree most loudly — encrypt the sensitive
parts, store nothing else.

**Recap of what you just decided:**

- Security and privacy are orthogonal axes. Score both deliberately.
- Five common collisions: fingerprinting, telemetry, error logs, login enumeration, crash
  payloads. Each gets a documented position.
- A three-rule logging policy (no PII, no secrets, no full bodies).
- `FLAG_SECURE` per sensitive Activity, scoped backup-exclusion, unambiguous error copy.
- A telemetry-event shape that carries purpose + retention + PII-free attributes.
- A user-facing "What we collect" screen as the trust artefact.
- A per-release trade-off matrix that documents every non-obvious call.
