package org.openstrap.shared.device

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.app.data.db.SyncLog
import org.openstrap.protocol.hexToBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Reading the sync log back into the app, the way the Diagnostics screen does it: a plain callback
 * on the main thread, because nothing that crosses into Swift may be a `suspend` function or a
 * `Flow`. The rows come out of the same database the engine wrote them to.
 */
class SyncLogReadTest {
    /** The fake key the protocol tests use. */
    private val key = "3c4a6f0e9b1d22e7a8c5f01b6d9e4a77"

    private val minutes = 4

    /** A whole minute, an hour ago: inside the strap's clock and inside the sync's lookback. */
    private val first = (Clock.System.now().epochSeconds - 3600) / 60 * 60

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

    /** Connects and waits for the auto sync that follows `ready`. */
    private suspend fun connected(listener: RecordingListener): Harness {
        val h = Harness(listener, key, first, minutes)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)
        listener.ready.await()
        listener.nextSync("done")
        return h
    }

    /** Waits for the callback: the test asks the engine the way the app does. */
    private suspend fun StrapEngine.recent(limit: Int): List<SyncLog> {
        val answer = CompletableDeferred<List<SyncLog>>()
        recentSyncs(limit) { answer.complete(it) }
        return answer.await()
    }

    @Test
    fun anEngineThatHasNotSyncedHasNoRows() = runTest {
        val listener = RecordingListener()
        val h = Harness(listener, key, first, minutes)
        assertTrue(h.engine.recent(50).isEmpty())
        h.close()
    }

    @Test
    fun recentSyncsReturnsTheNewestRowsFirst() = runTest {
        val listener = RecordingListener()
        val h = connected(listener)
        h.engine.appVisible = true
        h.engine.syncNow()
        listener.nextSync("done")

        val rows = h.engine.recent(50)
        assertEquals(2, rows.size)
        assertEquals(listOf("manual", "auto"), rows.map { it.trigger })
        assertEquals(listOf(true, false), rows.map { it.foreground })
        assertTrue(rows[0].start >= rows[1].start, "${rows.map { it.start }}")
        assertEquals(minutes, rows[1].rows)
        assertEquals(null, rows[0].error)
        h.close()
    }

    @Test
    fun recentSyncsStopsAtTheLimit() = runTest {
        val listener = RecordingListener()
        val h = connected(listener)
        h.engine.syncNow()
        listener.nextSync("done")

        val newest = h.engine.recent(1)
        assertEquals(1, newest.size)
        assertEquals("manual", newest.single().trigger)
        h.close()
    }
}
