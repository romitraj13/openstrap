package org.openstrap.shared.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryCopy
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.Summaries
import org.openstrap.app.DemoData
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.shared.clock.ScreenClock
import kotlin.math.abs

/* ------------------------------------------------------------------------------------------------ */

/** The night's minutes by stage, as the Sleep screen's legend totals them. */
data class StageTotals(
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
)

/** One stage of the night as the hypnogram draws it. [stage] is `awake`, `light`, `deep` or `rem`. */
data class StageSegment(val stage: String, val start: Long, val end: Long)

/** One point of the day's heart rate: a five-minute average, as the Android day chart draws it. */
data class HeartSample(val timestamp: Long, val bpm: Int)

/** One day of the Readiness trend: [value] is null where there was no sleep to score. */
data class TrendDay(val date: String, val value: Int?)

/** A Readiness contributor: its rating word, its explanation, and its bar. */
data class RecoveryContributor(
    val label: String,
    val rating: String,
    val detail: String,
    /** 0-1, the contributor's score over 100. */
    val fraction: Float,
    /** The Android panel's grouping: `Body`, `Sleep` or `Load`. */
    val group: String,
)

/** Charge, and where the day's charge went, as the Recovery screen's two cards show it. */
data class ChargeSummary(
    val latest: Int?,
    val atWake: Int?,
    val headline: String,
    val body: String,
    val recharged: Int,
    val drainedExercise: Int,
    val drainedStress: Int,
    val drainedLiving: Int,
    val highest: Int?,
    val lowest: Int?,
    val anchored: Boolean,
    /** The card's hint when there is no charge to show yet; empty once there is. */
    val hint: String,
    val stressThresholdText: String,
)

/**
 * Everything the Today screen shows for one local day.
 *
 * [recoveryState] is `Recovery.Phase` (`withheld`, `learning`, `scored`, `calibrated`) and
 * [recoveryLabel] the exact text the Android card prints in its corner ("Learning 6/7 nights",
 * "Calibrated · +3 vs yesterday"). [sleepScore] is the strap's own score when it sent one, else
 * OpenStrap's, from the same minutes [sleepMinutes] reports.
 */
data class TodaySnapshot(
    val date: String,
    val recoveryScore: Int?,
    val recoveryState: String,
    val recoveryLabel: String,
    val recoveryHeadline: String,
    val sleepScore: Int?,
    val sleepMinutes: Int,
    val stages: StageTotals,
    val restingHr: Int?,
    val averageSpo2: Float?,
    val steps: Int,
    /** Epoch seconds the last history sync finished; 0 when there has never been one. */
    val lastSync: Long,
)

/** The night that ended on [date]: its session, its stages, and the sentence about it. */
data class SleepSnapshot(
    val date: String,
    val start: Long,
    val end: Long,
    val segments: List<StageSegment>,
    val score: Int?,
    val sleepMinutes: Int,
    val stages: StageTotals,
    val headline: String,
    val body: String,
)

/** The day's heart rate, plus the resting heart rate the screen compares it with. */
data class HeartSnapshot(
    val date: String,
    val samples: List<HeartSample>,
    val restingHr: Int?,
    /** True when [restingHr] is our own estimate rather than the strap's daily value. */
    val restingEstimated: Boolean,
    val latestHr: Int?,
)

/**
 * The Recovery screen: the score, its sentence, what drove it, the seven days behind it, Charge,
 * and the explanation the Android screen folds away. Every string is the Android one; nothing here
 * is new copy.
 */
data class RecoverySnapshot(
    val date: String,
    val score: Int?,
    val headline: String,
    val body: String,
    val state: String,
    val stateLabel: String,
    /** Why there is no score, in the card's words; empty when there is one. */
    val emptyText: String,
    val contributors: List<RecoveryContributor>,
    val trend: List<TrendDay>,
    val charge: ChargeSummary,
    /** "How it works": what Readiness is, and what Charge is. */
    val explanation: String,
    val chargeExplanation: String,
    /** The quiet notes under "What drove it": capped score, HRV range, data-quality flags. */
    val notes: List<String>,
)

/* ------------------------------------------------------------------------------------------------ */

/**
 * The screens' reads, in the shape Swift can call: a plain method per screen, an ISO date
 * (`yyyy-MM-dd`) in, one plain data class out on the main thread. No `suspend` function and no
 * `Flow` crosses into Swift (plan section 6).
 *
 * The values are the Android app's: [RecoveryEngine] computes Readiness and Charge (card C4 copied
 * it into `shared/`), [Narrative] supplies the wording, and the day's steps, resting heart rate and
 * oxygen average follow `DayViewModel.stateFor`. This class only reads; `StrapEngine` writes.
 *
 * @param databasePath the same SQLite file `StrapEngine` syncs into (`:memory:` in tests).
 */
