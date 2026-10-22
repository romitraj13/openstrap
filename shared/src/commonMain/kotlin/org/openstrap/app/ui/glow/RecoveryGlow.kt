package org.openstrap.app.ui.glow

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.data.BodySignals
import org.openstrap.app.data.BodySignalsCopy
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.data.format
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.shared.clock.ScreenClock

/*
 * What Compose's Recovery tab computes, in one place Swift can call (card U6).
 *
 * `GlowRecoveryScreen.kt` works out more than formatting: which sentence the card leads with, how the
 * seven-day trend is labelled, the Charge buckets and their readouts, the fold of the contributors,
 * and the six body signals with their normal, their word and their week. Standing instruction 5 says
 * such logic is copied into `shared/` with a JVM test rather than written again in Swift, so the two
 * builds cannot drift; the drawing stays Swift's. Every function below says the Compose lines it was
 * copied from, and is unchanged apart from `java.time` becoming `kotlinx.datetime`, `Color` becoming
 * an accent name for `Theme.swift`, and `Math.round` becoming `round(...)`.
 */

/** A body-signal tile's dot (`MetricColors.*` in GlowRecoveryScreen.kt:374-382); `Theme.swift` maps it. */
enum class RecoveryAccent { Heart, Hrv, Temperature, SleepLight, Spo2, Stress }

/** A charge-flow row's bar (GlowRecoveryScreen.kt:241-244), named for `Theme.swift`. */
enum class ChargeFlowAccent { Good, Steps, Stress, SleepLight }

/**
 * The last [History.NORMAL_DAYS] days of each body signal, oldest first, as `rememberSignals`
 * (GlowRecoveryScreen.kt:360-384) asks `history(m.daily, from, end)` for. A day without data is
 * simply absent, as Compose's list is.
 */
data class SignalHistories(
    val restingHr: List<DayStat> = emptyList(),
    val hrv: List<DayStat> = emptyList(),
    val skinTemperature: List<DayStat> = emptyList(),
    val breathing: List<DayStat> = emptyList(),
    val spo2: List<DayStat> = emptyList(),
    val stress: List<DayStat> = emptyList(),
)

/** One day of the Readiness trend: its short weekday and the score (`GlowDay`, GlowRecoveryScreen.kt:162). */
data class RecoveryTrendDay(val label: String, val value: Float?)

/** Everything the Recovery card draws (`RecoveryCard`, GlowRecoveryScreen.kt:105-168). */
data class RecoveryCardModel(
    /** The header's note: "Learning 3/7 nights", "Calibrated · +6 vs yesterday", … (:118-128). */
    val trailing: String?,
    /** The sentence under the title, when there is no score (:129-140). */
    val body: String?,
    /**
     * True when [body] is the "once it has 7 good nights" sentence — the withheld state, before
     * enough nights. The UI test finds the state by this rather than by matching the sentence.
     */
    val learning: Boolean,
    /** "Sleep 64 · 6h 52m asleep", from the sleep contributor (:141). */
    val sleepNote: String?,
    /** The strap's own score, labelled "Strap's estimate" (:145); null when it isn't shown. */
    val strapValue: String?,
    /** "HRV 70 · resting HR 66. Shown for reference; …" (:150). */
    val strapNote: String?,
    /** The strap's fallbacks, one line each (:152-153). */
    val notes: List<String>,
    /** The score, or null when the day has none. */
    val score: Int?,
    /** The line beside the hero number: `Narrative.readinessHeadline` (:156). */
    val heroFacts: List<String>,
    /** The advice under it: `Narrative.readinessBody` (:158). */
    val heroBody: String?,
    val trend: List<RecoveryTrendDay>,
    /** True when any day of the trend has a score, so the chart is drawn at all (:159). */
    val hasTrend: Boolean,
    /** `overviewColor(score, strainAlert)`, as a tone `Theme.swift` names a colour for. */
    val tone: OverviewTone,
)

/** Everything the Charge card draws (`ChargeCard`, GlowRecoveryScreen.kt:191-216). */
data class ChargeCardModel(
    /** "78 at waking" (:196). */
    val trailing: String?,
    /** The line shown instead of the number when there is nothing to draw (:197-205). */
    val hint: String?,
    /** "${now.toInt()}", or null when the day has no Charge (:208). */
    val value: String?,
    val unit: String,
    /** The charge headline and "+41 · −33" (:208). */
    val facts: List<String>,
    /** The day's curve in quarter-hour buckets, gaps null (:210-211). */
    val values: List<Float?>,
    /** "00", "06", "12", "18", "24" (:101). */
    val labels: List<String>,
    /** What the glow and the chart's colour are drawn from: `now`, or null. */
    val level: Float?,
)

