package org.openstrap.app.ui.glow

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.data.BodySignalsCopy
import org.openstrap.app.data.CheckIn
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.format
import org.openstrap.app.data.formatDuration
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.plusDays
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric

/*
 * What Compose's Today computes, in one place Swift can call.
 *
 * `GlowTodayScreen.kt` works out more than formatting: which score leads and by how much, the
 * seven-day baselines behind What changed, the weekly recap's averages and its sentence, the
 * hypnogram's rows and readouts. Standing instruction 5 says such logic is copied into `shared/`
 * with a JVM test rather than written again in Swift, so the two builds cannot drift; the drawing
 * stays Swift's. Every function below says the Compose lines it was copied from, and is unchanged
 * apart from `java.time` becoming `kotlinx.datetime` and `Math.round` becoming `round(...)`.
 */

/** The three scores on Today's top card, named so Swift can pick the colour from `Theme.swift`. */
enum class TodayAccent { Charge, Recovery, Sleep }

/** How the top card glows (`overviewColor`, GlowTodayScreen.kt:357); `Theme.swift` maps it. */
enum class OverviewTone { None, Strain, Good, Middling, Low }

/** One score on the top card (`Score`, GlowTodayScreen.kt:277). */
data class TodayScore(
    val label: String,
    val value: Int?,
    val accent: TodayAccent,
    /** How far the score moved since [against]'s point; null when either end is missing. */
    val change: Int?,
    val against: String,
    /** One line under a bar: still learning, why there's no score, or that this is the strap's. */
    val note: String? = null,
    /** The same line with room to say why, under the gauge. */
    val noteLong: String? = null,
    /** True when [value] is the strap's own score standing in for ours. */
    val estimate: Boolean = false,
)

/** The top card: the leading score as the hero, the other two as bars (GlowTodayScreen.kt:205). */
data class TodayOverview(
    val lead: TodayScore,
    val second: TodayScore,
    val sleep: TodayScore,
    val chargeLeads: Boolean,
    /** The one-line verdict in the gauge; null while there is no score to give one. */
    val verdict: String?,
    val tone: OverviewTone,
)

/** The accent a What changed tile's dot takes; `Theme.swift` maps it to `MetricColors`. */
enum class SignalAccent { Heart, Hrv, Spo2, Temperature, Stress, SleepLight }

/** One key statistic with its usual value (`Signal`, GlowTodayScreen.kt:400). */
data class SignalTile(
    val label: String,
    val accent: SignalAccent,
    /** The day's value, formatted; null when the day has none. */
    val value: String?,
    val unit: String,
    /** The seven-day normal, formatted to the same decimals; null while there isn't one. */
    val baseline: String?,
    /** +1 above the baseline, -1 below, 0 within tolerance, null without both ends. */
    val direction: Int?,
    /** Whether going up is good; colours the arrow. Null for the statistics where neither is. */
    val higherIsBetter: Boolean?,
    val target: Metric,
)

/** One stage block of a night (`GlowStage`, GlowCharts.kt): fractions of the night, and its row. */
data class SleepStageBlock(val from: Float, val to: Float, val row: Int, val readout: String)

/** A night ready for `GlowHypnogram` (`GlowSleepStages`, GlowSleepScreen.kt:175). */
data class SleepStageChart(
    val blocks: List<SleepStageBlock>,
    /** Deepest last: Awake, REM, Light, Deep (`DayViewModel.SLEEP_STAGE_ROWS`). */
    val rowNames: List<String>,
    /** First and last clock time under the chart. */
    val labels: List<String>,
)

/** One of the strap's alerts, as its notification words it (`alertText`, GlowTodayExtras.kt:40). */
data class AlertLine(val text: String, val clock: String, val metric: Metric, val epoch: Long)

/** One figure in the weekly recap (`Fact`, GlowTodayExtras.kt:100). */
data class RecapFact(val label: String, val value: String)

/**
 * Monday's look back (GlowTodayExtras.kt:74): [range] is the header's "14 Oct – 20 Oct", [facts]
 * are in Compose's order (Steps a day, Recovery, Asleep a night, Workouts) and [note] is the
 * comparison sentence under them.
 */
