package org.openstrap.app.ui.glow

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.format
import org.openstrap.app.data.formatDuration
import org.openstrap.app.data.minusDays
import org.openstrap.shared.clock.ScreenClock
import kotlin.time.Instant

/*
 * What Compose's workout screens compute, in one place Swift can call (card U8).
 *
 * `GlowWorkoutScreen.kt` holds both the one workout (`GlowWorkoutScreen`) and the whole list
 * (`GlowWorkoutsScreen`). This file copies what they work out beyond plain formatting: the heart
 * rate's ten-second buckets and their touch readouts, the recovery rating and its words, each
 * zone's range and share, the training effect's figures, and the days the all-workouts list groups
 * by. Standing instruction 5 says such logic lives in `shared/` with a JVM test, not in Swift.
 *
 * Unchanged apart from `java.time` becoming `kotlinx.datetime`, `Color` becoming an accent name for
 * `Theme.swift`, and `LocalDate.now()` becoming [ScreenClock]'s (card F7).
 */

/** The five zones' names and colours (WorkoutStyle.kt:6-7), zone 1 first. */
private val ZONE_NAMES = listOf("Warm-up", "Fat burn", "Aerobic", "Anaerobic", "Maximum")
private val ZONE_ACCENTS = listOf(
    ActivityAccent.SleepLight, ActivityAccent.Hrv, ActivityAccent.Good, ActivityAccent.Warn, ActivityAccent.Heart,
)

/** A drop as "−24", a rise as "+3" (WorkoutStyle.kt:10). */
fun signedDrop(drop: Int): String = when {
    drop > 0 -> "−$drop"
    drop < 0 -> "+${-drop}"
    else -> "0"
}

// MARK: - The workout's heart rate (HeartCard, GlowWorkoutScreen.kt:66-106)

/** The glowing heart-rate card: the average, the peak, and every second of the workout. */
data class WorkoutHeartModel(
    /** "Every second", the header's note (:71). */
    val trailing: String,
    /** "132", or "-" without an average (:73). */
    val average: String,
    /** "Peak 165", then "45m · 420 kcal" (:74). */
    val facts: List<String>,
    /** False until the strap sends the workout's stream (:76-80). */
    val hasHeartRate: Boolean,
    /** "The strap hasn't sent this workout's heart rate yet." (:78). */
    val emptyNote: String?,
    /** Ten-second averages over the workout, gaps null (:84-94). */
    val values: List<Float?>,
    /** The first and last clock times under the chart (:97). */
    val labels: List<String>,
    /** The workout's first second, so a touch readout can name the time it is over (:81). */
    val start: Long,
    /** The bucket width the readouts use: ten seconds (:84). */
    val stepSeconds: Long,
    /** "The strap kept recording for 3m after you stopped, for recovery heart rate." (:100-103). */
    val tailNote: String?,
    /** The rate the card's heart glows at (`GlowMotion.Heartbeat`, :68). */
    val heartbeat: Int,
)

// MARK: - How the time split (SummaryPanel, GlowWorkoutScreen.kt:108-121)

/** Active and paused time, the lowest rate, and the steps the strap counted. */
data class WorkoutSummaryModel(
    /** "Active" — "45m", or "—" (:115). */
    val active: String,
    /** "Paused" — "2m", or "—" (:116). */
    val paused: String,
    /** "Lowest HR" — "96", or "—" (:117). */
    val lowestHr: String,
    /** "1,240 steps.", when the strap counted any (:119). */
    val stepsNote: String?,
)

// MARK: - Recovery heart rate (RecoveryPanel, GlowWorkoutScreen.kt:123-151)

/** How fast the heart rate fell once the workout stopped, and how that rates. */
data class WorkoutRecoveryModel(
    /** "Excellent", "Good", "Fair", "Low", or null when the effort was too easy to rate (:127-133). */
    val label: String?,
    /** The label's colour: good for a big drop, bad for a small one; neutral when unrated (:128-133). */
    val accent: ActivityAccent?,
    /** "After 1 min" — "−24 bpm" (:137). */
    val afterOne: String,
    /** "After 2 min" — "−38 bpm", when the strap recorded that long (:138). */
    val afterTwo: String?,
    /** Whether the bar is drawn: only for a rated drop (:140). */
    val rated: Boolean,
    /** The bar: the drop against 40 bpm (:140). */
    val fraction: Float,
    val note: String,
)

