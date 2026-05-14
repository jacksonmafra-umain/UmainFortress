package com.umain.fortress.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String,
    val displayName: String,
    val type: String,
    val maskedNumber: String,
    val balanceMinorUnits: Long,
    val currency: String,
)

@Serializable
data class TransactionDto(
    val id: String,
    val accountId: String,
    val timestampEpochMs: Long,
    val description: String,
    val counterparty: String,
    val amountMinorUnits: Long,
    val currency: String,
    val category: String,
    val riskLevel: String,
)

@Serializable
data class DashboardSnapshot(
    val primaryAccount: AccountDto,
    val accounts: List<AccountDto>,
    val recentTransactions: List<TransactionDto>,
)
