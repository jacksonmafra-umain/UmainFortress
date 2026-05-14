package com.umain.fortress.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransferChallengeRequest(
    val sourceAccountId: String,
    val recipientName: String,
    val recipientIban: String,
    val amountMinorUnits: Long,
    val currency: String,
    val memo: String? = null,
)

@Serializable
data class TransferChallengeSummary(
    val sourceAccountDisplayName: String,
    val sourceMaskedNumber: String,
    val recipientName: String,
    val recipientIban: String,
    val amountMinorUnits: Long,
    val currency: String,
    val memo: String? = null,
)

@Serializable
data class TransferChallengeResponse(
    val nonceB64: String,
    val expiresAtEpochMs: Long,
    val summary: TransferChallengeSummary,
)

@Serializable
data class TransferVerifyResponse(
    val transactionId: String,
    val newBalanceMinorUnits: Long,
    val currency: String,
)
