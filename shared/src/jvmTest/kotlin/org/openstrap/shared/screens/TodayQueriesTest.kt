package org.openstrap.shared.screens

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.atStartOfDay
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.ui.dashboard.Metric
import kotlin.time.Clock

/**
 * The two reads card U5 added to `ScreenQueries`: the strap's own Readiness for the day (Today's
 * "Strap's estimate") and a month of one metric for Monday's recap. Both are read through a real
 * database, as the iOS screens read them.
 */
class TodayQueriesTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today = Clock.System.todayIn(zone)
    private val todayIso = today.toString()
    private val directory = File("build/tmp/today-queries-test")
    private val files = mutableListOf<File>()
    private var queries: ScreenQueries? = null

    @BeforeTest
    fun installMain() {
        directory.mkdirs()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun removeMain() {
        queries?.close()
        Dispatchers.resetMain()
        for (file in files) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }

    private fun newPath(): String =
        File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath.also { files += File(it) }

    /** The sample month, then a ScreenQueries over the same file. */
    private suspend fun seeded(): ScreenQueries {
        val path = newPath()
        val database = OpenStrapIosDatabase.open(path)
        try {
            SampleData.load(database.healthDao(), SampleData.DEMO_DAYS)
        } finally {
            database.close()
        }
        return ScreenQueries(path).also { queries = it }
    }

    /** [ScreenQueries] answers on the main thread; the unconfined dispatcher runs it here. */
    private fun <T> await(block: ((T) -> Unit) -> Unit): T {
        var value: T? = null
        val latch = CountDownLatch(1)
        block { answer ->
            value = answer
            latch.countDown()
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "the read never answered")
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    @Test
    fun `the straps readiness for a day is read back, and is null without one`() = kotlinx.coroutines.runBlocking {
        val path = newPath()
        val queries = ScreenQueries(path).also { this@TodayQueriesTest.queries = it }
        // Nothing synced: no estimate to show.
        assertNull(await<StrapReadinessRow?> { done -> queries.strapReadiness(todayIso, done) })

        // The strap's own score for the day, as a sync writes it.
        val dayStart = today.atStartOfDay(zone).epochSeconds
        val database = OpenStrapIosDatabase.open(path)
        try {
            database.healthDao().insertStrapReadiness(
                listOf(
                    StrapReadinessRow(
                        day = dayStart, updatedAt = dayStart + 3600, score = 88, hrvScore = 30, restingHrScore = 20,
                        skinTemperatureScore = 18, breathingScore = 20, sleepHrv = 55, sleepRestingHr = 50,
                        algorithmVersion = "1.0.0",
                    ),
                ),
            )
        } finally {
            database.close()
        }

        val row = assertNotNull(await<StrapReadinessRow?> { done -> queries.strapReadiness(todayIso, done) })
        assertEquals(88, row.score)
        // A malformed date answers null rather than crashing Swift.
        assertNull(await<StrapReadinessRow?> { done -> queries.strapReadiness("not a date", done) })
    }

    @Test
    fun `a month of steps ending today covers both weeks the recap compares`() = kotlinx.coroutines.runBlocking {
        val queries = seeded()
        val steps = await<List<org.openstrap.app.data.DayStat>> { done -> queries.monthHistory(Metric.Steps, todayIso, done) }

        assertTrue(steps.isNotEmpty(), "the sample month has no step history")
        // The last 30 days ending today, so the seven before last week are inside it too.
        assertTrue(steps.all { it.date in today.minusDays29()..today })
        assertEquals(today, steps.last().date)
        // Today's own total is the day's steps, as the Today screen shows them.
        val day = await<org.openstrap.app.ui.dashboard.DayUiState> { done -> queries.day(todayIso, done) }
        assertEquals(day.minuteSteps.toFloat(), steps.last().value)
    }

    private fun LocalDate.minusDays29(): LocalDate = LocalDate.fromEpochDays(toEpochDays() - 29)
}
