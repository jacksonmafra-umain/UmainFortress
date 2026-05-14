package com.umain.fortress.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TrustedDeviceDto(
    val id: String,
    val deviceId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class TrustedDevicesResponse(val devices: List<TrustedDeviceDto>)

@Serializable
data class ActiveSessionDto(
    val id: String,
    val deviceId: String,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

@Serializable
data class ActiveSessionsResponse(val sessions: List<ActiveSessionDto>)

@Serializable
data class SecurityOkResponse(val ok: Boolean = false)