class HealthQueries(private val databasePath: String) {
    private val database = OpenStrapIosDatabase.open(databasePath)
    private val healthDao = database.healthDao()
    private val settings = SettingsRepository(database.settingsDao())
    private val syncLog = database.syncLogDao()

    /** One worker, so a screen's two reads don't race each other over the database. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    // ---- The four screens -----------------------------------------------------------------------

    /** Today: Recovery's score and state, the night's sleep, resting HR, SpO2, steps and last sync. */
    fun today(date: String, onResult: (TodaySnapshot) -> Unit) = query(date, onResult, ::emptyToday) { day ->
        val zone = ScreenClock.zone()
        val window = DayWindow.of(day, zone)
        val result = RecoveryEngine(healthDao).compute(day, settings.profile(), zone, ScreenClock.nowEpochSeconds())
        val night = result.tonight
        val stages = night?.let { healthDao.sleepStagesOnce(it.start) }.orEmpty()
        val totals = totals(stages)
        val asleep = if (night == null || stages.isEmpty()) night?.asleepMinutes ?: 0 else sleptMinutes(totals)
        val series = daySeries(window)
        val stored = healthDao.restingHrOnce(window.from, window.to).lastOrNull()?.bpm
        TodaySnapshot(
            date = day.toString(),
            recoveryScore = result.readiness.score,
            recoveryState = result.readiness.phase.name.lowercase(),
            recoveryLabel = phaseLabel(result),
            recoveryHeadline = result.readiness.score
                ?.let { Narrative.readinessHeadline(it, result.readiness.strainAlert) }
                .orEmpty(),
            sleepScore = night?.let { it.strapScore ?: Recovery.sleepScore(it.copy(asleepMinutes = asleep), result.sleepNeed) },
            sleepMinutes = asleep,
            stages = totals,
            restingHr = stored ?: if (series.size >= MIN_MINUTES_FOR_ESTIMATE) Summaries.estimateRestingHr(series) else null,
            averageSpo2 = night?.let { averageSpo2(it.start, it.end) },
            steps = Summaries.totalSteps(healthDao.activityOnce(window.from, window.to)),
            lastSync = syncLog.lastEnd() ?: 0,
        )
    }

    /** The main sleep that ended on [date]: start, end, and its stages in order. */
    fun sleep(date: String, onResult: (SleepSnapshot) -> Unit) = query(date, onResult, ::emptySleep) { day ->
        val zone = ScreenClock.zone()
        val result = RecoveryEngine(healthDao).compute(day, settings.profile(), zone, ScreenClock.nowEpochSeconds())
        val night = result.tonight
        if (night == null) {
            emptySleep(date)
        } else {
            val stages = healthDao.sleepStagesOnce(night.start)
            val totals = totals(stages)
            val asleep = if (stages.isEmpty()) night.asleepMinutes else sleptMinutes(totals)
            // The score sees the minutes the screen prints, and the night's continuity from the same stages.
            val score = night.strapScore ?: Recovery.sleepScore(night.copy(asleepMinutes = asleep), result.sleepNeed)
            val session = healthDao.sleepSessionsOnce(night.start, night.end).firstOrNull { it.start == night.start }
            SleepSnapshot(
                date = day.toString(),
                start = night.start,
                end = night.end,
                segments = stages.map { StageSegment(stageName(it.stage), it.start, it.end) },
                score = score,
                sleepMinutes = asleep,
                stages = totals,
                headline = Narrative.sleepHeadline(score),
                body = session?.let { Narrative.sleepBody(it, asleep * 60L) }.orEmpty(),
            )
        }
    }

    /** The day's heart rate as five-minute averages, and the resting HR it is compared with. */
    fun heart(date: String, onResult: (HeartSnapshot) -> Unit) = query(date, onResult, ::emptyHeart) { day ->
        val window = DayWindow.of(day, ScreenClock.zone())
        val series = Summaries.bucketAverage(daySeries(window))
        val stored = healthDao.restingHrOnce(window.from, window.to).lastOrNull()?.bpm
        val estimated = if (stored != null) null else if (series.size >= MIN_MINUTES_FOR_ESTIMATE) Summaries.estimateRestingHr(series) else null
        HeartSnapshot(
            date = day.toString(),
            samples = series.map { HeartSample(it.first, it.second) },
            restingHr = stored ?: estimated,
            restingEstimated = stored == null && estimated != null,
            latestHr = series.lastOrNull()?.second,
        )
    }

