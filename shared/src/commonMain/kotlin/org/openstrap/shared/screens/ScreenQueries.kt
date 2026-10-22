package org.openstrap.shared.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.atStartOfDay
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.ui.dashboard.DayStateLoader
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.app.ui.glow.SignalHistories
import org.openstrap.app.ui.workout.WorkoutsLoader
import org.openstrap.shared.clock.ScreenClock

/** The morning check-in for a day: [feeling] is 1-5, null until the user answered. */
data class CheckInAnswer(val date: String, val feeling: Int?) {
    val answered: Boolean get() = feeling != null
}

/**
 * The screens' reads and writes, in the shape Swift can call: one plain method per screen, ISO dates
 * in and out, one plain answer on the main thread. No `suspend` function and no `Flow` crosses into
 * Swift (plan section 6), exactly as [org.openstrap.shared.health.HealthQueries] does for the four
 * screens before them.
 *
 * The values are the Android app's: [DayStateLoader] is `DayViewModel`'s state building,
 * [WorkoutsLoader] is `WorkoutsViewModel`'s, and [SettingsRepository] holds the profile and goals
 * `SettingsStore` does. This class only wires them together and moves the answers to the main
 * thread; it computes nothing of its own.
 *
 * @param databasePath the same SQLite file `StrapEngine` syncs into (`:memory:` in tests).
 */
class ScreenQueries(private val databasePath: String) {
    private val database = OpenStrapIosDatabase.open(databasePath)
    private val healthDao = database.healthDao()
    private val settings = SettingsRepository(database.settingsDao())
    private val repository = HealthRepository(healthDao)
    private val dayState = DayStateLoader(repository, settings)
    private val workoutList = WorkoutsLoader(healthDao, settings)

    /** One worker, so a screen's reads don't race each other over the database. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /** The `changes()` subscription, so a second call replaces the first instead of doubling it. */
    private var changesJob: Job? = null

    // ---- The day ---------------------------------------------------------------------------------

    /** Today's (or [date]'s) state, as `DayViewModel.state` builds it. A malformed date is an empty day. */
    fun day(date: String, onResult: (DayUiState) -> Unit) = read(date, { DayUiState() }, onResult) { d ->
        dayState.load(d, null)
    }

    /** Readiness and charge, or null when the day can't be scored (or the date is malformed). */
    fun recovery(date: String, onResult: (RecoveryResult?) -> Unit) = read(date, { null }, onResult) { d ->
        dayState.recovery(d)
    }

    /** One value per day of [range], ending today. */
    fun history(metric: Metric, range: Range, onResult: (List<DayStat>) -> Unit) {
        history(metric, range, todayIso(), onResult)
    }

    /** One value per day of [range], ending on [date]. Days without data are absent. */
    fun history(metric: Metric, range: Range, date: String, onResult: (List<DayStat>) -> Unit) =
        read(date, { emptyList() }, onResult) { d ->
            dayState.history(metric, range, d)
        }

    /** Local days the calendar lets you pick, oldest first, as `yyyy-MM-dd`. */
    fun daysWithData(onResult: (List<String>) -> Unit) = readAll({ emptyList() }, onResult) {
        dayState.daysWithData().map { it.toString() }.sorted()
    }

    /**
     * The last month of [metric] ending on [date]: Today's Monday recap reads steps this way so
     * both weeks it compares are inside the answer. Days without data are absent.
     */
    fun monthHistory(metric: Metric, date: String, onResult: (List<DayStat>) -> Unit) =
        history(metric, Range.Month, date, onResult)

    /**
     * The strap's own Readiness for [date], or null when it reported none. Android's `strapReadiness`
     * read: it stands in as "Strap's estimate" while OpenStrap's own score is withheld.
     */
    fun strapReadiness(date: String, onResult: (StrapReadinessRow?) -> Unit) = read(date, { null }, onResult) { d ->
        val day = d.atStartOfDay(ScreenClock.zone()).epochSeconds
        healthDao.strapReadinessOnce(day, day).firstOrNull()
    }

