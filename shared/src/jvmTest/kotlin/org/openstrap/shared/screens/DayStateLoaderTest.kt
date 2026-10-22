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
import kotlinx.datetime.LocalDate
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.app.ui.dashboard.DayStateLoader
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.app.ui.dashboard.SLEEP_NIGHTS
import org.openstrap.protocol.services.StepTotals
import org.openstrap.shared.clock.DemoDay
import org.openstrap.shared.clock.ScreenClock

/**
 * `DayStateLoader` over a real database seeded with the copied debug sample month: the day the
 * Android `DayViewModel` builds from its flow combines, read one shot at a time.
 */
class DayStateLoaderTest {
    private val zone = DemoDay.ZONE
    private val today: LocalDate = DemoDay.TODAY
    private val directory = File("build/tmp/day-state-loader-test")
    private val files = mutableListOf<File>()
    private var database: OpenStrapIosDatabase? = null

    @BeforeTest
    fun makeDirectory() {
        directory.mkdirs()
        ScreenClock.pinDemo()
    }

    @AfterTest
    fun close() {
        database?.close()
        ScreenClock.unpin()
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

    // ---- Recovery, history and the calendar ------------------------------------------------------

    @Test
    fun recoveryIsScoredAfterTheLearningPeriod() = runTest {
        val (loader, _) = loader()
        val result = assertNotNull(loader.recovery(today), "recovery answers for a seeded day")
        // A month of wear is past the seven nights the baselines need, so the score is there.
        assertNotNull(result.readiness.score, "a month of sample data is scored")
        assertEquals(today, result.date)
    }

    @Test
    fun recoveryOfAnEmptyDatabaseIsNoScoreRatherThanNull() = runTest {
        val (loader, _) = loader(days = 0)
        val result = assertNotNull(loader.recovery(today), "recovery still answers with nothing to score")
        assertNull(result.readiness.score)
    }

    @Test
    fun historyReturnsOnePointPerDayOfTheRange() = runTest {
        val (loader, db) = loader()
        val dao = db.healthDao()
        val repository = HealthRepository(dao)

        // The read behind the screen, from the same place the Android detail screen asks: one row per
        // day that has data, and history is that list cut to the range. (The count is taken from the
        // read rather than written down: SQLite's date() and kotlinx-datetime's zone can disagree on
        // this machine for the one row that sits exactly on a day's midnight, and the year window is
        // the only one old enough to see it.)
        suspend fun daily(metric: Metric, days: Int) =
            repository.daily(metric.daily, today.minusDays((days - 1).toLong()), today, zone).first()
                .filter { it.date in today.minusDays((days - 1).toLong())..today }

        // Week: the last seven days, one point each, in order.
        val week = loader.history(Metric.Steps, Range.Week, today)
        val weekRead = daily(Metric.Steps, Range.Week.days)
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().date)
        assertEquals(today, week.last().date)
        assertEquals(weekRead.map { it.date }, week.map { it.date })
        assertEquals(weekRead.map { it.value }, week.map { it.value })
        week.zipWithNext().forEach { (a, b) -> assertEquals(1, b.date.toEpochDays().toInt() - a.date.toEpochDays().toInt()) }

        // Month: thirty days, one point each, still consecutive and still ending today.
        val month = loader.history(Metric.Steps, Range.Month, today)
        val monthRead = daily(Metric.Steps, Range.Month.days)
        assertEquals(30, month.size)
        assertEquals(today.minusDays(29), month.first().date)
        assertEquals(today, month.last().date)
        assertEquals(monthRead.map { it.date }, month.map { it.date })
        month.zipWithNext().forEach { (a, b) -> assertEquals(1, b.date.toEpochDays().toInt() - a.date.toEpochDays().toInt()) }

        // Year: every day of the sample month, none outside the year, and the same dates as the month
        // because the sample wears a month and no more.
        val year = loader.history(Metric.Steps, Range.Year, today)
        val yearRead = daily(Metric.Steps, Range.Year.days)
        assertEquals(yearRead.map { it.date }, year.map { it.date })
        assertEquals(today, year.last().date)
        assertTrue(year.all { it.date in today.minusDays(364)..today })
        assertTrue(year.map { it.date }.containsAll(month.map { it.date }), "the year covers the month")
        assertEquals(year.size, year.map { it.date }.distinct().size, "one point per day")

        // Day: the day itself, one point.
        val day = loader.history(Metric.Steps, Range.Day, today)
        assertEquals(1, day.size)
        assertEquals(today, day.first().date)

        // Another metric through the same call: heart rate is that day's average of the heart-rate
        // table, exactly as HealthRepository.daily gives it.
        val heart = loader.history(Metric.HeartRate, Range.Week, today)
        val heartRead = daily(Metric.HeartRate, Range.Week.days)
        assertEquals(7, heart.size)
        assertEquals(heartRead.map { it.value }, heart.map { it.value })
        // The point is a real average of the day's readings, so it has readings and sits in bpm range.
        assertTrue(heart.last().count > 0)
        assertTrue(heart.last().value in 30f..230f)
    }

    @Test
    fun historyOfADayWithNoDataIsEmpty() = runTest {
        val (loader, _) = loader()
        assertTrue(loader.history(Metric.Steps, Range.Week, LocalDate(1999, 1, 1)).isEmpty())
    }

    @Test
    fun daysWithDataIsTheSeededMonth() = runTest {
        val (loader, db) = loader()
        val days = loader.daysWithData()
        // Every seeded day has heart rate, steps and a sleep, so every one of them is pickable.
        val seeded = (0 until 30).map { today.minusDays(it) }.toSet()
        assertTrue(days.containsAll(seeded), "every seeded day is in the calendar")
        assertTrue(days.contains(today), "today is in the calendar")
        assertEquals(today, days.max())
        // The loader hands over the repository's own set, unchanged.
        assertEquals(HealthRepository(db.healthDao()).daysWithData(zone).first(), days)
    }
}
