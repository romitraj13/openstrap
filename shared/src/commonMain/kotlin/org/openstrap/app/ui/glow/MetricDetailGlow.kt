package org.openstrap.app.ui.glow

import kotlin.math.round
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.atZone
import org.openstrap.app.data.format
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.plusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.dashboard.ABOUT
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.RELATED
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.shared.clock.ScreenClock
import kotlin.time.Instant

/*
 * What Compose's metric detail computes, in one place Swift can call (card U9).
 *
 * `GlowMetricDetail.kt` works out more than formatting: the day's series and the window it is drawn
 * over, the evenly spaced buckets and their touch readouts, the words above and beside the number,
 * the range's own series with a week of a year, the normal, the lowest and highest, and the panels'
 * sentences. Standing instruction 5 says such logic is copied into `shared/` with a JVM test rather
 * than written again in Swift, so the two builds cannot drift; the drawing stays Swift's. Every
 * function below names the Compose lines it was copied from, and is unchanged apart from
 * `java.time` becoming `kotlinx.datetime`, `Color` becoming `MetricColor` for `Theme.swift`, and
 * `Math.round` becoming `round(...)`.
 */

/** How a card draws its series: a line, the day's hour bars, or the resting trend's stems. */
enum class MetricChartKind { Line, Bars, Trend }

/** A day on the resting-heart-rate trend (`GlowDay`, GlowMetricDetail.kt:242). */
data class MetricTrendPoint(val label: String, val value: Float?)

/**
 * One card's chart, in the shape the three Swift charts take.
 *
 * [values] are evenly spaced with nulls for gaps (`GlowLineChart`), [bars] is the day's hours
 * (`GlowDotBars`), [days] the resting trend (`GlowTrend`); only the one its [kind] names is filled.
 * [readouts] is the touch text for each point or column, already worded (`describe`); [bandLow] and
 * [bandHigh] are your normal drawn as a band; [legend] is the line under a history chart.
 */
data class MetricChartModel(
    val kind: MetricChartKind,
    val values: List<Float?> = emptyList(),
    val bars: List<Float> = emptyList(),
    val days: List<MetricTrendPoint> = emptyList(),
    val labels: List<String> = emptyList(),
    val showAverage: Boolean = true,
    val bandLow: Float? = null,
    val bandHigh: Float? = null,
    val readouts: List<String> = emptyList(),
    val legend: String? = null,
)

/** The metric's leading card: what it heads with, the number, and its chart. */
data class MetricCardModel(
    /** `GlowHeader`'s title: "Latest", "Resting", "Total", "Day average", "Daily average", … (:201, :267). */
    val header: String,
    /** `GlowHeader`'s trailing note, which only a history card has (:268). */
    val trailing: String? = null,
    /** True when the card carries the Live switch — today's heart rate on a connected strap (:135). */
    val live: Boolean = false,
    val liveOn: Boolean = false,
    /** The dot-matrix hero number, nil when the day or range has none; the comma is already dropped (:232). */
    val value: String? = null,
    val unit: String = "",
    /** The two short lines beside the number (:217-223, :279-285). */
    val facts: List<String> = emptyList(),
    /** Why there is no number, in the card's words (:227, :274); nil when there is one. */
    val emptyText: String? = null,
    val chart: MetricChartModel? = null,
)

/** A label, its value and the day it belongs to: the Lowest and Highest panel's two columns (:332-344). */
data class MetricFactRow(val label: String, val value: String, val note: String)

/** The Your normal panel (:348-362). */
data class MetricNormalModel(
    val trailing: String,
    val hasNormal: Boolean,
    /** "75 bpm" — the usual value, empty while there is no normal. */
    val usual: String = "",
    /** "66–83" — the usual range, empty while there is no normal. */
    val range: String = "",
    val note: String,
    /** While there is no normal: the days so far over the seven it needs, for the bar (:353). */
    val progress: Float? = null,
)

/** The About panel: the metric's name, what it is, and what moves it (:364-372). */
data class MetricAboutModel(val title: String, val what: String, val moves: String)

/**
 * Everything one metric's detail screen draws, for one range of one day: the header, the four
 * ranges, the card, the Lowest/Highest panel, Your normal, About and Related.
 */
data class MetricDetailModel(
    val title: String,
    val subtitle: String,
    val ranges: List<String>,
    val rangeIndex: Int,
    /**
     * The history the screen must ask `ScreenQueries.history` for: `maxOf(range.days, History.NORMAL_DAYS)`
     * days ending on the day (:124), which is a `Month` read for Day, Week and Month and a `Year` read
     * for Year. The screen fetches before it can draw the card.
     */
    val historyRange: Range,
    val card: MetricCardModel,
    /** Empty on the Day range, and when the range has no days with data (:139-140). */
    val lowHigh: List<MetricFactRow> = emptyList(),
    val normal: MetricNormalModel,
    val about: MetricAboutModel,
    val related: List<Metric> = emptyList(),
)

