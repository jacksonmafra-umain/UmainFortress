# 03 — The Interceptor Pattern: Mastering Transport Concurrency

> "Five tabs of your app fire a request at the same instant. All five tokens are expired. Without
> a mutex, you've just logged the user out." — *Fortress field notes*

**TL;DR** — A naive auth interceptor fires one refresh per 401. Under concurrency that
self-immolates: each refresh revokes the others' refresh tokens, the rotation lineage looks like
reuse to the server, and the user gets booted. This file walks the single-flight refresh in
[`AuthInterceptor`](../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt),
why a Mutex (not a CountDownLatch, not `synchronized`) is the right tool, and how the same flow
defeats — or enables — a handful of attacks.

| | 🛡️ Defender | ⚔️ Attacker |
|---|---|---|
| **Goal** | Serve all concurrent 401s with one refresh | Cause a refresh storm that revokes the user's session |
| **Key idea** | Mutex around the refresh; replay original requests with the new token | Race conditions in your interceptor are gifts |
| **Worst failure** | Each 401 fires its own refresh → mass revocation → user signed out | Long-lived access tokens with no rotation, no replay defence |

---

## 🛡️ Defender — "Refresh once, replay many"

### The failure mode without a mutex

Three parallel calls land on the server, all carrying the same expired access token. Each gets
a 401. A naive interceptor does:

```
401 received → refresh → save tokens → replay original
```

In parallel, three times. The first refresh succeeds and rotates the refresh token. The second
fires with the (now revoked) refresh token and the server says: this token has been used by
someone else, **revoke the entire family**. The third does the same. The server is doing exactly
what it should — reuse detection. But the user is now signed out, and they only ever opened
the app once.

### The fix: single-flight via Mutex

[`AuthInterceptor.refreshIfNeeded`](../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt):

```kotlin
private suspend fun refreshIfNeeded(staleSession: Session): Session? = refreshMutex.withLock {
    val current = tokenStore.current() ?: return null
    // If another caller already swapped the access token while we waited, just use theirs.
    if (current.accessToken != staleSession.accessToken) return current
    // ...run refresh against /auth/refresh, save the new session, return it
}
```

Three things matter here:

1. **`Mutex.withLock`** — only one coroutine executes the body at a time. The others suspend
   until they acquire the lock.
2. **The recheck inside the lock** — by the time you acquire the lock, someone else may already
   have refreshed. If `tokenStore.current()` shows a different access token than the one you
   came in with, refresh is unnecessary — return the existing one.
3. **`runBlocking` to bridge OkHttp's synchronous interceptor with the suspending mutex** —
   OkHttp's interceptor chain runs synchronously on a worker thread, so we have to bridge into
   coroutines. `runBlocking` here is safe because we're already on an off-main thread.

The mutex is **process-wide static** (`private val refreshMutex = Mutex()` in the companion).
There must be exactly one mutex instance; otherwise each caller serialises against itself only.

### Why not `synchronized`?

`synchronized` blocks a thread. Inside it we want to call a suspending function (the Ktor refresh
call). Mixing the two needs `runBlocking`, which works but holds the thread for the duration of
the network round-trip — and OkHttp's dispatcher has a fixed thread pool. Burn enough of those
and the rest of your network stack starves.

`Mutex` cooperates with coroutines: when you `withLock`, other coroutines can use the dispatcher
while you wait. In an Activity ViewModel that processes many requests, the difference is the
gap between "responsive UI" and "ANR".

### Why not `Channel<Unit>(1)` / `CountDownLatch`?

You can — but the semantics are clumsier. A Mutex is exactly "one writer at a time"; that's
what we want. Channels are useful when you also need to broadcast the result to N waiters at
once; here every waiter goes through the same path (recheck → return current), so no broadcast
needed.

### The flow, end-to-end

```
Caller A (request)  Caller B (request)  Caller C (request)
       │                  │                  │
       ▼                  ▼                  ▼
   401 (expired)      401 (expired)      401 (expired)
       │                  │                  │
       ▼                  ▼                  ▼
  withLock {          withLock {         withLock {
    refresh()           recheck:           recheck:
    saveTokens          tokens             tokens
    return new          changed →          changed →
  }                     return             return
       │                  │                  │
       ▼                  ▼                  ▼
   replay w/          replay w/          replay w/
   new token          new token          new token
```

Exactly one network refresh fires. The other two callers wait in microseconds, then replay
their original request with the fresh token.

### The `SKIP_AUTH` escape hatch

Some calls **must not** trigger an auth refresh on 401:

