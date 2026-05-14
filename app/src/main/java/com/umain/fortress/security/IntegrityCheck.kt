package com.umain.fortress.security

import android.content.Context
import com.umain.fortress.BuildConfig
import com.umain.fortress.devmode.DevModeStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * Aggregates device-trust signals for the app to decide what the user can do.
 *
 * The production path will call Play Integrity, combine with RASP signals, and produce a
 * single verdict to the UI. Until that lands, the shipped implementation is two things:
 *
 *  1. A deterministic "Trusted" baseline (no signals firing).
 *  2. In debug builds (BuildConfig.ALLOW_DEV_MODE), the [DevModeStore] toggles can flip the
 *     verdict to Limited / Untrusted so the rest of the app can be exercised end-to-end without
 *     needing a real rooted device or a real Play Integrity verdict. Release builds ignore
 *     the dev-mode toggles entirely.
 *
 * See docs/05-play-integrity.md and docs/11-root-detection.md for the real-world picture.
 */
class IntegrityCheck(
    @Suppress("unused") private val context: Context,
    private val devModeStore: DevModeStore,
) {
    suspend fun current(): IntegrityVerdict {
        if (!BuildConfig.ALLOW_DEV_MODE) return IntegrityVerdict.Trusted

        val dev = devModeStore.state.first()
        val reasons = buildList {
            if (dev.simulateRoot) add("Dev mode · simulated root / Magisk detected")
            if (dev.simulateMitm) add("Dev mode · simulated MITM proxy on the wire")
            if (dev.simulateReplay) add("Dev mode · simulated replayed challenge")
            if (dev.simulateIntegrityFail) add("Dev mode · simulated Play Integrity rejection")
        }
        return when {
            reasons.isEmpty() -> IntegrityVerdict.Trusted
            dev.simulateIntegrityFail || dev.simulateRoot -> IntegrityVerdict.Untrusted(reasons)
            else -> IntegrityVerdict.Limited(reasons)
        }
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
