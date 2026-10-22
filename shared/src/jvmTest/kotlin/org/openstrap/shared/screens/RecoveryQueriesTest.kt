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
import kotlinx.datetime.todayIn
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.glow.RecoveryGlow
import kotlin.time.Clock

/**
 * The learning state, over the seeded month: the days at the start of it have fewer than the seven
 * nights a Readiness score needs, so they draw `GlowRecoveryScreen.kt:135-137`'s sentence. Card U6's
 * UI test walks back to one of them with the header's previous-day button, and this pins that such a
 * day exists in the month the demo launch writes.
 */
class RecoveryQueriesTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = Clock.System.todayIn(zone)
    private val directory = File("build/tmp/recovery-queries-test")
    private val files = mutableListOf<File>()
    private var queries: ScreenQueries? = null

    @BeforeTest
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun removeMain() {
        queries?.close()
        Dispatchers.resetMain()
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
    fun `a day near the start of the seeded month draws the learning state's sentence`() {
        kotlinx.coroutines.runBlocking {
            val queries = seeded()
            val learning =
                "OpenStrap scores Recovery once it has ${Recovery.SCORED_NIGHTS} good nights of your own HRV and resting heart rate."

            // The UI test's loop: walk back from today until the card shows that sentence.
            var found: Pair<Int, String>? = null
            for (back in 0..29) {
                val date = LocalDate.fromEpochDays(today.toEpochDays() - back)
                val day = await<DayUiState> { done -> queries.day(iso(date), done) }
                val rec = await<RecoveryResult?> { done -> queries.recovery(iso(date), done) }
                val shown = RecoveryGlow.forDay(day, rec) ?: continue
                if (shown.readiness.score != null) continue
                val sentence = RecoveryGlow.noScoreBody(shown.readiness)
                if (sentence == learning) {
                    found = back to sentence
                    break
                }
            }

            val (back, sentence) = assertNotNull(found, "no day in the seeded month drew the learning state's sentence")
            assertEquals(learning, sentence)
            // It is near the start, so the UI test's thirty previous-day taps reach it.
            assertTrue(back in 1..29, "the learning day is $back days back")
            println("U6 learning state: $back days back (${LocalDate.fromEpochDays(today.toEpochDays() - back)})")

            // And the days it walks past first have a score, so the card is the scored one there.
            val yesterday = await<DayUiState> { done -> queries.day(iso(LocalDate.fromEpochDays(today.toEpochDays() - 1)), done) }
            val yRec = await<RecoveryResult?> { done -> queries.recovery(iso(LocalDate.fromEpochDays(today.toEpochDays() - 1)), done) }
            val yCard = RecoveryGlow.card(RecoveryGlow.forDay(yesterday, yRec)!!, null)
            assertNotNull(yCard.score, "yesterday has no Recovery score, so the walk would start on the learning state")
        }
    }
}
