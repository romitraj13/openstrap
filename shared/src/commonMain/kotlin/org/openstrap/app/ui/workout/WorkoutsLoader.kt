package org.openstrap.app.ui.workout

import kotlinx.coroutines.flow.first
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.data.settings.UserProfile
import kotlin.math.roundToInt

/**
 * The logic of `WorkoutsViewModel` (`app/src/main/kotlin/org/openstrap/app/ui/workout/
 * WorkoutsViewModel.kt` lines 27-58), read one shot instead of through `stateIn` flows.
 *
 * The ViewModel combines `dao.workouts()`, `dao.sportEffects()` and `dao.lastWorkoutHeartRates()`
 * with `RecoveryEngine(dao).maxHeartRate(profile)`; here the same reads are taken once each and the
 * same `Workouts.analyse` call builds each row. The max-HR read is the expensive one (it looks back
 * 90 days), so it is kept until the profile changes — the ViewModel gets the same effect from its
 * shared `stateIn`.
 *
 * @param dao the same `HealthDao` the sync writes through (`StrapEngine`'s database).
 */
class WorkoutsLoader(
    private val dao: HealthDao,
    private val settings: SettingsRepository,
) {
    private var maxHrFor: UserProfile? = null
    private var cachedMaxHr: Int? = null

    /** The app's one max-HR rule (tested value, Tanaka, strap, raised by observed peaks). */
    suspend fun fallbackMaxHr(): Int {
        val profile = settings.profile()
        cachedMaxHr?.let { if (maxHrFor == profile) return it }
        val value = RecoveryEngine(dao).maxHeartRate(profile).roundToInt()
        maxHrFor = profile
        cachedMaxHr = value
        return value
    }

    /** The stored workouts, newest first, each with its last heart-rate sample and its sport effect. */
    suspend fun workouts(): List<WorkoutAnalysis> {
        val ws = dao.workouts().first()
        val effects = dao.sportEffects().first()
        val last = dao.lastWorkoutHeartRates().first()
        val maxHr = fallbackMaxHr()
        val lastByWorkout = last.associateBy { it.workoutStart }
        return ws.map { w -> Workouts.analyse(w, listOfNotNull(lastByWorkout[w.start]), effects, maxHr) }
    }

    /** One workout with its whole heart-rate stream, so the detail screen can cut the real end. */
    suspend fun workout(start: Long): WorkoutAnalysis? {
        val w = dao.workout(start).first() ?: return null
        val hr = dao.workoutHeartRate(start).first()
        val effects = dao.sportEffects().first()
        val maxHr = fallbackMaxHr()
        return Workouts.analyse(w, hr, effects, maxHr)
    }

    /** The strap's sport-effect records, newest last: recovery time, VO2 max and weekly load after each workout. */
    suspend fun effects(): List<SportEffectRow> = dao.sportEffects().first()
}
