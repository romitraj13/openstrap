package org.openstrap.app.ui.glow

import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.data.Activity
import org.openstrap.app.data.EnergyExpenditure
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.Vo2max
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.DailyPai
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.format
import org.openstrap.app.data.formatDuration
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.shared.clock.ScreenClock
import kotlin.time.Instant

/*
 * What Compose's Activity tab computes, in one place Swift can call (card U8).
 *
 * `GlowActivityScreen.kt` works out more than formatting: the steps goal's sentence, which facts the
 * energy panel can offer, PAI's points, its rolling total's segments, the load and fitness rows'
 * fractions and every note under them. Standing instruction 5 says such logic is copied into
 * `shared/` with a JVM test rather than written again in Swift, so the two builds cannot drift; the
 * drawing stays Swift's. Every function below names the Compose lines it came from, and is unchanged
 * apart from `java.time` becoming `kotlinx.datetime`, `Color` becoming an accent name for
 * `Theme.swift`, and `LocalDate.now()`/`System.currentTimeMillis()` becoming [ScreenClock]'s (card
 * F7: a `-demoData` run must draw the same day at any hour).
 */

/** A colour `GlowActivityScreen.kt` passes to Compose as a `Color`; `Theme.swift` maps the name back. */
enum class ActivityAccent { Steps, Heart, Hrv, Good, Warn, SleepLight }

/** PAI's weekly target: 100 or more over the last seven days (:53). */
private const val PAI_TARGET = 100

/** Recent workouts listed under the day's own, before "All workouts" (:56). */
private const val RECENT_WORKOUTS = 3

// MARK: - Steps (StepsCard, GlowActivityScreen.kt:105-132)

/** The steps card: the day's hero total, its goal, and the three facts under the chart. */
data class StepsModel(
    /** How far the glow has advanced through the day, 0..1 (:108). */
    val progress: Float,
    /** "68% of 8,000", or "Goal reached" (:112). */
    val goal: String,
    /** "Most of the benefit is reached by about 8,000 steps a day (Paluch et al. 2022).", when we know it (:129). */
    val stepGoalNote: String?,
    /** The strap's counter against its minute records, more than 5 % apart (:127). */
    val divergeNote: String?,
    /** "Active hours" — "6 of 8" (:120). */
    val activeHours: String,
    /** "Distance" — "≈ 4.2 km", or "—" (:122). */
    val distance: String,
    /** "Active kcal" — "412", or "—" (:123). */
    val activeKcal: String,
)

// MARK: - Active minutes (MvpaPanel, GlowActivityScreen.kt:165-181)

/** Moderate-to-vigorous minutes today and over the week, against the WHO's 150 (:170-178). */
data class ActiveMinutesModel(
    /** "Last 7 days", the header's note (:168). */
    val trailing: String,
    /** "This week" — "120 / 150" (:170). */
    val week: String,
    /** "Moderate" — "96" (:171). */
    val moderate: String,
    /** "Vigorous" — "12" (:172). */
    val vigorous: String,
    /** The bar: the week's equivalent minutes against the target (:174). */
    val fraction: Float,
    val note: String,
)

// MARK: - Energy (EnergyPanel, GlowActivityScreen.kt:136-161)

/** Energy expenditure, or the row that asks for the profile it needs (:138-159). */
data class EnergyModel(
    /** False when there is no weight, height and age: the panel is the profile row (:139-144). */
    val hasEstimate: Boolean,
    /** "Partial day", the header's note (:138). */
    val partial: String?,
    /** "Total" — "≈ 2,140 kcal" (:147). */
    val total: String,
    /** "At rest" — "1,780 /day" (:148). */
    val atRest: String,
    /** "Activity level" — "1.42", or "—" (:149). */
    val activityLevel: String,
    val note: String,
)

// MARK: - PAI (PaiPanel, GlowActivityScreen.kt:188-266)

/** One of the seven days under the PAI bar (:208-218). */
data class PaiDayRow(
    /** The weekday's first two letters, oldest first: "Th", "Fr", … (:211). */
    val name: String,
    /** `paiPoints`: "+12", "+<1" or "–" (:232). */
    val points: String,
    /** Today, whose points are heart red (:214). */
    val today: Boolean,
    /** A day with no points (:214). */
    val empty: Boolean,
)

