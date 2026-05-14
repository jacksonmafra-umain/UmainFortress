package com.umain.fortress.auth

import com.umain.fortress.security.Session
import com.umain.fortress.security.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single read-side authority for "is there a session, and if so, is it still alive?".
 *
 * Treats the encrypted [TokenStore] as the source of truth; layers a freshness check on top.
 * Callers who need to *react* to login/logout observe [stateFlow]; one-shot consumers call
 * [snapshot].
 */
class SessionManager(
    private val tokenStore: TokenStore,
) {
    val stateFlow: Flow<SessionState> = tokenStore.session.map { it.toState() }

    suspend fun snapshot(): SessionState = tokenStore.current().toState()

    suspend fun clear() = tokenStore.clear()

    private fun Session?.toState(): SessionState {
        if (this == null) return SessionState.SignedOut
        val now = System.currentTimeMillis()
        return if (now < accessExpiresAtEpochMs) SessionState.Active(this)
        else SessionState.Expired(this)
    }
}

sealed class SessionState {
    data object SignedOut : SessionState()
    data class Active(val session: Session) : SessionState()
    data class Expired(val session: Session) : SessionState()
}
