package com.umain.fortress.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.auth.SessionManager
import com.umain.fortress.auth.SessionState
import com.umain.fortress.onboarding.OnboardingStore
import com.umain.fortress.security.IntegrityCheck
import com.umain.fortress.security.IntegrityVerdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val sessionManager: SessionManager,
    private val integrityCheck: IntegrityCheck,
    private val onboardingStore: OnboardingStore,
) : ViewModel() {

    private val _decision = MutableStateFlow<SplashDecision>(SplashDecision.Loading)
    val decision: StateFlow<SplashDecision> = _decision.asStateFlow()

    init {
        viewModelScope.launch {
            // Give the integrity + session probes a minimum visible duration so the splash
            // doesn't flicker — feels intentional rather than glitchy.
            delay(450)
            val verdict = integrityCheck.current()
            val session = sessionManager.snapshot()
            val hasSeenOnboarding = onboardingStore.snapshot()
            _decision.value = decide(verdict, session, hasSeenOnboarding)
        }
    }

    private fun decide(
        verdict: IntegrityVerdict,
        state: SessionState,
        hasSeenOnboarding: Boolean,
    ): SplashDecision = when {
        verdict is IntegrityVerdict.Untrusted -> SplashDecision.Blocked(verdict.reasons)
        state is SessionState.Active -> SplashDecision.GoBiometric
        !hasSeenOnboarding -> SplashDecision.GoOnboarding
        state is SessionState.Expired -> SplashDecision.GoLogin
        else -> SplashDecision.GoLogin
    }
}

sealed class SplashDecision {
    data object Loading : SplashDecision()
    data object GoOnboarding : SplashDecision()
    data object GoLogin : SplashDecision()
    data object GoBiometric : SplashDecision()
    data class Blocked(val reasons: List<String>) : SplashDecision()
}
