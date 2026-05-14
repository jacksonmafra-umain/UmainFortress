package com.umain.fortress.ui.screens.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.auth.SessionManager
import com.umain.fortress.auth.SessionState
import com.umain.fortress.security.BiometricKeyStore
import com.umain.fortress.security.StepUpAuthenticator
import com.umain.fortress.security.StepUpError
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom

class BiometricUnlockViewModel(
    private val sessionManager: SessionManager,
    private val stepUpAuthenticator: StepUpAuthenticator,
) : ViewModel() {

    private val _state = MutableStateFlow(BiometricUnlockUiState())
    val state: StateFlow<BiometricUnlockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val active = sessionManager.stateFlow.first()
            _state.update { it.copy(displayName = (active as? SessionState.Active)?.session?.userDisplayName) }
        }
    }

    fun authenticate(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Fortress")
            .setSubtitle("Use your biometric to continue")
            .setNegativeButtonText("Sign out")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }

        _state.update { it.copy(prompting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // For unlock we use the device-binding signing key, freshly seeded with a random
                // challenge so a TEE-signed signature must occur right now to proceed.
                stepUpAuthenticator.signChallenge(
                    activity = activity,
                    alias = BiometricKeyStore.ALIAS_DEVICE_BINDING,
                    challenge = challenge,
                    prompt = prompt,
                )
                _state.update { it.copy(prompting = false) }
                onUnlocked()
            } catch (e: StepUpError) {
                _state.update { it.copy(prompting = false, errorMessage = e.message) }
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            sessionManager.clear()
            onSignedOut()
        }
    }
}

data class BiometricUnlockUiState(
    val displayName: String? = null,
    val prompting: Boolean = false,
    val errorMessage: String? = null,
)
