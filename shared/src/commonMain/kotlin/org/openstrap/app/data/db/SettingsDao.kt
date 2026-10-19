package org.openstrap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The `key_value` table: what Android's DataStore holds, minus everything secret. Reads are
 * suspending rather than `Flow`s because nothing that crosses into Swift may be a `Flow`.
 */
@Dao
interface SettingsDao {
    @Query("SELECT value FROM key_value WHERE `key` = :key")
    suspend fun value(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: KeyValue)

    @Query("DELETE FROM key_value WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("SELECT * FROM key_value ORDER BY `key`")
    suspend fun all(): List<KeyValue>
}

/** The sync log: one row per history sync, newest last. */
@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(row: SyncLog): Long

    @Query("SELECT COUNT(*) FROM sync_log")
    suspend fun count(): Int

    @Query("SELECT * FROM sync_log ORDER BY start, id")
    suspend fun all(): List<SyncLog>

    @Query("SELECT MAX(`end`) FROM sync_log")
    suspend fun lastEnd(): Long?

    @Query("SELECT COALESCE(SUM(rows), 0) FROM sync_log")
    suspend fun totalRows(): Int
}
