package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.openstrap.app.DemoData
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.ui.dashboard.DayStateLoader
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.shared.clock.AppClock
import org.openstrap.shared.clock.DemoDay
import org.openstrap.shared.clock.PhoneClock
import org.openstrap.shared.clock.ScreenClock
import kotlin.time.Instant

/**
 * Card F7: the `-demoData` day is a fixed day, not the runner's.
 *
 * Before F7 the seed asked the wall clock for "today" and for "how far into today to write", so the
 * same commit seeded a different amount of wear on every CI run — and Today's leading score moved
 * with it (29, 50 and 72 in three runs of F6's code).
 *
 * Card U8: `-demoData` fills [DemoData] (Android's emulator seed, three weeks with a workout) instead
 * of the old `SampleData` month, so the workout screens have a workout to draw. This test still pins
 * the same fixed demo day, so the seed writes the same rows at any hour in any runner's zone.
 */
class DemoDayTest {
    private val directory = File("build/tmp/demo-day-test")
    private val files = mutableListOf<File>()
    private val databases = mutableListOf<OpenStrapIosDatabase>()

    @BeforeTest
    fun makeDirectory() {
        directory.mkdirs()
        ScreenClock.phone = PhoneClock
        ScreenClock.unpin()
    }

    @AfterTest
    fun close() {
        databases.forEach { it.close() }
        ScreenClock.phone = PhoneClock
        ScreenClock.unpin()
        for (file in files) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }

    private fun database(): OpenStrapIosDatabase {
        val path = File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath
        files += File(path)
        return OpenStrapIosDatabase.open(path).also { databases += it }
    }

    @Test
    fun `the demo data ends at the demo mid morning`() = runTest {
        ScreenClock.pinDemo()
        val database = database()
        DemoData.fill(database.healthDao())

        val day = DayWindow.of(LocalDate(2026, 10, 21), TimeZone.UTC)
        val minutes = database.healthDao().activity(day.from, day.to).first()

        // The seed writes the day up to the pinned 09:30 instant and not a minute past it, so the
        // last awake minute is the one before the fixed mid-morning, whatever the runner's clock says.
        assertTrue(minutes.isNotEmpty(), "the demo day has no activity")
        assertTrue(minutes.all { it.timestamp < DemoDay.epochSeconds }, "a minute is at or past 09:30 UTC")
        assertEquals(DemoDay.epochSeconds - 60, minutes.maxOf { it.timestamp }, "the demo day should stop at 09:29 UTC")
    }

    @Test
    fun `the demo day has a workout with its heart-rate stream and sport effect`() = runTest {
        ScreenClock.pinDemo()
        val database = database()
        DemoData.fill(database.healthDao())

        val day = DayWindow.of(DemoDay.TODAY, TimeZone.UTC)
        val workouts = database.healthDao().workoutsOnce(day.from, day.to)
        assertTrue(workouts.isNotEmpty(), "the demo day has no workout to open")
        val run = workouts.first()
        assertEquals(1, run.sportType, "the demo workout is an outdoor run")
        assertTrue(
            database.healthDao().workoutHeartRate(run.start).first().isNotEmpty(),
            "the workout has no heart-rate stream for its chart",
        )
        assertTrue(
            database.healthDao().sportEffects().first().isNotEmpty(),
            "the workout has no sport effect for its training-effect panel",
        )
    }

    // ---- The card's step 2 ----------------------------------------------------------------------

    /**
     * The demo day twice, on two runners whose clocks disagree: one just after the demo mid-morning,
     * one in the evening of the next day, in a zone five and a half hours ahead. The demo pin is the
     * only thing that makes the two equal — without it the second run seeds a different amount of
     * wear for a different day.
     */
    @Test
    fun `two loads of the demo day at different wall-clock times give the same DayUiState`() = runTest {
        val morning = loadOfTheDemoDay(wall = FixedClock(Instant.parse("2026-10-21T09:31:00Z"), TimeZone.UTC))
        val evening = loadOfTheDemoDay(wall = FixedClock(Instant.parse("2026-10-22T20:15:00Z"), TimeZone.of("Asia/Kolkata")))

        assertEquals(morning, evening, "the demo day followed the runner's wall clock")
        assertEquals(DemoDay.TODAY, morning.date)
    }

    /** Seeds the demo month and reads the demo day, with [wall] standing in for the runner's clock. */
    private suspend fun loadOfTheDemoDay(wall: AppClock): DayUiState {
        ScreenClock.phone = wall
        ScreenClock.pinDemo()
        val database = database()
        DemoData.fill(database.healthDao())
        val loader = DayStateLoader(HealthRepository(database.healthDao()), SettingsRepository(database.settingsDao()))
        return loader.load(DemoDay.TODAY, todaySteps = null, zone = ScreenClock.zone())
    }

    /**
     * A clock that answers one instant in one zone: the runner's clock at a chosen hour, which no
     * test can move on a real machine.
     */
    private class FixedClock(private val instant: Instant, private val zone: TimeZone) : AppClock {
        override fun nowEpochSeconds(): Long = instant.epochSeconds
        override fun zone(): TimeZone = zone
        override fun today(): LocalDate = instant.toLocalDateTime(zone).date
    }
}