/** One segment of the rolling total's bar (PaiStack, GlowActivityScreen.kt:242-266). */
data class PaiSegment(
    /** Where the segment starts, in points from the bar's left edge (:252). */
    val from: Float,
    /** How wide it is, in points (:255). */
    val width: Float,
    /** Today's segment is heart red; the others are white, the oldest faintest (:256-260). */
    val today: Boolean,
    /** The white segment's opacity, when it is not today's (:258-259). */
    val white: Float,
)

/** The rolling 7-day score against its target, the days that make it up, and what drops off next. */
data class PaiModel(
    /** False until the strap has sent a PAI; the panel is then one note (:192-195). */
    val hasPai: Boolean,
    /** "63", the total (:197). */
    val total: String,
    /** " / 100", beside it (:198). */
    val target: String,
    /** "Today" — "+12", when the day has its own points (:200-201). */
    val today: String?,
    val days: List<PaiDayRow>,
    /**
     * Each of the seven days' points, oldest first. The bar's segments are drawn from these with the
     * bar's own width (`paiSegments`), because where a segment starts is arithmetic, not drawing.
     */
    val daily: List<Float>,
    val note: String,
)

// MARK: - Training load (LoadPanel, GlowActivityScreen.kt:270-329)

/** Today's load from heart rate beside the strap's weekly load and its productive range. */
data class LoadModel(
    /** False when the day has no Recovery result: the panel is empty apart from a possible header. */
    val hasRecovery: Boolean,
    /** "Strain" — "9.4" (:275). */
    val strain: String,
    /** "Load today" — "142" (:276). */
    val loadToday: String,
    /** "vs your usual" — "72th pct", or "—" (:277). */
    val vsUsual: String,
    /** Fitness, fatigue and form, when the engine has a trend (:279-285). */
    val hasTrend: Boolean,
    val fitness: String,
    val fatigue: String,
    val form: String,
    val trendNote: String,
    /** "Each minute counts by how hard your heart works …" (:287-290). */
    val effortNote: String,
    /** Acute:chronic, when it can be worked out (:292-300). */
    val hasAcwr: Boolean,
    val acwr: String,
    /** "Contested", the pill's word (:297). */
    val acwrWord: String,
    val acwrNote: String,
    /** The strap's weekly load against its productive range (:301-318). */
    val hasWeek: Boolean,
    val weekLoad: String,
    /** "Productive", "Below productive" or "Above productive" (:309-313). */
    val weekWord: String,
    /** The word's colour, and the bar's (:311-316). */
    val weekAccent: ActivityAccent,
    val weekFraction: Float,
    val weekNote: String,
    /** "Max HR is estimated. Add your age for accurate effort numbers.", when it is (:320-327). */
    val maxHrNote: String?,
)

// MARK: - Fitness (FitnessPanel, GlowActivityScreen.kt:333-371)

/** VO2 max and its trend, recovery time left, max heart rate, and the estimate behind them. */
data class FitnessModel(
    /** "VO2 max" — "48", or "—" (:341). */
    val vo2: String,
    /** "Recovery" — "Done", "1h 20m left", or "—" (:342-346). */
    val recovery: String,
    /** "Max HR" — "181", or "—" (:347). */
    val maxHr: String,
    /** The last seven VO2 max readings, oldest first; fewer than two means no sparkline (:349). */
    val spark: List<Float?>,
    /** `recovery.vo2max`'s estimate, when the engine made one (:352-360). */
    val hasEstimate: Boolean,
    /** "Estimated fitness (VO2 max)" — "≈ 42–52" (:354). */
    val estimate: String,
    val estimateNote: String,
    /** "Estimated fitness: …", when the estimate is withheld (:361). */
    val withheldNote: String?,
    /** The last line under the panel (:363-369). */
    val note: String,
)

// MARK: - Workouts (WorkoutsPanel, GlowActivityScreen.kt:375-400)

