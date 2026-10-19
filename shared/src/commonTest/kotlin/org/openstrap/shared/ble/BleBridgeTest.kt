package org.openstrap.shared.ble

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

class FakeDriver : BleDriver {
    lateinit var bridge: BleBridge
    val calls = mutableListOf<String>()
    var answer: (Long, String) -> Unit = { id, _ -> bridge.onComplete(id) }
    override fun write(requestId: Long, characteristic: String, data: ByteArray, withResponse: Boolean) { calls += "write $characteristic ${data.size} $withResponse"; answer(requestId, characteristic) }
    override fun read(requestId: Long, characteristic: String) { calls += "read $characteristic"; bridge.onRead(requestId, byteArrayOf(1, 2, 3)) }
    override fun setNotify(requestId: Long, characteristic: String, enabled: Boolean) { calls += "notify $characteristic $enabled"; answer(requestId, characteristic) }
}

class BleBridgeTest {
    private val ch = "00000016-0000-3512-2118-0009af100700"
    private fun setup(timeout: Long = 10_000): Pair<FakeDriver, BleBridge> {
        val d = FakeDriver(); val b = BleBridge(d, timeout); d.bridge = b
        b.onConnected(listOf(ch.uppercase())); return d to b
    }

    @Test fun writeReadNotifyRoundTrip() = runTest {
        val (d, b) = setup()
        b.write(ch, byteArrayOf(9, 9), withResponse = true)
        assertContentEquals(byteArrayOf(1, 2, 3), b.read(ch))
        b.enableNotifications(ch)
        assertEquals(listOf("write $ch 2 true", "read $ch", "notify $ch true"), d.calls)
        assertTrue(b.hasCharacteristic(ch.uppercase()))
    }

    @Test fun failureSurfacesAsGattException() = runTest {
        val (d, b) = setup()
        d.answer = { id, _ -> b.onFailed(id, "boom") }
        assertEquals("boom", assertFailsWith<GattException> { b.write(ch, byteArrayOf(1)) }.message)
    }

    @Test fun staleCompletionIsIgnoredAndTimeoutFires() = runTest {
        val (d, b) = setup(timeout = 1_000)
        d.answer = { id, _ -> b.onComplete(id + 100) }
        assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> { b.write(ch, byteArrayOf(1)) }
    }

    @Test fun disconnectFailsPendingOp() = runTest {
        val (d, b) = setup()
        d.answer = { _, _ -> }
        val w = async { runCatching { b.write(ch, byteArrayOf(1)) } }
        yield()
        b.onDisconnected("link lost")
        assertEquals("Disconnected: link lost", w.await().exceptionOrNull()?.message)
        assertFailsWith<GattException> { b.write(ch, byteArrayOf(1)) }
    }

    @Test fun notificationsReachCollectors() = runTest {
        val (_, b) = setup()
        val got = async { b.notifications.first() }
        yield()
        assertTrue(b.onNotification(ch.uppercase(), byteArrayOf(7)))
        val n = got.await()
        assertEquals(ch, n.characteristic); assertContentEquals(byteArrayOf(7), n.value)
    }
}
