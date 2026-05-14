package com.umain.fortress.ui.screens.cards

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.network.api.CardRevealResult
import com.umain.fortress.network.api.CardsApi
import com.umain.fortress.network.api.CardsResult
import com.umain.fortress.network.api.CardToggleResult
import com.umain.fortress.network.api.StepUpApi
import com.umain.fortress.network.api.StepUpChallengeResult
import com.umain.fortress.network.dto.CardDto
import com.umain.fortress.network.dto.CardRevealResponse
import com.umain.fortress.security.BiometricKeyStore
import com.umain.fortress.security.DeviceIdProvider
import com.umain.fortress.security.StepUpAuthenticator
import com.umain.fortress.security.StepUpError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CardsViewModel(
    private val cardsApi: CardsApi,
    private val stepUpApi: StepUpApi,
    private val stepUpAuthenticator: StepUpAuthenticator,
    private val deviceIdProvider: DeviceIdProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CardsUiState())
    val state: StateFlow<CardsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = cardsApi.listCards()) {
                is CardsResult.Success -> _state.update {
                    it.copy(loading = false, cards = r.cards)
                }
                is CardsResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.message)
                }
            }
        }
    }

    fun toggleFreeze(card: CardDto) {
        _state.update { it.copy(togglingIds = it.togglingIds + card.id, errorMessage = null) }
        viewModelScope.launch {
            val r = if (card.frozen) cardsApi.unfreeze(card.id) else cardsApi.freeze(card.id)
            _state.update { current ->
                val nextToggling = current.togglingIds - card.id
                when (r) {
                    is CardToggleResult.Success -> current.copy(
                        togglingIds = nextToggling,
                        cards = current.cards.map { if (it.id == r.card.id) r.card else it },
                        revealed = if (r.card.frozen) current.revealed - r.card.id else current.revealed,
                    )
                    is CardToggleResult.Failure -> current.copy(
                        togglingIds = nextToggling,
                        errorMessage = r.message,
                    )
                }
            }
        }
    }

    fun reveal(activity: FragmentActivity, card: CardDto) {
        if (card.frozen) {
            _state.update { it.copy(errorMessage = "Unfreeze the card before revealing the PAN") }
            return
        }
        _state.update { it.copy(revealingIds = it.revealingIds + card.id, errorMessage = null) }
        viewModelScope.launch {
            val challenge = when (val c = stepUpApi.requestCardRevealChallenge(card.id)) {
                is StepUpChallengeResult.Success -> c.response
                is StepUpChallengeResult.Failure -> {
                    _state.update { it.copy(revealingIds = it.revealingIds - card.id, errorMessage = "Challenge: ${c.message}") }
                    return@launch
                }
            }
            val nonceBytes = Base64.decode(challenge.nonceB64, Base64.NO_WRAP)
            val prompt = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Reveal card")
                .setSubtitle("${card.brand.uppercase()} · ${card.panMasked}")
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
                _state.update { it.copy(revealingIds = it.revealingIds - card.id, errorMessage = e.message) }
                return@launch
            }
            val sigB64 = Base64.encodeToString(signed, Base64.NO_WRAP)
            when (val v = stepUpApi.verifyCardReveal(
                cardId = card.id,
                nonceB64 = challenge.nonceB64,
                signatureB64 = sigB64,
                deviceId = deviceIdProvider.current(),
            )) {
                is CardRevealResult.Success -> _state.update {
                    it.copy(
                        revealingIds = it.revealingIds - card.id,
                        revealed = it.revealed + (card.id to v.response),
                    )
                }
                is CardRevealResult.Failure -> _state.update {
                    it.copy(revealingIds = it.revealingIds - card.id, errorMessage = "Verify: ${v.message}")
                }
            }
        }
    }

    fun hide(cardId: String) {
        _state.update { it.copy(revealed = it.revealed - cardId) }
    }
}

data class CardsUiState(
    val loading: Boolean = false,
    val cards: List<CardDto> = emptyList(),
    val revealed: Map<String, CardRevealResponse> = emptyMap(),
    val togglingIds: Set<String> = emptySet(),
    val revealingIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
)