/** The day's workouts, then the most recent others, then a way to the full list. */
data class WorkoutsPanelModel(
    /** "3 recorded", or null when there are none (:381). */
    val trailing: String?,
    /** The note for a strap that has recorded nothing (:383). */
    val emptyNote: String?,
    /** "No workouts this day.", when the selected day has none (:386). */
    val noTodayNote: String?,
    val today: List<WorkoutAnalysis>,
    val recent: List<WorkoutAnalysis>,
    /** True when there are more workouts than the panel lists (:393-398). */
    val showAll: Boolean,
)

// MARK: - The tab's own functions

object ActivityGlow {

    // ---- StepsCard, GlowActivityScreen.kt:105-132 ------------------------------------------------

    /** The steps card: the goal's progress, its sentence, and the three facts (:107-129). */
    fun steps(state: DayUiState, activity: Activity.Day?, goals: Goals): StepsModel {
        val progress = if (goals.steps > 0) state.steps.toFloat() / goals.steps else 0f
        val percent = if (goals.steps > 0) state.steps * 100 / goals.steps else 0
        val goal = if (state.steps >= goals.steps) "Goal reached" else "%d%% of %,d".format(percent, goals.steps)
        // Ours from step length and height when we can; the strap's own figure otherwise (:121-122).
        val distance = (activity?.distanceMeters ?: state.distanceMeters?.toFloat())
            ?.let { "≈ %.1f km".format(it / 1000f) } ?: "—"
        return StepsModel(
            progress = progress.coerceIn(0f, 1f),
            goal = goal,
            stepGoalNote = activity?.let {
                "Most of the benefit is reached by about %,d steps a day (Paluch et al. 2022).".format(it.stepGoal)
            },
            divergeNote = if (Activity.stepsDiverge(state.steps, state.minuteSteps)) {
                "The strap's step counter (%,d) and its minute records (%,d) differ by more than 5 %."
                    .format(state.steps, state.minuteSteps)
            } else null,
            activeHours = "${state.activeHours} of ${goals.activeHours}",
            distance = distance,
            activeKcal = state.activeCalories?.toString() ?: "—",
        )
    }

    // ---- MvpaPanel, GlowActivityScreen.kt:165-181 ------------------------------------------------

    /** Moderate-to-vigorous minutes against the WHO's 150 a week (:166-179). */
    fun activeMinutes(a: Activity.Day): ActiveMinutesModel {
        val target = Activity.MVPA_WEEKLY_TARGET
        val note = "Brisk walking (100+ steps a minute) is moderate, 130+ is vigorous and counts double. " +
            "Effort without steps, like cycling, counts from heart rate after 10 minutes" +
            if (a.mvpa.fromHeartRate > 0) " (${a.mvpa.fromHeartRate} min today, HR-assisted)." else "."
        return ActiveMinutesModel(
            trailing = "Last 7 days",
            week = "${a.weekEquivalent} / $target",
            moderate = "${a.mvpa.moderateMinutes}",
            vigorous = "${a.mvpa.vigorousMinutes}",
            fraction = a.weekEquivalent / target.toFloat(),
            note = note,
        )
    }

    // ---- EnergyPanel, GlowActivityScreen.kt:136-161 ----------------------------------------------

    /**
     * Energy expenditure, or the profile row that asks for the three settings it needs (:138-159).
     * The missing-profile row's own words are the screen's (GlowActivityScreen.kt:141).
     */
    fun energy(e: EnergyExpenditure.Day?): EnergyModel {
        if (e == null) {
            return EnergyModel(
                hasEstimate = false, partial = null, total = "", atRest = "", activityLevel = "", note = "",
            )
        }
        val note = listOfNotNull(
            "From your resting metabolism (Mifflin-St Jeor), your steps each minute and, for effort without steps, your heart rate.",
            if (e.partial) "Covers ${formatDuration(e.minutesWithData * 60L)} of wear, so it's a partial total." else null,
            if (e.palImplausible) "That's an unusual total for a full day; check your profile and how the strap was worn." else null,
            if (e.hrAssistedMinutes > 0) "${e.hrAssistedMinutes} min HR-assisted." else null,
            "Food digestion isn't included.",
        ).joinToString(" ")
        return EnergyModel(
            hasEstimate = true,
            partial = e.takeIf { it.partial }?.let { "Partial day" },
            total = "≈ ${e.totalKcal.roundToInt()} kcal",
            atRest = "${e.bmrKcal.roundToInt()} /day",
            activityLevel = e.pal?.let { "%.2f".format(it) } ?: "—",
            note = note,
        )
    }