/** The detail screen's computations, copied from `GlowMetricDetail.kt` (card U9). */
object MetricDetailGlow {
    /** The hour labels a day's chart and a day of steps share (:100). */
    val HOURS = listOf("00", "06", "12", "18", "24")

    /** The four range labels, in `Range.entries`' order (:132). */
    val RANGES = Range.entries.map { it.label }

    /** The screen's model on the app's clock; the Swift call. */
    fun model(
        metric: Metric,
        state: DayUiState,
        stats: List<DayStat>?,
        rangeIndex: Int,
        liveEnabled: Boolean,
        live: Int?,
        canUseLive: Boolean,
    ): MetricDetailModel = model(metric, state, stats, rangeIndex, liveEnabled, live, canUseLive, ScreenClock.today(), ScreenClock.zone())

    /** The screen's model on [today] and in [zone], for the tests. */
    fun model(
        metric: Metric,
        state: DayUiState,
        stats: List<DayStat>?,
        rangeIndex: Int,
        liveEnabled: Boolean,
        live: Int?,
        canUseLive: Boolean,
        today: LocalDate,
        zone: TimeZone,
    ): MetricDetailModel {
        // `val range = Range.entries[rangeIndex]` (:122); an index past the end draws the day.
        val range = Range.entries.getOrElse(rangeIndex) { Range.Day }
        val index = Range.entries.indexOf(range)
        val end = state.date
        val normal = stats?.let { History.normal(it, end) }
        val summary = if (range == Range.Day) {
            null
        } else {
            stats?.let { History.summary(it, end.minusDays((range.days - 1).toLong()), end) }
        }
        val card = if (range == Range.Day) {
            // `if (metric == Metric.HeartRate && state.date == LocalDate.now() && canUseLive) liveEnabled else null` (:135).
            val liveShown = if (metric == Metric.HeartRate && state.date == today && canUseLive) liveEnabled else null
            dayCard(metric, state, normal, liveShown, live, zone)
        } else {
            historyCard(metric, range, stats, normal, end, summary)
        }
        val days = stats?.count { it.date in end.minusDays((History.NORMAL_DAYS - 1).toLong())..end } ?: 0
        val (what, moves) = ABOUT.getValue(metric)
        return MetricDetailModel(
            title = metric.title,
            subtitle = dayName(state.date, today),
            ranges = RANGES,
            rangeIndex = index,
            historyRange = if (range == Range.Year) Range.Year else Range.Month,
            card = card,
            lowHigh = if (summary == null) emptyList() else lowHigh(metric, range, summary),
            normal = normalPanel(metric, normal, days),
            about = MetricAboutModel(
                title = "About ${metric.title.lowercase()}",
                what = what,
                moves = moves,
            ),
            related = RELATED[metric].orEmpty(),
        )
    }

    // ---- Shared pieces (GlowMetricDetail.kt:59-96) ----------------------------------------------

