package com.umain.fortress.network.api

import com.umain.fortress.network.dto.ApiError
import com.umain.fortress.network.dto.LoginRequest
import com.umain.fortress.network.dto.RefreshRequest
import com.umain.fortress.network.dto.TokenPairResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class AuthApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun login(email: String, password: String, deviceId: String): AuthResult {
        val response: HttpResponse = client.post(url("/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = password, deviceId = deviceId))
        }
        return parse(response)
    }

    suspend fun refresh(refreshToken: String, deviceId: String): AuthResult {
        val response: HttpResponse = client.post(url("/auth/refresh")) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = refreshToken, deviceId = deviceId))
        }
        return parse(response)
    }

    private suspend fun parse(response: HttpResponse): AuthResult = when (response.status) {
        HttpStatusCode.OK -> AuthResult.Success(response.body())
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
            val err = runCatching { response.body<ApiError>() }
                .getOrElse { ApiError("UNAUTHORIZED", "Invalid credentials") }
            AuthResult.Rejected(err.code, err.message)
        }
        else -> AuthResult.NetworkFailure("Unexpected status ${response.status}")
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class AuthResult {
    data class Success(val tokens: TokenPairResponse) : AuthResult()
    data class Rejected(val code: String, val message: String) : AuthResult()
    data class NetworkFailure(val message: String) : AuthResult()
}
