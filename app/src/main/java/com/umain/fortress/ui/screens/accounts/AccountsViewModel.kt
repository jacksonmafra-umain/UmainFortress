package com.umain.fortress.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.network.api.AccountsApi
import com.umain.fortress.network.api.AccountsResult
import com.umain.fortress.network.dto.AccountDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountsViewModel(
    private val accountsApi: AccountsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = accountsApi.listAccounts()) {
                is AccountsResult.Success -> _state.update {
                    it.copy(loading = false, accounts = result.accounts)
                }
                is AccountsResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = result.message)
                }
            }
        }
    }
}

data class AccountsUiState(
    val loading: Boolean = false,
    val accounts: List<AccountDto> = emptyList(),
    val errorMessage: String? = null,
)