    /** `dayName` (:71-75): today, yesterday, then "EEE d MMM". */
    fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]} ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
    }

    /** Averages timed points into evenly spaced buckets across [from]..[to]; empty buckets are gaps (:151-160). */
    fun evenly(points: List<ChartPoint>, from: Long, to: Long, seconds: Long): List<Float?> {
        val n = ((to - from + 1) / seconds).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        points.forEach {
            val i = ((it.x - from) / seconds).toInt()
            if (i in 0 until n) {
                sums[i] += it.y
                counts[i]++
            }
        }
        return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }

    /** `clock` (:162): the time of an epoch second as "HH:mm". */
    fun clock(epoch: Long, zone: TimeZone): String =
        Instant.fromEpochSeconds(epoch).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    // ---- The day's card (DayCard, :177-257) -----------------------------------------------------

    private fun dayCard(
        metric: Metric,
        state: DayUiState,
        normal: History.Normal?,
        liveEnabled: Boolean?,
        live: Int?,
        zone: TimeZone,
    ): MetricCardModel {
        val dayFrom = state.dayStart
        val dayTo = state.dayEnd
        // The day's series (and its window) for metrics drawn as a line (:181-189).
        val series: List<ChartPoint> = when (metric) {
            Metric.HeartRate -> state.heartRate
            Metric.Hrv -> state.hrv
            Metric.Spo2 -> state.spo2
            Metric.Stress -> state.stress
            Metric.SkinTemperature -> state.skinTemperature
            Metric.Breathing -> state.breathRate
            else -> emptyList()
        }
        val night = metric == Metric.Breathing && state.sleep != null
        val from = if (night) state.sleep!!.start else dayFrom
        val to = if (night) state.sleep!!.end else dayTo
        val value: Float? = when (metric) {
            Metric.HeartRate -> (live ?: state.latestHeartRate)?.toFloat()
            Metric.RestingHr -> state.restingHeartRate?.toFloat()
            Metric.Steps -> state.steps.toFloat()
            else -> series.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat()
        }
        val header = when (metric) {
            Metric.HeartRate -> if (live != null) "Now" else "Latest"
            Metric.RestingHr -> "Resting"
            Metric.Steps -> "Total"
            Metric.Breathing -> "Average during sleep"
            else -> "Day average"
        }
        // Heart rate's usual range is a daily average, not a reading; the day's spread says more (:217-223).
        val facts = when (metric) {
            Metric.HeartRate -> series.takeIf { it.isNotEmpty() }?.let { s ->
                listOf(
                    "Low ${s.minOf { it.y }.toInt()} · high ${s.maxOf { it.y }.toInt()}",
                    "Average ${s.map { it.y }.average().toInt()}",
                )
            }.orEmpty()
            else -> normalFacts(metric, value, normal)
        }
        if (value == null) {
            return MetricCardModel(
                header = header,
                live = liveEnabled != null,
                liveOn = liveEnabled == true,
                emptyText = if (metric == Metric.Breathing) {
                    "Breathing rate is measured during sleep."
                } else {
                    "No ${metric.title.lowercase()} for this day yet."
                },
            )
        }
        val unit = metric.unit.ifEmpty { "/ 100" }
        val chart = when (metric) {
            Metric.Steps -> {
                // `GlowDotBars(state.hourlySteps, labels = HOURS, accent = Color.White)` (:236-239).
                val bars = state.hourlySteps
                MetricChartModel(
                    kind = MetricChartKind.Bars,
                    bars = bars,
                    labels = HOURS,
                    showAverage = false,
                    readouts = bars.mapIndexed { h, v ->
                        "%02d:00–%02d:00 · %,d steps".format(h, (h + 1) % 24, round(v).toLong())
                    },
                )
            }
            Metric.RestingHr -> if (state.restingTrend.any { it.value != null }) {
                MetricChartModel(
                    kind = MetricChartKind.Trend,
                    days = state.restingTrend.map { MetricTrendPoint(it.label, it.value) },
                    showAverage = false,
                )
            } else {
                null
            }
            else -> if (series.isNotEmpty()) {
                // Readings come every minute for heart rate, sparser for the rest (:246).
                val bucket = if (metric == Metric.HeartRate) 5 * 60L else 15 * 60L
                val values = evenly(series, from, to, bucket)
                val labels = if (night) listOf(clock(from, zone), clock(to, zone)) else HOURS
                MetricChartModel(
                    kind = MetricChartKind.Line,
                    values = values,
                    labels = labels,
                    readouts = values.mapIndexed { i, v ->
                        "${metric.format(v ?: 0f)} ${metric.unit} · ${clock(from + i * bucket, zone)}".replace("  ", " ")
                    },
                )
            } else {
                null
            }
        }
        return MetricCardModel(
            header = header,
            live = liveEnabled != null,
            liveOn = liveEnabled == true,
            value = metric.format(value).replace(",", ""),
            unit = unit,
            facts = facts,
            chart = chart,
        )
    }

    /** "Usually 75" and where the value sits against your normal, as hero facts (:165-173). */
    private fun normalFacts(metric: Metric, value: Float?, normal: History.Normal?): List<String> = listOfNotNull(
        normal?.let { "Usually ${metric.format(it.mean)}" },
        when (History.status(value, normal)) {
            1 -> "Above your normal"
            -1 -> "Below your normal"
            0 -> "Within your normal"
            else -> null
        },
    )

    // ---- A week, month or year (HistoryCard, :260-329) -----------------------------------------

    private fun historyCard(
        metric: Metric,
        range: Range,
        stats: List<DayStat>?,
        normal: History.Normal?,
        end: LocalDate,
        summary: History.Summary?,
    ): MetricCardModel {
        val header = if (metric == Metric.Steps) "Steps a day" else "Daily average"
        val trailing = when (range) {
            Range.Week -> "Last 7 days"
            Range.Month -> "Last 30 days"
            else -> "Last 12 months"
        }
        if (summary == null) {
            return MetricCardModel(
                header = header,
                trailing = trailing,
                emptyText = if (stats == null) {
                    "Loading…"
                } else {
                    "No ${metric.title.lowercase()} in this range yet."
                },
            )
        }
        val facts = buildList {
            if (metric == Metric.Steps) add("%,d in total".format(round(summary.total).toLong()))
            add("${summary.days} days with data")
            // Over a year, a month's average inside last month's normal says nothing (:282).
            if (range != Range.Year) normalFacts(metric, summary.average, normal).lastOrNull()?.let(::add)
        }
        // `summary` is non-null only when `stats` is, so this always has the days it draws.
        val series = series(range, stats ?: emptyList(), end.minusDays((range.days - 1).toLong()), end)
        return MetricCardModel(
            header = header,
            trailing = trailing,
            value = metric.format(summary.average).replace(",", ""),
            unit = metric.unit.ifEmpty { "/ 100" },
            facts = facts,
            chart = MetricChartModel(
                kind = MetricChartKind.Line,
                values = series.values,
                labels = series.labels,
                showAverage = false,
                bandLow = normal?.low,
                bandHigh = normal?.high,
                readouts = series.dates.mapIndexed { i, date ->
                    val label = if (range == Range.Year) {
                        "Week of ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
                    } else {
                        "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]} ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
                    }
                    "${metric.format(series.values.getOrNull(i) ?: 0f)} ${metric.unit} · $label".replace("  ", " ")
                },
                // `if (normal != null) "Your normal" else "Your normal shows after ${MIN_NORMAL_DAYS} days of data"` (:301-304).
                legend = if (normal != null) {
                    "Your normal"
                } else {
                    "Your normal shows after ${History.MIN_NORMAL_DAYS} days of data"
                },
            ),
        )
    }

    private class Series(
        val values: List<Float?>,
        val labels: List<String>,
        val dates: List<LocalDate>,
    )

    /** One value a day, or one a week over a year; gaps are null. Also the axis labels and each point's date (:311-329). */
    private fun series(range: Range, stats: List<DayStat>, start: LocalDate, end: LocalDate): Series {
        val inRange = stats.filter { it.date in start..end }
        val dates: List<LocalDate>
        val byDate: Map<LocalDate, Float>
        if (range == Range.Year) {
            val first = start.minusDays((start.dayOfWeek.isoDayNumber - 1).toLong())
            dates = generateSequence(first) { it.plusDays(7) }.takeWhile { it <= end }.toList()
            byDate = History.weekly(inRange).associate { it.date to it.value }
        } else {
            dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
            byDate = inRange.associate { it.date to it.value }
        }
        val labels = when (range) {
            Range.Week -> dates.map { WEEKDAYS[it.dayOfWeek.isoDayNumber - 1].take(2) }
            Range.Month -> FOUR_POINTS.map { fraction ->
                val date = dates[((dates.size - 1) * fraction).toInt()]
                "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
            }
            else -> FOUR_POINTS.map { fraction ->
                val date = dates[((dates.size - 1) * fraction).toInt()]
                MONTHS[date.monthNumber - 1]
            }
        }
        return Series(dates.map { byDate[it] }, labels, dates)
    }

    /** `listOf(0f, 1/3f, 2/3f, 1f)`, the four dates a month or year axis names (:325-326). */
    private val FOUR_POINTS = listOf(0f, 1f / 3f, 2f / 3f, 1f)

    // ---- The panels (LowHighPanel, NormalPanel, :332-362) --------------------------------------

    private fun lowHigh(metric: Metric, range: Range, summary: History.Summary): List<MetricFactRow> {
        fun row(label: String, stat: DayStat) = MetricFactRow(
            label = if (range == Range.Year) "$label day" else label,
            value = "${metric.format(stat.value)} ${metric.unit}".trim(),
            note = "${WEEKDAYS[stat.date.dayOfWeek.isoDayNumber - 1]} ${stat.date.dayOfMonth} ${MONTHS[stat.date.monthNumber - 1]}",
        )
        return listOf(row("Lowest", summary.lowest), row("Highest", summary.highest))
    }

    private fun normalPanel(metric: Metric, normal: History.Normal?, days: Int): MetricNormalModel {
        val trailing = "Last ${History.NORMAL_DAYS} days"
        if (normal == null) {
            return MetricNormalModel(
                trailing = trailing,
                hasNormal = false,
                note = "Your normal shows after ${History.MIN_NORMAL_DAYS} days of data ($days so far). " +
                    "It's the range your daily values usually fall in.",
                progress = days / History.MIN_NORMAL_DAYS.toFloat(),
            )
        }
        return MetricNormalModel(
            trailing = trailing,
            hasNormal = true,
            usual = "${metric.format(normal.mean)} ${metric.unit}".trim(),
            range = "${metric.format(normal.low)}–${metric.format(normal.high)}",
            note = "From ${normal.days} days. Most of your days fall inside this range; " +
                "outside it is worth a look, not a worry.",
        )
    }

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