data class WeeklyRecap(val range: String, val facts: List<RecapFact>, val note: String)

/** The statistics What changed offers, in its order (`TODAY_TILES`, GlowTodayExtras.kt:131). */
val TODAY_TILES = listOf(
    Metric.RestingHr, Metric.Hrv, Metric.Spo2, Metric.SkinTemperature, Metric.Stress, Metric.Breathing,
)

/** How long after waking Readiness stays the headline before Charge takes over (ScreenParts.kt:176). */
private const val READINESS_HOURS_AFTER_WAKING = 3

/** The metrics What changed's picker offers, worded as `Metric.title` is. */
object TodayGlow {

    // ---- GlowTodayScreen.kt:189 ------------------------------------------------------------------

    /** Averages timed points into evenly spaced buckets across [range]; empty buckets are null (gaps). */
    fun bucket(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
        val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        points.forEach {
            val i = ((it.x - range.first) / seconds).toInt()
            if (i in 0 until n) { sums[i] += it.y; counts[i]++ }
        }
        return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }

    // ---- GlowTodayScreen.kt:205-269 --------------------------------------------------------------

    /**
     * The day's three scores, with the leading one first. [readinessBefore] is yesterday's Readiness
     * (the trend's second-to-last value), and [strapScore] the strap's own score for the day.
     */
    fun overview(
        chargeLatest: Float?,
        chargeAtWake: Float?,
        readiness: Recovery.Readiness?,
        readinessBefore: Int?,
        strapScore: Int?,
        sleepScore: Int?,
        previousSleepScore: Int?,
        chargeLeads: Boolean,
    ): TodayOverview {
        val chargeNow = chargeLatest?.toInt()
        val readinessNow = readiness?.score
        val strain = readiness?.strainAlert == true
        val charge = TodayScore(
            label = "Charge",
            value = chargeNow,
            accent = TodayAccent.Charge,
            change = chargeNow.minus(chargeAtWake?.toInt()),
            against = "since waking",
        )
        // Without our own score the strap's stands in as its estimate, with why ours is missing.
        val estimate = readiness?.takeIf { readinessNow == null && it.showStrapEstimate }?.let { strapScore }
        val recovery = TodayScore(
            label = "Recovery",
            value = readinessNow ?: estimate,
            accent = TodayAccent.Recovery,
            change = readinessNow.minus(readinessBefore),
            against = "vs yesterday",
            note = readiness?.let { readinessState(it, estimate != null, short = true) },
            noteLong = readiness?.let { readinessState(it, estimate != null, short = false) },
            estimate = estimate != null,
        )
        val sleep = TodayScore(
            label = "Sleep",
            value = sleepScore,
            accent = TodayAccent.Sleep,
            change = sleepScore.minus(previousSleepScore),
            against = "vs night before",
        )
        val lead = if (chargeLeads) charge else recovery
        val second = if (chargeLeads) recovery else charge
        val verdict = when {
            strain -> BodySignalsCopy.SHORT
            lead.value == null || lead.estimate -> null
            chargeLeads -> Narrative.chargeHeadline(lead.value)
            else -> Narrative.readinessHeadline(lead.value, alert = false)
        }
        return TodayOverview(
            lead = lead,
            second = second,
            sleep = sleep,
            chargeLeads = chargeLeads,
            verdict = verdict,
            tone = overviewTone(lead.value.takeUnless { lead.estimate }, strain),
        )
    }

    /** `Int?.minus(Int?)`: null when either side is missing (GlowTodayScreen.kt:271). */
    private fun Int?.minus(other: Int?): Int? = if (this == null || other == null) null else this - other

    // ---- GlowTodayScreen.kt:287 ------------------------------------------------------------------

