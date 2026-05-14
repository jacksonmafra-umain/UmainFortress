package com.umain.fortress.network.api

import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.network.dto.DashboardSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

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

    suspend fun listAccounts(): AccountsResult {
        val response: HttpResponse = client.get(url("/me/accounts"))
        return if (response.status == HttpStatusCode.OK) {
            AccountsResult.Success(response.body<AccountsResponse>().accounts)
        } else {
            AccountsResult.Failure("Status ${response.status}")
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"
}

sealed class DashboardResult {
    data class Success(val snapshot: DashboardSnapshot) : DashboardResult()
    data class Failure(val message: String) : DashboardResult()
}

sealed class AccountsResult {
    data class Success(val accounts: List<AccountDto>) : AccountsResult()
    data class Failure(val message: String) : AccountsResult()
}

@Serializable
private data class AccountsResponse(val accounts: List<AccountDto>)