// MARK: - Zones (ZonesPanel, GlowWorkoutScreen.kt:153-173)

/** One heart-rate zone, hottest first (:158-170). */
data class WorkoutZoneRow(
    /** "Maximum" … "Warm-up" (:165). */
    val name: String,
    /** "90+", or "80–89" (:159-166). */
    val range: String,
    /** "12m" (:167). */
    val time: String,
    /** Its share of the workout (:169). */
    val fraction: Float,
    val accent: ActivityAccent,
)

/** Time in each of the five zones, and the max the zones are cut at. */
data class WorkoutZonesModel(
    /** "Max 181 bpm", the header's note (:157). */
    val trailing: String,
    /** Zone 5 first, as Compose draws them (:158). */
    val rows: List<WorkoutZoneRow>,
)

// MARK: - Training effect (EffectPanel, GlowWorkoutScreen.kt:175-193)

/** The strap's own training effect, VO2 max and recovery time for the workout. */
data class WorkoutEffectModel(
    /** "From the strap", the header's note (:180). */
    val trailing: String,
    /** "Aerobic" — "3.4", or "—" (:182). */
    val aerobic: String,
    /** "Anaerobic" — "1.2", or "—" (:183). */
    val anaerobic: String,
    /** "Load" — "142", or "—" (:184). */
    val load: String,
    /** "VO₂ max" — "48", or "—" (:188). */
    val vo2: String,
    /** "Recovery time" — "1h 20m", or "—" (:189). */
    val recoveryTime: String,
    /** "Training effect runs 0–5 for each system. …" (:191). */
    val note: String,
)

// MARK: - The whole list (GlowWorkoutsScreen, GlowWorkoutScreen.kt:195-218)

/** One day of the all-workouts list: its name, and its workouts. */
data class WorkoutDayGroup(
    /** "Today", "Yesterday", or "Tue 20 Oct" (:209, GlowMetricDetail.kt:71). */
    val title: String,
    /** The year, when the day is not this year's (:209). */
    val trailing: String?,
    val workouts: List<WorkoutAnalysis>,
)

/** Every workout, newest first, one panel per day (:197-217). */
data class AllWorkoutsModel(
    /** "3 recorded", the header's subtitle (:201). */
    val subtitle: String,
    /** The note for a strap that has recorded nothing (:203). */
    val emptyNote: String?,
    val groups: List<WorkoutDayGroup>,
)

// MARK: - The screens' own functions

object WorkoutGlow {

    // ---- HeartCard, GlowWorkoutScreen.kt:66-106 --------------------------------------------------

    /** The heart-rate card: the average, the facts beside it, and the workout's own stream (:67-103). */
    fun heart(a: WorkoutAnalysis): WorkoutHeartModel =
        heart(a, ScreenClock.zone())