    /**
     * The Readiness state in a few words, as the Recovery tab words it; null once it's settled.
     * [short] fits half the card at phone width, so there the strap's estimate says only that.
     */
    fun readinessState(r: Recovery.Readiness, estimate: Boolean, short: Boolean): String? {
        val state = when {
            r.score != null -> if (r.phase == Recovery.Phase.Learning) "Learning ${r.baselineNights}/${Recovery.SETTLED_NIGHTS} nights" else null
            !r.withheld -> "No sleep yet"
            r.phase == Recovery.Phase.Withheld -> "Learning ${r.baselineNights}/${Recovery.SCORED_NIGHTS} nights"
            else -> "Needs HRV data"
        }
        return when {
            !estimate -> state
            short || state == null -> "Strap's estimate"
            // Kept under ~30 characters so the gauge line never wraps at phone width.
            r.phase == Recovery.Phase.Withheld -> "Strap's estimate, ${r.baselineNights}/${Recovery.SCORED_NIGHTS} nights"
            else -> "Strap's estimate, no HRV yet"
        }
    }

    // ---- GlowTodayScreen.kt:357 ------------------------------------------------------------------

    /** Teal when the leading score is good, amber when middling or strained, coral when low. */
    fun overviewTone(score: Int?, strain: Boolean): OverviewTone = when {
        score == null -> OverviewTone.None
        strain -> OverviewTone.Strain
        score >= 70 -> OverviewTone.Good
        score >= 50 -> OverviewTone.Middling
        else -> OverviewTone.Low
    }

    // ---- ScreenParts.kt:183 ----------------------------------------------------------------------

    /**
     * Whether Charge is the Today hero: only for today, once Charge has a value, and once you've been
     * awake three hours (or from noon when no sleep was recorded). Past days lead with Readiness.
     */
    fun chargeLeads(
        date: LocalDate,
        today: LocalDate,
        chargeLatest: Float?,
        sleepEnd: Long?,
        nowEpoch: Long,
        hour: Int,
    ): Boolean {
        if (date != today || chargeLatest == null) return false
        return if (sleepEnd != null) nowEpoch - sleepEnd >= READINESS_HOURS_AFTER_WAKING * 3600L
        else hour >= 12
    }

    // ---- GlowTodayScreen.kt:146 ------------------------------------------------------------------

    /** Mornings only: today, before noon. */
    fun isMorning(date: LocalDate, today: LocalDate, hour: Int): Boolean = date == today && hour < 12

    /** [isMorning] with the ISO dates Swift holds; a malformed date is not a morning. */
    fun isMorning(iso: String, todayIso: String, hour: Int): Boolean {
        val date = parseDate(iso) ?: return false
        val today = parseDate(todayIso) ?: return false
        return isMorning(date, today, hour)
    }

    /** [chargeLeads] with the ISO dates Swift holds; a malformed date never leads with Charge. */
    fun chargeLeads(
        iso: String,
        todayIso: String,
        chargeLatest: Float?,
        sleepEnd: Long?,
        nowEpoch: Long,
        hour: Int,
    ): Boolean {
        val date = parseDate(iso) ?: return false
        val today = parseDate(todayIso) ?: return false
        return chargeLeads(date, today, chargeLatest, sleepEnd, nowEpoch, hour)
    }

    /** [chargeLeads] over the day the screen holds, so Swift never names the nullable sleep end. */
    fun chargeLeads(state: DayUiState, todayIso: String, chargeLatest: Float?, nowEpoch: Long, hour: Int): Boolean {
        val today = parseDate(todayIso) ?: return false
        return chargeLeads(state.date, today, chargeLatest, state.sleep?.end, nowEpoch, hour)
    }

    /** Yesterday's Readiness: `readinessTrend.dropLast(1).lastOrNull()?.second` (GlowTodayScreen.kt:211). */
    fun readinessBefore(rec: RecoveryResult?): Int? =
        rec?.readinessTrend?.dropLast(1)?.lastOrNull()?.second

    /** The metric a stored tile name names, or null when it has since been renamed. */
    fun metric(name: String): Metric? = Metric.entries.firstOrNull { it.name == name }

    /** Every statistic the picker offers, in `TODAY_TILES`' order. */
    fun allTodayTiles(): List<Metric> = TODAY_TILES

    private fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    // ---- MainActivity.kt:178 ---------------------------------------------------------------------

