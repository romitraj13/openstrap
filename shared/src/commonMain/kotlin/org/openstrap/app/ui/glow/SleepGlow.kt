package org.openstrap.app.ui.glow

import kotlinx.datetime.TimeZone
import org.openstrap.app.data.BodySignalsCopy
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SleepRegularity
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.format
import org.openstrap.app.data.formatDuration
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.SleepVital
import org.openstrap.app.ui.dashboard.SleepWindowsModel
import org.openstrap.app.ui.dashboard.bandAccent
import org.openstrap.app.ui.dashboard.minutesClock
import org.openstrap.app.ui.dashboard.sleepVitals
import org.openstrap.app.ui.dashboard.sleepWeek
import org.openstrap.app.ui.dashboard.sleepWindows

/*
 * What Compose's Sleep tab computes, in one place Swift can call (card U7).
 *
 * `GlowSleepScreen.kt` works out more than formatting: which number the night leads with, the
 * sentence under it, the night's facts, each stage's share, the contributors' ratings, the vitals'
 * words against your normal, the nap lines and the week's regularity sentence. Standing instruction
 * 5 says such logic is copied into `shared/` with a JVM test rather than written again in Swift, so
 * the two builds cannot drift; the drawing stays Swift's. Every function below names the Compose
 * lines it came from, and is unchanged apart from `java.time` becoming `kotlinx.datetime`, `Color`
 * becoming an accent name for `Theme.swift`, and `Instant`/`DateTimeFormatter` becoming `clockOf`.
 */

/** A colour a Sleep panel passes to Compose as a `Color`; `Theme.swift` maps the name back. */
enum class SleepAccent { Heart, Hrv, Temperature, Spo2, SleepLight, SleepDeep, SleepRem, SleepAwake, Good, Warn, Stress }

/** Everything the night's hero draws (`NightCard`, GlowSleepScreen.kt:122-171). */
data class SleepHeroModel(
    /** "23:12 – 07:01", the header's note (:127). */
    val trailing: String?,
    /** True when the night has no session at all, so the card is the two empty sentences (:128-135). */
    val missing: Boolean,
    /** "No sleep recorded for this night. …", or "No night sleep recorded, only naps." */
    val emptyText: String?,
    /** The dot matrix's text: the score, else the time asleep as "7:04" (:142-145). */
    val value: String?,
    /** "Sleep score", or "asleep · goal 8h" (:147-150). */
    val label: String?,
    /** The sentence under it: `Narrative.sleepHeadline`, or "7h 4m asleep" (:152-155). */
    val headline: String?,
    /** "7h 4m asleep · 7h 49m in bed · woke 3×" (:156-163). */
    val facts: String?,
    /** The arc: score/100, else time asleep against the goal (:141). */
    val progress: Float?,
    val hasScore: Boolean,
)

/** One row of the Stages panel (StageRows, GlowSleepScreen.kt:200-219). */
data class SleepStageRow(
    val label: String,
    /** `formatDuration` of the stage's seconds. */
    val value: String,
    /** Its share of the night, as Compose prints it: "47%". */
    val percent: String,
    /** The bar's length, 0..1. */
    val fraction: Float,
    val accent: SleepAccent,
)

/** One contributor under "What shaped the score" (`Narrative.Contributor`, GlowSleepScreen.kt:225-234). */
data class SleepContributorRow(val label: String, val rating: String, val detail: String, val fraction: Float)

/** The "What shaped the score" panel (ContributorsPanel, GlowSleepScreen.kt:221-237). */
data class SleepContributorsModel(val rows: List<SleepContributorRow>, val note: String)

/** One chart in the night-charts panel (`GlowLineChart`, GlowSleepScreen.kt:268-282). */
data class SleepChartRow(
    val title: String,
    /** "Lowest 48 · avg 51 bpm", "avg 12.5 br/min" (:269, :277). */
    val trailing: String,
    /** Five-minute averages across the night, gaps null. */
    val values: List<Float?>,
    val accent: SleepAccent,
    /** 110.dp for heart rate, 90.dp for breathing. */
    val height: Float,
    /** "bpm", or "br/min" for the breathing line (:272, :280). */
    val unit: String,
)

/** The heart-rate and breathing charts under the night (NightChartsPanel, GlowSleepScreen.kt:259-284). */
data class NightChartsModel(
    val rows: List<SleepChartRow>,
    val labels: List<String>,
    /** The night's first second, so a touch readout can name the time it is over. */
    val start: Long,
)