    /** [heart] in [zone], for the tests. */
    fun heart(a: WorkoutAnalysis, zone: TimeZone): WorkoutHeartModel {
        val w = a.workout
        val facts = listOfNotNull(
            w.maxHeartRate?.let { "Peak $it" },
            // Compose's inner `listOfNotNull(...).joinToString(" · ")` always yields one entry, so the
            // second fact is here even when it is empty (:74).
            listOfNotNull(formatDuration(a.durationSeconds), w.calories?.let { "$it kcal" }).joinToString(" · "),
        )
        if (a.heartRate.isEmpty()) {
            return WorkoutHeartModel(
                trailing = "Every second",
                average = w.averageHeartRate?.toString() ?: "-",
                facts = facts,
                hasHeartRate = false,
                emptyNote = "The strap hasn't sent this workout's heart rate yet.",
                values = emptyList(),
                labels = emptyList(),
                start = w.start,
                stepSeconds = HEART_STEP,
                tailNote = null,
                heartbeat = w.averageHeartRate ?: 100,
            )
        }
        val first = w.start
        val last = maxOf(a.heartRate.last().timestamp, a.end)
        // Ten-second buckets keep the line readable on a phone; the readout still says the time (:83-94).
        val n = ((last - first) / HEART_STEP + 1).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        a.heartRate.forEach { s ->
            val i = ((s.timestamp - first) / HEART_STEP).toInt()
            if (i in 0 until n) {
                sums[i] += s.bpm.toFloat()
                counts[i]++
            }
        }
        val values = List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
        return WorkoutHeartModel(
            trailing = "Every second",
            average = w.averageHeartRate?.toString() ?: "-",
            facts = facts,
            hasHeartRate = true,
            emptyNote = null,
            values = values,
            // No average line: it would count the recovery tail and disagree with the strap's average (:96-97).
            labels = listOf(clock(first, zone), clock(last, zone)),
            start = first,
            stepSeconds = HEART_STEP,
            tailNote = if (a.heartRate.last().timestamp > a.end + 30) {
                "The strap kept recording for ${formatDuration((a.heartRate.last().timestamp - a.end + 30) / 60 * 60)} after you stopped, for recovery heart rate."
            } else null,
            heartbeat = w.averageHeartRate ?: 100,
        )
    }

    /** The touch readout on a heart-rate point: "132 bpm · 17:24" (:97-99). */
    fun heartReadout(start: Long, index: Int, value: Float): String =
        heartReadout(start, index, value, ScreenClock.zone())

    /** [heartReadout] in [zone], for the tests. */
    fun heartReadout(start: Long, index: Int, value: Float, zone: TimeZone): String =
        "${value.toInt()} bpm · ${clock(start + index * HEART_STEP, zone)}"

    // ---- SummaryPanel, GlowWorkoutScreen.kt:108-121 ----------------------------------------------

    /** How the workout's time split, and what else the strap counted (:113-119). */
    fun summary(a: WorkoutAnalysis): WorkoutSummaryModel {
        val w = a.workout
        val active = w.exerciseSeconds?.toLong()
        return WorkoutSummaryModel(
            active = active?.let { formatDuration(it) } ?: "—",
            paused = active?.let { formatDuration((a.durationSeconds - it).coerceAtLeast(0)) } ?: "—",
            lowestHr = w.minHeartRate?.let { "$it" } ?: "—",
            stepsNote = w.steps?.takeIf { it > 0 }?.let { "%,d steps.".format(it) },
        )
    }

    // ---- RecoveryPanel, GlowWorkoutScreen.kt:123-151 ---------------------------------------------

    /** How fast the heart rate fell, rated only when the effort was hard enough to mean something (:125-148). */
    fun recovery(a: WorkoutAnalysis): WorkoutRecoveryModel? {
        val drop = a.recovery60 ?: return null
        val rated = a.recoveryIsMeaningful
        val label = when {
            !rated -> null
            drop >= 25 -> "Excellent"
            drop >= 18 -> "Good"
            drop >= 12 -> "Fair"
            else -> "Low"
        }
        val accent = when {
            !rated -> null
            drop >= 18 -> ActivityAccent.Good
            else -> ActivityAccent.Warn
        }
        val note = if (rated) {
            "How fast your heart rate fell from ${a.endHeartRate} bpm once you stopped. A bigger drop usually means a " +
                "fitter heart; under 12 bpm in the first minute is on the low side. Compare it across similar workouts."
        } else {
            "You finished at ${a.endHeartRate ?: "—"} bpm, close to resting, so there was little to recover from. " +
                "Recovery heart rate is rated after harder efforts."
        }
        return WorkoutRecoveryModel(
            label = label,
            accent = accent,
            afterOne = "${signedDrop(drop)} bpm",
            afterTwo = a.recovery120?.let { "${signedDrop(it)} bpm" },
            rated = rated,
            fraction = drop / 40f,
            note = note,
        )
    }

    // ---- ZonesPanel, GlowWorkoutScreen.kt:153-173 ------------------------------------------------

