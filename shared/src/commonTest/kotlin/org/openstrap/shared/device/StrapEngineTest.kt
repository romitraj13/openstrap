package org.openstrap.shared.device

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.openstrap.app.device.PairingErrors
import org.openstrap.protocol.auth.AuthException
import org.openstrap.protocol.hexToBytes
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.services.Gender
import org.openstrap.protocol.services.UserProfile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records what the engine tells the app. The engine delivers on Dispatchers.Main, which the tests replace. */
internal class RecordingListener : EngineListener {
    val states = mutableListOf<Pair<String, String?>>()
    val logs = mutableListOf<String>()
    val heartRates = mutableListOf<Int>()
    var firmware: String? = "<unset>"
    var battery = Int.MIN_VALUE
    var charging = false
    val ready = CompletableDeferred<Unit>()
    val failed = CompletableDeferred<String>()
    val device = CompletableDeferred<Unit>()
    val heartRate = CompletableDeferred<Int>()
    val disconnected = CompletableDeferred<String>()

    /** Every `onSync` call, in order. */
    val syncs = mutableListOf<Triple<String, String?, Long>>()
    /** Buffered so a sync that finishes before the test asks for it isn't lost. */
    private val syncEvents = Channel<Triple<String, String?, Long>>(Channel.UNLIMITED)

    /** The next sync state named [state], waiting as long as it takes. */
    suspend fun nextSync(state: String): Triple<String, String?, Long> {
        while (true) {
            val call = syncEvents.receive()
            if (call.first == state) return call
        }
    }

    override fun onState(state: String, detail: String?) {
        states += state to detail
        when (state) {
            "ready" -> ready.complete(Unit)
            "failed" -> failed.complete(detail ?: "")
            "disconnected" -> disconnected.complete(detail ?: "")
        }
    }

    override fun onDevice(firmware: String?, batteryPercent: Int, charging: Boolean) {
        this.firmware = firmware
        this.battery = batteryPercent
        this.charging = charging
        device.complete(Unit)
    }

    override fun onHeartRate(bpm: Int) {
        heartRates += bpm
        heartRate.complete(bpm)
    }

    override fun onLog(line: String) { logs += line }

    override fun onSync(state: String, detail: String?, lastSyncEpochSeconds: Long) {
        val call = Triple(state, detail, lastSyncEpochSeconds)
        syncs += call
        syncEvents.trySend(call)
    }
}

class StrapEngineTest {
    /** The fake key the protocol tests use. */
    private val key = "3c4a6f0e9b1d22e7a8c5f01b6d9e4a77"

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

    private fun harness(listener: RecordingListener) = harnesses.track(DeviceHarness(listener, key))

    private suspend fun readyHarness(listener: RecordingListener): DeviceHarness {
        val h = harness(listener)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)
        listener.ready.await()
        listener.device.await()
        return h
    }

    @Test
    fun linkUpAuthenticatesAndReportsFirmwareBatteryAndProfile() = runTest {
        val listener = RecordingListener()
        val h = harness(listener)
        // A profile set before connecting goes out after auth, as StrapManager.watchProfile does.
        h.engine.profile = UserProfile(
            birthYear = 1990, birthMonth = 1, birthDay = 1,
            gender = Gender.MALE, heightCm = 180, weightKg = 75f,
        )
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)

        listener.ready.await()
        listener.device.await()

        assertEquals(listOf("connecting", "authenticating", "ready"), listener.states.map { it.first })
        assertEquals("3.25.0.3", listener.firmware)
        assertEquals(77, listener.battery)
        assertEquals(false, listener.charging)

        // The channel handshake really ran: the strap saw the services request and the battery read.
        val endpoints = h.strap.received.replayCache.map { it.first }
        assertTrue(Endpoints.BATTERY in endpoints, "$endpoints")
        assertTrue(h.strap.phoneAcks.isNotEmpty())

        // 03 §3.5: `01 mask u32` then birthday, gender, height, weight × 200, all little-endian.
        val profile = h.strap.received.replayCache.first { it.first == Endpoints.USER_INFO }.second
        assertContentEquals("01 0f000000 c607 01 01 00 b400 983a".hexToBytes(), profile)
        h.close()
    }

    @Test
    fun linkUpAcceptsTheKeyShapesThePairScreenAllows() = runTest {
        // Upper case with separators and a 0x prefix, as a paste from Zepp's screen may look.
        val pasted = "0x3C:4A:6F:0E:9B:1D:22:E7:A8:C5:F0:1B:6D:9E:4A:77"
        val listener = RecordingListener()
        val h = harness(listener)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, pasted)
        listener.ready.await()
        assertEquals("ready", listener.states.last().first)
        h.close()
    }

    @Test
    fun theAuthKeyNeverAppearsInTheLog() = runTest {
        val listener = RecordingListener()
        val h = harness(listener)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)
        listener.ready.await()

        assertTrue(listener.logs.isNotEmpty())
        val leaks = listener.logs.filter { it.contains(key, ignoreCase = true) }
        assertTrue(leaks.isEmpty(), "the key leaked: $leaks")
        h.close()
    }

    @Test
    fun aWrongKeyReportsThePairingMessage() = runTest {
        val listener = RecordingListener()
        val h = harness(listener)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, "00000000000000000000000000000000")

        val detail = listener.failed.await()
        assertEquals(PairingErrors.authFailure(AuthException(0x26, "")).message, detail)
        assertTrue(listener.states.none { it.first == "ready" })
        h.close()
    }

    @Test
    fun liveHeartRateDeliversBpmAndStops() = runTest {
        val listener = RecordingListener()
        val h = readyHarness(listener)

        h.engine.setLiveHeartRate(true)
        // The simulated strap answers a subscription with `05 hr u16` and pushes 0x48 = 72 bpm.
        assertEquals(72, listener.heartRate.await())

        h.engine.setLiveHeartRate(false)
        val opened = h.strap.received.replayCache.map { it.first }
        assertTrue(Endpoints.HEART_RATE in opened, "$opened")
        h.close()
    }

    @Test
    fun linkDownReportsDisconnected() = runTest {
        val listener = RecordingListener()
        val h = readyHarness(listener)

        h.engine.linkDown("peripheral did disconnect")
        assertEquals("peripheral did disconnect", listener.disconnected.await())
        h.close()
    }

    /**
     * Nothing the engine started may still be running once its close() has returned.
     *
     * The session reader ACKs every chunk the strap sends, and `StrapEngine.close()` drops the link
     * first, so a reader parked in `BleBridge.op` at that moment throws `GattException("Not
     * connected")` on the engine's background worker, where nothing catches it. On iOS that kills
     * the test binary, so the failure is reported against whichever test is running then — which is
     * how a test that never connects a strap came to fail with this exception.
     *
     * To make the window certain instead of a race, the ACK for the strap's first heart-rate push is
     * held by the fake driver until the harness releases it.
     */
    @Test
    fun closeLeavesNoOperationInFlight() = runTest {
        val listener = RecordingListener()
        val h = harness(listener)
        h.driver.ready()
        h.engine.linkUp(h.driver.discovered, key)
        listener.ready.await()

        h.driver.holdAnswers = true
        h.engine.setLiveHeartRate(true)
        h.driver.heldWrite.await()

        h.close()

        assertTrue(listener.states.none { it.first == "failed" }, "${listener.states}")
        assertEquals(0, h.driver.writesInFlight, "background work outlived close()")
    }
}