    /**
     * The statistics What changed shows: the stored names in their stored order, unknown names
     * dropped; nothing chosen (or nothing recognisable) means all six.
     */
    fun todayTiles(names: List<String>?): List<Metric> =
        names?.mapNotNull { name -> Metric.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.isNotEmpty() }
            ?: TODAY_TILES

    // ---- GlowTodayScreen.kt:413-425 --------------------------------------------------------------

    /**
     * The statistics What changed shows, in [tiles]' order: each with its formatted value and
     * seven-day baseline, and an arrow where the value moved away from the normal.
     */
    fun signalTiles(state: DayUiState, tiles: List<Metric>): List<SignalTile> {
        val b = state.baselines
        val signals = listOf(
            signal("Resting HR", SignalAccent.Heart, state.restingHeartRate?.toFloat(), b.restingHr, "bpm", 0, 1.5f, false, Metric.RestingHr),
            signal("HRV", SignalAccent.Hrv, state.hrv.avg()?.toFloat(), b.hrv, "ms", 0, 3f, true, Metric.Hrv),
            signal("Blood oxygen", SignalAccent.Spo2, state.spo2.avg()?.toFloat(), b.spo2, "%", 0, 1f, true, Metric.Spo2),
            signal(
                "Skin temp", SignalAccent.Temperature,
                state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(),
                b.skinTemperature, "°C", 1, 0.3f, null, Metric.SkinTemperature,
            ),
            signal("Stress", SignalAccent.Stress, state.stress.avg()?.toFloat(), b.stress, "", 0, 4f, false, Metric.Stress),
            signal("Breathing", SignalAccent.SleepLight, state.breathRate.avg()?.toFloat(), b.breathRate, "/min", 0, 1f, null, Metric.Breathing),
        ).filter { (it.value != null || it.baseline != null) && it.target in tiles }
            .sortedBy { tiles.indexOf(it.target) }
        return signals.map {
            SignalTile(
                label = it.label,
                accent = it.accent,
                value = it.format(it.value),
                unit = it.unit,
                baseline = it.format(it.baseline),
                direction = Narrative.direction(it.value, it.baseline, it.tolerance),
                higherIsBetter = it.higherIsBetter,
                target = it.target,
            )
        }
    }

    /** `Signal` (GlowTodayScreen.kt:400), before its numbers are formatted. */
    private class Signal(
        val label: String,
        val accent: SignalAccent,
        val value: Float?,
        val baseline: Float?,
        val unit: String,
        val decimals: Int,
        val tolerance: Float,
        val higherIsBetter: Boolean?,
        val target: Metric,
    ) {
        fun format(v: Float?): String? = v?.let { "%.${decimals}f".format(it) }
    }

    private fun signal(
        label: String,
        accent: SignalAccent,
        value: Float?,
        baseline: Float?,
        unit: String,
        decimals: Int,
        tolerance: Float,
        higherIsBetter: Boolean?,
        target: Metric,
    ) = Signal(label, accent, value, baseline, unit, decimals, tolerance, higherIsBetter, target)

    /** `List<ChartPoint>.avg()` (ScreenParts.kt:191): the day's mean, rounded; null without readings. */
    private fun List<ChartPoint>.avg(): Int? {
        val values = map { it.y }
        return if (values.isEmpty()) null else round(values.average()).toInt()
    }

    // ---- GlowTodayScreen.kt:466-485 --------------------------------------------------------------

    /** Under the steps: the goal's progress, the active hours and PAI when there is one. */
    fun activityFacts(state: DayUiState, goals: Goals): List<String> {
        val goal = if (state.steps >= goals.steps) "Goal reached" else "%d%% of %,d".format(state.steps * 100 / goals.steps, goals.steps)
        return listOfNotNull(goal, "Active ${state.activeHours} of ${goals.activeHours} h", state.pai?.let { "PAI ${it.total.roundToInt()}" })
    }

    /** The touch readout on an hourly bar: "05:00–06:00 · 1,234 steps". */
    fun hourStepReadout(hour: Int, value: Float): String =
        "%02d:00–%02d:00 · %,d steps".format(hour, (hour + 1) % 24, round(value).toLong())

