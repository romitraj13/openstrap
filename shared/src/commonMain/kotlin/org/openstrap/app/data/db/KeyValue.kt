package org.openstrap.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** All timestamps are epoch seconds (UTC). */

/** Settings, one row per key: what Android keeps in DataStore. No secret goes in here. */
@Entity(tableName = "key_value")
data class KeyValue(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * One history sync, written when it ends: when it started and finished, what asked for it, whether
 * the app was on screen, how many rows it wrote, and the error if it failed. Android keeps this only
 * in log lines; on iOS it is a table so a sync that went wrong away from the phone can be read back.
 */
@Entity(tableName = "sync_log")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch seconds the sync started. */
    val start: Long,
    /** Epoch seconds it ended. */
    val end: Long,
    /** `full`, `auto`, `push` or `manual`. */
    val trigger: String,
    /** Whether the app was on screen when the sync ran. */
    val foreground: Boolean,
    /** Rows written to the health tables. */
    val rows: Int,
    val error: String? = null,
)
