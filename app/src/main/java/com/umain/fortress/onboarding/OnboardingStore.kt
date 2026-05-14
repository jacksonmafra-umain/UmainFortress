package com.umain.fortress.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "fortress_onboarding")

/**
 * Tiny preference holding "has this install seen the onboarding pager?".
 *
 * Lives outside the encrypted token vault because (a) it has no security value and (b) it has
 * to be readable at splash time before any session restore has happened.
 */
class OnboardingStore(private val context: Context) {

    val hasSeen: Flow<Boolean> = context.onboardingDataStore.data.map { it[KEY_SEEN] ?: false }

    suspend fun snapshot(): Boolean = hasSeen.first()

    suspend fun markSeen() {
        context.onboardingDataStore.edit { it[KEY_SEEN] = true }
    }

    private companion object {
        val KEY_SEEN = booleanPreferencesKey("has_seen_onboarding_v1")
    }
}
