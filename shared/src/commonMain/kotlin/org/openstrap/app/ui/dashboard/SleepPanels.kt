package org.openstrap.app.ui.dashboard

import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.format
import org.openstrap.app.data.formatDuration
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.glow.SleepAccent
import kotlin.time.Instant

/*
 * `app/src/main/kotlin/org/openstrap/app/ui/dashboard/SleepPanels.kt`, the half of it that computes
 * rather than draws (card U7). Same package, same names, same numbers.
 *
 * What changed: `java.time` became `kotlinx.datetime`, `Color` became a [SleepAccent] name for
 * `Theme.swift` (shared Kotlin has no colour type, exactly as `RecoveryGlow` passes accent names),
 * and the `@Composable` `SleepWindows` canvas stayed in Swift while its geometry moved here, because
 * where a bar starts and ends on the week's clock is arithmetic, not drawing.
 */

/** One cell of the sleep vitals grid (SleepPanels.kt:31-37). */
data class SleepVital(
    val metric: Metric,
    val label: String,
    val value: String,
    val note: String?,
    val accent: SleepAccent,
) {
    /** Whether the note says it's outside your usual range, so it takes the accent colour (:87). */
    val off: Boolean get() = note?.endsWith("than usual") == true

    /** The pill's word when the vital is off your usual range: "Higher", "Lower" (:253). */
    val pill: String? get() = if (off) note?.removeSuffix(" than usual") else null
}

/**
 * Lowest and average heart rate, HRV, blood oxygen, breathing and skin temperature during last
 * night's sleep (SleepPanels.kt:44-84). HRV, blood oxygen, breathing and skin temperature are
 * compared with your previous nights once there is a week of them.
 */
fun sleepVitals(state: DayUiState): List<SleepVital> = sleepVitals(state, TimeZone.currentSystemDefault())

/** [sleepVitals] in [zone], for the tests. */
fun sleepVitals(state: DayUiState, zone: TimeZone): List<SleepVital> {
    val session = state.sleep ?: return emptyList()
    val tonight = state.nights.lastOrNull()?.takeIf { it.date == state.date }
    val previous = state.nights.dropLast(1)
    fun normal(pick: (SleepNight) -> Float?): History.Normal? =
        History.normal(
            previous.mapNotNull { n -> pick(n)?.let { DayStat(n.date, it, it, it, 1) } },
            state.date.minusDays(1),
            SLEEP_NIGHTS - 1,
        )
    fun word(value: Float?, normal: History.Normal?, learned: Int): String? = when (History.status(value, normal)) {
        null -> if (value == null) null else "Learning $learned/${History.MIN_NORMAL_DAYS} nights"
        1 -> "Higher than usual"
        -1 -> "Lower than usual"
        else -> "Usual"
    }
    fun learned(pick: (SleepNight) -> Float?): Int =
        previous.count { pick(it) != null }.coerceAtMost(History.MIN_NORMAL_DAYS)

    val hr = state.sleepHeartRate.filter { it.x in session.start..session.end }
    val lowest = hr.minByOrNull { it.y }
    val average = session.averageHeartRate
        ?: hr.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.roundToInt()
    val skinNormal = normal { it.skinTemperature }

    return listOfNotNull(
        lowest?.let {
            SleepVital(
                Metric.HeartRate, "Lowest heart rate", "${it.y.roundToInt()} bpm",
                "at ${clockOf(it.x, zone)}", SleepAccent.Heart,
            )
        },
        average?.let { SleepVital(Metric.HeartRate, "Average heart rate", "$it bpm", "while asleep", SleepAccent.Heart) },
        tonight?.hrv?.let { v ->
            SleepVital(
                Metric.Hrv, "HRV", "${v.roundToInt()} ms",
                word(v, normal { it.hrv }, learned { it.hrv }), SleepAccent.Hrv,
            )
        },
        tonight?.spo2?.let { v ->
            SleepVital(
                Metric.Spo2, "Blood oxygen", "${v.roundToInt()} %",
                word(v, normal { it.spo2 }, learned { it.spo2 }), SleepAccent.Spo2,
            )
        },
        tonight?.breathing?.let { v ->
            SleepVital(
                Metric.Breathing, "Breathing", "%.1f br/min".format(v),
                word(v, normal { it.breathing }, learned { it.breathing }), SleepAccent.SleepLight,
            )
        },
        tonight?.skinTemperature?.let { v ->
            // Skin temperature means little on its own; once there's a normal, show how far off it is (:76-81).
            val value = skinNormal?.let { "%+.1f °C".format(v - it.mean).replace("-", "−") } ?: "%.1f °C".format(v)
            SleepVital(
                Metric.SkinTemperature, "Skin temperature", value,
                word(v, skinNormal, learned { it.skinTemperature }), SleepAccent.Temperature,
            )
        },
    )
}

