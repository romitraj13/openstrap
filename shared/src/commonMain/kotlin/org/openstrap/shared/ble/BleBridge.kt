package org.openstrap.shared.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class GattException(message: String) : Exception(message)

/** A characteristic notification. [characteristic] is the full lowercase 128-bit UUID string. */
class GattNotification(val characteristic: String, val value: ByteArray)

/** What the shared sync code needs from a GATT connection. Mirrors the Android app's GattClient. */
interface GattPort {
    val connected: StateFlow<Boolean>
    val notifications: SharedFlow<GattNotification>
    fun hasCharacteristic(characteristic: String): Boolean
    suspend fun write(characteristic: String, data: ByteArray, withResponse: Boolean = false)
    suspend fun read(characteristic: String): ByteArray
    suspend fun enableNotifications(characteristic: String)
}

/**
 * Implemented in Swift over CoreBluetooth. Each call carries a requestId; Swift must answer every call
 * exactly once through BleBridge.onComplete / onRead / onFailed. Calls may arrive on any thread.
 */
interface BleDriver {
    fun write(requestId: Long, characteristic: String, data: ByteArray, withResponse: Boolean)
    fun read(requestId: Long, characteristic: String)
    fun setNotify(requestId: Long, characteristic: String, enabled: Boolean)
}

/** Kotlin side of the Swift/Kotlin BLE boundary. One operation in flight at a time, like Android. */
@OptIn(ExperimentalAtomicApi::class)
class BleBridge(private val driver: BleDriver, private val timeoutMs: Long = 10_000) : GattPort {
    private class Pending(val id: Long, val result: CompletableDeferred<ByteArray>)

    private val opLock = Mutex()
    private val nextId = AtomicLong(1)
    private val pending = AtomicReference<Pending?>(null)
    private val characteristics = AtomicReference<Set<String>>(emptySet())

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected

    private val _notifications = MutableSharedFlow<GattNotification>(
        extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val notifications: SharedFlow<GattNotification> = _notifications

    // ---- Called from Swift ----
    /** After service discovery: every characteristic UUID found, lowercase. */
    fun onConnected(discovered: List<String>) {
        characteristics.store(discovered.map { it.lowercase() }.toSet())
        _connected.value = true
    }

    fun onDisconnected(reason: String) {
        _connected.value = false
        characteristics.store(emptySet())
        pending.load()?.result?.completeExceptionally(GattException("Disconnected: $reason"))
    }

    fun onComplete(requestId: Long) = finish(requestId) { it.complete(ByteArray(0)) }
    fun onRead(requestId: Long, value: ByteArray) = finish(requestId) { it.complete(value) }
    fun onFailed(requestId: Long, message: String) = finish(requestId) { it.completeExceptionally(GattException(message)) }

    /** Returns false if the buffer was full and the notification was dropped (logged by the caller). */
    fun onNotification(characteristic: String, value: ByteArray): Boolean =
        _notifications.tryEmit(GattNotification(characteristic.lowercase(), value))

    private fun finish(requestId: Long, action: (CompletableDeferred<ByteArray>) -> Unit) {
        val p = pending.load() ?: return
        if (p.id == requestId) action(p.result)
    }

    // ---- GattPort, used by shared Kotlin code ----
    override fun hasCharacteristic(characteristic: String) = characteristic.lowercase() in characteristics.load()

    override suspend fun write(characteristic: String, data: ByteArray, withResponse: Boolean) {
        op { id -> driver.write(id, characteristic.lowercase(), data, withResponse) }
    }

    override suspend fun read(characteristic: String): ByteArray =
        op { id -> driver.read(id, characteristic.lowercase()) }

    override suspend fun enableNotifications(characteristic: String) {
        op { id -> driver.setNotify(id, characteristic.lowercase(), true) }
    }

    private suspend fun op(start: (Long) -> Unit): ByteArray = opLock.withLock {
        if (!_connected.value) throw GattException("Not connected")
        val p = Pending(nextId.fetchAndAdd(1), CompletableDeferred())
        pending.store(p)
        try {
            start(p.id)
            withTimeout(timeoutMs) { p.result.await() }
        } finally {
            pending.store(null)
        }
    }
}
