package com.umain.fortress.security

import android.content.Context
import kotlinx.serialization.Serializable

/**
 * Aggregates device-trust signals for the app to decide what the user can do.
 *
 * The shipped implementation in this slice is a deterministic stub — it returns a
 * [IntegrityVerdict.Trusted] verdict so the rest of the app can be wired end-to-end. The
 * full implementation will call Play Integrity's standard API (long-lived nonce + token,
 * server verification), combine with RASP signals (root detection, Frida detection, debugger
 * attach), and surface a single verdict to the UI.
 *
 * See [docs/05-play-integrity.md] and [docs/11-root-detection.md] for the full reasoning.
 */
class IntegrityCheck(
    @Suppress("unused") private val context: Context,
) {
    suspend fun current(): IntegrityVerdict {
        // TODO: real implementation:
        //   - IntegrityManagerFactory.createStandard(context).requestIntegrityToken(...)
        //   - send token to backend /integrity/verify
        //   - combine with RASP signals from RaspProbe
        //   - cache verdict per process lifetime
        return IntegrityVerdict.Trusted
    }
}

@Serializable
sealed class IntegrityVerdict {
    abstract val label: String
    abstract val reasons: List<String>

    @Serializable
    data object Trusted : IntegrityVerdict() {
        override val label: String = "Trusted"
        override val reasons: List<String> = emptyList()
    }

    @Serializable
    data class Limited(override val reasons: List<String>) : IntegrityVerdict() {
        override val label: String = "Limited"
    }

    @Serializable
    data class Untrusted(override val reasons: List<String>) : IntegrityVerdict() {
        override val label: String = "Untrusted"
    }
}
