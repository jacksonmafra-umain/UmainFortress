---
title: "Passkeys with androidx.credentials"
slug: passkeys
level: advanced
estimated_minutes: 30
status: published
company: Fortress
tags:
  - passkeys
  - fido2
  - webauthn
  - credentials
summary: >
  Wire passkey enrolment and sign-in via androidx.credentials, talk to a FIDO2 /
  WebAuthn-compatible server, and handle the recovery-flow attacks the spec almost gets
  right. Side-by-side examples with the legacy password path you are replacing.
references:
  - title: "Passkeys (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/04-passkeys.md
  - title: "androidx.credentials — official docs"
    url: https://developer.android.com/training/sign-in/passkeys
  - title: "WebAuthn — W3C Recommendation"
    url: https://www.w3.org/TR/webauthn/
---

## Welcome to passwordless

Understand what a passkey actually is, what changes about your server, and why the
recovery flow is the part most teams get wrong.

A passkey is a FIDO2 credential: a public-private keypair where the private key lives in
a platform credential store (Android's `Credential Manager` backed by Google Password
Manager, or the user's chosen credential provider). The server stores only the public
key. Sign-in is a signed challenge; there is no password to leak, phish, or reuse.

> **Why this matters.** The dominant vector for account takeover in 2026 is credential
> stuffing — passwords reused across breaches. Passkeys remove the credential that
> can be stuffed.

---

## Step 1: The three flows you implement

Passkey integration is three flows, not one:

1. **Enrolment for an existing-account user.** They are signed in; they click "Add a
   passkey". You create a credential and persist its public key.
2. **Sign-in via passkey.** They open the app at the Login screen; you call the
   credential manager; the OS shows the passkey picker; signature is sent to your
   server.
3. **Recovery without a passkey.** They lost their device. Without their device they
   cannot sign with the private key. This is the soft underbelly of every passwordless
   system. We address it last and most carefully.

Each flow is independent and ships independently.

> **Why this matters.** The naïve "add a passkey button" approach handles flow 1 only,
> and leaves the user worse off when they lose the device. Plan all three.

---

## Step 2: Add the dependency and the digital-asset link

Passkeys require `androidx.credentials` and a `/.well-known/assetlinks.json` published
under your domain. The server uses the link to tell Android which app(s) are allowed to
present passkeys for that domain.

```kotlin
// app/build.gradle.kts
dependencies {
  implementation("androidx.credentials:credentials:1.5.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
}
```

```json
// hosted at https://fortress.dev/.well-known/assetlinks.json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls",
                 "delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.umain.fortress",
      "sha256_cert_fingerprints": ["AB:CD:EF:...:Play release signing key SHA-256..."]
    }
  }
]
```

The asset-link binds your domain to your app's signing certificate. Without it, the OS
refuses to present passkeys for that domain to your app.

> **Why this matters.** The asset-link is the only thing preventing a malicious app
> from claiming to be you and harvesting passkeys.

---

## Step 3: Server-side — generate the enrolment challenge

The server returns a signed challenge with the relying-party id (`rp.id`), the user
handle, and a nonce. The client passes it verbatim to the credential manager.

```ts
import { generateRegistrationOptions } from "@simplewebauthn/server";

router.post("/passkeys/register/start", requireAuth, async (req, res) => {
  const userId = req.claims.sub;
  const user = await users.find({ id: userId });

  const options = await generateRegistrationOptions({
    rpName: "Fortress",
    rpID: "fortress.dev",
    userID: Buffer.from(user.id),
    userName: user.email,
    userDisplayName: user.displayName,
    timeout: 60_000,
    attestationType: "indirect",
    authenticatorSelection: {
      residentKey: "required",          // store the passkey on-device
      userVerification: "required",     // require biometric / PIN
      authenticatorAttachment: "platform",
    },
  });
  await challenges.upsert({ id: options.challenge, userId, kind: "register" });
  res.json(options);
});
```

`residentKey: required` ensures the credential is discoverable on the device (the
"username-less" sign-in path works). `userVerification: required` makes biometric a
prerequisite at enrolment.

> **Why this matters.** Without `residentKey: required` you lose the magic of "sign in
> without typing anything". Without `userVerification: required` the passkey can be
> used without a biometric, which weakens the assurance.

---

## Step 4: Client-side — create the credential

`androidx.credentials` exposes a single suspendable call.

```kotlin
suspend fun enrolPasskey(activity: Activity, optionsJson: String): String {
  val request = CreatePublicKeyCredentialRequest(
    requestJson = optionsJson,
    preferImmediatelyAvailableCredentials = true,
  )
  val manager = CredentialManager.create(activity)
  val response = manager.createCredential(activity, request) as CreatePublicKeyCredentialResponse
  return response.registrationResponseJson
}
```

The returned JSON is a structured WebAuthn attestation response. It contains the public
key, the credential id, and a signature over the challenge that the server will verify.

> **Why this matters.** `androidx.credentials` is the entire client integration. You do
> not implement WebAuthn yourself; the OS does.

---

## Step 5: Server-side — verify and persist

```ts
import { verifyRegistrationResponse } from "@simplewebauthn/server";

router.post("/passkeys/register/finish", requireAuth, async (req, res) => {
  const userId = req.claims.sub;
  const { response } = req.body;
  const challenge = await challenges.findOne({ userId, kind: "register" });
  if (!challenge) return res.status(400).json({ code: "NO_CHALLENGE" });

  const verification = await verifyRegistrationResponse({
    response,
    expectedChallenge: challenge.id,
    expectedOrigin: "https://fortress.dev",
    expectedRPID: "fortress.dev",
    requireUserVerification: true,
  });
  if (!verification.verified) return res.status(400).json({ code: "VERIFICATION_FAILED" });

  await passkeys.upsert({
    id: verification.registrationInfo!.credentialID.toString("base64url"),
    userId,
    publicKey: verification.registrationInfo!.credentialPublicKey.toString("base64"),
    counter: verification.registrationInfo!.counter,
    transports: response.response.transports ?? [],
    createdAtEpochMs: Date.now(),
  });
  await challenges.delete({ id: challenge.id });
  res.json({ ok: true });
});
```

The server now has the public key. Every future sign-in is the same shape with
`generateAuthenticationOptions` and `verifyAuthenticationResponse`.

> **Why this matters.** The "no shared secret" property holds end-to-end. Your server
> stores zero information that, on its own, lets anyone sign in.

---

## Step 6: Sign-in — the discoverable-credential path

The sweet spot of passkeys is "show me the picker; I tap; I'm in". No username field.

```kotlin
suspend fun signInWithPasskey(activity: Activity, optionsJson: String): String {
  val publicKey = GetPublicKeyCredentialOption(optionsJson)
  val request = GetCredentialRequest(listOf(publicKey))
  val manager = CredentialManager.create(activity)
  val result = manager.getCredential(activity, request)
  val response = result.credential as PublicKeyCredential
  return response.authenticationResponseJson
}
```

```ts
router.post("/auth/login/passkey/start", async (_req, res) => {
  const options = await generateAuthenticationOptions({
    rpID: "fortress.dev",
    timeout: 60_000,
    userVerification: "required",
    allowCredentials: [],  // empty = discoverable / username-less
  });
  await challenges.upsert({ id: options.challenge, kind: "auth" });
  res.json(options);
});
```

Empty `allowCredentials` is the trick — the server says "any passkey for this domain".
The OS picker shows the user every passkey they have for `fortress.dev`. They tap one,
biometric-verify, and the server identifies which user from the credential id in the
response.

> **Why this matters.** The username-less flow is the UX that drives adoption. Anything
> that asks for an email first defeats the point.

---

## Step 7: Migration — passwords alongside passkeys

Users do not migrate atomically. Plan for the long tail.

- Show a banner on Login: *"You can sign in faster with a passkey. Set one up next time
  you sign in."*
- After successful password sign-in, prompt to enrol a passkey.
- Always offer "Sign in another way" on the Login screen — passkey, password, magic
  email link.
- Track per-user `hasPasskey` boolean. Surface it in support tickets so the helpdesk
  knows the user can use the fast path.

Plan for the 36-month migration. Some users will not adopt passkeys for years.

> **Why this matters.** A migration that requires users to opt in is a migration that
> stalls. Persistent gentle nudging is the right tempo.

---

## Step 8: Recovery — the unglamorous part

A user with a passkey on one device, no other devices, loses the device. They cannot
sign in. The naïve answer ("we'll let them recover via email") reintroduces every
phishing vector the passkey removed.

Three real options:

1. **Multi-device passkey sync (Google Password Manager).** The passkey is replicated
   across the user's Google devices. Lose the phone, the iPad still has it. Works only
   if the user is signed into the same Google account on multiple devices.
2. **Hardware security keys as backup.** A YubiKey enrolled alongside the device
   passkey. The user keeps it in a drawer. Works for security-conscious users; not for
   the median.
3. **Out-of-band recovery via human-verified KYC.** Government ID re-upload, video
   selfie. Slow, expensive, but the only path that does not rely on a shared secret.

A real product needs at least (1) and (3). (2) is a luxury option.

> **Why this matters.** Recovery is where attackers will spend their time. Get this
> wrong and you have shipped a single-device dependency disguised as zero-trust auth.

---

## Step 9: Threats unique to passkey-aware attackers

Passkeys defeat password attacks. They do not defeat all attacks:

1. **Phishing via Account Recovery.** Attacker socially engineers your support desk
   into resetting the passkey. Counter: support desk has its own zero-trust process.
2. **Device theft + biometric coercion.** The thief forces the user to authenticate.
   Counter: nothing technical — duress codes are not part of the spec yet.
3. **Malicious credential provider.** A rogue Android credential-manager app could
   harvest enrolment requests. Counter: rely on Google Password Manager or another
   audited provider; do not allow arbitrary credential providers for fintech-grade
   flows.

```kotlin
val request = CreatePublicKeyCredentialRequest(
  requestJson = optionsJson,
  // Only the platform authenticator — no third-party credential providers.
  preferImmediatelyAvailableCredentials = true,
)
```

> **Why this matters.** Passkeys are excellent against the threat model they target.
> Knowing the gaps prevents over-claiming.

---

## Step 10: Telemetry and operational health

Four events:

```ts
type PasskeyEvent =
  | { kind: "passkey.enrolled"; userId: string }
  | { kind: "passkey.signin.success"; userId: string; credentialId: string }
  | { kind: "passkey.signin.failed"; userId?: string; reason: string }
  | { kind: "passkey.recovered"; userId: string; method: "sync" | "yubikey" | "kyc" };
```

Aggregate: enrolment rate (the rollout health), sign-in success rate (the experience),
recovery distribution (whether recovery is too painful and users drop off). Each is a
distinct dashboard.

> **Why this matters.** A passkey rollout without telemetry is a rollout you discover
> failing only when the support backlog explodes.

---

## Wrap-Up

You can now wire passkeys end-to-end — server-side WebAuthn, `androidx.credentials`
on the client, an honest recovery story, and the telemetry to know whether the
migration is working.

Next mission:
- [Stateless Auth Blueprint](/codelabs/stateless-auth-blueprint) — the JWT layer that
  sits on top of passkey-authenticated sessions.
- [Biometric Hardening](/codelabs/biometric-hardening) — the `userVerification: required`
  promise turned into a `CryptoObject` signature.
- [Zero Trust](/codelabs/zero-trust) — the policy framework passkeys plug into.

**Recap of the passkey stack:**

- Three independent flows: enrolment, sign-in, recovery.
- `androidx.credentials` for client integration; WebAuthn on the server.
- Digital asset-link required to bind your domain to your app.
- `residentKey: required` for the discoverable-credential / username-less UX.
- `userVerification: required` to keep biometric in the loop.
- Multi-device sync (Google Password Manager) + KYC recovery as the dual safety net.
- Telemetry on enrolment, sign-in, and recovery rates.
