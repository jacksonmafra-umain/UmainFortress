package com.umain.fortress.network.api

import com.umain.fortress.network.dto.CardDto
import com.umain.fortress.network.dto.CardFreezeResponse
import com.umain.fortress.network.dto.CardsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

class CardsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listCards(): CardsResult {
        val response: HttpResponse = client.get(url("/me/cards"))
        return if (response.status == HttpStatusCode.OK) {
            CardsResult.Success(response.body<CardsResponse>().cards)
        } else {
            CardsResult.Failure("Status ${response.status}")
        }
    }

    suspend fun freeze(cardId: String): CardToggleResult = toggle(cardId, freeze = true)
    suspend fun unfreeze(cardId: String): CardToggleResult = toggle(cardId, freeze = false)

    private suspend fun toggle(cardId: String, freeze: Boolean): CardToggleResult {
        val path = if (freeze) "/me/cards/$cardId/freeze" else "/me/cards/$cardId/unfreeze"
        val response: HttpResponse = client.post(url(path))
        return if (response.status == HttpStatusCode.OK) {
            CardToggleResult.Success(response.body<CardFreezeResponse>().card)
        } else {
            CardToggleResult.Failure("Status ${response.status}")
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class CardsResult {
    data class Success(val cards: List<CardDto>) : CardsResult()
    data class Failure(val message: String) : CardsResult()
}

sealed class CardToggleResult {
    data class Success(val card: CardDto) : CardToggleResult()
    data class Failure(val message: String) : CardToggleResult()
}