/** One nap, with what its row shows and what opening it adds (:304-331). */
data class SleepNap(
    val start: Long,
    val end: Long,
    /** "14:00 – 14:40 · 40m" (:311-314). */
    val line: String,
    /** The nap's hypnogram spans; empty when it has none. */
    val stages: List<StageSpan>,
    /** Its stage rows, when the strap staged it. */
    val stageRows: List<SleepStageRow>,
    /** "The strap didn't record stages for this nap." when it has none (:320). */
    val stagesNote: String?,
    /** "Heart rate · lowest 58 bpm" (:329). */
    val hrNote: String?,
    /** Five-minute averages across the nap, gaps null. */
    val hrValues: List<Float?>,
    val hrLabels: List<String>,
)

/** The day's naps (NapsPanel, GlowSleepScreen.kt:298-335). */
data class SleepNapsModel(val total: String, val rows: List<SleepNap>)

/** One figure in the week panel's row of three (`Fact`, GlowSleepScreen.kt:350-354). */
data class SleepWeekFact(val label: String, val value: String)

/** The week's regularity row, bar and sentence (:355-371). */
data class SleepRegularityModel(
    /** "100%" (:361). */
    val score: String,
    /** "Very regular" (:362). */
    val band: String,
    val accent: SleepAccent,
    /** "Fall asleep 23:15" (:365). */
    val fallAsleep: String,
    /** "Wake up 07:00" (:366). */
    val wakeUp: String,
    /** The bar: score/100 (:369). */
    val fraction: Float,
    /** Compose's sentence under the bar (:370). */
    val note: String,
)

/** "This week" (WeekPanel, GlowSleepScreen.kt:337-373). */
data class SleepWeekModel(
    /** "Last 7 nights" (:344). */
    val trailing: String,
    /** False when none of the seven nights has sleep, so the panel is one sentence (:345-348). */
    val hasWeek: Boolean,
    val emptyNote: String?,
    /** The seven nights on their clock (`SleepWindows`, :349). */
    val windows: SleepWindowsModel?,
    val facts: List<SleepWeekFact>,
    val regularity: SleepRegularityModel?,
    /** The sentence shown instead of the regularity rows while the timing is still learning (:356). */
    val learningNote: String?,
)

/** The breathing-disturbances panel (BreathingCard, GlowSleepScreen.kt:379-393). */
data class SleepBreathingModel(
    /** "3.4 /h" (:384). */
    val disturbances: String,
    /** "2.0 /h", or "—" before there is a usual (:385). */
    val usual: String,
    val note: String,
)

/** One of the sleep-health panel's three rows (:417-419). */
data class SleepHealthRow(val label: String, val value: String)

/** "Sleep health" (SleepHealthPanel, GlowSleepScreen.kt:399-426). */
data class SleepHealthModel(
    /** "2 of 3 good", when the night has the count (:402). */
    val trailing: String?,
    /** "80", "±42 min" or "—" (:404). */
    val regularityValue: String,
    /** "Regular", "Steady", "Variable" or "—" (:405). */
    val bandValue: String,
    /** Which sentence the panel explains itself with (:407-413). */
    val note: String,
    /** The three dimensions, empty when the night has no count (:414-419). */
    val rows: List<SleepHealthRow>,
    /** The midpoint line, when there is a count (:420-423). */
    val midpointNote: String?,
)

object SleepGlow {

    // ---- NightCard (GlowSleepScreen.kt:122-171) --------------------------------------------------

    /** The hero for [state]'s night, in [zone]. */
    fun hero(state: DayUiState, goalSeconds: Long, zone: TimeZone): SleepHeroModel {
        val session = state.sleep
        val trailing = session?.let { "${clock(it.start, zone)} – ${clock(it.end, zone)}" }
        if (session == null) {
            return SleepHeroModel(
                trailing = trailing,
                missing = true,
                emptyText = if (state.naps.isEmpty()) {
                    "No sleep recorded for this night. Wear the strap to bed and sync in the morning."
                } else {
                    "No night sleep recorded, only naps."
                },
                value = null,
                label = null,
                headline = null,
                facts = null,
                progress = null,
                hasScore = false,
            )
        }
        val asleep = state.asleepSeconds ?: (session.end - session.start)
        val score = session.score
        return SleepHeroModel(
            trailing = trailing,
            missing = false,
            emptyText = null,
            value = score?.toString() ?: "%d:%02d".format(asleep / 3600, asleep % 3600 / 60),
            label = if (score != null) "Sleep score" else "asleep · goal ${formatDuration(goalSeconds)}",
            headline = score?.let { Narrative.sleepHeadline(it) } ?: "${formatDuration(asleep)} asleep",
            facts = listOfNotNull(
                if (score != null) "${formatDuration(asleep)} asleep" else null,
                "${formatDuration(session.end - session.start)} in bed",
                session.wakeCount?.let { "woke $it×" },
            ).joinToString(" · "),
            progress = score?.let { it / 100f } ?: (asleep.toFloat() / goalSeconds),
            hasScore = score != null,
        )
    }