    // ---- PaiPanel, GlowActivityScreen.kt:188-266 -------------------------------------------------

    /** PAI's total, its seven days and what drops off next, in the phone's zone. */
    fun pai(state: DayUiState, week: List<DailyPai>): PaiModel =
        pai(state, week, ScreenClock.today(), ScreenClock.zone())

    /**
     * [pai] in [zone], comparing against [today] rather than reading a clock, for the tests.
     *
     * [week] is the strap's PAI rows for the seven days ending on the selected day (`dailyPai`); it
     * is a list rather than a map because that is what crosses into Swift.
     */
    fun pai(state: DayUiState, week: List<DailyPai>, today: LocalDate, zone: TimeZone): PaiModel {
        val byDay = week.associateBy { it.day }
        val selected = state.date.toString()
        val total = state.pai?.total ?: byDay[selected]?.total
        val days = (6 downTo 0).map { state.date.minusDays(it.toLong()) }
        val daily = days.map { byDay[it.toString()]?.daily ?: 0f }
        // `getDisplayName(TextStyle.SHORT, locale).take(2)`: "Th", "Fr" (:205).
        val names = days.map { WEEKDAYS_SHORT[it.dayOfWeek.isoDayNumber - 1].take(2) }
        if (total == null) {
            return PaiModel(
                hasPai = false, total = "", target = "", today = null,
                days = emptyList(), daily = emptyList(),
                note = "PAI shows after the strap's next sync. It counts minutes your heart rate is raised, harder minutes counting more.",
            )
        }
        val todayPoints = state.pai?.daily ?: byDay[selected]?.daily
        val rows = daily.mapIndexed { i, v ->
            PaiDayRow(
                name = names[i],
                points = paiPoints(v),
                today = i == daily.lastIndex,
                empty = v <= 0f,
            )
        }
        // The oldest day leaves the rolling total tomorrow; only worth saying when looking at today (:220).
        val dropping = daily.first().takeIf { it > 0f && state.date == today }
        val note = listOfNotNull(
            dropping?.let { "Tomorrow ${WEEKDAYS_FULL[days.first().dayOfWeek.isoDayNumber - 1]}'s ${paiPoints(it)} drops off." },
            if (total >= PAI_TARGET) "You're at or above $PAI_TARGET. Keeping it there week after week is what PAI is built around."
            else "${PAI_TARGET - total.roundToInt()} to go. Brisk walking adds a little; anything that gets you out of breath adds more.",
        ).joinToString(" ")
        return PaiModel(
            hasPai = true,
            total = "${total.roundToInt()}",
            target = " / $PAI_TARGET",
            today = todayPoints?.let { "+${it.roundToInt()}" },
            days = rows,
            daily = daily,
            note = note,
        )
    }

    /**
     * The rolling total as one bar to [PAI_TARGET], built from the days that fill it: one segment per
     * day, oldest first and faintest (it drops off next), today in heart red (:242-266). [width] is
     * the bar's width in points, because where each segment starts is arithmetic, not drawing.
     */
    fun paiSegments(daily: List<Float>, width: Float): List<PaiSegment> {
        val gap = 1.5f
        // A day with any points shows at least as a sliver, so tiny days don't vanish (:251).
        val sliver = 3f
        var x = 0f
        val out = mutableListOf<PaiSegment>()
        daily.forEachIndexed { i, v ->
            if (v <= 0f || (width > 0f && x >= width)) return@forEachIndexed
            val w = maxOf(width * v / PAI_TARGET, sliver)
            out += PaiSegment(
                from = x,
                width = w,
                today = i == daily.lastIndex,
                white = when {
                    i == daily.lastIndex -> 1f
                    i == 0 -> 0.35f
                    else -> 0.55f + 0.45f * i / daily.lastIndex
                },
            )
            x += w + gap
        }
        return out
    }

