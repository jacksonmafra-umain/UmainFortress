package com.umain.fortress.ui.screens.devmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.devmode.DevModeState
import com.umain.fortress.devmode.DevModeStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevModeViewModel(
    private val devModeStore: DevModeStore,
) : ViewModel() {

    val state: StateFlow<DevModeState> = devModeStore.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DevModeState(),
    )

    fun setSimulateRoot(value: Boolean) =
        viewModelScope.launch { devModeStore.setSimulateRoot(value) }.let {}

    fun setSimulateMitm(value: Boolean) =
        viewModelScope.launch { devModeStore.setSimulateMitm(value) }.let {}

    fun setSimulateReplay(value: Boolean) =
        viewModelScope.launch { devModeStore.setSimulateReplay(value) }.let {}

    fun setSimulateIntegrityFail(value: Boolean) =
        viewModelScope.launch { devModeStore.setSimulateIntegrityFail(value) }.let {}

    fun reset() {
        viewModelScope.launch {
            devModeStore.setSimulateRoot(false)
            devModeStore.setSimulateMitm(false)
            devModeStore.setSimulateReplay(false)
            devModeStore.setSimulateIntegrityFail(false)
        }
    }
}
