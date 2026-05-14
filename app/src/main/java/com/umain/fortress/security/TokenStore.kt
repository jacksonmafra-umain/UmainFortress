package com.umain.fortress.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.tokensDataStore by preferencesDataStore(name = "fortress_tokens")

/**
 * Encrypted at-rest storage for the session token pair.
 *
 * The tokens themselves are serialized as JSON, encrypted with [KeystoreVault] (AES-256-GCM,
 * TEE/StrongBox), then the resulting ciphertext + IV are base64-encoded and stored in DataStore.
 * DataStore is treated as a glorified file — its contents are opaque without the vault key.
 *
 * If the user clears the app's biometric or the device transfers, the AES key is destroyed by
 * the OS and the ciphertext becomes unrecoverable — exactly the property we want.
 */
class TokenStore(
    private val context: Context,
    private val json: Json,
) {
    private val vault = KeystoreVault()

    val session: Flow<Session?> = context.tokensDataStore.data.map { prefs ->
        val encoded = prefs[BLOB_KEY] ?: return@map null
        decode(encoded)
    }

    suspend fun current(): Session? = session.first()

    suspend fun save(session: Session) {
        val plaintext = json.encodeToString(session).toByteArray(Charsets.UTF_8)
        val blob = vault.encrypt(KeystoreVault.ALIAS_TOKEN_VAULT, plaintext)
        val encoded = encode(blob)
        context.tokensDataStore.edit { it[BLOB_KEY] = encoded }
    }

    suspend fun clear() {
        context.tokensDataStore.edit { it.remove(BLOB_KEY) }
    }

    private fun encode(blob: VaultBlob): String {
        val ivB64 = Base64.encodeToString(blob.iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(blob.ciphertext, Base64.NO_WRAP)
        return "$ivB64.$ctB64"
    }

    private fun decode(encoded: String): Session? = try {
        val parts = encoded.split('.')
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val plaintext = vault.decrypt(KeystoreVault.ALIAS_TOKEN_VAULT, VaultBlob(iv, ct))
        json.decodeFromString<Session>(String(plaintext, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    private companion object {
        val BLOB_KEY = stringPreferencesKey("session_blob_v1")
    }
}

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMs: Long,
    val userId: String,
    val userEmail: String,
    val userDisplayName: String,
)