/** One row of "Where your charge went" (`FlowRow`, GlowRecoveryScreen.kt:257-265). */
data class ChargeFlowRow(val label: String, val value: String, val fraction: Float, val accent: ChargeFlowAccent)

/** The "Where your charge went" panel (GlowRecoveryScreen.kt:237-248). */
data class ChargeFlowModel(val rows: List<ChargeFlowRow>, val highLow: String?, val stressNote: String)

/** One contributor under its group (:316-324). */
data class ContributorRow(val label: String, val rating: String, val detail: String, val fraction: Float)

/** One group of contributors (:309-314); [afterDivider] is Compose's `DotDivider` before it. */
data class ContributorGroup(val name: String, val rows: List<ContributorRow>, val afterDivider: Boolean)

/** The "What drove it" panel (GlowRecoveryScreen.kt:296-336). */
data class ContributorsModel(
    /** "Top 3 of 5" while folded (:307). */
    val trailing: String?,
    /** True when the panel has more than [CONTRIBUTORS_FOLDED] contributors, so it folds (:297). */
    val foldable: Boolean,
    val groups: List<ContributorGroup>,
    /** Capped, the HRV range, the strap fallbacks and the quality checks (:327-334). */
    val notes: List<String>,
)

/** One body-signal tile (`BodySignal` + `SignalTile`, GlowRecoveryScreen.kt:344-430). */
data class BodySignalTile(
    val metric: Metric,
    val label: String,
    val accent: RecoveryAccent,
    /** The value as Compose formats it; null shows "—" (:414). */
    val value: String?,
    val unit: String,
    /** "Faster"/"Slower"/"Higher"/"Lower"/"Normal"; null while there is no normal to compare with (:405-410). */
    val word: String?,
    /** Seven days, oldest first, gaps null (:428). */
    val week: List<Float?>,
    /** False when this signal has no normal yet, which the panel's note is about (:392). */
    val hasNormal: Boolean,
)

/** The "Body signals" panel (GlowRecoveryScreen.kt:388-394). */
data class SignalsModel(val tiles: List<BodySignalTile>, val note: String?)

/** The body-signal alert, when the night is off baseline (`BodySignalsPanel`, GlowRecoveryScreen.kt:175-187). */
data class BodySignalsModel(
    val title: String,
    val alert: Boolean,
    /** "Watching", or null for an alert (:178). */
    val trailing: String?,
    /** The signals and what they mean (:179-183). */
    val text: String,
    /** "Likely cause: …", when the day names one (:184). */
    val cause: String?,
    val disclaimer: String,
)

/** "How it works" (GlowRecoveryScreen.kt:466-496). */
data class HowItWorksModel(
    val explainer: String,
    val chargeExplainer: String,
    /** "The strap's own score", and its value; null when the strap reported no score (:484-488). */
    val strapLabel: String?,
    val strapValue: String?,
    /** "Ours", and our score; the second value is null when ours has none. */
    val oursLabel: String?,
    val oursValue: String?,
    /** The paragraph under the comparison (:489-492). */
    val strapNote: String?,
    val disclaimer: String,
)

/** The hours under the Charge chart (`HOURS`, GlowRecoveryScreen.kt:101). */
val CHARGE_HOURS = listOf("00", "06", "12", "18", "24")

/** Contributors shown before "What drove it" is opened (`CONTRIBUTORS_FOLDED`, GlowRecoveryScreen.kt:339). */
const val CONTRIBUTORS_FOLDED = 3

object RecoveryGlow {

    /**
     * The Recovery result for the day the screen is showing: Compose's
     * `recovery?.takeIf { it.date == state.date }` (GlowRecoveryScreen.kt:78), null while the day's
     * answer is still in flight or belongs to another day.
     */
    fun forDay(state: DayUiState, recovery: RecoveryResult?): RecoveryResult? =
        recovery?.takeIf { it.date == state.date }

    /**
     * The six empty months, before the day's first read lands. Swift names this rather than the
     * data class's defaults, which Kotlin/Native does not export.
     */
    fun noSignalHistories(): SignalHistories = SignalHistories()

