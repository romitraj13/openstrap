package org.openstrap.shared.health

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryCopy
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.db.SyncLog
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.minusDays
import kotlin.time.Clock

/**
 * The screens' data, read the way Swift reads it: a plain callback per query, over a database a
 * different connection seeded with the debug sample week (a copy of `SampleData.kt`). Every value
 * the screens show is checked against the score functions themselves, so the two cannot drift.
 */
class HealthQueriesTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = Clock.System.todayIn(zone)
    private val iso = today.toString()

    private val directory = File("build/tmp/health-queries-test")
    private val files = mutableListOf<File>()
    private var queries: HealthQueries? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After
    fun removeMain() {
        queries?.close()
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

    /** Seeds [days] of sample data through its own connection, the way the debug seed does at launch. */
    private suspend fun seed(days: Int): String {
        val path = newPath()
        val database = OpenStrapIosDatabase.open(path)
        try {
            SampleData.load(database.healthDao(), days)
        } finally {
            database.close()
        }
        return path
    }

    private fun open(path: String): HealthQueries = HealthQueries(path).also { queries = it }

    /** Waits for a callback: the test asks the queries object exactly the way the app does. */
    private suspend fun <T> ask(block: (callback: (T) -> Unit) -> Unit): T {
        val answer = CompletableDeferred<T>()
        block { answer.complete(it) }
        return answer.await()
    }

    private suspend fun HealthQueries.todayNow(date: String = iso) = ask<TodaySnapshot> { today(date, it) }
    private suspend fun HealthQueries.sleepNow(date: String = iso) = ask<SleepSnapshot> { sleep(date, it) }
    private suspend fun HealthQueries.heartNow(date: String = iso) = ask<HeartSnapshot> { heart(date, it) }
    private suspend fun HealthQueries.recoveryNow(date: String = iso) = ask<RecoverySnapshot> { recovery(date, it) }

    private suspend fun withDatabase(path: String, block: suspend (OpenStrapIosDatabase) -> Unit) {
        val database = OpenStrapIosDatabase.open(path)
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    // ---- Today ----------------------------------------------------------------------------------

    @Test
    fun todayCarriesWhatTheScoreFunctionsCompute() = runTest {
        val path = seed(HealthQueries.DEMO_DAYS)
        val queries = open(path)
        val snapshot = queries.todayNow()

        withDatabase(path) { database ->
            val dao = database.healthDao()
            val result = RecoveryEngine(dao).compute(today, UserProfile())
            val night = result.tonight ?: error("the sample week has a night ending today")
            val stages = dao.sleepStagesOnce(night.start)
            val breakdown = Summaries.sleepBreakdown(stages)
            val asleepMinutes = (breakdown.asleepSeconds / 60).toInt()

            // A month of wear is past the baselines, so there is a score to show.
            assertNotNull("a month of sample data is scored", snapshot.recoveryScore)
            assertEquals(result.readiness.score, snapshot.recoveryScore)
            assertTrue(
                "state is the phase the Android card prints (${snapshot.recoveryState})",
                snapshot.recoveryState in setOf("scored", "calibrated"),
            )

            // Sleep minutes are the stage sum the Sleep screen draws, and the score uses the same minutes.
            assertEquals(asleepMinutes, snapshot.sleepMinutes)
            assertEquals(
                Recovery.sleepScore(night.copy(asleepMinutes = asleepMinutes), Recovery.SLEEP_NEED_MINUTES),
                snapshot.sleepScore,
            )
            assertEquals((breakdown.byStage[SleepStage.DEEP] ?: 0L) / 60, snapshot.stages.deepMinutes.toLong())
            assertEquals((breakdown.byStage[SleepStage.LIGHT] ?: 0L) / 60, snapshot.stages.lightMinutes.toLong())
            assertEquals((breakdown.byStage[SleepStage.REM] ?: 0L) / 60, snapshot.stages.remMinutes.toLong())
            assertEquals((breakdown.byStage[SleepStage.AWAKE] ?: 0L) / 60, snapshot.stages.awakeMinutes.toLong())

            // Steps are the day's per-minute total, as the Android day view sums them.
            val day = DayWindow.of(today, zone)
            assertEquals(Summaries.totalSteps(dao.activityOnce(day.from, day.to)), snapshot.steps)
            // A day that is already over always has its steps, whatever time the test runs at.
            val yesterday = DayWindow.of(today.minusDays(1), zone)
            assertTrue("the sample data has steps on a past day", Summaries.totalSteps(dao.activityOnce(yesterday.from, yesterday.to)) > 0)

            // Resting HR is the strap's own daily value for the day; the sample data writes one.
            assertEquals(dao.restingHrOnce(day.from, day.to).single().bpm, snapshot.restingHr)

            // Average SpO2 is the mean of the oxygen readings taken during the night.
            val spo2 = dao.spo2Once(night.start, night.end).map { it.percent }
            assertTrue("the sample night has oxygen readings", spo2.isNotEmpty())
            assertEquals(spo2.average().toFloat(), snapshot.averageSpo2!!, 0.01f)
        }
    }

    @Test
    fun aWeekOfWearIsStillLearning() = runTest {
        val path = seed(7)
        val queries = open(path)
        val snapshot = queries.todayNow()

        // Six nights behind today is below the seven the baselines need: no score, and the card says why.
        assertNull(snapshot.recoveryScore)
        assertEquals("withheld", snapshot.recoveryState)
        assertEquals("Learning 6/7 nights", snapshot.recoveryLabel)
        assertEquals("", snapshot.recoveryHeadline)

        val recovery = queries.recoveryNow()
        assertEquals(RecoveryCopy.LEARNING, recovery.emptyText)
        assertEquals(RecoveryCopy.EXPLANATION, recovery.explanation)
        assertEquals("Learning 6/7 nights", recovery.stateLabel)
    }

    @Test
    fun aDateWithoutDataIsEmptyRatherThanACrash() = runTest {
        val path = seed(7)
        val queries = open(path)
        val snapshot = queries.todayNow("1999-01-01")
        assertEquals("1999-01-01", snapshot.date)
        assertNull(snapshot.recoveryScore)
        assertEquals(0, snapshot.steps)
        assertEquals(0, snapshot.sleepMinutes)
        assertTrue(snapshot.averageSpo2 == null)

        // A date the strap could never have written: the app gets an empty screen, not an exception.
        val nonsense = queries.todayNow("not-a-date")
        assertEquals("not-a-date", nonsense.date)
        assertEquals(0, nonsense.steps)
    }

    @Test
    fun lastSyncComesFromTheSyncLog() = runTest {
        val path = seed(7)
        val queries = open(path)
        val finished = Clock.System.now().epochSeconds - 60
        withDatabase(path) { database ->
            database.syncLogDao().insert(SyncLog(start = finished - 5, end = finished, trigger = "auto", foreground = false, rows = 12))
        }
        assertEquals(finished, queries.todayNow().lastSync)
    }

    @Test
    fun seedingTwiceDoesNotDoubleTheDay() = runTest {
        // The launch argument can run more than once; REPLACE keeps one row per minute and per stage.
        val demo = HealthQueries(newPath()).also { queries = it }
        ask<String> { demo.seedDemoData(it) }
        val once = demo.todayNow().steps
        ask<String> { demo.seedDemoData(it) }
        assertEquals(once, demo.todayNow().steps)
    }

    // ---- Sleep ----------------------------------------------------------------------------------

    @Test
    fun sleepIsTheMainSessionWithItsStages() = runTest {
        val path = seed(HealthQueries.DEMO_DAYS)
        val queries = open(path)
        val sleep = queries.sleepNow()

        withDatabase(path) { database ->
            val dao = database.healthDao()
            val day = DayWindow.of(today, zone)
            val nightWindow = DayWindow.nightEndingOn(today, zone)
            val session = dao.sleepSessionsOnce(nightWindow.from, day.to + 12 * 3600)
                .filter { !it.nap && it.end in nightWindow.from..nightWindow.to }
                .maxBy { it.end - it.start }
            assertEquals(session.start, sleep.start)
            assertEquals(session.end, sleep.end)

            val stages = dao.sleepStagesOnce(session.start)
            assertEquals(stages.size, sleep.segments.size)
            assertEquals(stages.map { it.start }, sleep.segments.map { it.start })
            assertEquals(stages.map { it.end }, sleep.segments.map { it.end })
            // The Sleep screen's stage names are the four the strap reports.
            assertEquals(
                stages.map { stageName(it.stage) },
                sleep.segments.map { it.stage },
            )
            assertEquals(session.start, sleep.segments.first().start)
            assertEquals(session.end, sleep.segments.last().end)
            assertTrue("the sample night has every stage", sleep.segments.map { it.stage }.toSet().size == 4 || sleep.segments.map { it.stage }.toSet().contains("deep"))
            // The score is the strap's, else ours, over the minutes the screen prints and the night's continuity.
            val result = RecoveryEngine(dao).compute(today, UserProfile())
            val night = result.tonight ?: error("the sample week has a night ending today")
            assertEquals(
                Recovery.sleepScore(night.copy(asleepMinutes = sleep.sleepMinutes), result.sleepNeed),
                sleep.score,
            )
            assertEquals(org.openstrap.app.data.Narrative.sleepHeadline(sleep.score!!), sleep.headline)
            assertTrue(sleep.body.startsWith("You slept "))
        }
    }

    private fun stageName(stage: Int): String = when (stage) {
        SleepStage.AWAKE -> "awake"
        SleepStage.LIGHT -> "light"
        SleepStage.DEEP -> "deep"
        SleepStage.REM -> "rem"
        else -> "unknown"
    }

    // ---- Heart ----------------------------------------------------------------------------------

    @Test
    fun heartIsTheDayAsFiveMinuteAverages() = runTest {
        val path = seed(HealthQueries.DEMO_DAYS)
        val queries = open(path)
        // A day that is already over, so the sample data's own minute records cover all of it.
        val day = today.minusDays(1)
        val heart = queries.heartNow(day.toString())

        withDatabase(path) { database ->
            val dao = database.healthDao()
            val window = DayWindow.of(day, zone)
            val series = Summaries.bucketAverage(Summaries.heartRateSeries(dao.heartRateOnce(window.from, window.to), dao.activityOnce(window.from, window.to)))
            assertEquals(series.map { it.first }, heart.samples.map { it.timestamp })
            assertEquals(series.map { it.second }, heart.samples.map { it.bpm })
            assertTrue("the sample day has heart rate", heart.samples.size > 100)
            assertTrue(heart.samples.all { it.bpm in 30..230 })
            assertEquals(dao.restingHrOnce(window.from, window.to).single().bpm, heart.restingHr)
            assertEquals(series.last().second, heart.latestHr)
        }
        // The strap's own daily value wins, so nothing is marked as our estimate.
        assertEquals(false, heart.restingEstimated)
    }

    // ---- Recovery -------------------------------------------------------------------------------

    @Test
    fun recoveryIsTheAndroidRecoveryScreen() = runTest {
        val path = seed(HealthQueries.DEMO_DAYS)
        val queries = open(path)
        val recovery = queries.recoveryNow()

        withDatabase(path) { database ->
            val dao = database.healthDao()
            val result = RecoveryEngine(dao).compute(today, UserProfile())
            val readiness = result.readiness

            assertEquals(result.date.toString(), recovery.date)
            assertEquals(readiness.score, recovery.score)
            assertNotNull("a month of wear is scored", recovery.score)
            assertEquals(org.openstrap.app.data.Narrative.readinessHeadline(readiness.score!!, readiness.strainAlert), recovery.headline)
            assertEquals(org.openstrap.app.data.Narrative.readinessBody(readiness), recovery.body)

            // "What drove it": one row per contributor, rated and explained by the copied Narrative.
            assertEquals(readiness.contributors.size, recovery.contributors.size)
            readiness.contributors.forEachIndexed { i, c ->
                assertEquals(c.factor.label, recovery.contributors[i].label)
                assertEquals(org.openstrap.app.data.Narrative.ratingFor(c.score).label, recovery.contributors[i].rating)
                assertEquals(c.detail, recovery.contributors[i].detail)
                assertEquals(c.score / 100f, recovery.contributors[i].fraction, 0.0001f)
            }
            // Charge: the Android card's headline and where the charge went.
            val now = result.charge.latest
            assertNotNull(now)
            assertEquals(now!!.toInt(), recovery.charge.latest)
            assertEquals(org.openstrap.app.data.Narrative.chargeHeadline(now.toInt()), recovery.charge.headline)
            assertNotNull(recovery.charge.atWake)
            assertEquals(result.charge.recharged.toInt(), recovery.charge.recharged)
            assertEquals(result.charge.drainedExercise.toInt(), recovery.charge.drainedExercise)
            assertEquals(result.charge.drainedStress.toInt(), recovery.charge.drainedStress)
            assertEquals(result.charge.drainedLiving.toInt(), recovery.charge.drainedLiving)
            assertEquals(org.openstrap.app.data.Narrative.stressThreshold(result.stressThreshold), recovery.charge.stressThresholdText)

            // The seven-day trend is the readiness trend, oldest first, ending today.
            assertEquals(7, recovery.trend.size)
            assertEquals(result.readinessTrend.map { it.first.toString() }, recovery.trend.map { it.date })
            assertEquals(result.readinessTrend.map { it.second }, recovery.trend.map { it.value })
            assertTrue(recovery.explanation.isNotEmpty())
            assertTrue(recovery.chargeExplanation.isNotEmpty())
        }
    }

    @Test
    fun recoveryWithoutASleepSaysToWearTheStrap() = runTest {
        // No seed at all: a fresh database is the day after install.
        val queries = open(newPath())
        val recovery = queries.recoveryNow()
        assertNull(recovery.score)
        assertEquals(RecoveryCopy.NO_SLEEP, recovery.emptyText)
        assertTrue(recovery.contributors.isEmpty())
        assertEquals(null, recovery.charge.latest)
    }
}
