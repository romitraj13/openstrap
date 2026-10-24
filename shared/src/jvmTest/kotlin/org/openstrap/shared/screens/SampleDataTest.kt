package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.shared.clock.DemoDay
import org.openstrap.shared.clock.ScreenClock

/**
 * `SampleData.load`: the sample month the JVM query tests seed. Card U8 moved `-demoData` to
 * `DemoData.fill` (Android's emulator seed, which has a workout), so this file now pins only the
 * tests' seed; a second launch must still not write the month a second time.
 *
 * Card F7: the tests read the fixed demo day, exactly as a `-demoData` run does, so they do not
 * depend on the hour or the zone of the machine running them.
 */
class SampleDataTest {
    private val zone = DemoDay.ZONE
    private val directory = File("build/tmp/sample-data-test")
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

    private fun dao(): HealthDao {
        val path = File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath
        files += File(path)
        val db = OpenStrapIosDatabase.open(path).also { database = it }
        return db.healthDao()
    }

    private suspend fun todayMinutes(dao: HealthDao): Int {
        val today = DemoDay.TODAY
        val day = DayWindow.of(today, zone)
        return dao.activity(day.from, day.to).first().size
    }

    @Test
    fun `seeding writes the sample month`() = runTest {
        val dao = dao()
        SampleData.load(dao, days = 30)
        val minutes = todayMinutes(dao)
        assertTrue(minutes > 0, "the seed wrote nothing for today")
        assertTrue(dao.sleepSessions(0L, Long.MAX_VALUE).first().isNotEmpty())
    }

    @Test
    fun `seeding a database that already holds today's wear adds nothing`() = runTest {
        val dao = dao()
        SampleData.load(dao, days = 30)
        val once = todayMinutes(dao)
        val sessions = dao.sleepSessions(0L, Long.MAX_VALUE).first().size

        // The UI test's second launch: the same month must not be written again on top of the first.
        SampleData.load(dao, days = 30)

        assertEquals(once, todayMinutes(dao), "the second seed doubled today's activity")
        assertEquals(sessions, dao.sleepSessions(0L, Long.MAX_VALUE).first().size, "the second seed doubled the nights")
    }
}