    // ---- RecoveryCard (GlowRecoveryScreen.kt:105-168) --------------------------------------------

    /** Everything the score card draws for [r], with the strap's own row when it has one. */
    fun card(r: RecoveryResult, strap: StrapReadinessRow?): RecoveryCardModel {
        val ready = r.readiness
        val score = ready.score
        val before = r.readinessTrend.dropLast(1).lastOrNull()?.second
        val sleep = ready.contributors.firstOrNull { it.factor == Recovery.Factor.Sleep }
        val strapScore = strap?.score
        val strapParts = strap?.let { row ->
            listOfNotNull(
                row.hrvScore?.let { "HRV $it" },
                row.restingHrScore?.let { "resting HR $it" },
                row.skinTemperatureScore?.let { "temperature $it" },
                row.breathingScore?.let { "breathing $it" },
            )
        }.orEmpty()
        return RecoveryCardModel(
            trailing = trailing(ready, r.readinessTrend),
            body = if (score == null) noScoreBody(ready) else null,
            learning = score == null && ready.withheld && ready.phase == Recovery.Phase.Withheld,
            sleepNote = if (score == null) sleep?.let { "Sleep ${it.score} · ${it.detail}" } else null,
            strapValue = if (score == null && ready.showStrapEstimate) strapScore?.toString() else null,
            strapNote = if (score == null && ready.showStrapEstimate && strapParts.isNotEmpty()) {
                strapParts.joinToString(" · ") + ". Shown for reference; it never enters OpenStrap's score."
            } else {
                null
            },
            notes = if (score == null) {
                listOfNotNull(
                    ready.fallbackHrv?.let { "HRV ${it.toInt()} ms (strap fallback)" },
                    ready.fallbackRestingHr?.let { "Resting HR ${it.toInt()} bpm (strap fallback)" },
                )
            } else {
                emptyList()
            },
            score = score,
            heroFacts = if (score != null) listOf(Narrative.readinessHeadline(score, ready.strainAlert)) else emptyList(),
            heroBody = if (score != null) Narrative.readinessBody(ready).takeIf { it.isNotEmpty() } else null,
            trend = trend(r),
            hasTrend = r.readinessTrend.any { it.second != null },
            tone = TodayGlow.overviewTone(score, ready.strainAlert),
        )
    }

    /** The header's note (:118-128). */
    private fun trailing(ready: Recovery.Readiness, trend: List<Pair<LocalDate, Int?>>): String? {
        val score = ready.score
        val before = trend.dropLast(1).lastOrNull()?.second
        return when (ready.phase) {
            Recovery.Phase.Withheld -> "Learning ${ready.baselineNights}/${Recovery.SCORED_NIGHTS} nights"
            Recovery.Phase.Learning -> "Learning ${ready.baselineNights}/${Recovery.SETTLED_NIGHTS} nights"
            Recovery.Phase.Calibrated ->
                if (score != null && before != null) "Calibrated · " + vsYesterday(score, before) else "Calibrated"
            else -> if (score != null && before != null) vsYesterday(score, before) else null
        }
    }

    /** The sentence a day without a score leads with (:132-140). */
    fun noScoreBody(ready: Recovery.Readiness): String = when {
        !ready.withheld -> "Recovery is scored each morning from the night's sleep. Wear the strap to bed."
        ready.phase == Recovery.Phase.Withheld ->
            "OpenStrap scores Recovery once it has ${Recovery.SCORED_NIGHTS} good nights of your own HRV and resting heart rate."
        else ->
            "Last night's HRV and resting heart rate didn't have enough clean readings, so there's no Recovery " +
                "score today. Sleep alone isn't readiness."
    }

    /** "6 vs yesterday", "−3 vs yesterday", or "Steady vs yesterday" (:218-219). */
    fun vsYesterday(score: Int, before: Int): String =
        if (score == before) "Steady vs yesterday" else "${if (score > before) "+" else "−"}${abs(score - before)} vs yesterday"

    /** The seven days of Readiness, labelled as `GlowTrend` prints them (:161-164). */
    fun trend(r: RecoveryResult): List<RecoveryTrendDay> =
        r.readinessTrend.map { (date, value) ->
            RecoveryTrendDay(label = WEEKDAYS[date.dayOfWeek.isoDayNumber - 1].take(2), value = value?.toFloat())
        }

