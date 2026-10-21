package org.openstrap.shared.device

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.openstrap.app.data.db.SyncLog
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

    /** Every harness this test opened; a failed assertion skips its close(), so tearDown closes it. */
    private val harnesses = DeviceHarnesses()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun installMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() {
        try {
            // Closing a harness is a suspend function: it waits for the engine's work to finish.
            runTest { harnesses.closeAll() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun harness(listener: RecordingListener) =
        harnesses.track(HistoryHarness(listener, key, first, minutes))

    /** Connects and waits for the auto sync that follows `ready`. */
    private suspend fun connected(listener: RecordingListener): DeviceHarness {
        val h = harness(listener)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)
        listener.ready.await()
        listener.nextSync("done")
        // The auto sync's own coroutine releases its lock just after it posts `done`; wait it out so
        // a test that asks for a manual sync next is not racing it.
        h.engine.drained()
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
        val h = harness(listener)
        assertTrue(h.engine.recent(50).isEmpty())
        h.close()
    }

    @Test
    fun recentSyncsReturnsTheNewestRowsFirst() = runTest {
        val listener = RecordingListener()
        val h = connected(listener)
        h.engine.appVisible = true
        h.syncNow(listener)

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
        h.syncNow(listener)

        val newest = h.engine.recent(1)
        assertEquals(1, newest.size)
        assertEquals("manual", newest.single().trigger)
        h.close()
    }
}