    /**
     * The month behind each of the Recovery tab's six body signals: `rememberSignals`' six
     * `history(m.daily, History.NORMAL_DAYS ending on the day)` reads (GlowRecoveryScreen.kt:360-384),
     * answered together so the screen paints its tiles in one pass. Days without data are absent.
     */
    fun signalHistories(date: String, onResult: (SignalHistories) -> Unit) =
        read(date, { SignalHistories() }, onResult) { d ->
            SignalHistories(
                restingHr = dayState.history(Metric.RestingHr, Range.Month, d),
                hrv = dayState.history(Metric.Hrv, Range.Month, d),
                skinTemperature = dayState.history(Metric.SkinTemperature, Range.Month, d),
                breathing = dayState.history(Metric.Breathing, Range.Month, d),
                spo2 = dayState.history(Metric.Spo2, Range.Month, d),
                stress = dayState.history(Metric.Stress, Range.Month, d),
            )
        }

    // ---- Workouts --------------------------------------------------------------------------------

    /** Every workout, newest first, as the Android list shows them. */
    fun workouts(onResult: (List<WorkoutAnalysis>) -> Unit) = readAll({ emptyList() }, onResult) {
        workoutList.workouts()
    }

    /** One workout with its whole heart-rate stream, or null when there is none at [start]. */
    fun workout(start: Long, onResult: (WorkoutAnalysis?) -> Unit) = readAll({ null }, onResult) {
        workoutList.workout(start)
    }

    // ---- Profile, goals and the check-in ---------------------------------------------------------

    fun profile(onResult: (UserProfile) -> Unit) = readAll({ UserProfile() }, onResult) { settings.profile() }

    fun saveProfile(profile: UserProfile, onDone: () -> Unit = {}) = write(onDone) { settings.saveProfile(profile) }

    fun goals(onResult: (Goals) -> Unit) = readAll({ Goals() }, onResult) { settings.goals() }

    fun saveGoals(goals: Goals, onDone: () -> Unit = {}) = write(onDone) { settings.saveGoals(goals) }

    /** The day's check-in; [CheckInAnswer.answered] is false until the user answers. */
    fun checkIn(date: String, onResult: (CheckInAnswer) -> Unit) = read(date, { CheckInAnswer(date, null) }, onResult) { d ->
        CheckInAnswer(d.toString(), repository.checkIn(d).first()?.feeling)
    }

    /** Answers - or clears, with null - the day's check-in; the value is clamped to 1..5. */
    fun saveCheckIn(date: String, feeling: Int?, onDone: () -> Unit = {}) = write(onDone) {
        LocalDate.parse(date).let { d -> repository.saveCheckIn(d, feeling) }
    }

    // ---- New synced data -------------------------------------------------------------------------

    /**
     * Calls [onChange] when new synced data lands, so a screen can ask again. This is
     * `DayViewModel.recovery`'s trigger: `HealthRepository.changes()` debounced by 800 ms, because a
     * sync writes hundreds of rows and each one is a change.
     */
    @OptIn(FlowPreview::class)
    fun changes(onChange: () -> Unit) {
        changesJob?.cancel()
        changesJob = scope.launch {
            repository.changes().debounce(800).collect {
                withContext(Dispatchers.Main) { onChange() }
            }
        }
    }

    /** Closes the connection and ends the worker. The object is not reusable afterwards. */
    fun close() {
        scope.cancel()
        database.close()
    }

    // ---- Plumbing --------------------------------------------------------------------------------

    private fun todayIso(): String = ScreenClock.today().toString()

    /** Runs [block] on the worker for a read that takes a date, and hands its answer to [onResult] on the main thread. */
    private fun <T> read(date: String, empty: () -> T, onResult: (T) -> Unit, block: suspend (LocalDate) -> T) {
        scope.launch {
            // A malformed date is a caller's mistake, not something to crash Swift over.
            val day = runCatching { LocalDate.parse(date) }.getOrNull()
            val value = if (day == null) empty() else guarded(empty) { block(day) }
            withContext(Dispatchers.Main) { onResult(value) }
        }
    }

    /** Runs [block] on the worker and hands its answer to [onResult] on the main thread. */
    private fun <T> readAll(empty: () -> T, onResult: (T) -> Unit, block: suspend () -> T) {
        scope.launch {
            val value = guarded(empty) { block() }
            withContext(Dispatchers.Main) { onResult(value) }
        }
    }

    /** Runs a write on the worker, then tells the caller on the main thread. */
    private fun write(onDone: () -> Unit, block: suspend () -> Unit) {
        scope.launch {
            guarded({ }, block)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** A failed read is an empty screen, not a crash in Swift; cancellation still passes through. */
    private suspend fun <T> guarded(empty: () -> T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        empty()
    }
}
