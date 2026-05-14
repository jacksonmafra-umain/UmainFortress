package com.umain.fortress.auth

import com.umain.fortress.network.api.AuthApi
import com.umain.fortress.network.api.AuthResult
import com.umain.fortress.network.dto.TokenPairResponse
import com.umain.fortress.security.DeviceIdProvider
import com.umain.fortress.security.Session
import com.umain.fortress.security.TokenStore

/**
 * Write-side authority for login / logout / refresh on disk. The interceptor handles
 * transparent refresh during live network calls; this class is for the *intentional* flows
 * (user-driven login, user-driven sign out).
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val sessionManager: SessionManager,
    private val deviceIdProvider: DeviceIdProvider,
    private val deviceBindingEnroller: DeviceBindingEnroller,
) {

    suspend fun login(email: String, password: String): LoginOutcome {
        return when (val result = authApi.login(email, password, deviceIdProvider.current())) {
            is AuthResult.Success -> {
                tokenStore.save(result.tokens.toSession())
                // Best-effort device-binding enrolment. Failure here doesn't block the login;
                // step-up flows that need a signed challenge will surface the gap.
                deviceBindingEnroller.enrolBestEffort()
                LoginOutcome.Success
            }
            is AuthResult.Rejected -> LoginOutcome.Rejected(result.message)
            is AuthResult.NetworkFailure -> LoginOutcome.NetworkError(result.message)
        }
    }

    suspend fun signOut() {
        sessionManager.clear()
    }

    private fun TokenPairResponse.toSession(): Session = Session(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessExpiresAtEpochMs = accessExpiresAtEpochMs,
        userId = user.id,
        userEmail = user.email,
        userDisplayName = user.displayName,
    )
}

sealed class LoginOutcome {
    data object Success : LoginOutcome()
    data class Rejected(val message: String) : LoginOutcome()
    data class NetworkError(val message: String) : LoginOutcome()
}
