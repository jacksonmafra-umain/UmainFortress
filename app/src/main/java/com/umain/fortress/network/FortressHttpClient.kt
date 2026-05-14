package com.umain.fortress.network

import com.umain.fortress.BuildConfig
import com.umain.fortress.network.interceptor.AuthInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Two Ktor clients sharing the same OkHttp engine baseline:
 *
 * - [anonymous]   — no auth interceptor; used for login, refresh, register flows.
 * - [authenticated] — adds [AuthInterceptor], which attaches the access token and transparently
 *                    re-issues a refresh on 401 (single-flight via a Mutex, see the interceptor
 *                    source).
 *
 * Certificate pinning lives at the OkHttp layer so Ktor inherits it for free. In the demo build
 * the pin set is empty (we hit a local backend); production should populate it from a list of
 * SHA-256 SPKI pins for the issuing CA + intermediate.
 */
class FortressHttpClient(
    private val baseUrl: String,
    private val authInterceptor: AuthInterceptor,
) {
    private val sharedJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val baseOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .certificatePinner(buildPinner())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor { line -> Timber.tag("HTTP").d(line) }
                            .apply { level = HttpLoggingInterceptor.Level.HEADERS },
                    )
                }
            }
            .build()
    }

    val anonymous: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine { preconfigured = baseOkHttp }
            install(ContentNegotiation) { json(sharedJson) }
            install(Logging) { level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE }
            expectSuccess = false
        }
    }

    val authenticated: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = baseOkHttp.newBuilder()
                    .addInterceptor(authInterceptor)
                    .build()
            }
            install(ContentNegotiation) { json(sharedJson) }
            install(Logging) { level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE }
            expectSuccess = false
        }
    }

    private fun buildPinner(): CertificatePinner {
        // Production: load pins from a config the backend can rotate.
        // Demo: no pins for local backend. See docs/08-network-warfare.md for production pinning.
        return CertificatePinner.Builder().build()
    }
}