    // ---- Charge (GlowRecoveryScreen.kt:191-233) --------------------------------------------------

    /**
     * Charge: the number, when you woke and the day's curve, for the day [dayStart]..[dayEnd]
     * (exclusive of the next midnight's first second, as `DayUiState` holds it).
     */
    fun chargeCard(c: Recovery.ChargeDay, dayStart: Long, dayEnd: Long): ChargeCardModel {
        val now = c.latest
        val drained = (c.drainedExercise + c.drainedStress + c.drainedLiving).toInt()
        val hint = when {
            !c.anchored ->
                "Charge starts after your first night wearing the strap. Sleep is what fills it, so it needs one to measure from."
            now == null -> "Charge appears once the strap has a few hours of heart rate."
            else -> null
        }
        return ChargeCardModel(
            trailing = c.atWake?.let { "${it.toInt()} at waking" },
            hint = hint,
            value = now?.toInt()?.toString(),
            unit = "/ 100",
            facts = if (now == null || hint != null) {
                emptyList()
            } else {
                listOf(Narrative.chargeHeadline(now.toInt()), "${signed(c.recharged, 1)} · ${signed(drained.toFloat(), -1)}")
            },
            values = chargeBuckets(c.points, dayStart, dayEnd),
            labels = CHARGE_HOURS,
            level = if (hint == null) now else null,
        )
    }

    /** Averages charge samples into quarter-hour buckets across the day; empty buckets are gaps (:222-231). */
    fun chargeBuckets(points: List<Recovery.ChargePoint>, dayStart: Long, dayEnd: Long): List<Float?> =
        bucket(points, dayStart, dayEnd, BUCKET_SECONDS)

    /** The touch readout on a charge point: "62 · 07:35" (:211-213). */
    fun chargeReadout(dayStart: Long, index: Int, value: Float): String =
        chargeReadout(dayStart, index, value, ScreenClock.zone())

    /** [chargeReadout] in [zone], for the tests. */
    fun chargeReadout(dayStart: Long, index: Int, value: Float, zone: TimeZone): String =
        "${value.toInt()} · ${clock(dayStart + index * BUCKET_SECONDS, zone)}"

    /** Fifteen minutes, the Charge chart's bucket (:210). */
    private const val BUCKET_SECONDS = 15 * 60L

    private fun bucket(points: List<Recovery.ChargePoint>, from: Long, to: Long, seconds: Long): List<Float?> {
        val n = ((to - from + 1) / seconds).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        points.forEach {
            val i = ((it.t - from) / seconds).toInt()
            if (i in 0 until n) { sums[i] += it.value; counts[i]++ }
        }
        return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }

    /** A charge amount with its sign, and no sign at all for zero ("−0" reads oddly) (:251-254). */
    fun signed(v: Float, sign: Int): String {
        val n = v.toInt()
        return if (n == 0) "0" else "${if (sign > 0) "+" else "−"}$n"
    }

    // ---- Where your charge went (GlowRecoveryScreen.kt:237-248) ----------------------------------

    fun flow(r: RecoveryResult): ChargeFlowModel {
        val c = r.charge
        val total = maxOf(c.recharged, c.drainedExercise + c.drainedStress + c.drainedLiving, 1f)
        return ChargeFlowModel(
            rows = listOf(
                ChargeFlowRow("Sleep and calm rest", signed(c.recharged, 1), c.recharged / total, ChargeFlowAccent.Good),
                ChargeFlowRow(
                    "Movement and exercise", signed(c.drainedExercise, -1), c.drainedExercise / total, ChargeFlowAccent.Steps,
                ),
                ChargeFlowRow(
                    "Physiological stress (estimate)", signed(c.drainedStress, -1), c.drainedStress / total, ChargeFlowAccent.Stress,
                ),
                ChargeFlowRow("Being awake", signed(c.drainedLiving, -1), c.drainedLiving / total, ChargeFlowAccent.SleepLight),
            ),
            highLow = if (c.highest != null && c.lowest != null) {
                "Highest ${c.highest.toInt()} · lowest ${c.lowest.toInt()} this day"
            } else {
                null
            },
            stressNote = Narrative.stressThreshold(r.stressThreshold),
        )
    }

    // ---- What drove it (GlowRecoveryScreen.kt:296-339) -------------------------------------------

