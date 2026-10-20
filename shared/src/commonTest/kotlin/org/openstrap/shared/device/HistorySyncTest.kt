package org.openstrap.shared.device

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.settings.SettingsRepository
import org.openstrap.protocol.hexToBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The history sync end to end, the way the phone sees it: a `SimulatedGdspStrap` behind the
 * `BleBridge`, the engine's own connect path, `HistorySync` and Room. Nothing here touches a fake
 * DAO or a fake session — the rows come out of the bundled SQLite database the engine opened.
 *
 * The strap holds ten minutes of activity at [first], one record a minute with steps 10, 11, … and
 * a heart rate of 90, 91, … The engine auto-syncs as soon as it is ready, so every test starts with
 * one sync already done.
 */
class HistorySyncTest {
    /** The fake key the protocol tests use. */
    private val key = "3c4a6f0e9b1d22e7a8c5f01b6d9e4a77"

    private val minutes = 10

    /** A whole minute, an hour ago: inside the strap's clock and inside the sync's lookback. */
    private val first = (Clock.System.now().epochSeconds - 3600) / 60 * 60
    private val end = first + minutes * 60L

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun installMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @AfterTest
    fun removeMain() = Dispatchers.resetMain()

    private class Harness(listener: RecordingListener, keyHex: String, first: Long, minutes: Int) {
        val strap = SimulatedStrap(keyHex.hexToBytes())
        val driver = FakeStrapDriver(strap)
        val engine = StrapEngine(driver, listener, OpenStrapIosDatabase.IN_MEMORY)
        val gdsp = SimulatedGdspStrap(first, minutes)

        init {
            driver.bridge = engine.bridge
            strap.gdsp = gdsp
        }

        fun close() {
            engine.close()
            driver.close()
        }
    }

    /** Connects and waits for the auto sync that follows `ready`; returns the harness and listener. */
    private suspend fun connected(): Pair<Harness, RecordingListener> {
        val listener = RecordingListener()
        val h = Harness(listener, key, first, minutes)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)
        listener.ready.await()
        listener.nextSync("done")
        return h to listener
    }

    @Test
    fun historySyncWritesTheActivityRows() = runTest {
        val (h, _) = connected()
        val rows = h.engine.db.healthDao().activityOnce(first, end - 1)
        assertEquals(minutes, rows.size, "${rows.map { it.timestamp }}")
        assertEquals((10 until 10 + minutes).toList(), rows.map { it.steps })
        assertEquals((90 until 90 + minutes).toList(), rows.map { it.heartRate })
        assertEquals((0 until minutes).map { first + it * 60L }, rows.map { it.timestamp })
        h.close()
    }

    @Test
    fun aSecondSyncWritesNoDuplicates() = runTest {
        val (h, listener) = connected()
        assertEquals(minutes, h.engine.db.healthDao().activityOnce(first, end - 1).size)

        // A manual sync starts from the cursor the first one left: the strap has nothing newer, so
        // the table is untouched.
        h.engine.syncNow()
        listener.nextSync("done")
        assertEquals(minutes, h.engine.db.healthDao().activityOnce(first, end - 1).size)
        // Every START asked for activity; the first sync took two (the window, then the empty reply
        // that ends the type) and the manual one asked from the cursor, not from the lookback.
        assertEquals(listOf(1, 1, 1), h.gdsp.starts.map { it.first })
        assertEquals(end, h.gdsp.starts.last().second)
        h.close()
    }

    @Test
    fun theCursorAdvances() = runTest {
        val (h, _) = connected()
        val settings = SettingsRepository(h.engine.db.settingsDao())
        // "gdsp_1" is the activity cursor (`gdsp_<type code>`), as SettingsStore keeps it on Android.
        assertEquals(end, settings.lastSync("gdsp_1"))
        h.close()
    }

    @Test
    fun aSyncLogRowExists() = runTest {
        val (h, listener) = connected()
        val logs = h.engine.db.syncLogDao().all()
        assertEquals(1, logs.size)
        val row = logs.single()
        assertEquals("auto", row.trigger)
        assertEquals(minutes, row.rows)
        assertNull(row.error)
        assertEquals(false, row.foreground)
        assertTrue(row.end >= row.start)

        h.engine.appVisible = true
        h.engine.syncNow()
        listener.nextSync("done")
        val after = h.engine.db.syncLogDao().all()
        assertEquals(2, after.size)
        assertEquals("manual", after.last().trigger)
        assertEquals(true, after.last().foreground)
        assertEquals(0, after.last().rows)
        assertNotNull(after.last().end)
        h.close()
    }
}
