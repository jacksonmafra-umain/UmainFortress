package com.umain.fortress.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CardDto(
    val id: String,
    val brand: String,
    val variant: String,
    val holderName: String,
    val panMasked: String,
    val expMonth: Int,
    val expYear: Int,
    val frozen: Boolean,
    val linkedAccountId: String? = null,
)

@Serializable
data class CardsResponse(val cards: List<CardDto>)

@Serializable
data class CardFreezeResponse(val card: CardDto)

@Serializable
data class CardRevealResponse(
    val panFull: String,
    val cvvFull: String,
    val expMonth: Int,
    val expYear: Int,
)
