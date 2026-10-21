package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.dashboard.Baselines
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.SleepNight

/**
 * The iOS-only accessors: the maps and nullable shapes that reach Swift badly, read back as lists
 * and plain numbers. Run by `:shared:iosSimulatorArm64Test` in CI.
 */
class ScreenAccessorsTest {
    private val day = LocalDate(2026, 10, 21)

    private fun state() = DayUiState(
        date = day,
        naps = listOf(SleepSession(start = 1_000, end = 2_000, nap = true), SleepSession(start = 5_000, end = 6_000, nap = true)),
        napStages = mapOf(
            5_000L to listOf(StageSpan(5_000, 5_500, SleepStage.LIGHT), StageSpan(5_500, 6_000, SleepStage.DEEP)),
            1_000L to listOf(StageSpan(1_000, 2_000, SleepStage.LIGHT)),
        ),
        napBreakdowns = mapOf(
            1_000L to Summaries.SleepBreakdown(asleepSeconds = 600, awakeSeconds = 0, byStage = mapOf(SleepStage.LIGHT to 600L)),
            5_000L to Summaries.SleepBreakdown(asleepSeconds = 1_000, awakeSeconds = 0, byStage = mapOf(SleepStage.LIGHT to 500L, SleepStage.DEEP to 500L)),
        ),
        nights = listOf(
            SleepNight(LocalDate(2026, 10, 20), SleepSession(start = 1, end = 2)),
            SleepNight(day, SleepSession(start = 3, end = 4)),
        ),
        baselines = Baselines(restingHr = 52f),
    )

    @Test
    fun theDayPrintsAsAnIsoDate() {
        assertEquals("2026-10-21", state().dateIso())
        assertEquals("2026-10-21", isoDate(day))
    }

    @Test
    fun napStagesComeBackAsAListInNapOrder() {
        val spans = state().napSpanList()
        assertEquals(2, spans.size)
        // Oldest nap first, whatever order the map held them in.
        assertEquals(1_000L, spans.first().start)
        assertEquals(5_000L, spans.last().start)
        assertEquals(1, spans.first().spans.size)
        assertEquals(2, spans.last().spans.size)
        assertEquals(SleepStage.DEEP, spans.last().spans.last().stage)
        assertEquals(5_500L, spans.last().spans.last().start)
        assertEquals(6_000L, spans.last().spans.last().end)
    }

    @Test
    fun napBreakdownsComeBackAsAListInNapOrder() {
        val breakdowns = state().napBreakdownList()
        assertEquals(2, breakdowns.size)
        assertEquals(1_000L, breakdowns.first().start)
        assertEquals(600L, breakdowns.first().breakdown.asleepSeconds)
        assertEquals(1_000L, breakdowns.last().breakdown.asleepSeconds)
        // The inner Map<Int, Long> reads as plain minutes.
        assertEquals(8, breakdowns.last().breakdown.minutesOf(SleepStage.LIGHT))
        assertEquals(0, breakdowns.last().breakdown.minutesOf(SleepStage.AWAKE))
    }

    @Test
    fun napsAndNightsArePlainLists() {
        assertTrue(state().hasNaps())
        assertFalse(DayUiState(date = day).hasNaps())
        assertEquals(2, state().nightList().size)
        assertEquals(day, state().nightList().last().date)
    }

    @Test
    fun relatedAndAboutComeBackAsLists() {
        assertEquals(listOf(Metric.RestingHr, Metric.Hrv, Metric.Stress), relatedMetrics(Metric.HeartRate))
        assertTrue(relatedMetrics(Metric.Steps).isNotEmpty())

        val about = aboutMetric(Metric.Spo2)
        assertEquals(2, about.size)
        assertTrue(about.first().startsWith("Blood oxygen:"))
        assertTrue(about.last().isNotBlank())
        // Every metric has both sentences (a missing one would be an empty list, not a crash).
        assertEquals(2, aboutMetric(Metric.Steps).size)
    }
}