    /** The day's heart rate in five-minute slots, gaps left null (`bucket` over the day). */
    fun heartBuckets(state: DayUiState): List<Float?> =
        bucket(state.heartRate, state.dayStart..state.dayEnd, 5 * 60)

    /** The touch readout on a heart-rate point: "62 bpm · 07:35". */
    fun heartReadout(dayStart: Long, index: Int, value: Float): String =
        heartReadout(dayStart, index, value, TimeZone.currentSystemDefault())

    /** [heartReadout] in [zone], for the tests. */
    fun heartReadout(dayStart: Long, index: Int, value: Float, zone: TimeZone): String =
        "${value.toInt()} bpm · ${clock(dayStart + index * 5 * 60L, zone)}"

    /** The heart card's only fact: the day's resting rate. */
    fun heartFacts(state: DayUiState): List<String> =
        listOfNotNull(state.restingHeartRate?.let { "Resting $it" })

    // ---- GlowTodayScreen.kt:488-516 --------------------------------------------------------------

    /** "7:05": the time spent asleep, as the Sleep card's hero number. */
    fun asleepClock(seconds: Long): String = "%d:%02d".format(seconds / 3600, (seconds % 3600) / 60)

    /** The two facts beside the sleep hero: the strap's score and what it means. */
    fun sleepFacts(score: Int?): List<String> =
        score?.let { listOf("Score $it", Narrative.sleepHeadline(it)) } ?: emptyList()

    /** "2 naps · 1h 0m · 13:00, 16:10", or empty without naps. */
    fun napsLine(naps: List<SleepSession>): String = napsLine(naps, TimeZone.currentSystemDefault())

    /** [napsLine] in [zone], for the tests. */
    fun napsLine(naps: List<SleepSession>, zone: TimeZone): String {
        if (naps.isEmpty()) return ""
        val total = naps.sumOf { it.end - it.start }
        val count = if (naps.size == 1) "1 nap" else "${naps.size} naps"
        return "$count · ${formatDuration(total)} · " + naps.joinToString(", ") { clock(it.start, zone) }
    }

    /** The sleep card's header note: "23:00 – 07:00" (GlowTodayScreen.kt:492). */
    fun sleepRange(session: SleepSession): String = sleepRange(session, TimeZone.currentSystemDefault())

    /** [sleepRange] in [zone], for the tests. */
    fun sleepRange(session: SleepSession, zone: TimeZone): String =
        "${clock(session.start, zone)} – ${clock(session.end, zone)}"

    /**
     * A workout row's second line (`WorkoutLine`, GlowActivityScreen.kt:411): the time it started
     * (with the day when [showDate]), how long it ran, its average rate and its calories.
     */
    fun workoutDetail(analysis: WorkoutAnalysis, showDate: Boolean): String =
        workoutDetail(analysis, showDate, TimeZone.currentSystemDefault())

    /** [workoutDetail] in [zone], for the tests. */
    fun workoutDetail(analysis: WorkoutAnalysis, showDate: Boolean, zone: TimeZone): String {
        val start = Instant.fromEpochSeconds(analysis.workout.start).toLocalDateTime(zone)
        val time = "%02d:%02d".format(start.hour, start.minute)
        val whenText = if (showDate) {
            "${WEEKDAYS[start.date.dayOfWeek.isoDayNumber - 1]} ${start.dayOfMonth} ${MONTHS[start.monthNumber - 1]} · $time"
        } else {
            time
        }
        return listOfNotNull(
            whenText,
            formatDuration(analysis.durationSeconds),
            analysis.workout.averageHeartRate?.let { "avg $it bpm" },
            analysis.workout.calories?.let { "$it kcal" },
        ).joinToString(" · ")
    }

    // ---- GlowSleepScreen.kt:175-192 (shared with Today) ------------------------------------------

    /**
     * The night's stages on the glow hypnogram's four rows, with the clock times under it and each
     * block's touch readout: "REM · 01:20–01:34 · 14 min".
     */
    fun sleepStageChart(spans: List<StageSpan>): SleepStageChart =
        sleepStageChart(spans, TimeZone.currentSystemDefault())

