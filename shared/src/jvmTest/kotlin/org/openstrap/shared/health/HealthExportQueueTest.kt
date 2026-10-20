package org.openstrap.shared.health

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.HrvSample
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.RestingHeartRate
import org.openstrap.app.data.db.SkinTemperature
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.db.Spo2Sample
import org.openstrap.app.data.db.StressSample

/**
 * The Apple Health export queue, read the way Swift reads it: a plain callback per type over a real
 * database file. The rows are the ones `HealthConnectExporter.kt` would build (same filters, same
 * minute buckets, same clipped sleep stages), and the cursor is the `key_value` row that stops a
 * second sync from writing the same samples again.
 */
class HealthExportQueueTest {
    private val directory = File("build/tmp/health-export-queue-test")
    private val files = mutableListOf<File>()
    private var queue: HealthExportQueue? = null

    /** 2026-09-01T00:00:00Z, the first reading in every test. */
    private val t0 = 1_788_220_800L

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After
    fun removeMain() {
        queue?.close()
        Dispatchers.resetMain()
        for (file in files) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }

    // ---- Harness --------------------------------------------------------------------------------

    private fun newPath(): String =
        File(directory, "openstrap-${System.nanoTime()}.db").absolutePath.also { files += File(it) }

    /** Seeds through a second connection, the way the engine writes while the queue is idle. */
    private suspend fun seed(block: suspend (OpenStrapIosDatabase) -> Unit): String {
        val path = newPath()
        val database = OpenStrapIosDatabase.open(path)
        try {
            block(database)
        } finally {
            database.close()
        }
        return path
    }

    private fun open(path: String): HealthExportQueue = HealthExportQueue(path).also { queue = it }

    private suspend fun pending(type: String, limit: Int = 1000): ExportBatch {
        val answer = CompletableDeferred<ExportBatch>()
        queue!!.pending(type, limit) { answer.complete(it) }
        return answer.await()
    }

    private suspend fun mark(type: String, upTo: Long) {
        val done = CompletableDeferred<Unit>()
        queue!!.markExported(type, upTo) { done.complete(Unit) }
        done.await()
    }

    private suspend fun cursor(type: String): Long {
        val answer = CompletableDeferred<Long>()
        queue!!.cursor(type) { answer.complete(it) }
        return answer.await()
    }

    private fun ExportBatch.values(): List<Int> = rows.map { it.value }

    // ---- The five types -------------------------------------------------------------------------

    /** The names Swift keys its HealthKit types on; the sync identifier is built from them. */
    @Test
    fun testTheFiveTypesAreTheOnesAppleHealthGets() {
        assertEquals(listOf("hr", "rhr", "spo2", "steps", "sleep"), HealthExportTypes.all)
        assertEquals(5, HealthExportTypes.all.distinct().size)
    }

    @Test
    fun testAnEmptyDatabaseHasNothingToExport() = runTest {
        open(seed { })
        for (type in HealthExportTypes.all) {
            val batch = pending(type)
            assertTrue("$type rows: ${batch.rows}", batch.rows.isEmpty())
            assertEquals(0L, batch.upTo)
            assertEquals("$type cursor", 0L, cursor(type))
        }
    }

    // ---- Heart rate -----------------------------------------------------------------------------

