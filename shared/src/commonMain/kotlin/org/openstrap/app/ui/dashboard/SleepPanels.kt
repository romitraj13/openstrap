package org.openstrap.app.ui.dashboard

import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.format
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

/** One moment as "HH:mm" in [zone] (`clockOf`, :214-215). */
fun clockOf(epochSeconds: Long, zone: TimeZone): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
    return "%02d:%02d".format(t.hour, t.minute)
}
