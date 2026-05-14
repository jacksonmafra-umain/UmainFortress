package com.umain.fortress.security

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs a [BiometricPrompt] bound to a [java.security.Signature] from [BiometricKeyStore], then
 * signs a server-issued challenge inside the authorised `CryptoObject`. The result is the bytes
 * proving "a strong biometric just occurred on this device, and the user authorized THIS
 * payload".
 *
 * Always use this — never invoke [BiometricPrompt] without a `CryptoObject`. A callback boolean
 * can be forged by Frida; signed bytes cannot.
 */
class StepUpAuthenticator(
    private val biometricKeyStore: BiometricKeyStore,
) {
    suspend fun signChallenge(
        activity: FragmentActivity,
        alias: String,
        challenge: ByteArray,
        prompt: PromptInfo,
    ): ByteArray = suspendCancellableCoroutine { cont ->
        val signature = try {
            biometricKeyStore.initSignature(alias)
        } catch (t: Throwable) {
            cont.resumeWithException(StepUpError.KeyUnavailable(t))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    cont.resumeWithException(StepUpError.AuthFailed(errorCode, errString.toString()))
                }

                override fun onAuthenticationFailed() {
                    // Single attempt failed — let the prompt keep showing for retries.
                    // Cancellation is signalled via onAuthenticationError above.
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val sig = result.cryptoObject?.signature
                            ?: throw StepUpError.MissingCryptoObject
                        sig.update(challenge)
                        cont.resume(sig.sign())
                    } catch (t: Throwable) {
                        cont.resumeWithException(StepUpError.SignFailed(t))
                    }
                }
            },
        )

        biometricPrompt.authenticate(prompt, BiometricPrompt.CryptoObject(signature))
        cont.invokeOnCancellation { biometricPrompt.cancelAuthentication() }
    }
}

sealed class StepUpError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object MissingCryptoObject : StepUpError("Result missing CryptoObject — refusing forged success")
    class KeyUnavailable(cause: Throwable) : StepUpError("Signing key unavailable", cause)
    class AuthFailed(val errorCode: Int, val errString: String) :
        StepUpError("Biometric error $errorCode: $errString")
    class SignFailed(cause: Throwable) : StepUpError("Signature failed inside CryptoObject", cause)
}