    /**
     * Android builds `hr` from the heart-rate table plus the activity minutes that carry a reading,
     * then keeps 30..230 bpm and one row per timestamp. HealthKit has one sample per reading rather
     * than Health Connect's one record per hour, so the row is the reading.
     */
    @Test
    fun testHeartRateRowsAreEachReadingInsideTheAndroidFilter() = runTest {
        open(
            seed { db ->
                db.healthDao().insertHeartRate(
                    listOf(
                        HeartRateSample(t0, 60),
                        HeartRateSample(t0 + 60, 25), // below the 30 bpm floor: dropped
                        HeartRateSample(t0 + 120, 300), // above the 230 bpm ceiling: dropped
                    ),
                )
                db.healthDao().insertActivity(
                    listOf(
                        // The same instant as a stored reading: the heart-rate row wins, once.
                        ActivityMinute(t0, steps = 0, intensity = 0, kind = 0, heartRate = 80),
                        // A reading only the activity minute carries: exported.
                        ActivityMinute(t0 + 180, steps = 0, intensity = 0, kind = 0, heartRate = 72),
                        // An activity minute with no reading at all.
                        ActivityMinute(t0 + 240, steps = 12, intensity = 1, kind = 0, heartRate = null),
                    ),
                )
            },
        )
        val batch = pending(HealthExportTypes.HR)
        assertEquals(listOf(t0 to 60, t0 + 180 to 72), batch.rows.map { it.start to it.value })
        assertEquals(listOf("hr", "hr"), batch.rows.map { it.type })
        // A reading is a point sample: it covers no time.
        assertTrue(batch.rows.all { it.end == it.start })
        assertEquals(t0 + 180, batch.upTo)
    }

    // ---- The cursor ----------------------------------------------------------------------------

    @Test
    fun testTheCursorOnlyHandsOutRowsAfterTheLastMarkedOne() = runTest {
        open(
            seed { db ->
                db.healthDao().insertHeartRate(
                    listOf(HeartRateSample(t0, 60), HeartRateSample(t0 + 60, 61), HeartRateSample(t0 + 120, 62)),
                )
            },
        )
        assertEquals(listOf(60, 61, 62), pending(HealthExportTypes.HR).values())

        mark(HealthExportTypes.HR, t0 + 60)
        assertEquals(t0 + 60, cursor(HealthExportTypes.HR))

        val second = pending(HealthExportTypes.HR)
        assertEquals(listOf(62), second.values())
        assertEquals(t0 + 120, second.upTo)
    }

    @Test
    fun testTheCursorSurvivesANewQueueOverTheSameFile() = runTest {
        val path = seed { db -> db.healthDao().insertHeartRate(listOf(HeartRateSample(t0, 60), HeartRateSample(t0 + 60, 61))) }
        open(path)
        mark(HealthExportTypes.HR, t0)
        queue!!.close()

        open(path)
        assertEquals(t0, cursor(HealthExportTypes.HR))
        assertEquals(listOf(61), pending(HealthExportTypes.HR).values())
    }

    @Test
    fun testMarkingAnOlderRowNeverMovesTheCursorBackwards() = runTest {
        open(seed { db -> db.healthDao().insertHeartRate(listOf(HeartRateSample(t0, 60), HeartRateSample(t0 + 60, 61))) })
        mark(HealthExportTypes.HR, t0 + 60)
        mark(HealthExportTypes.HR, t0)
        assertEquals(t0 + 60, cursor(HealthExportTypes.HR))
        assertTrue(pending(HealthExportTypes.HR).rows.isEmpty())
    }

    @Test
    fun testEachTypeKeepsItsOwnCursor() = runTest {
        open(
            seed { db ->
                db.healthDao().insertHeartRate(listOf(HeartRateSample(t0, 60)))
                db.healthDao().insertActivity(listOf(ActivityMinute(t0, steps = 12, intensity = 1, kind = 0, heartRate = null)))
            },
        )
        mark(HealthExportTypes.HR, t0)
        assertTrue(pending(HealthExportTypes.HR).rows.isEmpty())
        // The heart-rate cursor says nothing about the step count.
        assertEquals(0L, cursor(HealthExportTypes.STEPS))
        assertEquals(listOf(12), pending(HealthExportTypes.STEPS).values())
    }

