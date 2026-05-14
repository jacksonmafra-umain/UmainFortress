package com.umain.fortress.network.api

import com.umain.fortress.network.dto.ActiveSessionDto
import com.umain.fortress.network.dto.ActiveSessionsResponse
import com.umain.fortress.network.dto.TrustedDeviceDto
import com.umain.fortress.network.dto.TrustedDevicesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

class SecurityApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listDevices(): DevicesResult {
        val response: HttpResponse = client.get(url("/me/security/devices"))
        return if (response.status == HttpStatusCode.OK) {
            DevicesResult.Success(response.body<TrustedDevicesResponse>().devices)
        } else {
            DevicesResult.Failure("Status ${response.status}")
        }
    }

    suspend fun listSessions(): SessionsResult {
        val response: HttpResponse = client.get(url("/me/security/sessions"))
        return if (response.status == HttpStatusCode.OK) {
            SessionsResult.Success(response.body<ActiveSessionsResponse>().sessions)
        } else {
            SessionsResult.Failure("Status ${response.status}")
        }
    }

    suspend fun revokeDevice(bindingId: String): SecuritySimpleResult {
        val response: HttpResponse = client.delete(url("/me/security/devices/$bindingId"))
        return if (response.status == HttpStatusCode.OK) {
            SecuritySimpleResult.Success
        } else {
            SecuritySimpleResult.Failure("Status ${response.status}")
        }
    }

    suspend fun signOutAll(): SecuritySimpleResult {
        val response: HttpResponse = client.post(url("/me/security/sign-out-all"))
        return if (response.status == HttpStatusCode.OK) {
            SecuritySimpleResult.Success
        } else {
            SecuritySimpleResult.Failure("Status ${response.status}")
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class DevicesResult {
    data class Success(val devices: List<TrustedDeviceDto>) : DevicesResult()
    data class Failure(val message: String) : DevicesResult()
}

sealed class SessionsResult {
    data class Success(val sessions: List<ActiveSessionDto>) : SessionsResult()
    data class Failure(val message: String) : SessionsResult()
}

sealed class SecuritySimpleResult {
    data object Success : SecuritySimpleResult()
    data class Failure(val message: String) : SecuritySimpleResult()
}
