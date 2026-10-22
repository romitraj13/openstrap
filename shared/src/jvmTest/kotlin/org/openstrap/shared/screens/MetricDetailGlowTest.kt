package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.atZone
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.TrendPoint
import org.openstrap.app.ui.dashboard.ABOUT
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.RELATED
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.app.ui.glow.MetricChartKind
import org.openstrap.app.ui.glow.MetricDetailGlow
import org.openstrap.app.ui.dashboard.MetricColor

/**
 * What `GlowMetricDetail.kt` computes, copied into `MetricDetailGlow` so the iPhone's metric detail
 * reads exactly what the Android screen reads (standing instruction 5). Each test names the
 * Compose lines it holds.
 */
class MetricDetailGlowTest {
    private val utc = TimeZone.UTC

    /** A Wednesday, the demo day. */
    private val day = LocalDate(2026, 10, 21)

    private fun epoch(date: LocalDate = day, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).atZone(utc).epochSeconds

    /** A day of readings every five minutes from midnight, 60 bpm rising to 64 and round again. */
    private fun heartSeries(date: LocalDate = day): List<ChartPoint> =
        (0 until 288).map { ChartPoint(epoch(date) + it * 300L, 60f + (it % 5)) }

    /** [count] days of daily values ending on [end]: 60, 61, 62, … */
    private fun dailyStats(count: Int, end: LocalDate = day, first: Float = 60f): List<DayStat> =
        (0 until count).map { i ->
            val value = first + i
            DayStat(end.minusDays((count - 1 - i).toLong()), value, value - 2, value + 2, 40)
        }

    /** A state for the demo day with the heart series and nothing else unless a test adds it. */
    private fun dayState(
        date: LocalDate = day,
        heartRate: List<ChartPoint> = heartSeries(date),
        latestHeartRate: Int? = 64,
        steps: Int = 0,
        hourlySteps: List<Float> = List(24) { 0f },
        sleep: SleepSession? = null,
        breathRate: List<ChartPoint> = emptyList(),
        restingTrend: List<TrendPoint> = emptyList(),
        restingHeartRate: Int? = null,
    ) = DayUiState(
        date = date,
        steps = steps,
        hourlySteps = hourlySteps,
        heartRate = heartRate,
        latestHeartRate = latestHeartRate,
        restingHeartRate = restingHeartRate,
        restingTrend = restingTrend,
        sleep = sleep,
        breathRate = breathRate,
        dayStart = epoch(date),
        dayEnd = epoch(date) + 86_400,
    )

    /** The model in the test zone, on the demo day: the same call Swift makes, with its clock fixed. */
    private fun model(
        metric: Metric,
        state: DayUiState = dayState(),
        stats: List<DayStat>? = emptyList(),
        rangeIndex: Int = 0,
        liveEnabled: Boolean = false,
        live: Int? = null,
        canUseLive: Boolean = false,
    ) = MetricDetailGlow.model(metric, state, stats, rangeIndex, liveEnabled, live, canUseLive, day, utc)

    // ---- The header and the range switch (GlowMetricDetail.kt:60-96, 131-132) ------------------

    @Test
    fun `the header names the metric and the day`() {
        val header = model(Metric.HeartRate)
        assertEquals("Heart rate", header.title)
        assertEquals("Today", header.subtitle)
        assertEquals(listOf("Day", "Week", "Month", "Year"), header.ranges)
        assertEquals(0, header.rangeIndex)
        // The card's glow and the Related dots take the metric's own colour (`colorOf`, MetricInfo.kt:18).
        assertEquals(MetricColor.Heart, header.accent)

        // `dayName` (GlowMetricDetail.kt:71-75): today, yesterday, then "EEE d MMM".
        assertEquals("Yesterday", model(Metric.HeartRate, dayState(day.minusDays(1))).subtitle)
        assertEquals("Thu 15 Oct", model(Metric.HeartRate, dayState(day.minusDays(6))).subtitle)
    }

    // ---- The day's card (DayCard, GlowMetricDetail.kt:177-257) ----------------------------------