    // ---- StageRows (GlowSleepScreen.kt:200-219) --------------------------------------------------

    /** Minutes and share of each stage, in `SLEEP_NAMES`' order: Awake, REM, Light, Deep. */
    fun stageRows(b: Summaries.SleepBreakdown): List<SleepStageRow> {
        val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
        return SLEEP_NAMES.map { (stage, label) ->
            val secs = b.byStage[stage] ?: 0L
            SleepStageRow(
                label = label,
                value = formatDuration(secs),
                percent = "${secs * 100 / total}%",
                fraction = secs.toFloat() / total,
                accent = SLEEP_ACCENTS.getValue(stage),
            )
        }
    }

    // ---- VitalsPanel (GlowSleepScreen.kt:240-257) ------------------------------------------------

    /** The night's vitals as tiles, in `sleepVitals`' order; each opens its metric. */
    fun vitals(state: DayUiState): List<SleepVital> = sleepVitals(state)

    /** [vitals] in [zone], for the tests. */
    fun vitals(state: DayUiState, zone: TimeZone): List<SleepVital> = sleepVitals(state, zone)

    // ---- ContributorsPanel (GlowSleepScreen.kt:86-92, 221-237) ----------------------------------

    /**
     * What shaped the score, or null when the night has no score or no contributors: Compose draws
     * the panel only on `session.score != null && contributors.isNotEmpty()` (:92). [recovery] is the
     * day's own result, filtered here as the screen filters it at :79.
     */
    fun contributors(state: DayUiState, recovery: RecoveryResult?): SleepContributorsModel? {
        val session = state.sleep ?: return null
        if (session.score == null) return null
        val rec = recovery?.takeIf { it.date == state.date }
        val waso = Recovery.wasoMinutes(state.sleepStages.map { Triple(it.start, it.end, it.stage) })
        val list = Narrative.sleepContributors(
            session,
            state.asleepSeconds ?: 0,
            rec?.sleepNeed ?: Recovery.SLEEP_NEED_MINUTES,
            waso,
        )
        if (list.isEmpty()) return null
        return SleepContributorsModel(
            rows = list.map { SleepContributorRow(it.label, it.rating.label, it.detail, it.fraction) },
            note = "Ratings use general sleep guidance (7+ hours, about a fifth each of deep and REM sleep). " +
                "They're a guide, not a medical assessment.",
        )
    }

    // ---- NightChartsPanel (GlowSleepScreen.kt:259-284) -------------------------------------------

    /** Heart rate and breathing across the night, or null when neither has a reading (:265). */
    fun nightCharts(state: DayUiState, zone: TimeZone): NightChartsModel? {
        val session = state.sleep ?: return null
        val window = session.start..session.end
        val hr = state.sleepHeartRate.filter { it.x in window }
        val breath = state.breathRate.filter { it.x in window }
        if (hr.isEmpty() && breath.isEmpty()) return null
        val labels = listOf(clock(session.start, zone), clock(session.end, zone))
        val rows = buildList {
            if (hr.isNotEmpty()) {
                add(
                    SleepChartRow(
                        title = "Heart rate",
                        trailing = "Lowest ${hr.minOf { it.y }.toInt()} · avg ${hr.map { it.y }.average().toInt()} bpm",
                        values = evenly(hr, window, FIVE_MINUTES),
                        accent = SleepAccent.Heart,
                        height = 110f,
                        unit = "bpm",
                    ),
                )
            }
            if (breath.isNotEmpty()) {
                add(
                    SleepChartRow(
                        title = "Breathing",
                        trailing = "avg %.1f br/min".format(breath.map { it.y }.average()),
                        values = evenly(breath, window, FIVE_MINUTES),
                        accent = SleepAccent.SleepLight,
                        height = 90f,
                        unit = "br/min",
                    ),
                )
            }
        }
        return NightChartsModel(rows, labels, session.start)
    }

    // ---- NapsPanel (GlowSleepScreen.kt:298-335) --------------------------------------------------

