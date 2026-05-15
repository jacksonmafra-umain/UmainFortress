---
title: "OkHttp interceptor pattern — single-flight refresh"
slug: interceptor-pattern
level: intermediate
estimated_minutes: 30
status: published
company: Fortress
tags:
  - okhttp
  - ktor
  - refresh
  - concurrency
summary: >
  Build the network-layer interceptor that turns a 401 from any API into a single-flight
  refresh + replay — mutex-protected, race-free under concurrent traffic, with the exact
  failure modes a busy production app will hit.
references:
  - title: "OkHttp interceptor pattern (Fortress doc)"
    url: https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/03-interceptor-pattern.md
  - title: "OkHttp Interceptors — official docs"
    url: https://square.github.io/okhttp/interceptors/
  - title: "Ktor client Auth plugin"
    url: https://ktor.io/docs/client-auth.html
---

## Welcome to the interceptor pattern

Understand why every modern Android app has *exactly one* place where 401 → refresh →
replay happens, and what goes wrong when it has more.

The interceptor pattern centralises auth bookkeeping in the HTTP client. Any API call that
returns 401 triggers a refresh, the original request is retried with the new access token,
and the caller never knows. Done naively it produces a thundering herd of refresh
requests every time the access token expires. Done right it serialises into a single
in-flight refresh that every concurrent caller awaits.

> **Why this matters.** A 5-minute access token + ten concurrent screens loading at app
> startup is enough to DOS your auth service if every screen refreshes independently.
> Single-flight is not an optimisation; it is the bare minimum.

---

## Step 1: Decide where the interceptor lives

Two options on Android in 2026:

1. **OkHttp `Authenticator`** — fires only on 401, can fetch new credentials and resign the
   request. Synchronous; good fit when refresh is also synchronous.
2. **Ktor `Auth` plugin with `bearer { ... }`** — fully asynchronous, integrates with
   coroutines, ships with built-in single-flight.

The Fortress demo uses Ktor (it stacks naturally on top of OkHttp engine). We will show
both for completeness.

> **Why this matters.** Putting refresh logic inside a screen's ViewModel guarantees
> divergence the first time a second screen needs the same logic. The HTTP client is the
> single chokepoint by design.

---

## Step 2: A naive interceptor (do not ship this)

The shape we want to avoid:

```kotlin
class NaiveInterceptor(private val store: TokenStore) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()
    val withAuth = original.signed(store.access())
    var response = chain.proceed(withAuth)
    if (response.code == 401) {
      response.close()
      val newAccess = runBlocking { refresh(store.refresh()) } // <-- problem
      store.saveAccess(newAccess)
      response = chain.proceed(original.signed(newAccess))
    }
    return response
  }
}
```

Two problems: `runBlocking` on a worker thread (deadlock-prone) and no mutex (every
concurrent 401 starts its own refresh). Both are fixed below.

> **Why this matters.** The 80 % implementations of this pattern ship exactly this code.
> It works perfectly in development and falls apart on the first cold start with eight
> screens loading.

---

## Step 3: Add a mutex

A single `Mutex` guards the refresh path. While one coroutine refreshes, every other one
suspends until the refresh completes, then reads the freshly cached access token.

```kotlin
class SessionManager(
  private val store: TokenStore,
  private val authApi: AuthApi,
) {
  private val mutex = Mutex()
  @Volatile private var cachedAccess: String? = null

  suspend fun currentAccess(): String? {
    cachedAccess?.let { return it }
    return mutex.withLock { cachedAccess ?: loadAccessFromStore() }
  }

  suspend fun refresh(): String? = mutex.withLock {
    val refresh = store.loadRefresh() ?: return null
    when (val r = authApi.refresh(refresh)) {
      is RefreshResult.Success -> {
        cachedAccess = r.accessToken
        store.saveRefresh(r.refreshToken)
        r.accessToken
      }
      is RefreshResult.Failure -> {
        cachedAccess = null
        store.clear()
        null
      }
    }
  }
}
```

The `@Volatile` cache means non-contended reads stay fast. The mutex only serialises the
refresh path itself.

> **Why this matters.** A naive lock around every read kills throughput. Cache + mutex on
> the slow path is the standard pattern.

---

## Step 4: Wire the SessionManager into Ktor

Ktor's `Auth` plugin already calls `loadTokens` and `refreshTokens` for you. You provide
the implementations, the plugin handles the single-flight and the retry.

```kotlin
val client = HttpClient(OkHttp) {
  install(Auth) {
    bearer {
      loadTokens {
        val access = sessionManager.currentAccess() ?: return@loadTokens null
        val refresh = tokenStore.loadRefresh() ?: return@loadTokens null
        BearerTokens(access, refresh)
      }
      refreshTokens {
        val nextAccess = sessionManager.refresh() ?: return@refreshTokens null
        val nextRefresh = tokenStore.loadRefresh() ?: return@refreshTokens null
        BearerTokens(nextAccess, nextRefresh)
      }
      sendWithoutRequest { request -> request.url.host == BASE_HOST }
    }
  }
}
```

The plugin tracks in-flight refreshes itself; you do not have to. Concurrent callers all
see the same refreshed `BearerTokens` once it lands.

> **Why this matters.** Plugin code that gets concurrency right is rarer than it should
> be. `bearer { ... }` is one of the well-tested ones.

---

## Step 5: A raw OkHttp `Authenticator` for comparison

If you cannot use Ktor (legacy code path, third-party SDK requirement) the
`Authenticator` shape is the OkHttp-native equivalent.