    @Test
    fun `a day of heart rate is the latest reading and the days own chart`() {
        val card = model(Metric.HeartRate).card
        // Heart rate's latest card leads with the reading, not a daily average.
        assertEquals("Latest", card.header)
        assertEquals("64", card.value)
        assertEquals("bpm", card.unit)
        // The day's spread says more than the daily normal for a reading (:219-221).
        assertEquals(listOf("Low 60 · high 64", "Average 61"), card.facts)
        assertNull(card.emptyText)
        assertFalse(card.live, "the live switch needs a connected strap")

        val chart = assertNotNull(card.chart)
        assertEquals(MetricChartKind.Line, chart.kind)
        // Five-minute buckets across the day (:246-251): 86,400 / 300.
        assertEquals(288, chart.values.size)
        assertEquals(listOf("00", "06", "12", "18", "24"), chart.labels)
        assertEquals("60 bpm · 00:00", chart.readouts[0])
        assertEquals("61 bpm · 00:05", chart.readouts[1])
        assertTrue(chart.showAverage)
        assertNull(chart.legend)
        // The glow beats at the number it shows, and breathes at five a minute (:196).
        assertEquals("Heartbeat", card.motion)
        assertEquals(64, card.heartbeat)
        assertEquals(5, card.breathPerMinute)
        // The history the screen must ask for: `maxOf(range.days, History.NORMAL_DAYS)` days (:124).
        assertEquals(Range.Month, model(Metric.HeartRate).historyRange)
    }

    @Test
    fun `the live switch is offered only on todays heart rate`() {
        val live = model(
            Metric.HeartRate,
            liveEnabled = true,
            live = 88,
            canUseLive = true,
        )
        assertTrue(live.card.live)
        assertTrue(live.card.liveOn)
        // `if (live != null) "Now" else "Latest"` (:201) and the live reading is the number.
        assertEquals("Now", live.card.header)
        assertEquals("88", live.card.value)

        // A past day has no switch even on a connected strap (:135) …
        val yesterday = model(
            Metric.HeartRate,
            state = dayState(day.minusDays(1)),
            liveEnabled = true,
            live = 88,
            canUseLive = true,
        )
        assertFalse(yesterday.card.live)
        // … and still reads the strap's live number and says "Now": Compose's header asks
        // `if (live != null)` and its value asks `live ?: state.latestHeartRate` (:191, :201),
        // neither of which looks at the day.
        assertEquals("Now", yesterday.card.header)
        assertEquals("88", yesterday.card.value)

        val steps = model(
            Metric.Steps,
            state = dayState(steps = 12_345, hourlySteps = List(24) { (it + 1) * 100f }),
            liveEnabled = true,
            live = 88,
            canUseLive = true,
        )
        assertFalse(steps.card.live)
    }

    @Test
    fun `a day of steps draws the hours as bars`() {
        val card = model(
            Metric.Steps,
            state = dayState(steps = 12_345, hourlySteps = List(24) { (it + 1) * 100f }),
        ).card
        assertEquals("Total", card.header)
        // The dot-matrix hero drops the grouping comma (:232).
        assertEquals("12345", card.value)
        assertEquals("steps", card.unit)
        // No history behind the day, so no usual or status facts.
        assertEquals(emptyList(), card.facts)

        val chart = assertNotNull(card.chart)
        assertEquals(MetricChartKind.Bars, chart.kind)
        assertEquals(24, chart.bars.size)
        assertEquals(listOf("00", "06", "12", "18", "24"), chart.labels)
        assertEquals("00:00–01:00 · 100 steps", chart.readouts[0])
        assertEquals("23:00–00:00 · 2,400 steps", chart.readouts[23])
    }

    @Test
    fun `a day with no series says why instead of inventing a number`() {
        val hrv = model(
            Metric.Hrv,
            state = dayState(heartRate = emptyList(), latestHeartRate = null),
        )
        assertNull(hrv.card.value)
        assertEquals("No heart-rate variability for this day yet.", hrv.card.emptyText)
        assertNull(hrv.card.chart)

        // Breathing is measured in the night, and says so (:227).
        val breathing = model(
            Metric.Breathing,
            state = dayState(heartRate = emptyList(), latestHeartRate = null),
        )
        assertEquals("Breathing rate is measured during sleep.", breathing.card.emptyText)

        // A day of steps is never empty: the strap counts zero.
        assertEquals("0", model(Metric.Steps, state = dayState(steps = 0)).card.value)
    }

