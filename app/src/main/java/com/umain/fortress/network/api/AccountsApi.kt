package com.umain.fortress.network.api

import com.umain.fortress.network.dto.DashboardSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

class AccountsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun dashboard(): DashboardResult {
        val response: HttpResponse = client.get(url("/me/dashboard"))
        return if (response.status == HttpStatusCode.OK) {
            DashboardResult.Success(response.body())
        } else {
            DashboardResult.Failure("Status ${response.status}")
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class DashboardResult {
    data class Success(val snapshot: DashboardSnapshot) : DashboardResult()
    data class Failure(val message: String) : DashboardResult()
}
