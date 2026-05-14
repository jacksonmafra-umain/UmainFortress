package com.umain.fortress.di

import com.umain.fortress.auth.AuthRepository
import com.umain.fortress.auth.DeviceBindingEnroller
import com.umain.fortress.auth.SessionManager
import com.umain.fortress.ui.screens.accountdetail.AccountDetailViewModel
import com.umain.fortress.ui.screens.accounts.AccountsViewModel
import com.umain.fortress.ui.screens.auth.LoginViewModel
import com.umain.fortress.ui.screens.biometric.BiometricUnlockViewModel
import com.umain.fortress.ui.screens.dashboard.DashboardViewModel
import com.umain.fortress.ui.screens.onboarding.OnboardingViewModel
import com.umain.fortress.ui.screens.splash.SplashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    single { SessionManager(get()) }
    single { DeviceBindingEnroller(get(), get(), get()) }
    single { AuthRepository(get(), get(), get(), get(), get()) }

    viewModelOf(::SplashViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::BiometricUnlockViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::AccountsViewModel)
    viewModel { params -> AccountDetailViewModel(params.get(), get(), get(), get(), get()) }
}
