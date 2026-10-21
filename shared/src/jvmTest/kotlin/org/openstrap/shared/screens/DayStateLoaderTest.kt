package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.ui.dashboard.DayStateLoader
import org.openstrap.app.ui.dashboard.SLEEP_NIGHTS
import org.openstrap.protocol.services.StepTotals
import kotlin.time.Clock

/**
 * `DayStateLoader` over a real database seeded with the copied debug sample month: the day the
 * Android `DayViewModel` builds from its flow combines, read one shot at a time.
 */
class DayStateLoaderTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = Clock.System.todayIn(zone)
    private val directory = File("build/tmp/day-state-loader-test")
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

    /** A database of [days] sample days, and a loader over it. */
    private suspend fun loader(days: Int = SampleData.DEMO_DAYS): Pair<DayStateLoader, OpenStrapIosDatabase> {
        val path = File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath
        files += File(path)
        val db = OpenStrapIosDatabase.open(path).also { database = it }
        SampleData.load(db.healthDao(), days)
        return DayStateLoader(HealthRepository(db.healthDao()), SettingsRepository(db.settingsDao())) to db
    }

    // ---- The day --------------------------------------------------------------------------------

    @Test
    fun loadFillsTheDayFromTheSampleMonth() = runTest {
        val (loader, db) = loader()
        val dao = db.healthDao()
        val state = loader.load(today, null)

        val day = DayWindow.of(today, zone)
        val minutes = dao.activityOnce(day.from, day.to)
        val hr = dao.heartRateOnce(day.from, day.to)
        val series = Summaries.heartRateSeries(hr, minutes)

        // The day itself.
        assertEquals(today, state.date)
        assertEquals(day.from, state.dayStart)
        assertEquals(day.to, state.dayEnd)

        // Steps, hourly and active hours are the day's minute records.
        val total = Summaries.totalSteps(minutes)
        assertEquals(total, state.steps)
        assertEquals(total, state.minuteSteps)
        val hourly = Summaries.hourlySteps(minutes, day.from)
        assertEquals(24, state.hourlySteps.size)
        assertEquals(hourly, state.hourlySteps)
        assertEquals(Summaries.activeHours(hourly), state.activeHours)
        assertTrue(total > 0, "the sample day has steps")

        // Heart rate: five-minute buckets, the last reading, and the strap's own resting value.
        val buckets = Summaries.bucketAverage(series)
        assertEquals(buckets.map { it.first }, state.heartRate.map { it.x })
        assertEquals(buckets.map { it.second.toFloat() }, state.heartRate.map { it.y })
        assertTrue(state.heartRate.size > 100, "the sample day has heart rate")
        assertEquals(series.last().second, state.latestHeartRate)
        assertEquals(dao.restingHrOnce(day.from, day.to).last().bpm, state.restingHeartRate)
        assertEquals(false, state.restingEstimated)
        assertEquals(7, state.restingTrend.size)
        assertEquals(7, state.hrvTrend.size)
        assertEquals(state.restingTrend.last().value?.toInt(), state.restingHeartRate)

        // Sleep: the night's main session, its stages and the breakdown the screen draws.
        val night = assertNotNull(state.sleep, "the sample day has a night")
        val stages = dao.sleepStagesOnce(night.start)
        assertEquals(stages.size, state.sleepStages.size)
        assertEquals(stages.map { it.start }, state.sleepStages.map { it.start })
        assertEquals(stages.map { it.end }, state.sleepStages.map { it.end })
        assertEquals(stages.map { it.stage }, state.sleepStages.map { it.stage })
        val breakdown = Summaries.sleepBreakdown(stages)
        assertEquals(breakdown.asleepSeconds, state.sleepBreakdown?.asleepSeconds)
        assertEquals(breakdown.asleepSeconds, state.asleepSeconds)
        assertEquals(breakdown.byStage[SleepStage.DEEP] ?: 0L, state.sleepBreakdown?.byStage?.get(SleepStage.DEEP))
        assertTrue(state.sleepStages.size > 3, "the sample night has stages")
        // The strap's score for the night before: the sample data sets none, so this is null.
        assertNull(state.previousSleepScore)
        assertTrue(state.naps.isEmpty())

        // Baselines: the seven days before, averaged. The sample data writes resting HR, HRV, SpO2
        // and stress, and no skin temperature or breathing rate.
        assertNotNull(state.baselines.restingHr, "baseline resting HR")
        assertNotNull(state.baselines.hrv, "baseline HRV")
        assertNotNull(state.baselines.spo2, "baseline SpO2")
        assertNotNull(state.baselines.stress, "baseline stress")
        assertNull(state.baselines.skinTemperature)
        assertNull(state.baselines.breathRate)

        // The fourteen nights behind the day, oldest first, each with its own session and vitals.
        assertEquals(SLEEP_NIGHTS, state.nights.size)
        assertEquals(today.minusDays((SLEEP_NIGHTS - 1).toLong()), state.nights.first().date)
        assertEquals(today, state.nights.last().date)
        state.nights.forEach { n ->
            assertNotNull(n.session, "${n.date} has a session")
            assertNotNull(n.asleepSeconds, "${n.date} has its asleep seconds")
        }
        // The vitals are averaged over the sleep, so the sample night's HRV shows up on it.
        assertNotNull(state.nights.last().hrv, "the night's HRV")

        // Heart rate across the night window, five-minute buckets.
        val nightWindow = DayWindow.nightEndingOn(today, zone)
        val nightHr = Summaries.bucketAverage(Summaries.heartRateSeries(dao.heartRateOnce(nightWindow.from, nightWindow.to), emptyList()))
        assertEquals(nightHr.map { it.first }, state.sleepHeartRate.map { it.x })
        assertTrue(state.sleepHeartRate.isNotEmpty(), "the night has heart rate")

        // The day's other series are the tables the sample data fills, or empty when it writes none.
        assertTrue(state.spo2.isNotEmpty())
        assertTrue(state.stress.isNotEmpty())
        assertTrue(state.hrv.isNotEmpty())
        assertTrue(state.skinTemperature.isEmpty())
        assertTrue(state.breathRate.isEmpty())
        assertTrue(state.sleepSpo2.isEmpty())
        assertTrue(state.hrvSdnn.isEmpty())
        assertNull(state.pai)
        assertNull(state.osaResult)
        assertTrue(state.osaEvents.isEmpty())
        assertNull(state.dailyMaxHeartRate)
        assertNull(state.distanceMeters)
        assertNull(state.activeCalories)
        assertTrue(state.alerts.isEmpty())
    }

    @Test
    fun loadingAPastDayUsesThatDaysRecords() = runTest {
        val (loader, db) = loader()
        val yesterday = today.minusDays(1)
        val state = loader.load(yesterday, null)
        val day = DayWindow.of(yesterday, zone)

        assertEquals(yesterday, state.date)
        assertEquals(Summaries.totalSteps(db.healthDao().activityOnce(day.from, day.to)), state.steps)
        // A past day is complete, so its hourly buckets carry the whole day's steps.
        assertEquals(state.steps.toFloat(), state.hourlySteps.sum())
        assertNotNull(state.sleep)
        assertEquals(yesterday, state.nights.last().date)
    }

    @Test
    fun aPlausibleLiveTotalIsUsedAndAnImplausibleOneIsIgnored() = runTest {
        val (loader, db) = loader()
        val day = DayWindow.of(today, zone)
        val synced = Summaries.totalSteps(db.healthDao().activityOnce(day.from, day.to))

        // The strap's own running total, with a distance that backs it up: used as is, as LiveSteps does.
        val live = loader.load(today, StepTotals(steps = synced + 500, distanceMeters = (synced + 500) * 3 / 4, calories = 100))
        assertEquals(synced + 500, live.steps)
        assertEquals(synced, live.minuteSteps)

        // Firmware 3.25's bad counter: 11,368 extra steps against 1,836 m is under a quarter-metre
        // stride, so the synced minutes win (a fresh LiveSteps has nothing to anchor on yet).
        val implausible = loader.load(today, StepTotals(steps = synced + 11368, distanceMeters = 1836, calories = 100))
        assertEquals(synced, implausible.steps)
        assertEquals(synced, implausible.minuteSteps)
    }

    @Test
    fun loadOfADayWithNoDataIsAnEmptyDayRatherThanACrash() = runTest {
        val (loader, _) = loader()
        val state = loader.load(LocalDate(1999, 1, 1), null)
        assertEquals(LocalDate(1999, 1, 1), state.date)
        assertEquals(0, state.steps)
        assertTrue(state.heartRate.isEmpty())
        assertNull(state.sleep)
        assertEquals(SLEEP_NIGHTS, state.nights.size)
        assertTrue(state.nights.all { it.session == null })
        assertNull(state.baselines.restingHr)
        assertEquals(24, state.hourlySteps.size)
    }
}
