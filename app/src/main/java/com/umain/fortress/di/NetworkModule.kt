package com.umain.fortress.di

import com.umain.fortress.BuildConfig
import com.umain.fortress.network.FortressHttpClient
import com.umain.fortress.network.api.AccountsApi
import com.umain.fortress.network.api.AuthApi
import com.umain.fortress.network.api.CardsApi
import com.umain.fortress.network.api.DeviceBindingApi
import com.umain.fortress.network.api.StepUpApi
import com.umain.fortress.network.interceptor.AuthInterceptor
import com.umain.fortress.security.DeviceIdProvider
import org.koin.dsl.module

val networkModule = module {
    single {
        AuthInterceptor(
            tokenStore = get(),
            deviceIdProvider = { get<DeviceIdProvider>().current() },
        ).also { it.setRefreshBaseUrl(BuildConfig.BASE_URL) }
    }
    single { FortressHttpClient(baseUrl = BuildConfig.BASE_URL, authInterceptor = get()) }
    single { AuthApi(get<FortressHttpClient>().anonymous, BuildConfig.BASE_URL) }
    single { AccountsApi(get<FortressHttpClient>().authenticated, BuildConfig.BASE_URL) }
    single { CardsApi(get<FortressHttpClient>().authenticated, BuildConfig.BASE_URL) }
    single { DeviceBindingApi(get<FortressHttpClient>().authenticated, BuildConfig.BASE_URL) }
    single { StepUpApi(get<FortressHttpClient>().authenticated, BuildConfig.BASE_URL) }
}
