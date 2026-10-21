package org.openstrap.shared.screens

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.atStartOfDay
import org.openstrap.app.data.atZone
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.glow.OverviewTone
import org.openstrap.app.ui.glow.TodayGlow
import kotlin.time.Clock

/**
 * The two reads card U5 added to `ScreenQueries`: the strap's own Readiness for the day (Today's
 * "Strap's estimate") and a month of one metric for Monday's recap. Both are read through a real
 * database, as the iOS screens read them.
 */
class TodayQueriesTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today = Clock.System.todayIn(zone)
    private val todayIso = today.toString()
    private val directory = File("build/tmp/today-queries-test")
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

    /** The sample month, then a ScreenQueries over the same file. */
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

    /** [ScreenQueries] answers on the main thread; the unconfined dispatcher runs it here. */
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

    @Test
    fun `the straps readiness for a day is read back, and is null without one`() = kotlinx.coroutines.runBlocking {
        val path = newPath()
        val queries = ScreenQueries(path).also { this@TodayQueriesTest.queries = it }
        // Nothing synced: no estimate to show.
        assertNull(await<StrapReadinessRow?> { done -> queries.strapReadiness(todayIso, done) })

        // The strap's own score for the day, as a sync writes it.
        val dayStart = today.atStartOfDay(zone).epochSeconds
        val database = OpenStrapIosDatabase.open(path)
        try {
            database.healthDao().insertStrapReadiness(
                listOf(
                    StrapReadinessRow(
                        day = dayStart, updatedAt = dayStart + 3600, score = 88, hrvScore = 30, restingHrScore = 20,
                        skinTemperatureScore = 18, breathingScore = 20, sleepHrv = 55, sleepRestingHr = 50,
                        algorithmVersion = "1.0.0",
                    ),
                ),
            )
        } finally {
            database.close()
        }

        val row = assertNotNull(await<StrapReadinessRow?> { done -> queries.strapReadiness(todayIso, done) })
        assertEquals(88, row.score)
        // A malformed date answers null rather than crashing Swift.
        assertNull(await<StrapReadinessRow?> { done -> queries.strapReadiness("not a date", done) })
    }

    @Test
    fun `a month of steps ending today covers both weeks the recap compares`() = kotlinx.coroutines.runBlocking {
        val queries = seeded()
        val steps = await<List<org.openstrap.app.data.DayStat>> { done -> queries.monthHistory(Metric.Steps, todayIso, done) }

        assertTrue(steps.isNotEmpty(), "the sample month has no step history")
        // The last 30 days ending today, so the seven before last week are inside it too.
        assertTrue(steps.all { it.date in today.minusDays29()..today })
        assertEquals(today, steps.last().date)
        // Today's own total is the day's steps, as the Today screen shows them.
        val day = await<org.openstrap.app.ui.dashboard.DayUiState> { done -> queries.day(todayIso, done) }
        assertEquals(day.minuteSteps.toFloat(), steps.last().value)
    }

    private fun LocalDate.minusDays29(): LocalDate = LocalDate.fromEpochDays(toEpochDays() - 29)

    @Test
    fun `the demo months evening glows in the leading scores band, never the no-score grey`() =
        kotlinx.coroutines.runBlocking {
            // Exactly the app's evening state (`-demoData -todayEvening`, TodayScreen.swift:33-56):
            // the seeded month, today, at 20:00. Card U5's screenshot U5-today-1.png is this screen.
            // The seed's own numbers roll with the date, so the assertion is the rule the screenshot
            // has to show — the card wears the leading score's band — not one day's number.
            val queries = seeded()
            val day = await<org.openstrap.app.ui.dashboard.DayUiState> { done -> queries.day(todayIso, done) }
            val rec = await<org.openstrap.app.data.RecoveryResult?> { done -> queries.recovery(todayIso, done) }
            val strap = await<StrapReadinessRow?> { done -> queries.strapReadiness(todayIso, done) }
            val chargeLeads = TodayGlow.chargeLeads(
                state = day,
                todayIso = todayIso,
                chargeLatest = rec?.charge?.latest,
                nowEpoch = nowAt(20),
                hour = 20,
            )
            val card = TodayGlow.overview(
                chargeLatest = rec?.charge?.latest,
                chargeAtWake = rec?.charge?.atWake,
                readiness = rec?.readiness,
                readinessBefore = TodayGlow.readinessBefore(rec),
                strapScore = strap?.score,
                sleepScore = day.sleep?.score,
                previousSleepScore = day.previousSleepScore,
                chargeLeads = chargeLeads,
            )

            val state = "chargeLeads=$chargeLeads lead=${card.lead.label} value=${card.lead.value} " +
                "estimate=${card.lead.estimate} verdict=${card.verdict} tone=${card.tone}"
            println("demo evening: $state")
            assertTrue(chargeLeads, "the evening demo should lead with Charge: $state")
            assertEquals("Charge", card.lead.label)
            val value = assertNotNull(card.lead.value, "the leading Charge has a value: $state")
            assertFalse(card.lead.estimate, "the leading Charge is not the straps estimate: $state")
            // overviewColor (GlowTodayScreen.kt:356-363): 70+, 50-69, and under 50 are three bands,
            // coral under 50. The no-score grey is only for a card with nothing to show.
            val band = when {
                value >= 70 -> OverviewTone.Good
                value >= 50 -> OverviewTone.Middling
                else -> OverviewTone.Low
            }
            assertNotEquals(OverviewTone.None, card.tone, "an evening with a score is never the grey card: $state")
            assertEquals(band, card.tone, "the leading score's band is the card's glow: $state")
            assertEquals(TodayGlow.overviewTone(value, strain = false), card.tone, "overviewTone agrees: $state")
        }

    /** [hour] o'clock today, local, in epoch seconds, as TodayFlags.now reads the clock. */
    private fun nowAt(hour: Int): Long =
        LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, hour, 0).atZone(zone).epochSeconds

    @Test
    fun `the six signals' month is read for the day, one list each`() = kotlinx.coroutines.runBlocking {
        val queries = seeded()
        val histories = await<org.openstrap.app.ui.glow.SignalHistories> { done -> queries.signalHistories(todayIso, done) }

        // The sample month writes resting HR, HRV, blood oxygen and stress every day, so each of those
        // is the whole window; a signal the seed never wrote comes back empty rather than invented.
        assertEquals(30, histories.restingHr.size, "the sample month's resting HR")
        assertEquals(30, histories.hrv.size, "the sample month's HRV")
        assertEquals(30, histories.spo2.size, "the sample month's blood oxygen")
        assertEquals(30, histories.stress.size, "the sample month's stress")
        assertTrue(histories.skinTemperature.isEmpty(), "the sample month writes no skin temperature")
        assertTrue(histories.breathing.isEmpty(), "the sample month writes no breathing rate")

        // The same 30 days ending on the selected one that History.normal reads.
        val from = today.minusDays29()
        assertTrue(histories.restingHr.all { it.date in from..today })
        assertEquals(today, histories.restingHr.last().date)
        // Today's own resting HR is the day's, as the Recovery tile shows it.
        val day = await<org.openstrap.app.ui.dashboard.DayUiState> { done -> queries.day(todayIso, done) }
        assertEquals(day.restingHeartRate?.toFloat(), histories.restingHr.last().value)

        // A malformed date answers six empty lists rather than crashing Swift.
        val empty = await<org.openstrap.app.ui.glow.SignalHistories> { done -> queries.signalHistories("not a date", done) }
        assertTrue(empty.restingHr.isEmpty() && empty.hrv.isEmpty() && empty.skinTemperature.isEmpty())
    }
}
