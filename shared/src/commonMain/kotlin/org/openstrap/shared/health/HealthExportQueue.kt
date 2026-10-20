package org.openstrap.shared.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.KeyValue
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SettingsDao
import org.openstrap.app.data.db.SleepStage

/* ------------------------------------------------------------------------------------------------ */

/** The five types this app writes to Apple Health, named the way the sync identifier names them. */
object HealthExportTypes {
    const val HR = "hr"
    const val RHR = "rhr"
    const val SPO2 = "spo2"
    const val STEPS = "steps"
    const val SLEEP = "sleep"

    /** Every type, in the order the Swift side exports them. */
    val all: List<String> = listOf(HR, RHR, SPO2, STEPS, SLEEP)
}

/**
 * One future Apple Health sample as plain data.
 *
 * [start] and [end] are epoch seconds. A reading is a point sample, so `start == end` there; a step
 * count covers its minute; a sleep stage covers its own start and end, already clipped into the
 * night it belongs to. [value] is beats per minute, an oxygen **percentage** (70..100, which Swift
 * turns into a fraction), a step count, or a [SleepStage] constant.
 */
data class ExportRow(
    val type: String,
    val start: Long,
    val end: Long,
    val value: Int,
)

/**
 * What one type has waiting: at most `limit` rows, oldest first, and the cursor [HealthExportQueue.markExported]
 * should be given once Swift has saved them. [upTo] is the highest [ExportRow.end] in [rows], or the
 * current cursor when there is nothing to do, so marking an empty batch changes nothing.
 */
data class ExportBatch(
    val type: String,
    val rows: List<ExportRow>,
    val upTo: Long,
)

/* ------------------------------------------------------------------------------------------------ */

/**
 * The rows Apple Health does not have yet, one type at a time, and the cursor that says where the
 * last successful export reached.
 *
 * The rows are the ones `HealthConnectExporter.kt` (`app/src/main/kotlin/org/openstrap/app/health/HealthConnectExporter.kt`
 * @ 19f2865) builds for Health Connect: the same 30..230 bpm filter over the heart-rate table plus
 * the activity minutes that carry a reading, one sample per reading (HealthKit has a sample per
 * reading, not Health Connect's one record per hour), steps as one-minute buckets, oxygen inside
 * 70..100, one resting-heart-rate row per day, and sleep stages clipped into their session. What is
 * not built there is not built here either: HRV (the strap's is RMSSD, Apple Health's is SDNN),
 * skin temperature and stress never enter this queue.
 *
 * The cursor lives per type in `key_value` (`health_export_<type>`), so a second sync writes only
 * what the first one did not. The rule is the sample's END being after the cursor: a one-minute
 * step bucket and a stage that ends after the cursor are not skipped at the boundary. A row Swift
 * saves twice keeps its sync identifier, which is what makes the export idempotent.
 *
 * Nothing that crosses into Swift is a `suspend` function or a `Flow` (plan section 6): each call
 * takes a plain callback, answered on the main thread.
 *
 * @param databasePath the file `StrapEngine` syncs into (`:memory:` in tests).
 */
class HealthExportQueue(private val databasePath: String) {
    private val database = OpenStrapIosDatabase.open(databasePath)
    private val healthDao: HealthDao = database.healthDao()
    private val settings: SettingsDao = database.settingsDao()

