package com.umain.fortress.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Per-action signing keys gated by **strong** biometric authentication.
 *
 * Each key is:
 * - Hardware-bound (`AndroidKeyStore` provider, StrongBox where available).
 * - ECDSA over P-256 / SHA-256.
 * - Single-operation: `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` — the TEE
 *   authorizes exactly one [Signature.sign] call per biometric ceremony.
 * - Invalidated by biometric enrolment: adding a new fingerprint/face destroys the key, so an
 *   attacker who briefly knows the device PIN cannot keep the existing signing capability.
 *
 * The matching public key is shipped to the backend during enrollment and used to verify the
 * signed step-up challenges. See [StepUpAuthenticator] for the runtime flow.
 */
class BiometricKeyStore {

    fun getOrCreatePublicKey(alias: String): PublicKey {
        ensureKey(alias)
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getCertificate(alias).publicKey
    }

    fun initSignature(alias: String): Signature {
        ensureKey(alias)
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(alias, null) as PrivateKey
        return Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
    }

    fun invalidate(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    private fun ensureKey(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) return

        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        setIsStrongBoxBacked(true)
                    } catch (_: Throwable) { /* fallback handled below on init */ }
                }
            }
            .build()

        try {
            gen.initialize(spec)
            gen.generateKeyPair()
        } catch (_: Exception) {
            // StrongBox unavailable — retry without.
            val fallback = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    }
                }
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            gen.initialize(fallback)
            gen.generateKeyPair()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        const val ALIAS_DEVICE_BINDING = "fortress.biometric.device_binding"
        const val ALIAS_STEP_UP = "fortress.biometric.step_up"
    }
}