    /** The Android Recovery screen: score, sentence, what drove it, the week, Charge, explanation. */
    fun recovery(date: String, onResult: (RecoverySnapshot) -> Unit) = query(date, onResult, ::emptyRecovery) { day ->
        val zone = ScreenClock.zone()
        val result = RecoveryEngine(healthDao).compute(day, settings.profile(), zone, ScreenClock.nowEpochSeconds())
        val readiness = result.readiness
        val score = readiness.score
        RecoverySnapshot(
            date = day.toString(),
            score = score,
            headline = score?.let { Narrative.readinessHeadline(it, readiness.strainAlert) }.orEmpty(),
            body = Narrative.readinessBody(readiness),
            state = readiness.phase.name.lowercase(),
            stateLabel = phaseLabel(result),
            emptyText = when {
                score != null -> ""
                !readiness.withheld -> RecoveryCopy.NO_SLEEP
                readiness.phase == Recovery.Phase.Withheld -> RecoveryCopy.LEARNING
                else -> RecoveryCopy.WITHHELD_TONIGHT
            },
            contributors = readiness.contributors.map { c ->
                RecoveryContributor(
                    label = c.factor.label,
                    rating = Narrative.ratingFor(c.score).label,
                    detail = c.detail,
                    fraction = c.score / 100f,
                    group = groupOf(c.factor),
                )
            },
            trend = result.readinessTrend.map { TrendDay(it.first.toString(), it.second) },
            charge = chargeSummary(result),
            explanation = RecoveryCopy.EXPLANATION,
            chargeExplanation = RecoveryCopy.CHARGE_EXPLANATION,
            notes = notes(readiness),
        )
    }

    // ---- Demo data ------------------------------------------------------------------------------

