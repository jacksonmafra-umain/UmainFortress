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

/**
 * Request body for `POST /me/cards`.
 *
 * The backend re-generates the masked / full PAN from [last4]; only the four trailing
 * digits the user supplies are persisted as identifying material on the client side.
 */
@Serializable
data class CreateCardRequest(
    val brand: String,
    val variant: String,
    val holderName: String,
    val last4: String,
    val expMonth: Int,
    val expYear: Int,
    val linkedAccountId: String? = null,
)

/** Response body for `POST /me/cards`. */
@Serializable
data class CreateCardResponse(val card: CardDto)
