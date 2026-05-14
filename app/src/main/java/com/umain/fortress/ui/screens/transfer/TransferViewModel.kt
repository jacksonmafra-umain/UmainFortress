package com.umain.fortress.ui.screens.transfer

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.network.api.AccountDetailResult
import com.umain.fortress.network.api.AccountsApi
import com.umain.fortress.network.api.StepUpApi
import com.umain.fortress.network.api.TransferChallengeResult
import com.umain.fortress.network.api.TransferVerifyResult
import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.network.dto.TransferChallengeRequest
import com.umain.fortress.network.dto.TransferChallengeResponse
import com.umain.fortress.network.dto.TransferVerifyResponse
import com.umain.fortress.security.BiometricKeyStore
import com.umain.fortress.security.DeviceIdProvider
import com.umain.fortress.security.StepUpAuthenticator
import com.umain.fortress.security.StepUpError
import com.umain.fortress.ui.format.formatMinorUnits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransferViewModel(
    private val sourceAccountId: String,
    private val accountsApi: AccountsApi,
    private val stepUpApi: StepUpApi,
    private val stepUpAuthenticator: StepUpAuthenticator,
    private val deviceIdProvider: DeviceIdProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = accountsApi.accountDetail(sourceAccountId)) {
                is AccountDetailResult.Success -> _state.update {
                    it.copy(sourceAccount = r.detail.account, currency = r.detail.account.currency)
                }
                is AccountDetailResult.Failure -> _state.update {
                    it.copy(errorMessage = "Couldn't load source account: ${r.message}")
                }
            }
        }
    }

    fun onRecipientNameChange(v: String) =
        _state.update { it.copy(recipientName = v, errorMessage = null) }

    fun onRecipientIbanChange(v: String) =
        _state.update { it.copy(recipientIban = v.uppercase(), errorMessage = null) }

    fun onAmountChange(v: String) {
        val cleaned = v.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        _state.update { it.copy(amountInput = cleaned, errorMessage = null) }
    }

    fun onMemoChange(v: String) =
        _state.update { it.copy(memo = v.take(140), errorMessage = null) }

    fun review() {
        val current = _state.value
        val amount = parseToMinorUnits(current.amountInput)
        when {
            current.sourceAccount == null -> return
            current.recipientName.isBlank() -> {
                _state.update { it.copy(errorMessage = "Recipient name is required") }
                return
            }
            current.recipientIban.length < 8 -> {
                _state.update { it.copy(errorMessage = "Enter a valid IBAN") }
                return
            }
            amount == null || amount <= 0L -> {
                _state.update { it.copy(errorMessage = "Enter a positive amount") }
                return
            }
            amount > current.sourceAccount.balanceMinorUnits -> {
                _state.update {
                    it.copy(
                        errorMessage = "Amount exceeds available balance " +
                            formatMinorUnits(current.sourceAccount.balanceMinorUnits, current.currency),
                    )
                }
                return
            }
        }

        _state.update { it.copy(phase = TransferPhase.Loading, errorMessage = null) }
        viewModelScope.launch {
            val request = TransferChallengeRequest(
                sourceAccountId = sourceAccountId,
                recipientName = current.recipientName.trim(),
                recipientIban = current.recipientIban.replace(" ", "").trim(),
                amountMinorUnits = amount!!,
                currency = current.currency,
                memo = current.memo.trim().ifBlank { null },
            )
            when (val r = stepUpApi.requestTransferChallenge(request)) {
                is TransferChallengeResult.Success -> _state.update {
                    it.copy(phase = TransferPhase.Reviewing, challenge = r.response)
                }
                is TransferChallengeResult.Failure -> _state.update {
                    it.copy(phase = TransferPhase.Editing, errorMessage = "Challenge: ${r.message}")
                }
            }
        }
    }

    fun confirm(activity: FragmentActivity) {
        val current = _state.value
        val challenge = current.challenge ?: return
        _state.update { it.copy(phase = TransferPhase.Verifying, errorMessage = null) }
        viewModelScope.launch {
            val nonceBytes = Base64.decode(challenge.nonceB64, Base64.NO_WRAP)
            val prompt = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm transfer")
                .setSubtitle(
                    "${formatMinorUnits(challenge.summary.amountMinorUnits, challenge.summary.currency)} " +
                        "to ${challenge.summary.recipientName}",
                )
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            val signed = try {
                stepUpAuthenticator.signChallenge(
                    activity = activity,
                    alias = BiometricKeyStore.ALIAS_DEVICE_BINDING,
                    challenge = nonceBytes,
                    prompt = prompt,
                )
            } catch (e: StepUpError) {
                _state.update {
                    it.copy(phase = TransferPhase.Reviewing, errorMessage = e.message ?: "Biometric failed")
                }
                return@launch
            }
            val sigB64 = Base64.encodeToString(signed, Base64.NO_WRAP)
            when (val r = stepUpApi.verifyTransfer(
                nonceB64 = challenge.nonceB64,
                signatureB64 = sigB64,
                deviceId = deviceIdProvider.current(),
            )) {
                is TransferVerifyResult.Success -> _state.update {
                    it.copy(phase = TransferPhase.Success, result = r.response)
                }
                is TransferVerifyResult.Failure -> _state.update {
                    it.copy(phase = TransferPhase.Reviewing, errorMessage = "Verify: ${r.message}")
                }
            }
        }
    }

    fun backToEditing() = _state.update {
        it.copy(phase = TransferPhase.Editing, challenge = null, errorMessage = null)
    }

    private fun parseToMinorUnits(input: String): Long? {
        if (input.isBlank()) return null
        val parts = input.split('.')
        val majorStr = parts[0].ifEmpty { "0" }
        val major = majorStr.toLongOrNull() ?: return null
        val minorStr = if (parts.size > 1) parts[1].take(2).padEnd(2, '0') else "00"
        val minor = minorStr.toLongOrNull() ?: return null
        return major * 100L + minor
    }
}

data class TransferUiState(
    val sourceAccount: AccountDto? = null,
    val recipientName: String = "",
    val recipientIban: String = "",
    val amountInput: String = "",
    val currency: String = "EUR",
    val memo: String = "",
    val phase: TransferPhase = TransferPhase.Editing,
    val challenge: TransferChallengeResponse? = null,
    val result: TransferVerifyResponse? = null,
    val errorMessage: String? = null,
)

enum class TransferPhase { Editing, Loading, Reviewing, Verifying, Success }