    /**
     * Fills the database with [DemoData] for the `-demoData` launch argument in debug builds. The
     * app calls this before the first screen appears; [onDone] gets a line for Diagnostics.
     */
    fun seedDemoData(onDone: (String) -> Unit) {
        scope.launch {
            val message = try {
                DemoData.fill(healthDao)
                "demo data: seeded"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "demo data: could not seed ($e)"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    /** Closes the connection and ends the worker. The object is not reusable afterwards. */
    fun close() {
        scope.cancel()
        database.close()
    }

    // ---- Plumbing -------------------------------------------------------------------------------

    /** Runs [block] on the worker and hands its answer to [onResult] on the main thread. */
    private fun <T> query(
        date: String,
        onResult: (T) -> Unit,
        empty: (String) -> T,
        block: suspend (LocalDate) -> T,
    ) {
        scope.launch {
            // A malformed date is a caller's mistake, not something to crash Swift over: it gets an
            // empty screen with the date it asked for.
            val day = runCatching { LocalDate.parse(date) }.getOrNull()
            val value = if (day == null) empty(date) else try {
                block(day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                empty(date)
            }
            withContext(Dispatchers.Main) { onResult(value) }
        }
    }

    private suspend fun daySeries(window: DayWindow): List<Pair<Long, Int>> =
        Summaries.heartRateSeries(
            healthDao.heartRateOnce(window.from, window.to),
            healthDao.activityOnce(window.from, window.to),
        )

    /** The night's oxygen: the sleep-only table when the strap filled it, else the daytime readings. */
    private suspend fun averageSpo2(from: Long, to: Long): Float? {
        val sleep = healthDao.sleepSpo2Once(from, to).map { it.percent }
        val values = if (sleep.isNotEmpty()) sleep else healthDao.spo2Once(from, to).map { it.percent }
        return if (values.isEmpty()) null else values.average().toFloat()
    }

    private fun totals(stages: List<SleepStage>): StageTotals {
        val breakdown = Summaries.sleepBreakdown(stages)
        fun minutes(stage: Int): Int = ((breakdown.byStage[stage] ?: 0L) / 60).toInt()
        return StageTotals(
            deepMinutes = minutes(SleepStage.DEEP),
            lightMinutes = minutes(SleepStage.LIGHT),
            remMinutes = minutes(SleepStage.REM),
            awakeMinutes = minutes(SleepStage.AWAKE),
        )
    }

    private fun sleptMinutes(totals: StageTotals): Int = totals.deepMinutes + totals.lightMinutes + totals.remMinutes

    private fun stageName(stage: Int): String = when (stage) {
        SleepStage.AWAKE -> "awake"
        SleepStage.LIGHT -> "light"
        SleepStage.DEEP -> "deep"
        SleepStage.REM -> "rem"
        else -> "rem"
    }

    private fun groupOf(factor: Recovery.Factor): String = when (factor) {
        Recovery.Factor.HrvBalance, Recovery.Factor.RestingHr, Recovery.Factor.Temperature,
        Recovery.Factor.Breathing, Recovery.Factor.BloodOxygen -> "Body"
        Recovery.Factor.Sleep, Recovery.Factor.SleepBalance, Recovery.Factor.RecoveryIndex -> "Sleep"
        Recovery.Factor.PreviousDayLoad -> "Load"
    }

    /** `RecoveryCard`'s trailing text: the phase, the night count while learning, else yesterday. */
    private fun phaseLabel(result: RecoveryResult): String {
        val ready = result.readiness
        val score = ready.score
        val before = result.readinessTrend.dropLast(1).lastOrNull()?.second
        return when (ready.phase) {
            Recovery.Phase.Withheld -> "Learning ${ready.baselineNights}/${Recovery.SCORED_NIGHTS} nights"
            Recovery.Phase.Learning -> "Learning ${ready.baselineNights}/${Recovery.SETTLED_NIGHTS} nights"
            Recovery.Phase.Calibrated -> if (score != null && before != null) "Calibrated · " + vsYesterday(score, before) else "Calibrated"
            else -> if (score != null && before != null) vsYesterday(score, before) else ""
        }
    }

    private fun vsYesterday(score: Int, before: Int): String =
        if (score == before) "Steady vs yesterday" else "${if (score > before) "+" else "−"}${abs(score - before)} vs yesterday"

    private fun chargeSummary(result: RecoveryResult): ChargeSummary {
        val charge = result.charge
        val now = charge.latest
        return ChargeSummary(
            latest = now?.toInt(),
            atWake = charge.atWake?.toInt(),
            headline = now?.let { Narrative.chargeHeadline(it.toInt()) }.orEmpty(),
            body = Narrative.chargeBody(charge),
            recharged = charge.recharged.toInt(),
            drainedExercise = charge.drainedExercise.toInt(),
            drainedStress = charge.drainedStress.toInt(),
            drainedLiving = charge.drainedLiving.toInt(),
            highest = charge.highest?.toInt(),
            lowest = charge.lowest?.toInt(),
            anchored = charge.anchored,
            hint = when {
                !charge.anchored -> RecoveryCopy.CHARGE_NEEDS_A_NIGHT
                now == null -> RecoveryCopy.CHARGE_NEEDS_HEART_RATE
                else -> ""
            },
            stressThresholdText = Narrative.stressThreshold(result.stressThreshold),
        )
    }

    /** The notes `ContributorsPanel` prints under the contributors, in its order. */
    private fun notes(readiness: Recovery.Readiness): List<String> = buildList {
        if (readiness.capped) add(RecoveryCopy.CAPPED)
        readiness.hrvRange?.let { add(RecoveryCopy.hrvRange(it, readiness.hrvWeekThin, readiness.hrvWeekNights)) }
        readiness.fallbackHrv?.let { add("HRV ${it.toInt()} ms (strap fallback): our own reading wasn't clean enough last night.") }
        readiness.fallbackRestingHr?.let { add("Resting HR ${it.toInt()} bpm (strap fallback): our own reading wasn't clean enough last night.") }
        readiness.qualityFlags.forEach { add("Check: $it") }
    }

    // ---- Empty answers --------------------------------------------------------------------------

    private fun emptyToday(date: String) = TodaySnapshot(
        date, null, "none", "", "", null, 0, StageTotals(0, 0, 0, 0), null, null, 0, 0,
    )

    private fun emptySleep(date: String) = SleepSnapshot(
        date, 0, 0, emptyList(), null, 0, StageTotals(0, 0, 0, 0), "", "",
    )

    private fun emptyHeart(date: String) = HeartSnapshot(date, emptyList(), null, false, null)

    private fun emptyRecovery(date: String) = RecoverySnapshot(
        date, null, "", "", "none", "", RecoveryCopy.NO_SLEEP,
        emptyList(), emptyList(),
        ChargeSummary(null, null, "", "", 0, 0, 0, 0, null, null, false, RecoveryCopy.CHARGE_NEEDS_A_NIGHT, ""),
        RecoveryCopy.EXPLANATION, RecoveryCopy.CHARGE_EXPLANATION, emptyList(),
    )

    companion object {
        /** Days the JVM query tests seed with [SampleData.load]; the `-demoData` path fills [DemoData] instead. */
        const val DEMO_DAYS = SampleData.DEMO_DAYS

        /** Minutes of heart rate a day needs before we estimate its resting HR ourselves (DayViewModel's). */
        private const val MIN_MINUTES_FOR_ESTIMATE = 6 * 60
    }
}
