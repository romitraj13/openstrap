package org.openstrap.app.device

/**
 * What the history sync is doing, as `StrapController.kt` @ 19f2865 lines 20-26 has it. Copied
 * unchanged: `HistorySync` writes it and the engine turns it into an `EngineListener.onSync` call.
 */
sealed interface SyncState {
    data object Idle : SyncState

    /** [progress] is the share of data types done (0–1), when known. */
    data class Running(val step: String, val progress: Float? = null) : SyncState
    data class Done(val atEpochSeconds: Long) : SyncState
    data class Failed(val reason: String) : SyncState
}