    /** One worker, so a mark and the next read cannot overtake each other. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /**
     * The next [limit] rows of [type], oldest first, plus the cursor that covers them. An unknown
     * type, or a read that fails, answers with an empty batch rather than throwing into Swift.
     */
    fun pending(type: String, limit: Int, onResult: (ExportBatch) -> Unit) {
        scope.launch {
            val batch = try {
                read(type, limit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportBatch(type, emptyList(), 0)
            }
            withContext(Dispatchers.Main) { onResult(batch) }
        }
    }

    /**
     * Records that everything up to [upToEpoch] is in Apple Health. Never moves the cursor
     * backwards, so a late, smaller batch cannot make the next sync write the same rows again.
     * [onDone] runs on the main thread once the row is written.
     */
    fun markExported(type: String, upToEpoch: Long, onDone: (() -> Unit)? = null) {
        scope.launch {
            try {
                if (type in HealthExportTypes.all) {
                    val key = keyOf(type)
                    val current = settings.value(key)?.toLongOrNull() ?: 0L
                    if (upToEpoch > current) settings.put(KeyValue(key, upToEpoch.toString()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The cursor stays where it was: the next sync hands the same rows over again, and
                // HealthKit's sync identifier turns that into an update rather than a duplicate.
            }
            if (onDone != null) withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** Where the last export of [type] reached, in epoch seconds; 0 before the first one. */
    fun cursor(type: String, onResult: (Long) -> Unit) {
        scope.launch {
            val value = try {
                cursorOf(type)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0L
            }
            withContext(Dispatchers.Main) { onResult(value) }
        }
    }

    /** Closes the connection and ends the worker. The object is not reusable afterwards. */
    fun close() {
        scope.cancel()
        database.close()
    }

    // ---- The reads ------------------------------------------------------------------------------

    private suspend fun read(type: String, limit: Int): ExportBatch {
        if (type !in HealthExportTypes.all) return ExportBatch(type, emptyList(), 0)
        val cursor = cursorOf(type)
        val rows = when (type) {
            HealthExportTypes.HR -> heartRate(cursor)
            HealthExportTypes.RHR -> restingHeartRate(cursor)
            HealthExportTypes.SPO2 -> bloodOxygen(cursor)
            HealthExportTypes.STEPS -> steps(cursor)
            else -> sleep(cursor)
        }
        val taken = rows.sortedBy { it.start }.take(limit.coerceAtLeast(0))
        return ExportBatch(type, taken, taken.maxOfOrNull { it.end } ?: cursor)
    }

    private suspend fun cursorOf(type: String): Long = settings.value(keyOf(type))?.toLongOrNull() ?: 0L

    /** Readings from the heart-rate table plus the activity minutes that carry one, as Android merges them. */
    private suspend fun heartRate(cursor: Long): List<ExportRow> {
        val from = cursor + 1
        val stored = healthDao.heartRateOnce(from, Long.MAX_VALUE)
        val fromActivity = healthDao.activityOnce(from, Long.MAX_VALUE)
            .mapNotNull { minute -> minute.heartRate?.let { HeartRateSample(minute.timestamp, it) } }
        return (stored + fromActivity)
            .filter { it.bpm in HR_MIN..HR_MAX }
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
            .map { ExportRow(HealthExportTypes.HR, it.timestamp, it.timestamp, it.bpm) }
    }

    private suspend fun restingHeartRate(cursor: Long): List<ExportRow> =
        healthDao.restingHrOnce(cursor + 1, Long.MAX_VALUE)
            .map { ExportRow(HealthExportTypes.RHR, it.day, it.day, it.bpm) }

    private suspend fun bloodOxygen(cursor: Long): List<ExportRow> =
        healthDao.spo2Once(cursor + 1, Long.MAX_VALUE)
            .filter { it.percent in SPO2_MIN..SPO2_MAX }
            .map { ExportRow(HealthExportTypes.SPO2, it.timestamp, it.timestamp, it.percent) }

    /** One row per activity minute with steps in it, from the minute to the next, as Android writes them. */
    private suspend fun steps(cursor: Long): List<ExportRow> =
        healthDao.activityOnce(cursor - STEP_SECONDS + 1, Long.MAX_VALUE)
            .filter { it.steps > 0 }
            .map { ExportRow(HealthExportTypes.STEPS, it.timestamp, it.timestamp + STEP_SECONDS, it.steps) }

    /**
     * The stages of every night that ended after the cursor, clipped into their session and dropped
     * when they are not one of the four stages HealthKit has a category for.
     */
    private suspend fun sleep(cursor: Long): List<ExportRow> = buildList {
        for (session in healthDao.sleepSessionsOnce(cursor + 1, Long.MAX_VALUE)) {
            for (stage in healthDao.sleepStagesOnce(session.start)) {
                // Rows synced before stage ends were made exclusive can be zero length, and a stage
                // must stay inside its session (HealthConnectExporter's two rules).
                val start = stage.start.coerceAtLeast(session.start)
                val end = maxOf(stage.end, stage.start + MIN_STAGE_SECONDS).coerceAtMost(session.end)
                if (end <= start) continue
                if (stage.stage !in SleepStage.AWAKE..SleepStage.REM) continue
                if (end <= cursor) continue
                add(ExportRow(HealthExportTypes.SLEEP, start, end, stage.stage))
            }
        }
    }

    private companion object {
        /** Health Connect's accepted heart-rate range, as the Android export filters it. */
        const val HR_MIN = 30
        const val HR_MAX = 230

        /** Health Connect's accepted oxygen percentage range. */
        const val SPO2_MIN = 70
        const val SPO2_MAX = 100

        /** The activity minute an Android step record covers. */
        const val STEP_SECONDS = 60L

        /** A sleep stage shorter than a minute is not written. */
        const val MIN_STAGE_SECONDS = 60L

        /** `key_value` prefix; the Android `last_sync_` keys belong to the strap's own sync cursors. */
        const val CURSOR_PREFIX = "health_export_"

        fun keyOf(type: String): String = CURSOR_PREFIX + type
    }
}
