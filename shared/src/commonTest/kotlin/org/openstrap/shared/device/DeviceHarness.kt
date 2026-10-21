package org.openstrap.shared.device

import kotlinx.coroutines.CompletableDeferred
import org.openstrap.app.data.db.OpenStrapIosDatabase
import org.openstrap.protocol.hexToBytes

/**
 * One engine on a simulated strap, with a [close] that really ends it.
 *
 * The point of this class is the teardown. `StrapEngine.close()` cancels its jobs without joining
 * them, and it drops the link *before* it cancels, so any coroutine still inside `BleBridge.op` at
 * that moment resumes with `GattException("Not connected")` on the engine's background worker, where
 * nothing catches it. On iOS that kills the test binary, so the failure is reported against whatever
 * test is running at the time — which is how `SyncLogReadTest.anEngineThatHasNotSyncedHasNoRows`,
 * a test that never connects a strap, once failed with a `GattException` instead of an assertion.
 *
 * [close] therefore empties the engine before it closes it: stop the strap's notification forwarders,
 * quiesce the driver (later writes answer at once, so no operation is left suspended), let the
 * engine's single worker thread run out what is already queued, and repeat, because the reader may
 * have resumed from an ACK and started one more write. Only then does the engine see its link go
 * down, and by then there is nothing inside the bridge for it to fail.
 */
internal open class DeviceHarness(
    val listener: RecordingListener,
    keyHex: String,
) {
    val strap = SimulatedStrap(keyHex.hexToBytes())
    val driver = FakeStrapDriver(strap)
    val engine = StrapEngine(driver, listener, OpenStrapIosDatabase.IN_MEMORY)

    private var closed = false

    init {
        driver.bridge = engine.bridge
    }

    /** Waits until the driver's forwarders are subscribed, so no early chunk is dropped. */
    suspend fun ready() = driver.ready()

    /**
     * A manual sync, started once the engine has finished the previous one, and waited out.
     *
     * `StrapEngine.startSync` records a request that arrives while a sync is still running under the
     * *running* sync's trigger (`startSync` at StrapEngine.kt:289 re-enters with the loop's own
     * `trigger`), so a Sync tapped in that microsecond reads back as "auto" — 2 of 40 rounds in a
     * probe. Waiting for the engine to go idle first makes the premise these tests state, a manual
     * sync after the auto one, actually true.
     */
    suspend fun syncNow(listener: RecordingListener) {
        engine.drained()
        engine.syncNow()
        listener.nextSync("done")
    }

    /** Ends the engine and the driver, and does not return until the engine's work has finished. */
    suspend fun close() {
        if (closed) return
        closed = true
        driver.stopForwarding()
        driver.quiesce()
        engine.drained()
        driver.quiesce()
        engine.drained()
        engine.close()
        driver.close()
    }
}

/** A harness whose simulated strap also serves history, so the engine auto-syncs after `ready`. */
internal class HistoryHarness(
    listener: RecordingListener,
    keyHex: String,
    first: Long,
    minutes: Int,
) : DeviceHarness(listener, keyHex) {
    val gdsp = SimulatedGdspStrap(first, minutes)

    init {
        strap.gdsp = gdsp
    }
}

/**
 * Every harness a test opened. A test that fails an assertion never reaches its own `close()`, so
 * each test class closes what is left in `@AfterTest` — otherwise the engine outlives the test and
 * its background work lands in the next one, which is the leak this exists to stop.
 */
internal class DeviceHarnesses {
    private val open = mutableListOf<DeviceHarness>()

    fun <T : DeviceHarness> track(harness: T): T {
        open += harness
        return harness
    }

    suspend fun closeAll() {
        for (harness in open) harness.close()
        open.clear()
    }
}

/**
 * Lets the engine's single worker thread run everything already queued for it: [StrapEngine.recentSyncs]
 * is launched on that same dispatcher, so its callback cannot run before the work queued ahead of it.
 */
internal suspend fun StrapEngine.drained() {
    val done = CompletableDeferred<Unit>()
    recentSyncs(0) { done.complete(Unit) }
    done.await()
}
