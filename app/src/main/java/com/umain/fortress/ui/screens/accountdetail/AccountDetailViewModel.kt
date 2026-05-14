package com.umain.fortress.ui.screens.accountdetail

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.network.api.AccountDetailResult
import com.umain.fortress.network.api.AccountsApi
import com.umain.fortress.network.api.RevealIbanResult
import com.umain.fortress.network.api.StepUpApi
import com.umain.fortress.network.api.StepUpChallengeResult
import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.network.dto.TransactionDto
import com.umain.fortress.security.BiometricKeyStore
import com.umain.fortress.security.DeviceIdProvider
import com.umain.fortress.security.StepUpAuthenticator
import com.umain.fortress.security.StepUpError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountDetailViewModel(
    private val accountId: String,
    private val accountsApi: AccountsApi,
    private val stepUpApi: StepUpApi,
    private val stepUpAuthenticator: StepUpAuthenticator,
    private val deviceIdProvider: DeviceIdProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountDetailUiState())
    val state: StateFlow<AccountDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = accountsApi.accountDetail(accountId)) {
                is AccountDetailResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        account = r.detail.account,
                        transactions = r.detail.transactions,
                    )
                }
                is AccountDetailResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.message)
                }
            }
        }
    }

    fun revealIban(activity: FragmentActivity) {
        _state.update { it.copy(revealing = true, revealError = null) }
        viewModelScope.launch {
            val challengeResp = when (val c = stepUpApi.requestRevealChallenge(accountId)) {
                is StepUpChallengeResult.Success -> c.response
                is StepUpChallengeResult.Failure -> {
                    _state.update {
                        it.copy(revealing = false, revealError = "Challenge: ${c.message}")
                    }
                    return@launch
                }
            }
            val nonceBytes = Base64.decode(challengeResp.nonceB64, Base64.NO_WRAP)
            val prompt = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Reveal IBAN")
                .setSubtitle("Use your biometric to show the full IBAN")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            val signedBytes = try {
                stepUpAuthenticator.signChallenge(
                    activity = activity,
                    alias = BiometricKeyStore.ALIAS_DEVICE_BINDING,
                    challenge = nonceBytes,
                    prompt = prompt,
                )
            } catch (e: StepUpError) {
                _state.update { it.copy(revealing = false, revealError = e.message) }
                return@launch
            }
            val sigB64 = Base64.encodeToString(signedBytes, Base64.NO_WRAP)
            when (val v = stepUpApi.verifyReveal(
                accountId = accountId,
                nonceB64 = challengeResp.nonceB64,
                signatureB64 = sigB64,
                deviceId = deviceIdProvider.current(),
            )) {
                is RevealIbanResult.Success -> _state.update {
                    it.copy(revealing = false, revealedIban = v.response.ibanFull)
                }
                is RevealIbanResult.Failure -> _state.update {
                    it.copy(revealing = false, revealError = "Verify: ${v.message}")
                }
            }
        }
    }
}

data class AccountDetailUiState(
    val loading: Boolean = false,
    val account: AccountDto? = null,
    val transactions: List<TransactionDto> = emptyList(),
    val errorMessage: String? = null,
    val revealing: Boolean = false,
    val revealedIban: String? = null,
    val revealError: String? = null,
)
