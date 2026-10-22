package org.openstrap.shared.screens

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.glow.RecoveryGlow
import org.openstrap.shared.clock.DemoDay
import org.openstrap.shared.clock.ScreenClock

/**
 * The learning state, over the seeded month: the days at the start of it have fewer than the seven
 * nights a Readiness score needs, so they draw `GlowRecoveryScreen.kt:135-137`'s sentence. Card U6's
 * UI test walks back to one of them with the header's previous-day button, and this pins that such a
 * day exists in the month the demo launch writes.
 */
class RecoveryQueriesTest {
    private val zone = DemoDay.ZONE
    private val today: LocalDate = DemoDay.TODAY
    private val directory = File("build/tmp/recovery-queries-test")
    private val files = mutableListOf<File>()
    private var queries: ScreenQueries? = null

    @BeforeTest
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Card F7: the fixed demo day, so "26 days back" is 26 days back from the same day every run.
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

    private suspend fun seeded(): ScreenQueries {
        val path = newPath()
        val database = OpenStrapIosDatabase.open(path)
        try {
            SampleData.load(database.healthDao(), SampleData.DEMO_DAYS)
        } finally {
            database.close()
        }
        return ScreenQueries(path).also { queries = it }
    }

    private fun <T> await(block: ((T) -> Unit) -> Unit): T {
        var value: T? = null
        val latch = CountDownLatch(1)
        block { answer ->
            value = answer
            latch.countDown()
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "the read never answered")
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun iso(date: LocalDate): String = date.toString()

    @Test
    fun `the learning state covers the first days of the seeded month, and the UI test can reach it`() {
        kotlinx.coroutines.runBlocking {
            val queries = seeded()
            val learning =
                "OpenStrap scores Recovery once it has ${Recovery.SCORED_NIGHTS} good nights of your own HRV and resting heart rate."

            // The days the Recovery card calls the learning state, newest first.
            fun dayBack(back: Int): LocalDate = LocalDate.fromEpochDays(today.toEpochDays() - back)
            fun card(back: Int): org.openstrap.app.ui.glow.RecoveryCardModel? {
                val day = await<DayUiState> { done -> queries.day(iso(dayBack(back)), done) }
                val rec = await<RecoveryResult?> { done -> queries.recovery(iso(dayBack(back)), done) }
                val shown = RecoveryGlow.forDay(day, rec) ?: return null
                return RecoveryGlow.card(shown, null)
            }

            val learningDays = (1..29).filter { card(it)?.learning == true }
            println("U6 learning days back: $learningDays")

            // The UI test taps the header's previous-day button 26 times; two swallowed taps of slack
            // either side must still land on a learning day.
            assertTrue(learningDays.isNotEmpty(), "no day in the seeded month is the learning state")
            assertTrue(
                (24..28).all { it in learningDays },
                "26 taps lands on days 24..28, and these are not all learning days: $learningDays",
            )
            // Its sentence is the one the card shows, word for word.
            val first = card(learningDays.last())!!
            assertTrue(first.learning)
            assertEquals(learning, first.body)
            // And the day the walk starts on is a scored one, not the learning state.
            assertEquals(false, card(1)?.learning)
            assertNotNull(card(1)?.score, "yesterday has no Recovery score, so the walk would start on the learning state")
        }
    }

    /** Fills [org.openstrap.app.DemoData] (Android's emulator seed, three weeks) instead of the SampleData month. */
    private suspend fun seededDemo(): ScreenQueries {
        val path = newPath()
        val database = OpenStrapIosDatabase.open(path)
        try {
            org.openstrap.app.DemoData.fill(database.healthDao())
        } finally {
            database.close()
        }
        return ScreenQueries(path).also { queries = it }
    }

    @Test
    fun `the demo seed's learning window is reachable`() {
        kotlinx.coroutines.runBlocking {
            val queries = seededDemo()
            fun dayBack(back: Int): LocalDate = LocalDate.fromEpochDays(today.toEpochDays() - back)
            fun card(back: Int): org.openstrap.app.ui.glow.RecoveryCardModel? {
                val day = await<DayUiState> { done -> queries.day(iso(dayBack(back)), done) }
                val rec = await<RecoveryResult?> { done -> queries.recovery(iso(dayBack(back)), done) }
                val shown = RecoveryGlow.forDay(day, rec) ?: return null
                return RecoveryGlow.card(shown, null)
            }
            val learningDays = (1..21).filter { card(it)?.learning == true }

            // The demo seed is three weeks, so its learning window is the week at its start, days
            // 14-20 back. The UI test taps previous-day 17 times; two swallowed taps either side
            // must still land on a learning day.
            assertTrue(learningDays.isNotEmpty(), "no day in the demo seed is the learning state")
            assertTrue(
                (15..19).all { it in learningDays },
                "17 taps lands on days 15..19, and these are not all learning days: $learningDays",
            )
            // The day the walk starts on (yesterday) is a scored one, not the learning state.
            assertEquals(false, card(1)?.learning)
            assertNotNull(card(1)?.score, "yesterday has no Recovery score, so the walk would start on the learning state")
        }
    }
}
