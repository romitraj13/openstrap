package org.openstrap.shared.screens

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.OpenStrapIosDatabase
import kotlin.time.Clock

/**
 * `SampleData.load`: the demo month the `-demoData` launch argument writes. Card U5 relaunches the
 * app once per screen state, so a second launch must not write the month a second time.
 */
class SampleDataTest {
    private val zone = TimeZone.currentSystemDefault()
    private val directory = File("build/tmp/sample-data-test")
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

    private fun dao(): HealthDao {
        val path = File(directory, "openstrap-${files.size}-${System.nanoTime()}.db").absolutePath
        files += File(path)
        val db = OpenStrapIosDatabase.open(path).also { database = it }
        return db.healthDao()
    }

    private suspend fun todayMinutes(dao: HealthDao): Int {
        val today = Clock.System.todayIn(zone)
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