    /** [sleepStageChart] in [zone], for the tests. */
    fun sleepStageChart(spans: List<StageSpan>, zone: TimeZone): SleepStageChart {
        val start = spans.minOfOrNull { it.start } ?: 0L
        val end = (spans.maxOfOrNull { it.end } ?: start).coerceAtLeast(start + 1)
        // AWAKE 0, REM 1, LIGHT 2, DEEP 3: DayViewModel.SLEEP_STAGE_ROWS.
        val names = listOf("Awake", "REM", "Light", "Deep")
        fun at(fraction: Float) = clock(start + ((end - start) * fraction).toLong(), zone)
        val blocks = spans.map { span ->
            val from = (span.start - start).toFloat() / (end - start)
            val to = (span.end - start).toFloat() / (end - start)
            val row = SLEEP_STAGE_ROWS[span.stage] ?: 0
            val minutes = (((end - start) * (to - from)) / 60).toInt().coerceAtLeast(1)
            SleepStageBlock(from = from, to = to, row = row, readout = "${names[row]} · ${at(from)}–${at(to)} · $minutes min")
        }
        return SleepStageChart(blocks = blocks, rowNames = names, labels = listOf(clock(start, zone), clock(end, zone)))
    }

    /** `DayViewModel.SLEEP_STAGE_ROWS`: Awake at the top, Deep at the bottom. */
    private val SLEEP_STAGE_ROWS = mapOf(
        SleepStage.AWAKE to 0,
        SleepStage.REM to 1,
        SleepStage.LIGHT to 2,
        SleepStage.DEEP to 3,
    )

    // ---- GlowTodayExtras.kt:40-65 ----------------------------------------------------------------

    /** The strap's alerts this day, newest first, in the same words as its notification. */
    fun alertLines(alerts: List<HealthAlertRow>): List<AlertLine> =
        alertLines(alerts, TimeZone.currentSystemDefault())

    /** [alertLines] in [zone], for the tests. */
    fun alertLines(alerts: List<HealthAlertRow>, zone: TimeZone): List<AlertLine> =
        alerts.sortedByDescending { it.timestamp }.map { alert ->
            val text = when (alert.type) {
                1 -> "High heart rate: ${alert.value} bpm"
                2 -> "Low heart rate: ${alert.value} bpm"
                3 -> "High stress: ${alert.value}"
                4 -> "Low blood oxygen: ${alert.value}%"
                5 -> "Irregular heart rhythm"
                else -> "Alert from the strap"
            }
            val metric = when (alert.type) { 3 -> Metric.Stress; 4 -> Metric.Spo2; else -> Metric.HeartRate }
            AlertLine(text, clock(alert.timestamp, zone), metric, alert.timestamp)
        }

    /** The line under the alerts: the wellness advice plus the disclaimer (GlowTodayExtras.kt:61). */
    fun alertsDisclaimer(): String =
        "If you feel unwell, checking in with a healthcare professional may be helpful. ${BodySignalsCopy.DISCLAIMER}"

    // ---- GlowTodayExtras.kt:74-126 ---------------------------------------------------------------

    /**
     * Monday's look back: the seven days before [DayUiState.date] against the seven before that.
     * [steps] is one row per day over both weeks (the days that have data); days outside them are
     * ignored, as Compose's `filter` does.
     */
    fun weeklyRecap(
        state: DayUiState,
        recovery: RecoveryResult?,
        workouts: List<WorkoutAnalysis>,
        steps: List<DayStat>,
    ): WeeklyRecap = weeklyRecap(state, recovery, workouts, steps, TimeZone.currentSystemDefault())