    @Test
    fun testABatchStopsAtTheLimitAndTheCursorCoversOnlyWhatItHandedOut() = runTest {
        open(
            seed { db ->
                db.healthDao().insertHeartRate((0 until 5).map { HeartRateSample(t0 + it * 60, 60 + it) })
            },
        )
        val first = pending(HealthExportTypes.HR, limit = 2)
        assertEquals(listOf(60, 61), first.values())
        assertEquals(t0 + 60, first.upTo)

        mark(HealthExportTypes.HR, first.upTo)
        // The next batch starts at the third reading, not at the first one again.
        assertEquals(listOf(62, 63), pending(HealthExportTypes.HR, limit = 2).values())
    }

    // ---- Steps ---------------------------------------------------------------------------------

    /** Android writes one record per activity minute with steps, from the minute to the next. */
    @Test
    fun testStepsRowsAreMinuteBucketsAndOnlyMinutesThatHaveSteps() = runTest {
        open(
            seed { db ->
                db.healthDao().insertActivity(
                    listOf(
                        ActivityMinute(t0, steps = 0, intensity = 0, kind = 0, heartRate = null),
                        ActivityMinute(t0 + 60, steps = 12, intensity = 1, kind = 0, heartRate = null),
                        ActivityMinute(t0 + 120, steps = 3, intensity = 1, kind = 0, heartRate = null),
                    ),
                )
            },
        )
        val batch = pending(HealthExportTypes.STEPS)
        assertEquals(listOf(t0 + 60 to 12, t0 + 120 to 3), batch.rows.map { it.start to it.value })
        assertEquals(listOf(t0 + 60 to t0 + 120, t0 + 120 to t0 + 180), batch.rows.map { it.start to it.end })
        assertEquals(t0 + 180, batch.upTo)
    }

    /** The minute after the last exported one still ends after the cursor, so it is not skipped. */
    @Test
    fun testTheNextMinuteOfStepsIsNotLostAtTheCursorBoundary() = runTest {
        val path = seed { db ->
            db.healthDao().insertActivity(
                listOf(ActivityMinute(t0, steps = 5, intensity = 1, kind = 0, heartRate = null)),
            )
        }
        open(path)
        val first = pending(HealthExportTypes.STEPS)
        assertEquals(listOf(5), first.values())
        mark(HealthExportTypes.STEPS, first.upTo)

        // The strap syncs the next minute after that export; its end is exactly one minute later.
        val database = OpenStrapIosDatabase.open(path)
        try {
            database.healthDao().insertActivity(
                listOf(ActivityMinute(t0 + 60, steps = 6, intensity = 1, kind = 0, heartRate = null)),
            )
        } finally {
            database.close()
        }
        assertEquals(listOf(6), pending(HealthExportTypes.STEPS).values())
    }

    // ---- SpO2 and resting heart rate -----------------------------------------------------------

    @Test
    fun testSpO2RowsCarryThePercentageInsideTheAndroidRange() = runTest {
        open(
            seed { db ->
                db.healthDao().insertSpo2(
                    listOf(Spo2Sample(t0, 69), Spo2Sample(t0 + 60, 70), Spo2Sample(t0 + 120, 97), Spo2Sample(t0 + 180, 101)),
                )
            },
        )
        val batch = pending(HealthExportTypes.SPO2)
        assertEquals(listOf(t0 + 60 to 70, t0 + 120 to 97), batch.rows.map { it.start to it.value })
        assertTrue(batch.rows.all { it.end == it.start })
    }

    @Test
    fun testRestingHeartRateRowsUseTheDayTheyBelongTo() = runTest {
        val day = t0 - t0 % 86_400
        open(
            seed { db ->
                db.healthDao().insertRestingHr(listOf(RestingHeartRate(day, 52), RestingHeartRate(day + 86_400, 49)))
            },
        )
        val batch = pending(HealthExportTypes.RHR)
        assertEquals(listOf(day to 52, day + 86_400 to 49), batch.rows.map { it.start to it.value })
        assertTrue(batch.rows.all { it.start == it.end })
    }

    // ---- Sleep ---------------------------------------------------------------------------------

