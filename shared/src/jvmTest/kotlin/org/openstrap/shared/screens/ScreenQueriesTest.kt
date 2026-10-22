package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.shared.clock.DemoDay
import org.openstrap.shared.clock.ScreenClock

/**
 * `ScreenQueries` is the one class Swift calls for the screens, so the tests ask it exactly the way
 * the app does: an ISO date in, a plain answer on a callback, no `suspend` and no `Flow` in sight.
 */
class ScreenQueriesTest {
    private val zone = DemoDay.ZONE
    private val today = DemoDay.TODAY
    private val iso = today.toString()
    private val directory = File("build/tmp/screen-queries-test")
    private val files = mutableListOf<File>()
    private var queries: ScreenQueries? = null

    @BeforeTest
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Card F7: read the fixed demo day, as a `-demoData` run does, whatever hour this runs at.
        ScreenClock.pinDemo()
    }

    @AfterTest
    fun removeMain() {
        queries?.close()
        Dispatchers.resetMain()
        ScreenClock.unpin()
        for (file in files) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }

    private fun newPath(): String =
        File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath.also { files += File(it) }

    /** Seeds sample wear through its own connection, the way the debug seed does at launch. */
    private suspend fun seed(days: Int = SampleData.DEMO_DAYS): String {
        val path = newPath()
        val database = OpenStrapIosDatabase.open(path)
        try {
            SampleData.load(database.healthDao(), days)
        } finally {
            database.close()
        }
        return path
    }

    private fun open(path: String): ScreenQueries = ScreenQueries(path).also { queries = it }

    /** Waits for a callback: the test asks the queries object exactly the way Swift does. */
    private suspend fun <T> ask(block: (callback: (T) -> Unit) -> Unit): T {
        val answer = CompletableDeferred<T>()
        block { answer.complete(it) }
        return answer.await()
    }

    private suspend fun ScreenQueries.dayNow(date: String = iso) = ask<org.openstrap.app.ui.dashboard.DayUiState> { day(date, it) }
    private suspend fun ScreenQueries.recoveryNow(date: String = iso) = ask<org.openstrap.app.data.RecoveryResult?> { recovery(date, it) }
    private suspend fun ScreenQueries.historyNow(metric: Metric, range: Range, date: String = iso) =
        ask<List<org.openstrap.app.data.DayStat>> { history(metric, range, date, it) }

    private suspend fun ScreenQueries.workoutsNow() = ask<List<org.openstrap.app.data.WorkoutAnalysis>> { workouts(it) }
    private suspend fun ScreenQueries.workoutNow(start: Long) = ask<org.openstrap.app.data.WorkoutAnalysis?> { workout(start, it) }
    private suspend fun ScreenQueries.daysNow() = ask<List<String>> { daysWithData(it) }
    private suspend fun ScreenQueries.profileNow() = ask<UserProfile> { profile(it) }
    private suspend fun ScreenQueries.goalsNow() = ask<Goals> { goals(it) }
    private suspend fun ScreenQueries.checkInNow(date: String = iso) = ask<CheckInAnswer> { checkIn(date, it) }

    /** Saves and waits for the write to land: the callback is the "it is stored" signal Swift gets. */
    private suspend fun ScreenQueries.saveProfileNow(p: UserProfile) = ask<Unit> { callback -> saveProfile(p) { callback(Unit) } }
    private suspend fun ScreenQueries.saveGoalsNow(g: Goals) = ask<Unit> { callback -> saveGoals(g) { callback(Unit) } }
    private suspend fun ScreenQueries.saveCheckInNow(date: String, feeling: Int?) =
        ask<Unit> { callback -> saveCheckIn(date, feeling) { callback(Unit) } }

    // ---- The day, recovery and history ------------------------------------------------------------

    @Test
    fun dayIsTheAndroidTodayState() = runTest {
        val queries = open(seed())
        val state = queries.dayNow()
        assertEquals(today, state.date)
        assertTrue(state.steps > 0, "the sample day has steps")
        assertEquals(state.minuteSteps, state.steps)
        assertTrue(state.heartRate.isNotEmpty())
        assertNotNull(state.sleep)
        assertTrue(state.nights.isNotEmpty())
        assertNotNull(state.baselines.restingHr)
        assertEquals(DayWindow.of(today, zone).from, state.dayStart)
    }

    @Test
    fun aMalformedDateGivesAnEmptyDayNotACrash() = runTest {
        val queries = open(seed(7))
        val state = queries.dayNow("not-a-date")
        assertEquals(0, state.steps)
        assertTrue(state.heartRate.isEmpty())
        assertNull(state.sleep)
        // An empty day still dates itself, so a screen can draw its header.
        assertEquals(today, state.date)
    }

    @Test
    fun recoveryIsScoredAfterTheLearningPeriod() = runTest {
        val queries = open(seed())
        val result = assertNotNull(queries.recoveryNow(), "recovery answers")
        assertNotNull(result.readiness.score, "a month of sample data is scored")
        // A malformed date answers with nothing rather than throwing across the boundary.
        assertNull(queries.recoveryNow("nope"))
    }

    @Test
    fun historyIsOnePointPerDayOfTheRange() = runTest {
        val queries = open(seed())
        assertEquals(7, queries.historyNow(Metric.Steps, Range.Week).size)
        assertEquals(30, queries.historyNow(Metric.Steps, Range.Month).size)
        assertEquals(today, queries.historyNow(Metric.Steps, Range.Week).last().date)
        assertTrue(queries.historyNow(Metric.Steps, Range.Week, "1999-01-01").isEmpty())
    }

    // ---- Workouts ---------------------------------------------------------------------------------

    @Test
    fun workoutsAreNewestFirstAndOneCanBeOpened() = runTest {
        val path = seed()
        val db = OpenStrapIosDatabase.open(path)
        val day = DayWindow.of(today, zone)
        val older = day.from + 9 * 3600
        val newer = day.from + 18 * 3600
        try {
            db.healthDao().insertWorkouts(
                listOf(
                    Workout(older, older + 1800, 1, 1800, 210, 140, 178, 96, 2.4f, 0.6f, 120, 45, 3000),
                    Workout(newer, newer + 1200, 60, 1200, 90, 110, 130, 80, 1.2f, 0f, 60, 40, 1500),
                ),
            )
        } finally {
            db.close()
        }
        val queries = open(path)
        val list = assertNotNull(queries.workoutsNow().takeIf { it.isNotEmpty() })
        assertEquals(2, list.size)
        assertEquals(newer, list.first().workout.start)
        assertEquals(older, list.last().workout.start)
        assertEquals("Outdoor run", list.last().sportName)
        assertEquals(older, assertNotNull(queries.workoutNow(older)).workout.start)
        assertNull(queries.workoutNow(older + 1))
    }

    // ---- The calendar, profile, goals and check-in ------------------------------------------------

    @Test
    fun daysWithDataIsIsoDates() = runTest {
        val queries = open(seed())
        val days = queries.daysNow()
        assertTrue(days.contains(iso), "today is in the calendar, as a yyyy-MM-dd string")
        // Every date parses as the ISO day it prints, and the list is in order.
        days.forEach { d -> assertEquals(d, kotlinx.datetime.LocalDate.parse(d).toString()) }
        assertEquals(days.sorted(), days)
    }

    @Test
    fun profileAndGoalsRoundTripThroughTheQueries() = runTest {
        val queries = open(seed(0))

        // A fresh install: nothing known, goals at the defaults.
        assertEquals(UserProfile(), queries.profileNow())
        assertEquals(Goals(), queries.goalsNow())

        val profile = UserProfile(birthYear = 1990, sex = UserProfile.Sex.Female, weightKg = 61.5f, heightCm = 168, sleepNeedMinutes = 480)
        queries.saveProfileNow(profile)
        assertEquals(profile, queries.profileNow())

        val goals = Goals(steps = 9000, sleepMinutes = 450, activeHours = 12)
        queries.saveGoalsNow(goals)
        assertEquals(goals, queries.goalsNow())
    }

    @Test
    fun checkInReadsWritesAndClears() = runTest {
        val queries = open(seed(7))

        // Nothing answered yet: the screen shows an empty check-in rather than a zero.
        val none = queries.checkInNow()
        assertEquals(iso, none.date)
        assertEquals(false, none.answered)
        assertNull(none.feeling)

        queries.saveCheckInNow(iso, 4)
        val answered = queries.checkInNow()
        assertEquals(true, answered.answered)
        assertEquals(4, answered.feeling)
        assertEquals(iso, answered.date)

        // Out of range is clamped to 1..5, as HealthRepository.saveCheckIn does.
        queries.saveCheckInNow(iso, 9)
        assertEquals(5, queries.checkInNow().feeling)

        // Null clears it, which is what the screen does when the user taps the answer off.
        queries.saveCheckInNow(iso, null)
        assertEquals(false, queries.checkInNow().answered)
    }

    // ---- New synced data --------------------------------------------------------------------------

    @Test
    fun changesFiresOnTheMainThread() = runBlockingTest {
        val queries = open(seed(7))
        val fired = CompletableDeferred<Unit>()
        queries.changes { if (!fired.isCompleted) fired.complete(Unit) }
        withTimeout(10_000) { fired.await() }
        assertTrue(fired.isCompleted)
    }

    /** `runTest` would skip the real debounce and Room's own threads; this waits for them. */
    private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}