    /** The contributors, grouped by what they are about, folded to the heaviest few until opened. */
    fun contributors(ready: Recovery.Readiness, open: Boolean): ContributorsModel {
        val folds = ready.contributors.size > CONTRIBUTORS_FOLDED
        // `sortedByDescending { weight }.take(3).toSet()` (:300).
        val shown = if (folds && !open) {
            ready.contributors.sortedByDescending { it.factor.weight }.take(CONTRIBUTORS_FOLDED).toSet()
        } else {
            ready.contributors.toSet()
        }
        val groups = listOf(
            "Body" to setOf(
                Recovery.Factor.HrvBalance, Recovery.Factor.RestingHr, Recovery.Factor.Temperature,
                Recovery.Factor.Breathing, Recovery.Factor.BloodOxygen,
            ),
            "Sleep" to setOf(Recovery.Factor.Sleep, Recovery.Factor.SleepBalance, Recovery.Factor.RecoveryIndex),
            "Load" to setOf(Recovery.Factor.PreviousDayLoad),
        )
        var first = true
        val built = groups.mapNotNull { (name, factors) ->
            val rows = ready.contributors.filter { it.factor in factors && it in shown }
                .sortedByDescending { it.factor.weight }
                .map { c ->
                    val rating = Narrative.ratingFor(c.score)
                    ContributorRow(label = c.factor.label, rating = rating.label, detail = c.detail, fraction = c.score / 100f)
                }
            if (rows.isEmpty()) return@mapNotNull null
            ContributorGroup(name = name.uppercase(), rows = rows, afterDivider = !first).also { first = false }
        }
        val rangeWord = when (ready.hrvRange) {
            Recovery.HrvRange.Below -> "below"
            Recovery.HrvRange.Within -> "within"
            Recovery.HrvRange.Above -> "above"
            null -> null
        }
        return ContributorsModel(
            trailing = if (folds && !open) "Top $CONTRIBUTORS_FOLDED of ${ready.contributors.size}" else null,
            foldable = folds,
            groups = built,
            notes = listOfNotNull(
                if (ready.capped) {
                    "Capped: one body signal is far from your normal, so good sleep can't fully make up for it."
                } else {
                    null
                },
                rangeWord?.let { word ->
                    "7-night HRV is $word your normal range" +
                        if (ready.hrvWeekThin) " (thin: ${ready.hrvWeekNights} of 7 nights)." else "."
                },
                ready.fallbackHrv?.let {
                    "HRV ${it.toInt()} ms (strap fallback): our own reading wasn't clean enough last night."
                },
                ready.fallbackRestingHr?.let {
                    "Resting HR ${it.toInt()} bpm (strap fallback): our own reading wasn't clean enough last night."
                },
            ) + ready.qualityFlags.map { "Check: $it" },
        )
    }

    // ---- Body signals (GlowRecoveryScreen.kt:344-452) -------------------------------------------

    /**
     * The six body signals, in Compose's order: today's value, the word against your normal, and the
     * week as a sparkline. [histories] is the month behind each normal.
     */
    fun signals(state: DayUiState, histories: SignalHistories): SignalsModel {
        val end = state.date
        val week = (6 downTo 0).map { end.minusDays(it.toLong()) }
        fun weekOf(stats: List<DayStat>): List<Float?> = week.map { d -> stats.firstOrNull { it.date == d }?.value }
        fun normalOf(stats: List<DayStat>): History.Normal? = History.normal(stats, end)

        val signals = listOf(
            build(
                Metric.RestingHr, "Resting HR", RecoveryAccent.Heart, state.restingHeartRate?.toFloat(),
                weekOf(histories.restingHr), normalOf(histories.restingHr), up = "Faster", down = "Slower",
            ),
            build(
                Metric.Hrv, "HRV", RecoveryAccent.Hrv, state.hrv.avg()?.toFloat(),
                weekOf(histories.hrv), normalOf(histories.hrv),
            ),
            build(
                Metric.SkinTemperature, "Skin temp", RecoveryAccent.Temperature,
                state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(),
                weekOf(histories.skinTemperature), normalOf(histories.skinTemperature), asDeviation = true,
            ),
            build(
                Metric.Breathing, "Breathing", RecoveryAccent.SleepLight, state.breathRate.avg()?.toFloat(),
                weekOf(histories.breathing), normalOf(histories.breathing),
            ),
            build(
                Metric.Spo2, "Blood oxygen", RecoveryAccent.Spo2, state.spo2.avg()?.toFloat(),
                weekOf(histories.spo2), normalOf(histories.spo2),
            ),
            build(
                Metric.Stress, "Stress", RecoveryAccent.Stress, state.stress.avg()?.toFloat(),
                weekOf(histories.stress), normalOf(histories.stress),
            ),
        )
        return SignalsModel(
            tiles = signals,
            note = if (signals.all { !it.hasNormal }) {
                "Your normal builds over the first week of wear; until then each signal shows its week."
            } else {
                null
            },
        )
    }

