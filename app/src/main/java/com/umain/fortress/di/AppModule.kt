package com.umain.fortress.di

import com.umain.fortress.devmode.DevModeStore
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = false
            explicitNulls = false
        }
    }
    single { DevModeStore(androidContext()) }
}
