package com.umain.fortress.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umain.fortress.onboarding.OnboardingStore
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val onboardingStore: OnboardingStore,
) : ViewModel() {

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            onboardingStore.markSeen()
            onDone()
        }
    }
}
