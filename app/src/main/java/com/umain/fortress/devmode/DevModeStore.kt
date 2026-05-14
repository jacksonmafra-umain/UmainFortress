package com.umain.fortress.devmode

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.devModeDataStore by preferencesDataStore(name = "fortress_devmode")

/**
 * Persistent toggles for the Dev Mode screen — used to simulate attack scenarios at runtime.
 *
 * Each flag fakes a single condition (root detected, MITM, replayed challenge, etc.) so the
 * rest of the app can react as it would in the real attack. Production builds gate access to
 * this store behind `BuildConfig.ALLOW_DEV_MODE`.
 */
class DevModeStore(private val context: Context) {

    val state: Flow<DevModeState> = context.devModeDataStore.data.map { prefs ->
        DevModeState(
            simulateRoot = prefs[KEY_SIM_ROOT] ?: false,
            simulateMitm = prefs[KEY_SIM_MITM] ?: false,
            simulateReplay = prefs[KEY_SIM_REPLAY] ?: false,
            simulateIntegrityFail = prefs[KEY_SIM_INTEGRITY] ?: false,
        )
    }

    suspend fun setSimulateRoot(value: Boolean) =
        context.devModeDataStore.edit { it[KEY_SIM_ROOT] = value }.let {}

    suspend fun setSimulateMitm(value: Boolean) =
        context.devModeDataStore.edit { it[KEY_SIM_MITM] = value }.let {}

    suspend fun setSimulateReplay(value: Boolean) =
        context.devModeDataStore.edit { it[KEY_SIM_REPLAY] = value }.let {}

    suspend fun setSimulateIntegrityFail(value: Boolean) =
        context.devModeDataStore.edit { it[KEY_SIM_INTEGRITY] = value }.let {}

    private companion object {
        val KEY_SIM_ROOT = booleanPreferencesKey("sim_root")
        val KEY_SIM_MITM = booleanPreferencesKey("sim_mitm")
        val KEY_SIM_REPLAY = booleanPreferencesKey("sim_replay")
        val KEY_SIM_INTEGRITY = booleanPreferencesKey("sim_integrity")
    }
}

data class DevModeState(
    val simulateRoot: Boolean = false,
    val simulateMitm: Boolean = false,
    val simulateReplay: Boolean = false,
    val simulateIntegrityFail: Boolean = false,
)