    /** [weeklyRecap] in [zone], for the tests. */
    fun weeklyRecap(
        state: DayUiState,
        recovery: RecoveryResult?,
        workouts: List<WorkoutAnalysis>,
        steps: List<DayStat>,
        zone: TimeZone,
    ): WeeklyRecap {
        val end = state.date.minusDays(1)
        val start = end.minusDays(6)
        val beforeStart = start.minusDays(7)
        val beforeEnd = start.minusDays(1)

        fun stepsAvg(from: LocalDate, to: LocalDate): Double? =
            steps.filter { it.date in from..to }.map { it.value.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val stepsNow = stepsAvg(start, end)
        val stepsThen = stepsAvg(beforeStart, beforeEnd)

        // A night belongs to the morning it ended; last week's nights end Tuesday through this morning.
        fun asleepAvg(from: LocalDate, to: LocalDate): Double? =
            state.nights.filter { it.date in from..to }.mapNotNull { it.asleepSeconds }.takeIf { it.isNotEmpty() }
                ?.map { it.toDouble() }?.average()
        val sleepNow = asleepAvg(start.plusDays(1), state.date)
        val sleepThen = asleepAvg(beforeStart.plusDays(1), start)

        val recoveryAvg = recovery
            ?.takeIf { it.date == state.date }
            ?.readinessTrend
            ?.mapNotNull { it.second }
            ?.takeIf { it.isNotEmpty() }
            ?.average()
        val done = workouts.count { epochDay(it.workout.start, zone) in start..end }

        val facts = listOf(
            RecapFact("Steps a day", stepsNow?.let { "%,d".format(round(it).toLong()) } ?: "—"),
            RecapFact("Recovery", recoveryAvg?.let { round(it).toInt().toString() } ?: "—"),
            RecapFact("Asleep a night", sleepNow?.let { formatDuration(it.toLong()) } ?: "—"),
            RecapFact("Workouts", "$done"),
        )
        val changes = listOfNotNull(
            if (stepsNow != null && stepsThen != null && stepsThen > 0) {
                val pct = round((stepsNow - stepsThen) / stepsThen * 100).toInt()
                if (pct == 0) "Steps held steady" else "Steps ${if (pct > 0) "up" else "down"} ${kotlin.math.abs(pct)}%"
            } else null,
            if (sleepNow != null && sleepThen != null) {
                val d = (sleepNow - sleepThen).toLong()
                if (kotlin.math.abs(d) < 5 * 60) "sleep about the same" else "${formatDuration(kotlin.math.abs(d))} ${if (d > 0) "more" else "less"} sleep a night"
            } else null,
        )
        val note = if (changes.isEmpty()) {
            "Comparisons with the week before start once there are two weeks of data."
        } else {
            changes.joinToString(", ").replaceFirstChar { it.uppercase() } + " than the week before."
        }
        return WeeklyRecap(
            range = "${shortDate(start)} – ${shortDate(end)}",
            facts = facts,
            note = note,
        )
    }

    /** Whether today is the recap's day: a Monday. */
    fun isRecapDay(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY

    /** [isRecapDay] with the ISO date Swift holds; a malformed date is not a recap day. */
    fun isRecapDay(iso: String): Boolean = parseDate(iso)?.let { isRecapDay(it) } ?: false

    // ---- GlowTodayScreen.kt:167-180 --------------------------------------------------------------

    /** The selected day's workouts, in start order (hidden when there are none). */
    fun dayWorkouts(workouts: List<WorkoutAnalysis>, dayStart: Long, dayEnd: Long): List<WorkoutAnalysis> =
        workouts.filter { it.workout.start in dayStart..dayEnd }.sortedBy { it.workout.start }

    // ---- GlowCheckIn.kt:33 -----------------------------------------------------------------------

    /** "1 is poor, 5 is great." */
    fun checkInNote(): String =
        "1 is ${CheckIn.label(CheckIn.MIN).lowercase()}, 5 is ${CheckIn.label(CheckIn.MAX).lowercase()}."

    // ---- Helpers ---------------------------------------------------------------------------------

    /** "14 Oct": `DateTimeFormatter.ofPattern("d MMM")`, in English as the pattern's locale gives. */
    fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** `getDisplayName(TextStyle.SHORT, locale)`: "Mon" … "Sun". */
    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "HH:mm" of [epochSeconds] in the phone's zone. */
    fun clock(epochSeconds: Long): String = clock(epochSeconds, TimeZone.currentSystemDefault())

    /** "HH:mm" of [epochSeconds] in [zone]. */
    fun clock(epochSeconds: Long, zone: TimeZone): String {
        val time = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun epochDay(epochSeconds: Long, zone: TimeZone): LocalDate =
        Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone).date
}