/** `minutesClock` (:212): an average time, printed as a clock face. */
fun minutesClock(minutes: Int): String = SleepHistory.clockMinutes(minutes).let { "%02d:%02d".format(it / 60, it % 60) }

/**
 * The last seven nights: when each sleep started and ended on a shared clock, with time asleep over
 * each bar, then the week's averages and how regular the timing was (SleepPanels.kt:89-124).
 */
class SleepWeek(
    val nights: List<SleepNight>,
    val slept: Int,
    val regularity: SleepHistory.Regularity?,
    val averageAsleep: String,
    val averageScore: String,
    val goalNights: Int,
) {
    /** "Sleep regularity shows once 5 of the last 7 nights have sleep (4 so far)." (:101-102). */
    val learningNote: String
        get() = "Sleep regularity shows once ${SleepHistory.MIN_REGULARITY_NIGHTS} of the last 7 nights have sleep ($slept so far)."

    /** The sentence under the regularity bar (:103-106). */
    val regularityNote: String?
        get() = regularity?.let {
            "Regularity is how much your fall-asleep and wake-up times moved over the last ${it.nights} nights: " +
                "100% means the same times every night, 50% about an hour either way."
        }
}

/** The last seven nights' facts, or null when none of them has sleep (:110-124). */
fun sleepWeek(state: DayUiState, goalSeconds: Long, zone: TimeZone = TimeZone.currentSystemDefault()): SleepWeek? {
    val week = state.nights.takeLast(SleepHistory.REGULARITY_NIGHTS)
    val slept = week.filter { it.session != null }
    if (slept.isEmpty()) return null
    val asleep = slept.mapNotNull { it.asleepSeconds }
    val scores = slept.mapNotNull { it.session?.score }
    return SleepWeek(
        nights = week,
        slept = slept.size,
        regularity = SleepHistory.regularity(
            state.nights.mapNotNull { n -> n.session?.let { n.date to it } }.toMap(),
            state.date,
            zone,
        ),
        averageAsleep = formatDuration(asleep.average().toLong()),
        averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString() ?: "—",
        goalNights = asleep.count { it >= goalSeconds },
    )
}

/** Zepp's bands in the metric colours the app already has (:126-130). */
fun bandAccent(band: SleepHistory.Band): SleepAccent = when (band) {
    SleepHistory.Band.VeryRegular, SleepHistory.Band.Regular -> SleepAccent.SleepRem
    SleepHistory.Band.FairlyRegular -> SleepAccent.SleepLight
    SleepHistory.Band.Irregular -> SleepAccent.SleepAwake
}

/** An hour mark down the week chart's clock (:169-176). */
data class SleepWindowHour(val label: String, val minute: Int)

/** One night's column in the week chart (:184-208). */
data class SleepWindowNight(
    /** The two-letter weekday, "Mo" … "Su" (:187). */
    val label: String,
    /** The last night is drawn at full strength (:199). */
    val isToday: Boolean,
    /** False when the strap never recorded the night, which the chart draws as a dot (:191-193). */
    val hasSleep: Boolean,
    /** Where the bar starts and ends, in minutes since noon; both 0 without sleep. */
    val startMinute: Int,
    val endMinute: Int,
    /** The time asleep over the bar, "6:30"; empty for a night without sleep. */
    val asleepLabel: String,
    /** True when the night reached the goal, which colours the label (:197, 203-206). */
    val metGoal: Boolean,
)

