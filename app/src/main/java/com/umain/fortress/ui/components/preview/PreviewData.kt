package com.umain.fortress.ui.components.preview

import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.network.dto.CardDto
import com.umain.fortress.network.dto.TransactionDto

/**
 * Hand-crafted preview fixtures for the design-system catalogue. Mirrors the shape of the
 * real DTOs so component previews exercise the same code paths as the running app.
 */
object PreviewData {

    /** Primary chequing account with a large positive balance. */
    val primaryAccount = AccountDto(
        id = "acc_primary",
        displayName = "Jack Walson",
        type = "checking",
        maskedNumber = "•••• 4455",
        balanceMinorUnits = 3_118_024L,
        currency = "USD",
    )

    /** Savings account used to exercise the alternate account icon. */
    val savingsAccount = AccountDto(
        id = "acc_savings",
        displayName = "Holiday savings",
        type = "savings",
        maskedNumber = "•••• 1599",
        balanceMinorUnits = 425_000L,
        currency = "USD",
    )

    /** Default-brand virtual card used by the card carousel preview. */
    val visaCard = CardDto(
        id = "card_visa",
        brand = "Visa",
        variant = "physical",
        holderName = "Jack Walson",
        panMasked = "•••• •••• •••• 4455",
        expMonth = 12,
        expYear = 2029,
        frozen = false,
        linkedAccountId = "acc_primary",
    )

    /** Mastercard variant, included so the carousel exercises a second gradient. */
    val masterCard = CardDto(
        id = "card_mc",
        brand = "Mastercard",
        variant = "virtual",
        holderName = "Jack Walson",
        panMasked = "•••• •••• •••• 1599",
        expMonth = 6,
        expYear = 2027,
        frozen = false,
        linkedAccountId = "acc_savings",
    )

    /** Outgoing debit transaction, rendered in the on-surface ink colour. */
    val debitTransaction = TransactionDto(
        id = "tx_debit",
        accountId = "acc_primary",
        timestampEpochMs = 0L,
        description = "Apple Inc",
        counterparty = "30 min ago",
        amountMinorUnits = -4_500L,
        currency = "USD",
        category = "shopping",
        riskLevel = "low",
    )

    /** Inbound credit, rendered in the success-green colour with the Receive icon. */
    val creditTransaction = TransactionDto(
        id = "tx_credit",
        accountId = "acc_primary",
        timestampEpochMs = 0L,
        description = "Jerry Helfer",
        counterparty = "12 Dec 2024",
        amountMinorUnits = 1_200L,
        currency = "USD",
        category = "transfer",
        riskLevel = "low",
    )

    /** Medium-risk transaction used to exercise the risk badge. */
    val riskyTransaction = TransactionDto(
        id = "tx_risk",
        accountId = "acc_primary",
        timestampEpochMs = 0L,
        description = "Dribbble",
        counterparty = "11 Dec 2024",
        amountMinorUnits = -3_500L,
        currency = "USD",
        category = "subscription",
        riskLevel = "medium",
    )
}