    /** "+12", "+<1" for a sliver of a point, "–" for none (:232). */
    fun paiPoints(v: Float): String = when {
        v <= 0f -> "–"
        v < 0.5f -> "+<1"
        else -> "+${v.roundToInt()}"
    }

    // ---- LoadPanel, GlowActivityScreen.kt:270-329 ------------------------------------------------

    /**
     * Today's load from heart rate beside the strap's weekly load and its productive range.
     * [effect] is `effects.lastOrNull { it.weeklyLoad != null }` (:94).
     */
    fun load(r: RecoveryResult?, effect: SportEffectRow?): LoadModel =
        load(r, effect, ScreenClock.nowEpochSeconds(), ScreenClock.zone())

    /** [load] at [now] in [zone], for the tests. */
    fun load(r: RecoveryResult?, effect: SportEffectRow?, now: Long, zone: TimeZone): LoadModel {
        val trend = r?.loadTrend
        val weekly = effect?.weeklyLoad
        val low = effect?.optimalLoadMin
        val high = effect?.optimalLoadMax
        val hasWeek = weekly != null && low != null && high != null
        val inRange = hasWeek && weekly!! >= low!! && weekly <= high!!
        return LoadModel(
            hasRecovery = r != null,
            strain = r?.let { "%.1f".format(it.strain) } ?: "",
            loadToday = r?.let { "${it.load.toInt()}" } ?: "",
            vsUsual = r?.strainPercentile?.let { "${it}th pct" } ?: "—",
            hasTrend = trend != null,
            fitness = trend?.let { "${it.ctl.toInt()}" } ?: "",
            fatigue = trend?.let { "${it.atl.toInt()}" } ?: "",
            form = trend?.let { "${if (it.form > 0.5f) "+" else ""}${it.form.toInt()}" } ?: "",
            trendNote = "Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue.",
            effortNote = r?.let {
                "Each minute counts by how hard your heart works between resting (${it.restingHr.toInt()} bpm) and maximum " +
                    "(${it.maxHr.toInt()} bpm${if (it.maxHrTested) ", from your test" else ""}). Strain runs 0–21 on a fixed curve: an hour at 60 % of reserve is about 7."
            } ?: "",
            hasAcwr = r?.acwr != null,
            acwr = r?.acwr?.let { "%.2f".format(it) } ?: "",
            acwrWord = "Contested",
            acwrNote = "Last 7 days against the last 4 weeks. Shown for interest only: the evidence for a \"safe\" ratio is disputed, so it never enters Recovery.",
            hasWeek = hasWeek,
            weekLoad = weekly?.let { "${it.roundToInt()}" } ?: "",
            weekWord = when {
                !hasWeek -> ""
                weekly!! < low!! -> "Below productive"
                weekly > high!! -> "Above productive"
                else -> "Productive"
            },
            weekAccent = if (inRange) ActivityAccent.Good else ActivityAccent.Steps,
            weekFraction = if (hasWeek) weekly!! / (high!! * 1.25f) else 0f,
            weekNote = if (hasWeek) {
                "Productive range $low–$high, from the strap (updated ${shortDate(effect!!.end, zone)}). Its load is on a different scale from today's."
            } else "",
            maxHrNote = r?.takeIf { !it.maxHrFromAge }?.let {
                "Max HR is estimated. Add your age for accurate effort numbers."
            },
        )
    }

    // ---- FitnessPanel, GlowActivityScreen.kt:333-371 ---------------------------------------------

    /** VO2 max and its trend across workouts, recovery time left from the last one, and max heart rate. */
    fun fitness(r: RecoveryResult?, workouts: List<WorkoutAnalysis>, effects: List<SportEffectRow>, highestToday: Int?): FitnessModel =
        fitness(r, workouts, effects, highestToday, ScreenClock.nowEpochSeconds())

