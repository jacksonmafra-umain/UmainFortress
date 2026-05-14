package com.umain.fortress.ui.screens.addcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.network.api.CardCreateResult
import com.umain.fortress.network.api.CardsApi
import com.umain.fortress.network.dto.CreateCardRequest
import com.umain.fortress.ui.screens.cards.CardsRefreshBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * View-model for the Add Card flow. Holds the form state, validates client-side so the
 * Submit button reflects readiness, and POSTs to `/me/cards` via [CardsApi.createCard].
 */
class AddCardViewModel(
    private val cardsApi: CardsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(AddCardUiState())
    val state: StateFlow<AddCardUiState> = _state.asStateFlow()

    fun onHolderName(v: String) = _state.update { it.copy(holderName = v, errorMessage = null) }
    fun onLast4(v: String) = _state.update {
        it.copy(last4 = v.filter(Char::isDigit).take(4), errorMessage = null)
    }
    fun onExpMonth(v: String) = _state.update {
        it.copy(expMonth = v.filter(Char::isDigit).take(2), errorMessage = null)
    }
    fun onExpYear(v: String) = _state.update {
        it.copy(expYear = v.filter(Char::isDigit).take(4), errorMessage = null)
    }
    fun onBrand(brand: CardBrand) = _state.update { it.copy(brand = brand, errorMessage = null) }
    fun onVariant(variant: CardVariant) = _state.update { it.copy(variant = variant, errorMessage = null) }

    fun submit(onCreated: () -> Unit) {
        val current = _state.value
        if (!current.isValid) return
        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val request = CreateCardRequest(
                brand = current.brand.wire,
                variant = current.variant.wire,
                holderName = current.holderName.trim(),
                last4 = current.last4,
                expMonth = current.expMonth.toInt(),
                expYear = current.expYear.toInt(),
            )
            when (val r = cardsApi.createCard(request)) {
                is CardCreateResult.Success -> {
                    CardsRefreshBus.signal()
                    _state.update { it.copy(submitting = false, created = r.card) }
                    onCreated()
                }
                is CardCreateResult.Failure -> _state.update {
                    it.copy(submitting = false, errorMessage = r.message)
                }
            }
        }
    }
}

/** Card brand offered by the picker. [wire] is the value sent over the API. */
enum class CardBrand(val wire: String, val label: String) {
    Visa("visa", "Visa"),
    Mastercard("mastercard", "Mastercard"),
    Amex("amex", "Amex"),
}

/** Card variant offered by the picker. [wire] is the value sent over the API. */
enum class CardVariant(val wire: String, val label: String) {
    Virtual("virtual", "Virtual"),
    Debit("debit", "Debit"),
    Credit("credit", "Credit"),
}

/**
 * UI state for [AddCardScreen]. [isValid] is recomputed eagerly from the typed inputs so
 * the Submit button can bind to a single boolean.
 */
data class AddCardUiState(
    val brand: CardBrand = CardBrand.Visa,
    val variant: CardVariant = CardVariant.Virtual,
    val holderName: String = "",
    val last4: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val created: com.umain.fortress.network.dto.CardDto? = null,
) {
    val isValid: Boolean
        get() {
            if (submitting) return false
            if (holderName.trim().length < 2) return false
            if (!last4.matches(Regex("^\\d{4}$"))) return false
            val m = expMonth.toIntOrNull() ?: return false
            if (m !in 1..12) return false
            val y = expYear.toIntOrNull() ?: return false
            val thisYear = Calendar.getInstance().get(Calendar.YEAR)
            if (y !in thisYear..(thisYear + 20)) return false
            return true
        }
}
