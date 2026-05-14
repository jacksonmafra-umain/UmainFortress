package com.umain.fortress.network.api

import com.umain.fortress.network.dto.DeviceBindingRegisterRequest
import com.umain.fortress.network.dto.DeviceBindingRegisterResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class DeviceBindingApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun register(deviceId: String, publicKeySpkiB64: String): DeviceBindingResult {
        val response: HttpResponse = client.post(url("/auth/device-binding/register")) {
            contentType(ContentType.Application.Json)
            setBody(DeviceBindingRegisterRequest(deviceId, publicKeySpkiB64))
        }
        return if (response.status == HttpStatusCode.OK) {
            DeviceBindingResult.Success(response.body<DeviceBindingRegisterResponse>())
        } else {
            DeviceBindingResult.Failure("Status ${response.status}")
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class DeviceBindingResult {
    data class Success(val response: DeviceBindingRegisterResponse) : DeviceBindingResult()
    data class Failure(val message: String) : DeviceBindingResult()
}
