package com.umain.fortress.di

import com.umain.fortress.security.BiometricKeyStore
import com.umain.fortress.security.DeviceIdProvider
import com.umain.fortress.security.IntegrityCheck
import com.umain.fortress.security.KeystoreVault
import com.umain.fortress.security.StepUpAuthenticator
import com.umain.fortress.security.TokenStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val securityModule = module {
    single { KeystoreVault() }
    single { TokenStore(androidContext(), get()) }
    single { BiometricKeyStore() }
    factory { StepUpAuthenticator(get()) }
    single { IntegrityCheck(androidContext(), get()) }
    single { DeviceIdProvider(androidContext()) }
}
