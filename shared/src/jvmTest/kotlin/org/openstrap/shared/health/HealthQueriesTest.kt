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
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.shared.clock.ScreenClock
import org.openstrap.shared.screens.ScreenQueries

/**
 * What `HealthQueries` still owns: the `-demoData` seed (card U9).
 *
 * Card U9 deleted the four snapshot reads this file used to pin (`today`, `sleep`, `heart`,
 * `recovery`) and their eight tests, because no screen called them any more. Their assertions were
 * not lost with them: the day, its stages, its heart series and its resting HR are pinned by
 * `ScreenQueriesTest.dayIsTheAndroidTodayState` and `DayStateLoaderTest.loadFillsTheDayFromTheSampleMonth`,
 * the learning state by `ScreenQueriesTest.recoveryIsScoredAfterTheLearningPeriod` and
 * `RecoveryQueriesTest`, the empty and malformed days by
 * `ScreenQueriesTest.aMalformedDateGivesAnEmptyDayNotACrash` and
 * `DayStateLoaderTest.loadOfADayWithNoDataIsAnEmptyDayRatherThanACrash`, and the last sync is read by
 * `StrapController` for Diagnostics, never by this class.
 *
 * The one assertion with no other home was the seed's idempotence, which stays here and reads the day
 * through `ScreenQueries`, the way the app does.
 */
class HealthQueriesTest {
    private val directory = File("build/tmp/health-queries-test")
    private val files = mutableListOf<File>()
    private var seed: HealthQueries? = null
    private var screens: ScreenQueries? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Card F7: the demo day is fixed, so a run at 03:00 and one at 13:00 read the same rows.
        ScreenClock.pinDemo()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After
    fun removeMain() {
        screens?.close()
        seed?.close()
        Dispatchers.resetMain()
        ScreenClock.unpin()
        for (file in files) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }

    private fun newPath(): String =
        File(directory, "openstrap-${System.nanoTime()}.db").absolutePath.also { files += File(it) }

    /** Waits for a callback: the test asks exactly the way the app does. */
    private suspend fun <T> ask(block: (callback: (T) -> Unit) -> Unit): T {
        val answer = CompletableDeferred<T>()
        block { answer.complete(it) }
        return answer.await()
    }

    @Test
    fun seedingTheDemoDataTwiceDoesNotDoubleTheDay() = runTest {
        // The launch argument can run more than once; REPLACE keeps one row per minute and per stage.
        val path = newPath()
        val seeding = HealthQueries(path).also { seed = it }
        val reading = ScreenQueries(path).also { screens = it }
        val iso = ScreenClock.today().toString()

        ask<String> { seeding.seedDemoData(it) }
        val once = ask<DayUiState> { reading.day(date = iso, onResult = it) }
        assertTrue("the demo seed has steps on its day", once.steps > 0)

        ask<String> { seeding.seedDemoData(it) }
        val twice = ask<DayUiState> { reading.day(date = iso, onResult = it) }
        assertEquals(once.steps, twice.steps)
        assertEquals(once.heartRate.map { it.y }, twice.heartRate.map { it.y })
    }
}
