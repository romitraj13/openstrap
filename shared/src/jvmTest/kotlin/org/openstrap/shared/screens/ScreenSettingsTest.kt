package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.data.settings.UserProfile

/**
 * The two settings the screens write: your goals (`GoalsDialog`) and your profile (`ProfileScreen`).
 * The fields and the stored keys are `SettingsStore`'s (lines 113-178), so a value written here is
 * the value the Android app stores under the same name.
 */
class ScreenSettingsTest {
    private val directory = File("build/tmp/screen-settings-test")
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

    private fun open(): Pair<SettingsRepository, OpenStrapIosDatabase> {
        val path = File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath
        files += File(path)
        val db = OpenStrapIosDatabase.open(path).also { database = it }
        return SettingsRepository(db.settingsDao()) to db
    }

    @Test
    fun goalsStartAtTheAppsDefaultsAndRoundTrip() = runTest {
        val (settings, _) = open()

        // GoalsDialog opens on these, as SettingsStore's flow starts at them.
        val defaults = settings.goals()
        assertEquals(Goals.DEFAULT_STEPS, defaults.steps)
        assertEquals(Goals.DEFAULT_SLEEP_MINUTES, defaults.sleepMinutes)
        assertEquals(Goals.DEFAULT_ACTIVE_HOURS, defaults.activeHours)
        assertEquals(Goals(), defaults)

        settings.saveGoals(Goals(steps = 9000, sleepMinutes = 450, activeHours = 12))
        val saved = settings.goals()
        assertEquals(9000, saved.steps)
        assertEquals(450, saved.sleepMinutes)
        assertEquals(12, saved.activeHours)

        // Written to the key_value table under Android's keys, so a later read (or another object)
        // sees the same values.
        database!!.settingsDao().apply {
            assertEquals("9000", value("goal_steps"))
            assertEquals("450", value("goal_sleep_minutes"))
            assertEquals("12", value("goal_active_hours"))
        }
        assertEquals(saved, SettingsRepository(database!!.settingsDao()).goals())
    }

    @Test
    fun profileRoundTripsEveryFieldAndClearsWhatIsRemoved() = runTest {
        val (settings, _) = open()
        assertEquals(UserProfile(), settings.profile())

        val profile = UserProfile(
            birthYear = 1990,
            sex = UserProfile.Sex.Male,
            weightKg = 72.5f,
            heightCm = 180,
            imperial = false,
            sleepNeedMinutes = 480,
            maxHeartRate = 190,
            activityLevel = 3,
            activityAnsweredAt = 1_700_000_000,
            bodyMeasuredAt = 1_700_000_100,
            betaBlocker = true,
            pregnant = false,
        )
        settings.saveProfile(profile)
        assertEquals(profile, settings.profile())
        // The same keys SettingsStore writes.
        database!!.settingsDao().apply {
            assertEquals("1990", value("profile_birth_year"))
            assertEquals("Male", value("profile_sex"))
            assertEquals("72.5", value("profile_weight_kg"))
            assertEquals("180", value("profile_height_cm"))
            assertEquals("false", value("profile_imperial"))
            assertEquals("480", value("profile_sleep_need_minutes"))
            assertEquals("190", value("profile_max_hr"))
            assertEquals("3", value("profile_activity_level"))
            assertEquals("1700000000", value("profile_activity_answered_at"))
            assertEquals("1700000100", value("profile_body_measured_at"))
            assertEquals("true", value("profile_beta_blocker"))
            assertEquals("false", value("profile_pregnant"))
        }

        // A field the user clears is removed, as SettingsStore's saveProfile does.
        settings.saveProfile(profile.copy(weightKg = null, maxHeartRate = null, sex = null))
        val cleared = settings.profile()
        assertNull(cleared.weightKg)
        assertNull(cleared.maxHeartRate)
        assertNull(cleared.sex)
        assertEquals(1990, cleared.birthYear)
        assertNull(database!!.settingsDao().value("profile_weight_kg"))
        assertNull(database!!.settingsDao().value("profile_max_hr"))
        assertNull(database!!.settingsDao().value("profile_sex"))

        // Reading a profile also works from a second object over the same database.
        assertEquals(cleared, SettingsRepository(database!!.settingsDao()).profile())
    }
}