    /** One `BodySignal` (:344-356) with its `SignalTile`'s formatted value and word (:397-410). */
    private fun build(
        metric: Metric,
        label: String,
        accent: RecoveryAccent,
        today: Float?,
        week: List<Float?>,
        normal: History.Normal?,
        up: String = "Higher",
        down: String = "Lower",
        asDeviation: Boolean = false,
    ): BodySignalTile {
        val value = today?.let { v ->
            if (asDeviation && normal != null) {
                val d = v - normal.mean
                "${if (d >= 0) "+" else "−"}${metric.format(abs(d))}"
            } else {
                metric.format(v)
            }
        }
        val word = when (History.status(today, normal)) {
            1 -> up
            -1 -> down
            0 -> "Normal"
            else -> null
        }
        return BodySignalTile(
            metric = metric,
            label = label,
            accent = accent,
            value = value,
            unit = metric.unit,
            word = word,
            week = week,
            hasNormal = normal != null,
        )
    }

    /** `List<ChartPoint>.avg()` (ScreenParts.kt:191): the day's mean, rounded; null without readings. */
    private fun List<ChartPoint>.avg(): Int? {
        val values = map { it.y }
        return if (values.isEmpty()) null else round(values.average()).toInt()
    }

    // ---- Body signals off baseline (GlowRecoveryScreen.kt:175-187) ------------------------------

    fun bodySignals(body: BodySignals.Day): BodySignalsModel {
        val alert = body.state == BodySignals.State.Alert
        val signals = body.signals.joinToString(", ").replaceFirstChar { it.uppercase() }
        val tail = if (alert) {
            BodySignalsCopy.CAUSES + " " + BodySignalsCopy.ADVICE
        } else {
            "One signal is outside your usual range. OpenStrap is keeping an eye on it."
        }
        return BodySignalsModel(
            title = BodySignalsCopy.TITLE,
            alert = alert,
            trailing = if (alert) null else "Watching",
            text = "$signals. $tail",
            cause = if (body.causes.isNotEmpty()) "Likely cause: ${body.causes.joinToString(", ")}." else null,
            disclaimer = BodySignalsCopy.DISCLAIMER,
        )
    }

    // ---- How it works (GlowRecoveryScreen.kt:466-496) -------------------------------------------

    fun howItWorks(ready: Recovery.Readiness, strap: StrapReadinessRow?): HowItWorksModel {
        val strapScore = strap?.score
        return HowItWorksModel(
            explainer =
                "Each morning OpenStrap compares your night with what's normal for you, not with other people: HRV, " +
                    "resting heart rate, skin temperature, breathing and blood oxygen, last night's sleep and the past " +
                    "few nights, and how hard the previous days were. The score is a weighted mix; one signal far from " +
                    "normal caps it.",
            chargeExplainer =
                "Charge is a running battery: sleep and calm rest fill it, movement, stress and simply being awake drain it.",
            strapLabel = if (strapScore != null) "The strap's own score" else null,
            strapValue = strapScore?.toString(),
            oursLabel = if (strapScore != null && ready.score != null) "Ours" else null,
            oursValue = if (strapScore != null) ready.score?.toString() else null,
            strapNote = if (strapScore != null) {
                "The strap's score doesn't count sleep length, so after a short night it reads higher than ours. " +
                    "OpenStrap keeps both each day to tune its own score once there are a couple of weeks to compare."
            } else {
                null
            },
            disclaimer = BodySignalsCopy.DISCLAIMER,
        )
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    /** "HH:mm" of [epochSeconds] in [zone]. */
    private fun clock(epochSeconds: Long, zone: TimeZone): String {
        val time = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    /** `getDisplayName(TextStyle.SHORT, locale)`: "Mon" … "Sun", cut to two letters where it is used. */
    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