    /** [fitness] at [now], for the tests. */
    fun fitness(
        r: RecoveryResult?,
        workouts: List<WorkoutAnalysis>,
        effects: List<SportEffectRow>,
        highestToday: Int?,
        now: Long,
    ): FitnessModel {
        val vo2 = workouts.sortedBy { it.workout.start }
            .mapNotNull { a -> (a.workout.vo2max ?: a.effect?.vo2max?.roundToInt())?.takeIf { it > 0 } }
        val last = effects.lastOrNull { it.recoveryMinutes != null }
        val left = last?.let { it.end + it.recoveryMinutes!! * 60L - now }
        val est = r?.vo2max
        val band = est?.band
        val age = est?.fitnessAge
        val sex = est?.sex
        val estimateNote = if (est != null) {
            "From your age, sex, BMI, resting heart rate (${est.restingHr.toInt()} bpm, while asleep) and usual activity " +
                "(Jurca et al. 2005). Typical error is about ±5 ml/kg/min. Updated weekly." +
                (band?.let { " ${it.label} for your age and sex (FRIEND 2022, treadmill)." } ?: "") +
                (if (age != null && sex != null) " That's ${Vo2max.Friend.fitnessAgeText(age, sex)}." else "")
        } else ""
        return FitnessModel(
            vo2 = vo2.lastOrNull()?.toString() ?: "—",
            recovery = when {
                left == null -> "—"
                left <= 0 -> "Done"
                else -> "${formatDuration(left)} left"
            },
            maxHr = r?.let { "${it.maxHr.toInt()}" } ?: "—",
            spark = if (vo2.size >= 2) vo2.takeLast(7).map { it.toFloat() } else emptyList(),
            hasEstimate = est != null,
            estimate = est?.let { "≈ ${it.low}–${it.high}" } ?: "",
            estimateNote = estimateNote,
            withheldNote = r?.vo2maxWithheld?.let { "Estimated fitness: ${it.reason}" },
            note = listOfNotNull(
                if (vo2.isEmpty()) "VO2 max appears after a workout the strap can estimate it from." else null,
                last?.let { "Recovery time is the strap's estimate of how long your last effort needs." },
                highestToday?.let { "Highest heart rate today $it bpm." },
            ).joinToString(" ").ifEmpty { "Record a workout on the strap to see VO2 max and recovery time." },
        )
    }

    // ---- WorkoutsPanel, GlowActivityScreen.kt:375-400 --------------------------------------------

    /** The day's workouts, then the most recent others, then a way to the full list. */
    fun workoutsPanel(state: DayUiState, workouts: List<WorkoutAnalysis>): WorkoutsPanelModel =
        workoutsPanel(state, workouts, ScreenClock.zone())

    /** [workoutsPanel] in [zone], for the tests. */
    fun workoutsPanel(state: DayUiState, workouts: List<WorkoutAnalysis>, zone: TimeZone): WorkoutsPanelModel {
        fun dayOf(a: WorkoutAnalysis): LocalDate =
            Instant.fromEpochSeconds(a.workout.start).toLocalDateTime(zone).date
        val today = workouts.filter { dayOf(it) == state.date }
        val recent = workouts.filter { dayOf(it) != state.date }.take(RECENT_WORKOUTS)
        return WorkoutsPanelModel(
            trailing = if (workouts.isEmpty()) null else "${workouts.size} recorded",
            emptyNote = if (workouts.isEmpty()) {
                "Workouts you start on the strap show up here after a sync, with your heart rate every second and how fast it recovered."
            } else null,
            noTodayNote = if (workouts.isEmpty() || today.isNotEmpty()) null else "No workouts this day.",
            today = today,
            recent = recent,
            showAll = workouts.size > today.size + recent.size,
        )
    }

    /** "EEE d MMM" of [epochSeconds] in [zone], as the productive range's note prints it (:424). */
    fun shortDate(epochSeconds: Long, zone: TimeZone): String {
        val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone).date
        return "${WEEKDAYS_SHORT[date.dayOfWeek.isoDayNumber - 1]} ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
    }

    /** `getDisplayName(TextStyle.SHORT, locale)`: "Mon" … "Sun". */
    private val WEEKDAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val WEEKDAYS_FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
