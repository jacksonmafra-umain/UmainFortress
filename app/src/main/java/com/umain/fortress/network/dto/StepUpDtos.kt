package com.umain.fortress.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceBindingRegisterRequest(
    val deviceId: String,
    val publicKeySpkiB64: String,
)

@Serializable
data class DeviceBindingRegisterResponse(val ok: Boolean = false)

@Serializable
data class StepUpChallengeResponse(
    val nonceB64: String,
    val expiresAtEpochMs: Long,
)

@Serializable
data class StepUpVerifyRequest(
    val nonceB64: String,
    val signatureB64: String,
    val deviceId: String,
)

@Serializable
data class RevealIbanResponse(val ibanFull: String)

@Serializable
data class AccountDetailResponse(
    val account: AccountDto,
    val transactions: List<TransactionDto>,
)
