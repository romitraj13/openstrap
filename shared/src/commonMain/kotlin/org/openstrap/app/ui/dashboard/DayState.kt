package org.openstrap.app.ui.dashboard

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.data.db.OsaEventRow
import org.openstrap.app.data.db.OsaResultRow
import org.openstrap.app.data.db.PaiSample
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.components.TrendPoint
import kotlin.time.Clock

/*
 * `DayViewModel.kt`'s state classes (lines 42-110 and 342-352), copied into `shared/` with the same
 * package and the same fields so the iPhone screens read what the Android screens read.
 *
 * What changed: `java.time.LocalDate` became `kotlinx.datetime.LocalDate`, and `LocalDate.now()`
 * became `Clock.System.todayIn(TimeZone.currentSystemDefault())` — the same day, read without a JVM.
 */

/** Nights of sleep loaded for the week view and the in-sleep vitals' usual values. */
const val SLEEP_NIGHTS = 14

/** Averages over the 7 days before the selected day; null where there's no data. */
data class Baselines(
    val restingHr: Float? = null,
    val hrv: Float? = null,
    val spo2: Float? = null,
    val stress: Float? = null,
    val skinTemperature: Float? = null,
    val breathRate: Float? = null,
)

data class DayUiState(
    val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val steps: Int = 0,
    /** Steps summed from the per-minute records; [steps] can be higher today from the strap's own running total. */
    val minuteSteps: Int = 0,
    val hourlySteps: List<Float> = List(24) { 0f },
    val activeHours: Int = 0,
    val heartRate: List<ChartPoint> = emptyList(),
    val latestHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    /** True when [restingHeartRate] is our own estimate rather than the strap's value. */
    val restingEstimated: Boolean = false,
    val restingTrend: List<TrendPoint> = emptyList(),
    val hrvTrend: List<TrendPoint> = emptyList(),
    /** The main sleep, carrying the strap's own score and stage totals when it reported them. */
    val sleep: SleepSession? = null,
    /** The strap's score for the night before, to show which way sleep moved. */
    val previousSleepScore: Int? = null,
    val naps: List<SleepSession> = emptyList(),
    /** Each nap's stages and totals, keyed by the nap's start. */
    val napStages: Map<Long, List<StageSpan>> = emptyMap(),
    val napBreakdowns: Map<Long, Summaries.SleepBreakdown> = emptyMap(),
    val sleepStages: List<StageSpan> = emptyList(),
    val sleepBreakdown: Summaries.SleepBreakdown? = null,
    val spo2: List<ChartPoint> = emptyList(),
    val stress: List<ChartPoint> = emptyList(),
    val hrv: List<ChartPoint> = emptyList(),
    val skinTemperature: List<ChartPoint> = emptyList(),
    val breathRate: List<ChartPoint> = emptyList(),
    val pai: PaiSample? = null,
    val baselines: Baselines = Baselines(),
    /** Blood oxygen measured during the night's sleep (type 38), separate from daytime [spo2]. */
    val sleepSpo2: List<ChartPoint> = emptyList(),
    /** HRV as SDNN (type 78). On a real strap these come every hour or two through the day, not once a night. */
    val hrvSdnn: List<ChartPoint> = emptyList(),
    /** The strap's sleep-apnea summary for the night; units unverified, see [OsaResultRow]. */
    val osaResult: OsaResultRow? = null,
    /** Oxygen-drop events during the night. */
    val osaEvents: List<OsaEventRow> = emptyList(),
    /** The day's highest exercise heart rate as the strap reported it. */
    val dailyMaxHeartRate: Int? = null,
    /** Distance walked from the extended activity record (cm per minute, about 75 cm a step); null without data. */
    val distanceMeters: Int? = null,
    /** Calories from the extended activity record; looks like active kcal per minute, not yet verified. */
    val activeCalories: Int? = null,
    /** Alerts the strap pushed during the day. */
    val alerts: List<HealthAlertRow> = emptyList(),
    /** The [SLEEP_NIGHTS] nights ending on this day, oldest first; nights without sleep have no session. */
    val nights: List<SleepNight> = emptyList(),
    /** Heart rate across the night window (5-minute averages), so sleep before midnight is included. */
    val sleepHeartRate: List<ChartPoint> = emptyList(),
    val dayStart: Long = 0,
    val dayEnd: Long = 0,
) {
    val asleepSeconds: Long? get() = sleepBreakdown?.asleepSeconds ?: sleep?.let { it.end - it.start }
}

/** One night's main sleep and the vitals averaged over it. */
data class SleepNight(
    val date: LocalDate,
    val session: SleepSession?,
    val hrv: Float? = null,
    val skinTemperature: Float? = null,
    val spo2: Float? = null,
    val breathing: Float? = null,
) {
    val asleepSeconds: Long? get() = session?.let(SleepHistory::asleepSeconds)
}