    @Test
    fun `breathing is charted over the night, not the day`() {
        val sleep = SleepSession(start = epoch(hour = 1), end = epoch(hour = 7))
        val series = (0 until 24).map { ChartPoint(epoch(hour = 1) + it * 900L, 14f + (it % 3)) }
        val breathing = model(
            Metric.Breathing,
            state = dayState(
                heartRate = emptyList(),
                latestHeartRate = null,
                sleep = sleep,
                breathRate = series,
            ),
        )
        val chart = assertNotNull(breathing.card.chart)
        // The window is the night, so the axis names its first and last clock times (:248).
        assertEquals(listOf("01:00", "07:00"), chart.labels)
        assertEquals(24, chart.values.size)
        assertEquals("14 br/min · 01:00", chart.readouts[0])
        // The average of the night is the day's number (:194).
        assertEquals("15", breathing.card.value)
    }

    @Test
    fun `resting heart rate is its trend, not a line`() {
        val trend = (0 until 7).map { TrendPoint(label = "Mo", value = 58f + it) }
        val resting = model(
            Metric.RestingHr,
            state = dayState(
                heartRate = emptyList(),
                latestHeartRate = null,
                restingTrend = trend,
                restingHeartRate = 64,
            ),
        )
        assertEquals("Resting", resting.card.header)
        assertEquals("64", resting.card.value)
        val chart = assertNotNull(resting.card.chart)
        assertEquals(MetricChartKind.Trend, chart.kind)
        assertEquals(7, chart.days.size)
        assertEquals("Mo", chart.days[0].label)
        assertEquals(58f, chart.days[0].value)

        // With no trend at all the card has no chart, as `if (state.restingTrend.any { … })` (:240).
        val empty = model(
            Metric.RestingHr,
            state = dayState(heartRate = emptyList(), latestHeartRate = null, restingHeartRate = 64),
        )
        assertNull(empty.card.chart)
    }

    // ---- A week, a month, a year (HistoryCard, GlowMetricDetail.kt:260-329) --------------------

    @Test
    fun `a week is seven daily values against your normal`() {
        val stats = dailyStats(30)
        val history = model(Metric.HeartRate, stats = stats, rangeIndex = 1)
        val card = history.card
        assertEquals("Daily average", card.header)
        assertEquals("Last 7 days", card.trailing)
        // The week's average, its day count, and where it sits against the 30-day normal (:279-285).
        assertEquals("86", card.value)
        assertEquals("bpm", card.unit)
        assertEquals(listOf("7 days with data", "Above your normal"), card.facts)

        val chart = assertNotNull(card.chart)
        assertEquals(MetricChartKind.Line, chart.kind)
        assertEquals(7, chart.values.size)
        // `dayOfWeek.getDisplayName(SHORT).take(2)` (:324): Thu 15 Oct … Wed 21 Oct.
        assertEquals(listOf("Th", "Fr", "Sa", "Su", "Mo", "Tu", "We"), chart.labels)
        // Your normal as the faint band, and the legend under the chart (:291, 301).
        val normal = assertNotNull(History.normal(stats, day))
        assertEquals(normal.low, chart.bandLow)
        assertEquals(normal.high, chart.bandHigh)
        assertEquals("Your normal", chart.legend)
        assertFalse(chart.showAverage)
        // A history card breathes at six a minute (:264).
        assertEquals("Breath", card.motion)
        assertEquals(6, card.breathPerMinute)
        assertEquals("89 bpm · Wed 21 Oct", chart.readouts[6])

        // Lowest and highest of the range, with their days (:332-344).
        assertEquals(2, history.lowHigh.size)
        assertEquals("Lowest", history.lowHigh[0].label)
        assertEquals("83 bpm", history.lowHigh[0].value)
        assertEquals("Thu 15 Oct", history.lowHigh[0].note)
        assertEquals("Highest", history.lowHigh[1].label)
        assertEquals("89 bpm", history.lowHigh[1].value)

        // Your normal (:348-362).
        assertTrue(history.normal.hasNormal)
        assertEquals("Last 30 days", history.normal.trailing)
        assertEquals("75 bpm", history.normal.usual)
        assertEquals("66–83", history.normal.range)
        assertNull(history.normal.progress)
        assertEquals(
            "From 30 days. Most of your days fall inside this range; outside it is worth a look, not a worry.",
            history.normal.note,
        )
    }

    @Test
    fun `a month is thirty daily values and their four dates`() {
        val history = model(Metric.HeartRate, stats = dailyStats(30), rangeIndex = 2)
        assertEquals("Last 30 days", history.card.trailing)
        val chart = assertNotNull(history.card.chart)
        assertEquals(30, chart.values.size)
        // `listOf(0f, 1/3f, 2/3f, 1f)` of "d MMM" (:325): 22 Sep … 21 Oct.
        assertEquals(listOf("22 Sep", "1 Oct", "11 Oct", "21 Oct"), chart.labels)
        assertEquals("60 bpm · Tue 22 Sep", chart.readouts[0])
    }

