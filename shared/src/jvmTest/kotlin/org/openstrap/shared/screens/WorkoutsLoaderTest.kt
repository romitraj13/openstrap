package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.RecoveryEngine
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.ui.workout.WorkoutsLoader
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * `WorkoutsLoader`, the `WorkoutsViewModel` logic read one shot: the list newest first, each workout
 * joined to its heart-rate stream and sport effect by the same `Workouts.analyse` the Android list
 * and detail screen call.
 */
class WorkoutsLoaderTest {
    private val zone = TimeZone.currentSystemDefault()
    private val directory = File("build/tmp/workouts-loader-test")
    private val files = mutableListOf<File>()
    private var database: OpenStrapIosDatabase? = null

    @BeforeTest
    fun makeDirectory() {
        directory.mkdirs()
    }

    @AfterTest
    fun close() {
        database?.close()
        for (file in files) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }

    /** A database with the sample month (for the max-HR read) and two workouts, newest last. */
    private suspend fun loader(): Triple<WorkoutsLoader, HealthDao, LongArray> {
        val path = File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath
        files += File(path)
        val db = OpenStrapIosDatabase.open(path).also { database = it }
        val dao = db.healthDao()
        SampleData.load(dao, SampleData.DEMO_DAYS)

        val today = Clock.System.todayIn(zone)
        val day = DayWindow.of(today, zone)
        val older = day.from + 9 * 3600
        val newer = day.from + 18 * 3600
        dao.insertWorkouts(
            listOf(
                Workout(older, older + 1800, 1, 1800, 210, 140, 178, 96, 2.4f, 0.6f, 120, 45, 3000),
                Workout(newer, newer + 1200, 60, 1200, 90, 110, 130, 80, 1.2f, 0f, 60, 40, 1500),
            ),
        )
        // The first workout has a heart-rate stream with the strap's three-minute recovery tail, and a
        // sport effect that names the real end and the max HR behind its zones.
        val hr = (0..1800 step 5).map { WorkoutHeartRate(older, older + it, 120 + it / 60) } +
            (0..180 step 5).map { WorkoutHeartRate(older, older + 1800 + it, 150 - it / 30) }
        dao.insertWorkoutHeartRate(hr)
        dao.insertSportEffects(
            listOf(
                SportEffectRow(end = older + 1800, recoveryMinutes = 24, vo2max = 46.5f, epoc = 12f, weeklyLoad = 300f, monthlyLoad = 900f, optimalLoadMin = 200, optimalLoadMax = 500, maximalHeartRate = 190),
            ),
        )
        return Triple(WorkoutsLoader(dao, SettingsRepository(db.settingsDao())), dao, longArrayOf(older, newer))
    }

    @Test
    fun workoutsAreNewestFirst() = runTest {
        val (loader, _, starts) = loader()
        val list = loader.workouts()
        assertEquals(2, list.size)
        // The Android list reads ORDER BY start DESC: the newest workout heads it.
        assertEquals(starts[1], list[0].workout.start)
        assertEquals(starts[0], list[1].workout.start)
        assertTrue(list[0].workout.start > list[1].workout.start)
    }

    @Test
    fun eachWorkoutIsAnalysedWithItsHeartRateAndSportEffect() = runTest {
        val (loader, dao, _) = loader()
        val older = loader.workouts().last()
        val effect = dao.sportEffects().first().single()

        // The effect's end wins over the stored one, and carries the max HR its zones are cut at.
        assertEquals(effect.end, older.end)
        assertEquals(effect.maximalHeartRate, older.maxHeartRate)
        assertEquals(effect, older.effect)
        // Started at 09:00 and ended when the effect says, so the duration is that gap.
        assertEquals(1800L, older.durationSeconds)
        assertEquals("Outdoor run", older.sportName)
        // A list row carries only the workout's last heart-rate sample (WorkoutsViewModel does the
        // same), so it has no zone times, no end heart rate and no recovery drop: those need the
        // whole stream, which the detail screen asks for below.
        assertEquals(5, older.zoneSeconds.size)
        assertTrue(older.zoneSeconds.all { it == 0 }, "a list row has only the last sample")
        assertNull(older.endHeartRate)
        assertNull(older.recovery60)

        // The workout without an effect falls back to the app's max HR, as Workouts.analyse is told to.
        val newer = loader.workouts().first()
        assertEquals(newer.workout.end, newer.end)
        assertNull(newer.effect)
        assertEquals(loader.fallbackMaxHr(), newer.maxHeartRate)
        assertEquals("Yoga", newer.sportName)
        assertTrue(newer.heartRate.isEmpty(), "the second workout has no stream")
        assertNull(newer.endHeartRate)
    }

    @Test
    fun oneWorkoutIsTheSameAnalysisTheListGivesPlusItsStream() = runTest {
        val (loader, _, starts) = loader()
        val listed = loader.workouts().last()
        val opened = assertNotNull(loader.workout(starts[0]))

        // The same workout, the same end, the same effect.
        assertEquals(listed.workout, opened.workout)
        assertEquals(listed.end, opened.end)
        assertEquals(listed.effect, opened.effect)
        // The detail screen reads the whole stream, so the zones are cut and the drop is measured.
        // (The tail starts exactly where the workout's own samples stop, so the join row is one row.)
        val stream = (0..1800 step 5).count() + (1805..1980 step 5).count()
        assertEquals(stream, opened.heartRate.size)
        assertEquals(5, opened.zoneSeconds.size)
        assertTrue(opened.zoneSeconds.sum() > 0, "the workout spent time in some zone")
        assertNotNull(opened.recovery60)
        assertNotNull(opened.recovery120)
        assertTrue(opened.recoveryIsMeaningful)

        // A start that isn't there is null, not an exception.
        assertNull(loader.workout(starts[0] + 1))
    }

    @Test
    fun effectsAreOldestFirst() = runTest {
        val (loader, dao, starts) = loader()
        dao.insertSportEffects(
            listOf(
                SportEffectRow(end = starts[1] + 1200, recoveryMinutes = 12, vo2max = 40f, epoc = 8f, weeklyLoad = 200f, monthlyLoad = 600f, optimalLoadMin = 150, optimalLoadMax = 400, maximalHeartRate = 170),
            ),
        )
        val effects = loader.effects()
        assertEquals(2, effects.size)
        // ORDER BY `end`, as WorkoutsViewModel.effects reads it.
        assertEquals(starts[0] + 1800, effects.first().end)
        assertEquals(starts[1] + 1200, effects.last().end)
    }

    @Test
    fun fallbackMaxHeartRateIsTheAppsOwnRule() = runTest {
        val (loader, dao, _) = loader()
        // RecoveryEngine.maxHeartRate over this profile, rounded, is what the ViewModel's flow emits.
        val expected = RecoveryEngine(dao).maxHeartRate(SettingsRepository(database!!.settingsDao()).profile()).roundToInt()
        assertEquals(expected, loader.fallbackMaxHr())
        // And it is cached per profile, so the 90-day read behind it runs once.
        assertEquals(expected, loader.fallbackMaxHr())
    }

    @Test
    fun noWorkoutsIsAnEmptyList() = runTest {
        val path = File(directory, "empty-${System.nanoTime()}.db").absolutePath
        files += File(path)
        val db = OpenStrapIosDatabase.open(path).also { database = it }
        val loader = WorkoutsLoader(db.healthDao(), SettingsRepository(db.settingsDao()))
        assertTrue(loader.workouts().isEmpty())
        assertNull(loader.workout(1))
        assertTrue(loader.effects().isEmpty())
    }
}
