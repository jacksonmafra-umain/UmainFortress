package com.umain.fortress.network.interceptor

import com.umain.fortress.network.dto.RefreshRequest
import com.umain.fortress.network.dto.TokenPairResponse
import com.umain.fortress.security.Session
import com.umain.fortress.security.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * OkHttp interceptor that:
 *
 * 1. Attaches the current access token to every outgoing request as `Authorization: Bearer ...`.
 * 2. On a 401 response, performs a **single-flight** refresh against the backend, replays the
 *    original request with the new token, and returns the replay's response.
 *
 * The single-flight guarantee is critical: multiple concurrent requests that all see a 401
 * must NOT each fire their own refresh (that would race, invalidate each other's refresh
 * tokens, and log the user out). The [refreshMutex] serializes them; only the first refresh
 * runs, the others wait, then they all replay with the freshly minted access token.
 *
 * See docs/03-interceptor-pattern.md for the full rationale and threat model.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val deviceIdProvider: () -> String = { "device-stub" },
) : Interceptor {

    /** Lazy plain Ktor client used only for the refresh call — must not loop back into us. */
    private val refreshClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            expectSuccess = false
        }
    }

    @Volatile
    private var refreshBaseUrl: String? = null

    fun setRefreshBaseUrl(baseUrl: String) {
        refreshBaseUrl = baseUrl
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip refresh/login routes — they handle their own tokens.
        if (originalRequest.header(SKIP_AUTH) != null) {
            return chain.proceed(originalRequest.newBuilder().removeHeader(SKIP_AUTH).build())
        }

        val session = runBlocking { tokenStore.current() }
        val firstResponse = chain.proceed(originalRequest.withBearer(session?.accessToken))
        if (firstResponse.code != HttpStatusCode.Unauthorized.value || session == null) {
            return firstResponse
        }

        Timber.tag("AuthInterceptor").i("401 received — attempting single-flight refresh")
        firstResponse.close()

        val refreshed = runBlocking { refreshIfNeeded(session) }
            ?: return chain.proceed(originalRequest.withBearer(null))

        return chain.proceed(originalRequest.withBearer(refreshed.accessToken))
    }

    private suspend fun refreshIfNeeded(staleSession: Session): Session? = refreshMutex.withLock {
        val current = tokenStore.current() ?: return null
        // If another caller already swapped the access token while we waited, just use theirs.
        if (current.accessToken != staleSession.accessToken) return current

        val baseUrl = refreshBaseUrl ?: return null
        val response: HttpResponse = try {
            refreshClient.post("${baseUrl.trimEnd('/')}/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(
                    RefreshRequest(
                        refreshToken = current.refreshToken,
                        deviceId = deviceIdProvider(),
                    ),
                )
            }
        } catch (t: Throwable) {
            Timber.tag("AuthInterceptor").w(t, "Refresh request failed")
            return null
        }
        if (response.status != HttpStatusCode.OK) {
            Timber.tag("AuthInterceptor").w("Refresh rejected with ${response.status}")
            tokenStore.clear()
            return null
        }
        val pair: TokenPairResponse = response.body()
        val updated = Session(
            accessToken = pair.accessToken,
            refreshToken = pair.refreshToken,
            accessExpiresAtEpochMs = pair.accessExpiresAtEpochMs,
            userId = pair.user.id,
            userEmail = pair.user.email,
            userDisplayName = pair.user.displayName,
        )
        tokenStore.save(updated)
        updated
    }

    private fun okhttp3.Request.withBearer(token: String?): okhttp3.Request {
        if (token.isNullOrBlank()) return this
        return newBuilder()
            .header(HttpHeaders.Authorization, "Bearer $token")
            .build()
    }

    companion object {
        const val SKIP_AUTH = "X-Fortress-Skip-Auth"
        private val refreshMutex = Mutex()
    }
}