    /**
     * The day's naps, or null when there are none (:99). [openStart] is the nap the reader has
     * opened, -1 for none.
     */
    fun naps(state: DayUiState, openStart: Long, zone: TimeZone): SleepNapsModel? {
        if (state.naps.isEmpty()) return null
        val rows = state.naps.map { nap ->
            val expanded = openStart == nap.start
            val stages = if (expanded) state.napStages[nap.start].orEmpty() else emptyList()
            val breakdown = if (expanded && stages.isNotEmpty()) state.napBreakdowns[nap.start] else null
            val hr = if (expanded) state.heartRate.filter { it.x in nap.start..nap.end } else emptyList()
            SleepNap(
                start = nap.start,
                end = nap.end,
                line = "${clock(nap.start, zone)} – ${clock(nap.end, zone)} · ${formatDuration(nap.end - nap.start)}",
                stages = stages,
                stageRows = breakdown?.let { stageRows(it) }.orEmpty(),
                stagesNote = if (expanded && stages.isEmpty()) "The strap didn't record stages for this nap." else null,
                // The strap reports no average for naps, so it comes from the day's heart rate (:325-331).
                hrNote = if (hr.isNotEmpty()) "Heart rate · lowest ${hr.minOf { it.y }.toInt()} bpm" else null,
                hrValues = if (hr.isNotEmpty()) evenly(hr, nap.start..nap.end, FIVE_MINUTES) else emptyList(),
                hrLabels = listOf(clock(nap.start, zone), clock(nap.end, zone)),
            )
        }
        return SleepNapsModel(formatDuration(state.naps.sumOf { it.end - it.start }), rows)
    }

    /** The nap a panel starts with open: a lone nap, else none (`state.naps.singleOrNull()?.start ?: -1L`, :301). */
    fun defaultOpenNap(state: DayUiState): Long = state.naps.singleOrNull()?.start ?: -1L

    // ---- The phone's own zone, for Swift ---------------------------------------------------------
    //
    // Compose reads `ZoneId.systemDefault()`; Swift has no kotlinx-datetime type to pass, so these
    // are the same calls in the phone's zone (as `RecoveryGlow.chargeReadout` does).

    /** [hero] in the phone's own zone. */
    fun hero(state: DayUiState, goalSeconds: Long): SleepHeroModel =
        hero(state, goalSeconds, TimeZone.currentSystemDefault())

    /** [nightCharts] in the phone's own zone. */
    fun nightCharts(state: DayUiState): NightChartsModel? =
        nightCharts(state, TimeZone.currentSystemDefault())

    /** [naps] in the phone's own zone. */
    fun naps(state: DayUiState, openStart: Long): SleepNapsModel? =
        naps(state, openStart, TimeZone.currentSystemDefault())

    /** [week] in the phone's own zone. */
    fun week(state: DayUiState, goalSeconds: Long): SleepWeekModel =
        week(state, goalSeconds, TimeZone.currentSystemDefault())

    // ---- WeekPanel (GlowSleepScreen.kt:337-373) --------------------------------------------------

    /**
     * "This week": the seven nights on their clock, the week's three facts and how regular the timing
     * was. [state]'s goal is the profile's sleep goal, as `LocalGoals.current.sleepSeconds` is.
     */
    fun week(state: DayUiState, goalSeconds: Long, zone: TimeZone): SleepWeekModel {
        val w = sleepWeek(state, goalSeconds, zone)
            ?: return SleepWeekModel(
                trailing = "Last 7 nights",
                hasWeek = false,
                emptyNote = "No sleep recorded in the last 7 nights.",
                windows = null,
                facts = emptyList(),
                regularity = null,
                learningNote = null,
            )
        val r = w.regularity
        return SleepWeekModel(
            trailing = "Last 7 nights",
            hasWeek = true,
            emptyNote = null,
            windows = sleepWindows(w.nights, r, state.date, goalSeconds, zone),
            facts = listOf(
                SleepWeekFact("Avg. asleep", w.averageAsleep),
                SleepWeekFact("Avg. score", w.averageScore),
                SleepWeekFact("Reached ${formatDuration(goalSeconds)}", "${w.goalNights} of ${w.slept}"),
            ),
            regularity = r?.let {
                SleepRegularityModel(
                    score = "${it.score}%",
                    band = it.band.label,
                    accent = bandAccent(it.band),
                    fallAsleep = "Fall asleep ${minutesClock(it.bedtime)}",
                    wakeUp = "Wake up ${minutesClock(it.wake)}",
                    fraction = it.score / 100f,
                    note = w.regularityNote.orEmpty(),
                )
            },
            learningNote = if (r == null) w.learningNote else null,
        )
    }

    // ---- BreathingCard (GlowSleepScreen.kt:379-393) ----------------------------------------------

