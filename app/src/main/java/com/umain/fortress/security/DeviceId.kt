package com.umain.fortress.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import java.util.UUID

private val Context.deviceIdDataStore by preferencesDataStore(name = "fortress_device")

/**
 * A persistent per-install device identifier used as one input to backend risk scoring.
 *
 * Generated once on first launch (random 128-bit UUID + secure random suffix), kept in
 * DataStore. Not a hardware identifier — those are unstable, restricted (`ANDROID_ID` is now
 * SSAID-scoped), and privacy-hostile. This ID is paired with the device-binding public key
 * (see [BiometricKeyStore.ALIAS_DEVICE_BINDING]) to bind a session to a specific install.
 */
class DeviceIdProvider(private val context: Context) {

    fun current(): String = runBlocking {
        val existing = context.deviceIdDataStore.data.first()[KEY]
        if (existing != null) return@runBlocking existing
        val fresh = generate()
        context.deviceIdDataStore.edit { it[KEY] = fresh }
        fresh
    }

    private fun generate(): String {
        val suffix = ByteArray(8).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        return "${UUID.randomUUID()}.$suffix"
    }

    private companion object {
        val KEY = stringPreferencesKey("device_id_v1")
    }
}
