package org.openstrap.app.ui.dashboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.History
import org.openstrap.app.data.LiveSteps
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.data.db.OsaEventRow
import org.openstrap.app.data.db.OsaResultRow
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.components.TrendPoint
import org.openstrap.protocol.services.StepTotals
import kotlin.time.Clock

/**
 * The body of `DayViewModel` (`app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayViewModel.kt`
 * lines 150-319) as one suspending call per screen read.
 *
 * The ViewModel joins a dozen Room `Flow`s with `combine`; here the same repository calls are read
 * once each (`.first()` — a one-shot read of the same query) and the same expressions build the
 * state, in the same order, so a value the Android screen shows is the value this returns. The live
 * `strapSteps` flow became the `todaySteps` argument, which Swift passes from `StrapEngine`.
 *
 * @param log where `Log.w("OpenStrap", …)` went; null/absent means nobody is listening.
 */
class DayStateLoader(
    private val repository: HealthRepository,
    private val settings: SettingsRepository,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) {

    /** Everything the Today screen shows for [date]; [todaySteps] is the strap's live counter, for today only. */
    suspend fun load(
        date: LocalDate,
        todaySteps: StepTotals?,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): DayUiState {
        val day = DayWindow.of(date, zone)
        val night = DayWindow.nightEndingOn(date, zone)
        val week = DayWindow(DayWindow.of(date.minusDays(6), zone).from, day.to)

        val act = repository.activity(day).first()
        val hr = repository.heartRate(day).first()
        val spo2 = repository.spo2(day).first()
        val stress = repository.stress(day).first()
        val hrv = repository.hrv(day).first()

        // Sessions ending from the start of the night into early tomorrow, so tonight's evening naps are included too.
        val nightBefore = DayWindow.nightEndingOn(date.minusDays(1), zone)
        val sessions = repository.sleepSessions(DayWindow(nightBefore.from, day.to + 12 * 3600)).first()
        // Main sleep = the longest non-nap session that ended in the night window.
        val session = sessions.filter { !it.nap && it.end in night.from..night.to }.maxByOrNull { it.end - it.start }
        val before = sessions.filter { !it.nap && it.end in nightBefore.from..nightBefore.to }.maxByOrNull { it.end - it.start }
        // Naps belong to the calendar day they started on, not the night window (which ends at 18:00).
        val naps = sessions.filter { it.nap && it.start in day.from..day.to }
        val mainStages = if (session == null) emptyList() else repository.sleepStages(session.start).first()
        val napStages: Map<Long, List<SleepStage>> =
            naps.associate { n -> n.start to repository.sleepStages(n.start).first() }

        val skin = repository.skinTemperature(day).first()
        val breath = repository.breathRate(night).first()
        val pai = repository.latestPai(day.to).first()?.takeIf { it.timestamp >= day.from }

        // The week behind the day: resting HR (stored or estimated) and HRV, one point a day.
        val resting = repository.restingHr(week.from, week.to).first()
        val weekHr = repository.heartRate(week).first()
        val weekAct = repository.activity(week).first()
        val weekHrv = repository.hrv(week).first()
        val days = (6 downTo 0).map { date.minusDays(it.toLong()) }
        val restingTrend = days.map { d ->
            val w = DayWindow.of(d, zone)
            val stored = resting.firstOrNull { it.day in w.from..w.to }?.bpm
            // Our own estimate (lowest 30-min average) only means something with most of a day's data.
            val series = Summaries.heartRateSeries(weekHr.filter { it.timestamp in w.from..w.to }, weekAct.filter { it.timestamp in w.from..w.to })
            val estimated = if (series.size >= MIN_MINUTES_FOR_ESTIMATE) Summaries.estimateRestingHr(series) else null
            TrendPoint(dayLabel(d), (stored ?: estimated)?.toFloat()) to (stored == null && estimated != null)
        }
        val hrvTrend = days.map { d ->
            val w = DayWindow.of(d, zone)
            val v = weekHrv.filter { it.timestamp in w.from..w.to }.map { it.milliseconds }
            TrendPoint(dayLabel(d), if (v.isEmpty()) null else v.average().toFloat())
        }

        // Baselines: the seven days before the selected day.
        val previous = DayWindow(DayWindow.of(date.minusDays(7), zone).from, DayWindow.of(date.minusDays(1), zone).to)
        fun List<Number>.mean() = if (isEmpty()) null else map { it.toFloat() }.average().toFloat()
        val base = Baselines(
            restingHr = repository.restingHr(previous.from, previous.to).first().map { it.bpm }.mean(),
            hrv = repository.hrv(previous).first().map { it.milliseconds }.mean(),
            spo2 = repository.spo2(previous).first().map { it.percent }.mean(),
            stress = repository.stress(previous).first().map { it.value }.mean(),
            skinTemperature = repository.skinTemperature(previous).first().map { it.celsius }.mean(),
            breathRate = repository.breathRate(previous).first().map { it.perMinute }.mean(),
        )

        val extended = repository.activityExtended(day).first()
        val more = MoreParts(
            sleepSpo2 = repository.sleepSpo2(night).first().map { ChartPoint(it.timestamp, it.percent.toFloat()) },
            hrvSdnn = repository.hrvSdnn(day).first().map { ChartPoint(it.timestamp, it.milliseconds) },
            osaResult = repository.osaResult(date, zone).first(),
            osaEvents = repository.osaEvents(night).first(),
            dailyMaxHeartRate = repository.dailyMaxHr(day).first()?.bpm,
            distanceMeters = extended.takeIf { it.isNotEmpty() }?.sumOf { it.distanceCm.toLong() }?.let { (it / 100).toInt() },
            activeCalories = extended.takeIf { it.isNotEmpty() }?.sumOf { it.calories },
            alerts = repository.healthAlerts(day).first(),
        )

        // The SLEEP_NIGHTS nights behind the day, with each night's vitals averaged over the sleep itself.
        val span = DayWindow(DayWindow.nightEndingOn(date.minusDays(SLEEP_NIGHTS - 1L), zone).from, night.to)
        val main = SleepHistory.mainSleeps(repository.sleepSessions(span).first(), zone)
        val spanHrv = repository.hrv(span).first()
        val spanSkin = repository.skinTemperature(span).first()
        val spanSpo2 = repository.sleepSpo2(span).first()
        val spanBreath = repository.breathRate(span).first()
        val nights = (SLEEP_NIGHTS - 1 downTo 0).map { ago ->
            val d = date.minusDays(ago.toLong())
            val s = main[d]
            // Averages over the sleep itself, not the whole night window.
            fun <T> during(samples: List<T>, time: (T) -> Long, value: (T) -> Float): Float? =
                s?.let { w -> samples.filter { time(it) in w.start..w.end }.map(value).takeIf { it.isNotEmpty() }?.average()?.toFloat() }
            SleepNight(
                date = d,
                session = s,
                hrv = during(spanHrv, { it.timestamp }, { it.milliseconds.toFloat() }),
                skinTemperature = during(spanSkin, { it.timestamp }, { it.celsius }),
                spo2 = during(spanSpo2, { it.timestamp }, { it.percent.toFloat() }),
                breathing = during(spanBreath, { it.timestamp }, { it.perMinute.toFloat() }),
            )
        }
        val sleepHeartRate = Summaries.bucketAverage(Summaries.heartRateSeries(repository.heartRate(night).first(), emptyList()))
            .map { ChartPoint(it.first, it.second.toFloat()) }

        // The strap's own running total covers steps whose minute records were already taken by another app.
        val live = if (date == Clock.System.todayIn(zone)) todaySteps else null
        val liveCounter = LiveSteps()
        val series = Summaries.heartRateSeries(hr, act)
        val hourly = Summaries.hourlySteps(act, day.from)

        return DayUiState(
            date = date,
            steps = liveCounter.today(Summaries.totalSteps(act), live),
            minuteSteps = Summaries.totalSteps(act),
            hourlySteps = hourly,
            activeHours = Summaries.activeHours(hourly),
            heartRate = Summaries.bucketAverage(series).map { ChartPoint(it.first, it.second.toFloat()) },
            latestHeartRate = series.lastOrNull()?.second,
            restingHeartRate = restingTrend.last().first.value?.toInt(),
            restingEstimated = restingTrend.last().second,
            restingTrend = restingTrend.map { it.first },
            hrvTrend = hrvTrend,
            sleep = session,
            previousSleepScore = before?.score,
            naps = naps,
            napStages = napStages.mapValues { (_, st) -> st.map { StageSpan(it.start, it.end, it.stage) } },
            napBreakdowns = napStages.filterValues { it.isNotEmpty() }.mapValues { (_, st) -> Summaries.sleepBreakdown(st) },
            sleepStages = mainStages.map { StageSpan(it.start, it.end, it.stage) },
            sleepBreakdown = if (mainStages.isEmpty()) null else Summaries.sleepBreakdown(mainStages),
            spo2 = spo2.map { ChartPoint(it.timestamp, it.percent.toFloat()) },
            stress = stress.map { ChartPoint(it.timestamp, it.value.toFloat()) },
            hrv = hrv.map { ChartPoint(it.timestamp, it.milliseconds.toFloat()) },
            skinTemperature = skin.map { ChartPoint(it.timestamp, it.celsius) },
            breathRate = breath.map { ChartPoint(it.timestamp, it.perMinute.toFloat()) },
            pai = pai,
            baselines = base,
            sleepSpo2 = more.sleepSpo2,
            hrvSdnn = more.hrvSdnn,
            osaResult = more.osaResult,
            osaEvents = more.osaEvents,
            dailyMaxHeartRate = more.dailyMaxHeartRate,
            distanceMeters = more.distanceMeters,
            activeCalories = more.activeCalories,
            alerts = more.alerts,
            nights = nights,
            sleepHeartRate = sleepHeartRate,
            dayStart = day.from,
            dayEnd = day.to,
        )
    }

    /**
     * Readiness and charge for [date], or null when the engine throws (`DayViewModel`'s `recovery`
     * logs and shows nothing rather than failing the screen).
     */
    suspend fun recovery(date: LocalDate): RecoveryResult? = try {
        repository.recovery(date, settings.profile())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("recovery failed", e)
        null
    }

    /**
     * One value per day for [metric] over [range] ending on [date], as the detail screen's
     * `history(metric.daily, from, end)` does: the read starts [History.NORMAL_DAYS] back so the
     * screen can draw "your normal", and the answer keeps the days inside the chosen range. Days
     * without data are absent, exactly as `HealthRepository.daily` leaves them.
     */
    suspend fun history(
        metric: Metric,
        range: Range,
        date: LocalDate,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DayStat> {
        val from = date.minusDays(maxOf(range.days, History.NORMAL_DAYS) - 1L)
        val start = date.minusDays(range.days - 1L)
        return repository.daily(metric.daily, from, date, zone).first().filter { it.date in start..date }
    }

    /** Local days that have any heart rate, steps or sleep, for the calendar. */
    suspend fun daysWithData(zone: TimeZone = TimeZone.currentSystemDefault()): Set<LocalDate> =
        repository.daysWithData(zone).first()

    private companion object {
        /** Minutes of heart-rate data a day needs before we estimate its resting HR ourselves. */
        const val MIN_MINUTES_FOR_ESTIMATE = 6 * 60

        /** `getDisplayName(TextStyle.SHORT, locale).take(2)`: "Mon", "Tue", … cut to two letters. */
        val SHORT_DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun dayLabel(d: LocalDate): String = SHORT_DAY_NAMES[d.dayOfWeek.isoDayNumber - 1].take(2)
    }
}

/** What the ViewModel's `more` combine carried. */
private data class MoreParts(
    val sleepSpo2: List<ChartPoint>,
    val hrvSdnn: List<ChartPoint>,
    val osaResult: OsaResultRow?,
    val osaEvents: List<OsaEventRow>,
    val dailyMaxHeartRate: Int?,
    val distanceMeters: Int?,
    val activeCalories: Int?,
    val alerts: List<HealthAlertRow>,
)