    /** The strap's oxygen-desaturation count against your own nights. */
    fun breathing(b: Recovery.BreathingDisturbances): SleepBreathingModel = SleepBreathingModel(
        disturbances = "%.1f /h".format(b.perHour),
        usual = b.usual?.let { "%.1f /h".format(it) } ?: "—",
        note = if (b.usual == null) {
            "Your usual builds after ${Recovery.SCORED_NIGHTS} nights with a breathing summary."
        } else {
            "Dips in blood oxygen during sleep, counted by the strap. They are fairly steady for each person, " +
                "so they're shown here rather than in Recovery. " + BodySignalsCopy.DISCLAIMER
        },
    )

    // ---- SleepHealthPanel (GlowSleepScreen.kt:399-426) -------------------------------------------

    /** How regular the last week was, and how many of the three dimensions were good last night. */
    fun sleepHealth(reg: SleepRegularity.Result, health: SleepRegularity.Health?): SleepHealthModel {
        fun word(v: Boolean?): String = when (v) {
            true -> "Good"
            false -> "Not quite"
            null -> "—"
        }
        return SleepHealthModel(
            trailing = health?.let { "${it.count} of 3 good" },
            regularityValue = reg.sri?.let { "${it.toInt()}" } ?: reg.midpointSdMinutes?.let { "±${it.toInt()} min" } ?: "—",
            bandValue = reg.band?.label
                ?: if (reg.midpointSdMinutes != null) (if (reg.good == true) "Steady" else "Variable") else "—",
            note = when {
                reg.sri != null ->
                    "How alike your sleep and wake times were, minute by minute, across the last 7 days " +
                        "(${reg.validPairs / 60} h compared)."
                reg.midpointSdMinutes != null ->
                    "Not enough wear for the regularity index yet, so this is how much your sleep's midpoint moved this week."
                else -> "Regularity needs about 5 days of wear around the clock, night and day."
            },
            rows = if (health == null) {
                emptyList()
            } else {
                listOf(
                    SleepHealthRow("Duration", word(health.duration)),
                    SleepHealthRow("Continuity", word(health.continuity)),
                    SleepHealthRow("Regularity", word(health.regularity)),
                )
            },
            midpointNote = health?.let {
                "Midpoint of your sleep ${SleepRegularity.clock(it.midpointMinutes)}" +
                    if (it.midpointOutside) {
                        ", outside 01:30–04:30. Shown for interest; it isn't counted."
                    } else {
                        "."
                    }
            },
        )
    }

    /** The touch readout on a night chart: "51 bpm · 23:12" (:272, 280). */
    fun readout(from: Long, index: Int, value: Float, unit: String, zone: TimeZone): String =
        "${value.toInt()} $unit · ${clock(from + index * FIVE_MINUTES, zone)}"

    /** [readout] in the phone's own zone, for Swift. */
    fun readout(from: Long, index: Int, value: Float, unit: String): String =
        readout(from, index, value, unit, TimeZone.currentSystemDefault())

    // ---- Helpers ---------------------------------------------------------------------------------

    /** Five minutes, the night charts' bucket (:270, :278, :328). */
    private const val FIVE_MINUTES = 5 * 60L

    /** Averages timed points into evenly spaced buckets across [range]; empty buckets are gaps (:286-296). */
    private fun evenly(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
        val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        points.forEach {
            val i = ((it.x - range.first) / seconds).toInt()
            if (i in 0 until n) {
                sums[i] += it.y
                counts[i]++
            }
        }
        return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    /** `SLEEP_NAMES` (ScreenParts.kt:60-65), in the order the map declares them. */
    private val SLEEP_NAMES = listOf(
        SleepStage.AWAKE to "Awake",
        SleepStage.REM to "REM",
        SleepStage.LIGHT to "Light",
        SleepStage.DEEP to "Deep",
    )

    /** `SLEEP_COLORS` (ScreenParts.kt:53-58), as accent names. */
    private val SLEEP_ACCENTS = mapOf(
        SleepStage.AWAKE to SleepAccent.SleepAwake,
        SleepStage.REM to SleepAccent.SleepRem,
        SleepStage.LIGHT to SleepAccent.SleepLight,
        SleepStage.DEEP to SleepAccent.SleepDeep,
    )

    /** "HH:mm" of [epochSeconds] in [zone] (`clock`, GlowSleepScreen.kt:114). */
    fun clock(epochSeconds: Long, zone: TimeZone): String =
        org.openstrap.app.ui.dashboard.clockOf(epochSeconds, zone)
}