- The refresh call itself (otherwise infinite loop).
- Login (no token yet to attach or refresh).
- Anonymous endpoints (`/auth/policy`, `/health`).

[`AuthInterceptor.SKIP_AUTH`](../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt)
is a per-request header sentinel. Callers add it; the interceptor sees it, strips it, and
returns the response unchanged. The `anonymous` Ktor client in [`FortressHttpClient`](../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
doesn't even install this interceptor — it can't loop. Belt and braces.

### What about the original request's body?

OkHttp can replay GET / HEAD / DELETE requests trivially — their bodies are empty (or buffered).
For POST/PUT, the original request body is a `RequestBody` — if it's a one-shot stream, replay
fails. In production, mark `RequestBody.isOneShot() == false` for sensitive request bodies, or
buffer them. The demo's bodies are all small JSON, which OkHttp buffers by default.

### Why this lives at OkHttp, not Ktor

Ktor has its own `Auth` plugin with a similar single-flight pattern. We push it down to OkHttp
because:

1. The OkHttp interceptor also runs for non-Ktor consumers (Coil image loads in this repo).
2. Certificate pinning, logging, and retry policy already live at OkHttp; auth fits the layer.
3. Ktor's plugin doesn't bind to a `runBlocking` bridge as cleanly when you also want a process-wide mutex shared with non-Ktor request flows.

If your app is Ktor-only, the Ktor `Auth` plugin is the simpler choice.

---

## ⚔️ Attacker — "I weaponise your concurrency"

### Bypass 1 — Trigger a refresh storm

If I find an unauthenticated DoS vector that makes the client fire N parallel authenticated
requests with the same expired token, and your interceptor has no mutex, I cause N refreshes.
The first succeeds, the rest get rejected by reuse detection, the lineage is revoked, the user
is signed out. I haven't stolen anything — I've **denied service** to the legitimate user.

**Counter:** the mutex. Refresh storm becomes one refresh, N replays.

### Bypass 2 — Race the access token swap

In a buggy interceptor that:

1. Reads the old access token,
2. Performs a refresh that swaps the access token,
3. Replays the original request *with the old token it captured at step 1*,

…the replay still 401s. Even worse, in some implementations, the replay triggers a second
refresh — feeding bypass 1.

**Counter:** the recheck inside the mutex makes step 1 read the *current* token, and the
replay path uses the fresh one returned from the lock.

### Bypass 3 — Replay an old refresh I lifted earlier

If I grabbed a refresh token via an old MITM (before pinning), I race the legitimate client. If
my refresh lands first, I get a fresh pair; the legitimate client's refresh gets revoked.

**Counter:**
- Pin certificates so the MITM was impossible in the first place ([08-network-warfare.md](08-network-warfare.md)).
- Reuse-detect on the server: when refresh #N is used twice, **revoke the whole lineage** for
  this user × device pair. Both clients get kicked, user must re-login. Telemetry surfaces the
  event to fraud.
- Bind the refresh to a device-bound public key (`cnf` claim) so my stolen refresh fails the
  device check.

### Bypass 4 — Force the interceptor to loop

If your interceptor doesn't have an escape hatch for the refresh call itself, calling
`/auth/refresh` while expired produces: refresh fails 401 → interceptor refreshes → refresh
fails 401 → ... burning your network stack and your battery until a timeout.

**Counter:** the `SKIP_AUTH` header, or — even better — a separate client for the refresh path
that doesn't install the interceptor at all. This repo uses both belts and braces.

### Bypass 5 — Steal the *original* response before replay

If the interceptor reads the 401 body before deciding to refresh, and the 401 body contains
useful info (token bound to UID, expected nonce, …), an attacker who can read the unencrypted
response — TLS broken, plaintext server — has a side channel.

**Counter:** TLS plus pinning eliminate the plaintext side channel. The interceptor closes the
first response before retrying (`firstResponse.close()`) so connections are reused efficiently.

---

## Cross-reference

- **What the refresh server actually does** → [01-stateless-auth.md](01-stateless-auth.md) (rotation), [06-token-lifecycle.md](06-token-lifecycle.md) (reuse detection)
- **Why an MITM cannot snatch the token** → [08-network-warfare.md](08-network-warfare.md)
- **Where the refresh token lives at rest** → [02-hardware-vault.md](02-hardware-vault.md)

## References

- [Part 3 — The Interceptor Pattern: Mastering Transport Concurrency](https://blog.stackademic.com/part-3-the-interceptor-pattern-mastering-transport-concurrency-dbe8eab8939b)
- [OkHttp — Interceptors](https://square.github.io/okhttp/features/interceptors/)
- [Kotlin coroutines — Mutex](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/)
