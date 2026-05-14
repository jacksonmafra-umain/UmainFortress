package com.umain.fortress.network.api

import com.umain.fortress.network.dto.CardRevealResponse
import com.umain.fortress.network.dto.RevealIbanResponse
import com.umain.fortress.network.dto.StepUpChallengeResponse
import com.umain.fortress.network.dto.StepUpVerifyRequest
import com.umain.fortress.network.dto.TransferChallengeRequest
import com.umain.fortress.network.dto.TransferChallengeResponse
import com.umain.fortress.network.dto.TransferVerifyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class StepUpApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun requestRevealChallenge(accountId: String): StepUpChallengeResult {
        val response: HttpResponse = client.post(url("/stepup/reveal/account/$accountId/challenge"))
        return if (response.status == HttpStatusCode.OK) {
            StepUpChallengeResult.Success(response.body<StepUpChallengeResponse>())
        } else {
            StepUpChallengeResult.Failure("Status ${response.status}")
        }
    }

    suspend fun verifyReveal(
        accountId: String,
        nonceB64: String,
        signatureB64: String,
        deviceId: String,
    ): RevealIbanResult {
        val response: HttpResponse = client.post(url("/stepup/reveal/account/$accountId/verify")) {
            contentType(ContentType.Application.Json)
            setBody(StepUpVerifyRequest(nonceB64, signatureB64, deviceId))
        }
        return if (response.status == HttpStatusCode.OK) {
            RevealIbanResult.Success(response.body<RevealIbanResponse>())
        } else {
            RevealIbanResult.Failure("Status ${response.status}")
        }
    }

    suspend fun requestTransferChallenge(request: TransferChallengeRequest): TransferChallengeResult {
        val response: HttpResponse = client.post(url("/stepup/transfer/challenge")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return if (response.status == HttpStatusCode.OK) {
            TransferChallengeResult.Success(response.body<TransferChallengeResponse>())
        } else {
            TransferChallengeResult.Failure("Status ${response.status}")
        }
    }

    suspend fun verifyTransfer(
        nonceB64: String,
        signatureB64: String,
        deviceId: String,
    ): TransferVerifyResult {
        val response: HttpResponse = client.post(url("/stepup/transfer/verify")) {
            contentType(ContentType.Application.Json)
            setBody(StepUpVerifyRequest(nonceB64, signatureB64, deviceId))
        }
        return if (response.status == HttpStatusCode.OK) {
            TransferVerifyResult.Success(response.body<TransferVerifyResponse>())
        } else {
            TransferVerifyResult.Failure("Status ${response.status}")
        }
    }

    suspend fun requestCardRevealChallenge(cardId: String): StepUpChallengeResult {
        val response: HttpResponse = client.post(url("/stepup/reveal/card/$cardId/challenge"))
        return if (response.status == HttpStatusCode.OK) {
            StepUpChallengeResult.Success(response.body<StepUpChallengeResponse>())
        } else {
            StepUpChallengeResult.Failure("Status ${response.status}")
        }
    }

    suspend fun verifyCardReveal(
        cardId: String,
        nonceB64: String,
        signatureB64: String,
        deviceId: String,
    ): CardRevealResult {
        val response: HttpResponse = client.post(url("/stepup/reveal/card/$cardId/verify")) {
            contentType(ContentType.Application.Json)
            setBody(StepUpVerifyRequest(nonceB64, signatureB64, deviceId))
        }
        return if (response.status == HttpStatusCode.OK) {
            CardRevealResult.Success(response.body<CardRevealResponse>())
        } else {
            CardRevealResult.Failure("Status ${response.status}")
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class StepUpChallengeResult {
    data class Success(val response: StepUpChallengeResponse) : StepUpChallengeResult()
    data class Failure(val message: String) : StepUpChallengeResult()
}

sealed class RevealIbanResult {
    data class Success(val response: RevealIbanResponse) : RevealIbanResult()
    data class Failure(val message: String) : RevealIbanResult()
}

sealed class TransferChallengeResult {
    data class Success(val response: TransferChallengeResponse) : TransferChallengeResult()
    data class Failure(val message: String) : TransferChallengeResult()
}

sealed class TransferVerifyResult {
    data class Success(val response: TransferVerifyResponse) : TransferVerifyResult()
    data class Failure(val message: String) : TransferVerifyResult()
}

sealed class CardRevealResult {
    data class Success(val response: CardRevealResponse) : CardRevealResult()
    data class Failure(val message: String) : CardRevealResult()
}