```kotlin
class FortressAuthenticator(
  private val sessionManager: SessionManager,
) : Authenticator {
  override fun authenticate(route: Route?, response: Response): Request? {
    // Avoid infinite loops: do not retry more than once.
    val attempts = response.priorResponseCount()
    if (attempts >= 1) return null
    val newAccess = runBlocking { sessionManager.refresh() } ?: return null
    return response.request.newBuilder()
      .header("Authorization", "Bearer $newAccess")
      .build()
  }
}

private fun Response.priorResponseCount(): Int {
  var r = priorResponse
  var n = 0
  while (r != null) { n++; r = r.priorResponse }
  return n
}
```

The recursion guard (`priorResponseCount`) is the second most common bug in this code path
— a 401 storm with no guard burns through every retry policy the user has.

> **Why this matters.** OkHttp lets `Authenticator` retry indefinitely. One forgotten
> guard turns "the refresh API is having a bad day" into "every device is hammering us
> until rate limits eat them".

---

## Step 6: Track in-flight requests, not just refreshes

What about the race where the access token expires *during* a request? The server returns
401 to *all* in-flight calls. With the Ktor plugin, the first one to receive 401 triggers
the refresh; every subsequent call that hits 401 in the same window picks up the new
token automatically because the plugin has already updated its cache.

The Authenticator path needs a tiny version stamp to do the same thing:

```kotlin
class SessionManager(/* … */) {
  private var version = 0L

  suspend fun refreshIfStale(seenVersion: Long): String? = mutex.withLock {
    if (seenVersion < version) return cachedAccess // someone else already refreshed
    val next = doRefresh() ?: return null
    version++
    next
  }
}
```

The interceptor reads `version` before sending, passes it into `refreshIfStale` on 401,
and the mutex serialises the actual refresh while letting late-arriving 401s short-circuit.

> **Why this matters.** Two screens both expire and both hit 401. Without a version
> stamp, both refresh. With it, only the first one does; the second one notices the
> refresh has already happened and just retries.

---

## Step 7: Handle network errors distinctly from auth errors

A 503, a `SocketTimeoutException`, a DNS failure — none of these are auth problems. The
interceptor must not refresh on them.

```kotlin
override fun authenticate(route: Route?, response: Response): Request? {
  if (response.code != 401) return null // <-- the only-on-401 guard
  // … refresh path here
}
```

Ktor's bearer plugin already enforces this. With Authenticator, the contract is "only
called on 401 / 407", so you are safe by construction.

> **Why this matters.** Refreshing on a 503 is how you turn a transient outage into a
> session-loss incident.

---

## Step 8: Add request-level idempotency

Replaying a request after refresh is safe only if the request itself is idempotent. POSTs
that create state (`/me/transfers`) must carry an idempotency key the server can
deduplicate on.

```kotlin
suspend fun submitTransfer(transfer: Transfer): TransferResult {
  val key = transfer.idempotencyKey ?: UUID.randomUUID().toString()
  return client.post("/me/transfers") {
    header("Idempotency-Key", key)
    setBody(transfer.copy(idempotencyKey = key))
  }.body()
}
```

The server keeps a 24-hour window of `(key → result)` mappings. A retried POST with the
same key returns the same result without re-creating anything.

> **Why this matters.** Replay-on-401 without idempotency is how you accidentally send the
> same transfer twice.

---

## Step 9: Log enough to debug the failure modes

The refresh path is invisible by design. That makes it hostile to debug when it does
break. Three log lines pay for themselves a hundred times over:

```kotlin
Timber.i("refresh.start version=$version refreshHash=${store.refreshFingerprint()}")
// … refresh call …
Timber.i("refresh.success version=${version + 1} newAccessExp=${claims.exp}")
// … or
Timber.w("refresh.fail code=${result.code} msg=${result.message}")
```

Never log the tokens themselves — fingerprint hash only. The fingerprint is enough to
correlate with server-side logs.

> **Why this matters.** Production debugging of token flows without logs is archaeology
> through device crashes. A small structured trail saves entire incidents.

---

## Step 10: Test the race

Force the race with two simultaneous coroutines:

```kotlin
@Test fun `concurrent calls only refresh once`() = runTest {
  val refreshCount = AtomicInteger()
  val sm = SessionManager(/* refresh hook increments */ refreshCount)
  coroutineScope {
    repeat(8) { launch { sm.currentAccess() } }
    repeat(8) { launch { sm.refresh() } }
  }
  assertEquals(1, refreshCount.get())
}
```

If your implementation passes that test, you have shipped the interceptor pattern
correctly.

> **Why this matters.** Concurrency bugs do not surface in single-user development. A
> repeatable unit test is the only honest proof.

---

## Wrap-Up

You can now turn any 401 from any API into a single-flight refresh + replay, race-free,
with idempotent retries and a small but useful log trail.

Next mission:
- [Stateless Auth Blueprint](/codelabs/stateless-auth-blueprint) for the server side this
  client talks to.
- [Hardware Vault](/codelabs/hardware-vault) for the persistent storage the refresh token
  lives in.

**Recap of what you just built:**

- A `SessionManager` with `@Volatile` cache + `Mutex.withLock` refresh.
- A Ktor `bearer { ... }` integration that gets single-flight for free.
- An OkHttp `Authenticator` equivalent with explicit recursion guard.
- A version stamp that prevents redundant refreshes on concurrent 401s.
- Idempotency keys on state-mutating POSTs so retries do not double-spend.
- A unit test that proves the race is closed.
