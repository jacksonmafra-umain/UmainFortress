package com.umain.fortress.ui.screens.cards

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide refresh signal for the Cards list.
 *
 * The Cards list and the Add Card screen live on separate navigation routes, so their
 * view-models don't share a backing store. Whenever a new card is created the Add Card
 * view-model emits on this bus and the Cards view-model — which collects in its
 * coroutine scope — replays the list fetch. Tiny, reactive, no DI plumbing required.
 */
object CardsRefreshBus {
    private val _events: MutableSharedFlow<Unit> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot stream of refresh signals, one event per successful card mutation upstream. */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /** Emit a refresh signal. Non-blocking; older buffered events are dropped. */
    fun signal() {
        _events.tryEmit(Unit)
    }
}
