package com.umain.fortress.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.network.api.AccountsApi
import com.umain.fortress.network.api.DashboardResult
import com.umain.fortress.network.dto.DashboardSnapshot
import com.umain.fortress.security.IntegrityCheck
import com.umain.fortress.security.IntegrityVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val accountsApi: AccountsApi,
    private val integrityCheck: IntegrityCheck,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val verdict = integrityCheck.current()
            when (val result = accountsApi.dashboard()) {
                is DashboardResult.Success -> _state.update {
                    it.copy(loading = false, snapshot = result.snapshot, integrity = verdict)
                }
                is DashboardResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = result.message, integrity = verdict)
                }
            }
        }
    }
}

data class DashboardUiState(
    val loading: Boolean = false,
    val snapshot: DashboardSnapshot? = null,
    val errorMessage: String? = null,
    val integrity: IntegrityVerdict = IntegrityVerdict.Trusted,
)