    @Test
    fun `a year is a value a week, labelled by month`() {
        val history = model(Metric.HeartRate, stats = dailyStats(30), rangeIndex = 3)
        val card = history.card
        assertEquals("Daily average", card.header)
        assertEquals("Last 12 months", card.trailing)
        // Over a year a month's average inside last month's normal says nothing, so no status (:283).
        assertEquals(listOf("30 days with data"), card.facts)
        assertEquals(Range.Year, history.historyRange)

        val chart = assertNotNull(card.chart)
        // Weeks from the Monday on or before the first day (:315-318): 53 of them here.
        assertEquals(53, chart.values.size)
        assertEquals(listOf("Oct", "Feb", "Jun", "Oct"), chart.labels)
        // The last five weeks are the only ones with data in these 30 days.
        assertTrue(chart.values.dropLast(5).all { it == null })
        assertTrue(chart.values.takeLast(5).all { it != null })
        assertEquals("88 bpm · Week of 19 Oct", chart.readouts[52])

        // A year's lowest and highest name their day (:338).
        assertEquals("Lowest day", history.lowHigh[0].label)
        assertEquals("Highest day", history.lowHigh[1].label)
    }

    @Test
    fun `your normal says how long until there is one`() {
        val history = model(Metric.HeartRate, stats = dailyStats(3), rangeIndex = 1)
        assertFalse(history.normal.hasNormal)
        assertEquals("Last 30 days", history.normal.trailing)
        assertEquals(3f / History.MIN_NORMAL_DAYS.toFloat(), history.normal.progress)
        assertEquals(
            "Your normal shows after 7 days of data (3 so far). It's the range your daily values usually fall in.",
            history.normal.note,
        )
        // The card's own legend says the same, shorter (:301-304).
        assertEquals("Your normal shows after 7 days of data", history.card.chart?.legend)
    }

    @Test
    fun `a history with no answer is loading, and one with no days says so`() {
        val loading = model(Metric.HeartRate, stats = null, rangeIndex = 1)
        assertEquals("Loading…", loading.card.emptyText)
        assertNull(loading.card.value)
        assertNull(loading.card.chart)
        assertEquals(emptyList(), loading.lowHigh)

        val empty = model(Metric.HeartRate, stats = emptyList(), rangeIndex = 1)
        assertEquals("No heart rate in this range yet.", empty.card.emptyText)
        assertNull(empty.card.chart)
    }

    // ---- About and Related (GlowMetricDetail.kt:364-389) ---------------------------------------

    @Test
    fun `about and related come from MetricInfo`() {
        val heart = model(Metric.HeartRate)
        assertEquals("About heart rate", heart.about.title)
        assertEquals(ABOUT.getValue(Metric.HeartRate).first, heart.about.what)
        assertEquals(ABOUT.getValue(Metric.HeartRate).second, heart.about.moves)
        assertEquals(RELATED.getValue(Metric.HeartRate), heart.related.map { it.metric })
        // Each Related row's dot is that metric's colour (:383).
        assertEquals(listOf(MetricColor.Heart, MetricColor.Hrv, MetricColor.Stress), heart.related.map { it.color })

        val steps = model(Metric.Steps)
        assertEquals(listOf(Metric.HeartRate), steps.related.map { it.metric })
        assertEquals(MetricColor.Steps, steps.accent)

        // `metric.title.lowercase()` in the panel's title (:368).
        assertEquals("About blood oxygen", model(Metric.Spo2).about.title)
    }

    @Test
    fun `an out-of-range range index falls back to the day`() {
        val history = model(Metric.HeartRate, rangeIndex = 9)
        assertEquals(0, history.rangeIndex)
        assertEquals("Latest", history.card.header)
        // Swift asks the same rule before it fetches, so the card and the read cannot disagree.
        assertEquals(Range.Day, Range.entries[history.rangeIndex])
        assertEquals(Range.Month, MetricDetailGlow.historyRange(0))
        assertEquals(Range.Month, MetricDetailGlow.historyRange(1))
        assertEquals(Range.Month, MetricDetailGlow.historyRange(2))
        assertEquals(Range.Year, MetricDetailGlow.historyRange(3))
        assertEquals(Range.Month, MetricDetailGlow.historyRange(9))
    }
}