/** The week's windows on one clock, everything the Swift canvas needs and nothing it has to work out. */
data class SleepWindowsModel(
    /** The chart's span, in minutes since noon, so a night never wraps (:148-156). */
    val topMinute: Int,
    val bottomMinute: Int,
    /** Every three hours between the top and the bottom (:169-176). */
    val hours: List<SleepWindowHour>,
    val nights: List<SleepWindowNight>,
    /** True when the timing is learnt, so the two dashed average lines are drawn (:177-183). */
    val hasAverage: Boolean,
    /** The dashed average lines: the average bedtime and wake time, minutes since noon. */
    val averageBedtimeMinute: Int,
    val averageWakeMinute: Int,
)

/**
 * One column per night on a clock running down the chart (evening at the top, morning at the
 * bottom), for `SleepWindows` (SleepPanels.kt:137-210). Where each bar starts and ends is arithmetic,
 * so it lives here; the canvas itself is Swift's.
 */
fun sleepWindows(
    week: List<SleepNight>,
    regularity: SleepHistory.Regularity?,
    today: LocalDate,
    goalSeconds: Long,
    zone: TimeZone,
): SleepWindowsModel {
    // Minutes since the noon before each night, so a night never wraps (:147-152).
    fun sinceNoon(epoch: Long, wake: Boolean): Int {
        val t = Instant.fromEpochSeconds(epoch).toLocalDateTime(zone)
        val m = t.hour * 60 + t.minute
        return if (wake) m + 12 * 60 else floorMod(m - 12 * 60, 24 * 60)
    }
    val spans = week.map { n ->
        n.session?.let {
            val bedtime = sinceNoon(it.start, false)
            bedtime to sinceNoon(it.end, true).coerceAtLeast(bedtime + 1)
        }
    }
    val known = spans.filterNotNull()
    val top = ((known.minOfOrNull { it.first } ?: (10 * 60)) / 60 - 1).coerceAtLeast(0) * 60
    val bottom = ((known.maxOfOrNull { it.second } ?: (20 * 60)) / 60 + 1).coerceAtMost(36) * 60
    val hours = buildList {
        var m = top
        while (m <= bottom) {
            add(SleepWindowHour(label = "%02d:00".format(floorMod(m / 60 + 12, 24)), minute = m))
            m += 3 * 60
        }
    }
    val nights = week.mapIndexed { i, n ->
        val span = spans[i]
        val asleep = n.asleepSeconds ?: 0
        SleepWindowNight(
            label = WEEKDAYS[n.date.dayOfWeek.isoDayNumber - 1].take(2),
            isToday = n.date == today,
            hasSleep = span != null,
            startMinute = span?.first ?: 0,
            endMinute = span?.second ?: 0,
            asleepLabel = if (span == null) "" else "%d:%02d".format(asleep / 3600, asleep % 3600 / 60),
            metGoal = asleep >= goalSeconds,
        )
    }
    return SleepWindowsModel(
        topMinute = top,
        bottomMinute = bottom,
        hours = hours,
        nights = nights,
        hasAverage = regularity != null,
        averageBedtimeMinute = regularity?.let { it.bedtime - 12 * 60 } ?: 0,
        averageWakeMinute = regularity?.let { it.wake + 12 * 60 } ?: 0,
    )
}

/** `getDisplayName(TextStyle.SHORT, locale)`, which Compose cuts to two letters. */
private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** `Math.floorMod` (:151, :166). */
private fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b

/** One moment as "HH:mm" in [zone] (`clockOf`, :214-215). */
fun clockOf(epochSeconds: Long, zone: TimeZone): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
    return "%02d:%02d".format(t.hour, t.minute)
}