    /** Time in each of the five zones, hottest first, against the workout's own length (:154-170). */
    fun zones(a: WorkoutAnalysis): WorkoutZonesModel {
        val total = a.zoneSeconds.sum().coerceAtLeast(1)
        val rows = (Workouts.ZONES - 1 downTo 0).map { z ->
            val lo = (50 + z * 10) * a.maxHeartRate / 100
            val hi = if (z == Workouts.ZONES - 1) "+" else "–${(60 + z * 10) * a.maxHeartRate / 100 - 1}"
            val secs = a.zoneSeconds[z]
            WorkoutZoneRow(
                name = ZONE_NAMES[z],
                range = "$lo$hi",
                time = formatDuration(secs.toLong()),
                fraction = secs.toFloat() / total,
                accent = ZONE_ACCENTS[z],
            )
        }
        return WorkoutZonesModel(trailing = "Max ${a.maxHeartRate} bpm", rows = rows)
    }

    // ---- EffectPanel, GlowWorkoutScreen.kt:175-193 -----------------------------------------------

    /** The strap's training effect, VO2 max and recovery time (:177-191). */
    fun effect(a: WorkoutAnalysis): WorkoutEffectModel {
        val w = a.workout
        val e = a.effect
        val vo2 = e?.vo2max ?: w.vo2max?.toFloat()
        return WorkoutEffectModel(
            trailing = "From the strap",
            aerobic = w.aerobicTrainingEffect?.let { "%.1f".format(it) } ?: "—",
            anaerobic = w.anaerobicTrainingEffect?.let { "%.1f".format(it) } ?: "—",
            load = w.trainingLoad?.toString() ?: "—",
            vo2 = vo2?.let { "%.0f".format(it) } ?: "—",
            recoveryTime = e?.recoveryMinutes?.takeIf { it > 0 }?.let { formatDuration(it * 60L) } ?: "—",
            note = "Training effect runs 0–5 for each system. VO₂ max shows up once the strap has enough data to estimate it.",
        )
    }

    // ---- GlowWorkoutsScreen, GlowWorkoutScreen.kt:195-218 ----------------------------------------

    /** Every workout, newest first, one panel per day (:199-215). */
    fun all(workouts: List<WorkoutAnalysis>): AllWorkoutsModel =
        all(workouts, ScreenClock.today(), ScreenClock.zone())

    /** [all] against [today] in [zone], for the tests. */
    fun all(workouts: List<WorkoutAnalysis>, today: LocalDate, zone: TimeZone): AllWorkoutsModel {
        val groups = workouts
            .groupBy { Instant.fromEpochSeconds(it.workout.start).toLocalDateTime(zone).date }
            .map { (date, day) ->
                WorkoutDayGroup(
                    title = dayName(date, today),
                    trailing = if (date.year != today.year) "${date.year}" else null,
                    workouts = day,
                )
            }
        return AllWorkoutsModel(
            subtitle = "${workouts.size} recorded",
            emptyNote = if (workouts.isEmpty()) "Workouts you start on the strap show up here after a sync." else null,
            groups = groups,
        )
    }

    /** The workout detail's subtitle: "Wed 21 Oct · 17:02" (:39). */
    fun dateLine(start: Long): String = dateLine(start, ScreenClock.zone())

    /** [dateLine] in [zone], for the tests. */
    fun dateLine(start: Long, zone: TimeZone): String {
        val time = Instant.fromEpochSeconds(start).toLocalDateTime(zone)
        return "${WEEKDAYS[time.date.dayOfWeek.isoDayNumber - 1]} ${time.dayOfMonth} ${MONTHS[time.monthNumber - 1]} · " +
            "%02d:%02d".format(time.hour, time.minute)
    }

    /** "Today", "Yesterday", or "Tue 20 Oct" (`dayName`, GlowMetricDetail.kt:71). */
    private fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]} ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
    }

    /** "HH:mm" of [epochSeconds] in [zone] (:38). */
    fun clock(epochSeconds: Long, zone: TimeZone): String {
        val time = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    /** The bucket width the heart card averages into and its readouts name (:84). */
    const val HEART_STEP: Long = 10L

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
