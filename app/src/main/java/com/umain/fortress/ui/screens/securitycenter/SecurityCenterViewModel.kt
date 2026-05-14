package com.umain.fortress.ui.screens.securitycenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.auth.SessionManager
import com.umain.fortress.network.api.DevicesResult
import com.umain.fortress.network.api.SecurityApi
import com.umain.fortress.network.api.SecuritySimpleResult
import com.umain.fortress.network.api.SessionsResult
import com.umain.fortress.network.dto.ActiveSessionDto
import com.umain.fortress.network.dto.TrustedDeviceDto
import com.umain.fortress.security.DeviceIdProvider
import com.umain.fortress.security.IntegrityCheck
import com.umain.fortress.security.IntegrityVerdict
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SecurityCenterViewModel(
    private val securityApi: SecurityApi,
    private val deviceIdProvider: DeviceIdProvider,
    private val integrityCheck: IntegrityCheck,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SecurityCenterUiState(currentDeviceId = deviceIdProvider.current()))
    val state: StateFlow<SecurityCenterUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val verdictDeferred = async { integrityCheck.current() }
            val devicesDeferred = async { securityApi.listDevices() }
            val sessionsDeferred = async { securityApi.listSessions() }

            val verdict = verdictDeferred.await()
            val devicesResult = devicesDeferred.await()
            val sessionsResult = sessionsDeferred.await()

            _state.update { current ->
                val devices = (devicesResult as? DevicesResult.Success)?.devices ?: current.devices
                val sessions = (sessionsResult as? SessionsResult.Success)?.sessions ?: current.sessions
                val error = listOfNotNull(
                    (devicesResult as? DevicesResult.Failure)?.message,
                    (sessionsResult as? SessionsResult.Failure)?.message,
                ).joinToString(" · ").ifBlank { null }
                current.copy(
                    loading = false,
                    devices = devices,
                    sessions = sessions,
                    verdict = verdict,
                    errorMessage = error,
                )
            }
        }
    }

    fun revokeDevice(device: TrustedDeviceDto) {
        if (device.deviceId == _state.value.currentDeviceId) return  // Refuse to lock yourself out
        _state.update { it.copy(revokingIds = it.revokingIds + device.id) }
        viewModelScope.launch {
            when (val r = securityApi.revokeDevice(device.id)) {
                SecuritySimpleResult.Success -> _state.update {
                    it.copy(
                        revokingIds = it.revokingIds - device.id,
                        devices = it.devices.filterNot { d -> d.id == device.id },
                        sessions = it.sessions.filterNot { s -> s.deviceId == device.deviceId },
                    )
                }
                is SecuritySimpleResult.Failure -> _state.update {
                    it.copy(revokingIds = it.revokingIds - device.id, errorMessage = r.message)
                }
            }
        }
    }

    fun signOutEverywhere(onSignedOut: () -> Unit) {
        _state.update { it.copy(signingOutAll = true, errorMessage = null) }
        viewModelScope.launch {
            val r = securityApi.signOutAll()
            if (r is SecuritySimpleResult.Success) {
                sessionManager.clear()
                onSignedOut()
            } else {
                _state.update {
                    it.copy(
                        signingOutAll = false,
                        errorMessage = (r as SecuritySimpleResult.Failure).message,
                    )
                }
            }
        }
    }
}

data class SecurityCenterUiState(
    val loading: Boolean = false,
    val devices: List<TrustedDeviceDto> = emptyList(),
    val sessions: List<ActiveSessionDto> = emptyList(),
    val currentDeviceId: String? = null,
    val revokingIds: Set<String> = emptySet(),
    val signingOutAll: Boolean = false,
    val verdict: IntegrityVerdict = IntegrityVerdict.Trusted,
    val errorMessage: String? = null,
)
