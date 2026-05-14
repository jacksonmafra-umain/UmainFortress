package com.umain.fortress.di

import com.umain.fortress.auth.AuthRepository
import com.umain.fortress.auth.DeviceBindingEnroller
import com.umain.fortress.auth.SessionManager
import com.umain.fortress.ui.screens.accountdetail.AccountDetailViewModel
import com.umain.fortress.ui.screens.accounts.AccountsViewModel
import com.umain.fortress.ui.screens.auth.LoginViewModel
import com.umain.fortress.ui.screens.biometric.BiometricUnlockViewModel
import com.umain.fortress.ui.screens.cards.CardsViewModel
import com.umain.fortress.ui.screens.dashboard.DashboardViewModel
import com.umain.fortress.ui.screens.devmode.DevModeViewModel
import com.umain.fortress.ui.screens.onboarding.OnboardingViewModel
import com.umain.fortress.ui.screens.securitycenter.SecurityCenterViewModel
import com.umain.fortress.ui.screens.splash.SplashViewModel
import com.umain.fortress.ui.screens.transfer.TransferViewModel
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
    viewModelOf(::CardsViewModel)
    viewModelOf(::SecurityCenterViewModel)
    viewModelOf(::DevModeViewModel)
    viewModel { params -> AccountDetailViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> TransferViewModel(params.get(), get(), get(), get(), get()) }
}