    /**
     * Android clips a stage into its session and drops anything shorter than a minute, because
     * Health Connect rejects a zero-length stage and a stage outside the session.
     */
    @Test
    fun testSleepStagesAreClippedToTheirSessionAndTheUnknownOnesSkipped() = runTest {
        val start = t0
        val end = t0 + 8 * 3600
        open(
            seed { db ->
                db.healthDao().insertSleepSessions(listOf(SleepSession(start, end, day = start, nap = false)))
                db.healthDao().insertSleepStages(
                    listOf(
                        SleepStage(start - 600, start + 900, start, SleepStage.LIGHT), // starts before the session
                        SleepStage(start + 900, start + 1800, start, SleepStage.DEEP),
                        SleepStage(start + 1800, start + 2700, start, SleepStage.REM),
                        SleepStage(start + 2700, start + 3000, start, SleepStage.AWAKE),
                        SleepStage(end - 300, end + 900, start, SleepStage.LIGHT), // runs past the session
                        SleepStage(start + 3000, start + 3300, start, 9), // not one of the four stages
                        SleepStage(end + 1200, end + 1200, start, SleepStage.LIGHT), // zero length
                    ),
                )
            },
        )
        val batch = pending(HealthExportTypes.SLEEP)
        assertEquals(
            listOf(
                start to (start + 900) to SleepStage.LIGHT,
                (start + 900) to (start + 1800) to SleepStage.DEEP,
                (start + 1800) to (start + 2700) to SleepStage.REM,
                (start + 2700) to (start + 3000) to SleepStage.AWAKE,
                (end - 300) to end to SleepStage.LIGHT,
            ),
            batch.rows.map { it.start to it.end to it.value },
        )
        assertTrue(batch.rows.all { it.type == "sleep" })
        assertEquals(end, batch.upTo)
    }

    /** A night the strap sends after the last export is found by the session, not by the stage. */
    @Test
    fun testANewNightIsFoundByItsSessionEndAfterTheCursor() = runTest {
        val first = t0 - 2 * 86_400
        val path = seed { db ->
            db.healthDao().insertSleepSessions(listOf(SleepSession(first, first + 7 * 3600, day = first, nap = false)))
            db.healthDao().insertSleepStages(listOf(SleepStage(first, first + 7 * 3600, first, SleepStage.LIGHT)))
        }
        open(path)
        mark(HealthExportTypes.SLEEP, first + 7 * 3600)

        // The strap syncs last night, whose session ends the next morning.
        val database = OpenStrapIosDatabase.open(path)
        try {
            database.healthDao().insertSleepSessions(listOf(SleepSession(t0, t0 + 7 * 3600, day = t0, nap = false)))
            database.healthDao().insertSleepStages(listOf(SleepStage(t0, t0 + 3600, t0, SleepStage.DEEP)))
        } finally {
            database.close()
        }

        val batch = pending(HealthExportTypes.SLEEP)
        assertEquals(listOf(t0), batch.rows.map { it.start })
        assertEquals(SleepStage.DEEP, batch.rows.single().value)
    }

    // ---- What is not exported ------------------------------------------------------------------

    /**
     * The strap's HRV is RMSSD and Apple Health only has an SDNN type; there is no honest Apple
     * Health type for skin temperature or stress. None of them may reach the queue.
     */
    @Test
    fun testHrvStressAndSkinTemperatureAreNeverExported() = runTest {
        open(
            seed { db ->
                db.healthDao().insertHrv(listOf(HrvSample(t0, 48)))
                db.healthDao().insertStress(listOf(StressSample(t0, 61)))
                db.healthDao().insertSkinTemperature(listOf(SkinTemperature(t0, 33.5f)))
            },
        )
        for (type in HealthExportTypes.all + listOf("hrv", "stress", "skin_temperature")) {
            val batch = pending(type)
            assertTrue("$type handed out ${batch.rows.size} rows", batch.rows.isEmpty())
        }
    }
}
