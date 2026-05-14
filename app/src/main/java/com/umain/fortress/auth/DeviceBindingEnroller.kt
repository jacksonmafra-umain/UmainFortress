package com.umain.fortress.auth

import android.util.Base64
import com.umain.fortress.network.api.DeviceBindingApi
import com.umain.fortress.network.api.DeviceBindingResult
import com.umain.fortress.security.BiometricKeyStore
import com.umain.fortress.security.DeviceIdProvider
import timber.log.Timber

/**
 * Generates the device-binding public key in the Android Keystore (if absent) and registers it
 * with the backend so subsequent step-up signatures can be verified.
 *
 * Best-effort: if the device has no biometric enrolled yet, key generation throws and we log
 * + bail out. The user can still use the app for non-sensitive operations; the next login (or
 * a manual retry from the Security Center) will try again.
 */
class DeviceBindingEnroller(
    private val biometricKeyStore: BiometricKeyStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val deviceBindingApi: DeviceBindingApi,
) {
    suspend fun enrolBestEffort(): Outcome = try {
        val publicKey = biometricKeyStore.getOrCreatePublicKey(BiometricKeyStore.ALIAS_DEVICE_BINDING)
        val spki = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        when (val r = deviceBindingApi.register(deviceIdProvider.current(), spki)) {
            is DeviceBindingResult.Success -> Outcome.Success
            is DeviceBindingResult.Failure -> {
                Timber.tag(TAG).w("Backend registration failed: ${r.message}")
                Outcome.Failed(r.message)
            }
        }
    } catch (t: Throwable) {
        Timber.tag(TAG).w(t, "Key generation or encoding failed")
        Outcome.Failed(t.message ?: "Enrolment failed")
    }

    sealed class Outcome {
        data object Success : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    private companion object {
        const val TAG = "DeviceBindingEnroller"
    }
}
