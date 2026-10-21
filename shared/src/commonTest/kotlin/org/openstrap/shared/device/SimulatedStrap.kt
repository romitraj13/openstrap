package org.openstrap.shared.device

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.openstrap.app.ble.StrapUuids
import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.StrapLink
import org.openstrap.protocol.hexToBytes
import org.openstrap.protocol.auth.Auth
import org.openstrap.protocol.crypto.Aes
import org.openstrap.protocol.crypto.B163
import org.openstrap.protocol.crypto.ChannelCipher
import org.openstrap.protocol.services.ConnectCodec
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.services.LiveHeartRate
import org.openstrap.protocol.services.TimeSync
import org.openstrap.protocol.services.UserProfile
import org.openstrap.protocol.transport.ChunkFlags
import org.openstrap.protocol.transport.Reassembler
import org.openstrap.protocol.transport.buildChunks
import org.openstrap.protocol.u8
import org.openstrap.shared.ble.BleBridge
import org.openstrap.shared.ble.BleDriver

/**
 * The device side of docs/protocol/01, copied from
 * `protocol/src/commonTest/kotlin/org/openstrap/protocol/ChannelSessionTest.kt` @ 19f2865 and extended
 * so that it answers everything the C3 engine asks for on connect: the payload-MTU query, the time
 * module, the user profile and live heart rate. In the protocol test only version, services, auth
 * and battery were answered.
 */
internal class SimulatedStrap(private val authKey: ByteArray) : StrapLink {
    override val payloadMtu = 20
    override val controlNotifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val dataNotifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    private val keys = B163.generateKeyPair()
    private val challenge = ByteArray(16) { (it * 5 + 1).toByte() }
    private var pendingCipher: ChannelCipher? = null
    private var cipher: ChannelCipher? = null
    private var deviceSession = 0
    val phoneAcks = mutableListOf<ByteArray>()
    /** Every message the phone sent, with the flags byte of its last chunk. */
    val received = MutableSharedFlow<Triple<Int, ByteArray, Int>>(replay = 64)
    private var lastFlags = 0
    var encryptedAcks = 0
    /** What the strap reports on 0016 in answer to the MTU query (payload MTU, not the ATT MTU). */
    var reportedPayloadMtu = 20

    /** The GDSP device behind this strap: it serves per-minute activity, and answers on 0x004B. */
    var gdsp: SimulatedGdspStrap? = null

    /** GDSP data packets, on the strap's history-data characteristic (HUAMI 0005), as on hardware. */
    val historyNotifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    private val reassembler = Reassembler({ true }, { sid, data, len ->
        // The device decrypts with its own rx counter, which starts at the phone's tx counter.
        cipher!!.decrypt(sid, data, len)
    }) { msg -> received.tryEmit(Triple(msg.endpoint, msg.payload, lastFlags)); respond(msg.endpoint, msg.payload) }

    override suspend fun writeControl(data: ByteArray) {
        if (data.contentEquals(byteArrayOf(1))) {
            controlNotifications.emit(byteArrayOf(2, 2, 1))
            return
        }
        if (data.size > 1) lastFlags = data[1].toInt() and 0xFF
        // The strap's ACK has the same `04 00 sess st idx` layout as the reassembler's.
        reassembler.accept(data)?.let { ack ->
            val status = ack[3].toInt()
            encryptedAcks += if (lastFlags and ChunkFlags.ENCRYPTED != 0) 1 else 0
            controlNotifications.emit(byteArrayOf(0x04, 0, ack[2], status.toByte(), ack[4]))
        }
        flushOutbox()
    }

    override suspend fun writeData(data: ByteArray) {
        phoneAcks += data
    }

    private val outbox = mutableListOf<Pair<Int, ByteArray>>()

    private fun respond(endpoint: Int, payload: ByteArray) {
        if (payload.isEmpty()) return
        when (endpoint) {
            Endpoints.SERVICES -> outbox += endpoint to servicesReply()
            Endpoints.AUTH -> when (payload.u8(0)) {
                Auth.CMD_KEY_EXCHANGE -> {
                    val shared = B163.sharedSecret(keys.privateKey, payload.copyOfRange(4, 52))
                    pendingCipher = ChannelCipher(shared, authKey)
                    outbox += endpoint to byteArrayOf(0x10, 4, 1) + challenge + keys.publicKey
                }
                Auth.CMD_PROVE -> {
                    val c = pendingCipher!!
                    val ok = payload.copyOfRange(1, 17).contentEquals(Aes.encrypt(authKey, challenge)) &&
                        payload.copyOfRange(17, 33).contentEquals(Aes.encrypt(c.sessionKey, challenge))
                    outbox += endpoint to byteArrayOf(0x10, 5, if (ok) 1 else 0x26)
                    if (ok) cipher = mirrored(c)
                }
            }
            Endpoints.CONNECT -> when (payload.u8(0)) {
                // `01` → `02 n u16` (02 §11).
                ConnectCodec.MTU_QUERY.u8(0) -> outbox += endpoint to ByteWriter()
                    .u8(0x02).u16(reportedPayloadMtu).toByteArray()
            }
            Endpoints.BATTERY -> outbox += endpoint to "04 01 4d 00".hexToBytes()
            Endpoints.TIME -> when (payload.u8(0)) {
                TimeSync.VERSION -> outbox += endpoint to byteArrayOf(0x02, 0x02)
                0x05 -> outbox += endpoint to byteArrayOf(0x06, 0x01)
                TimeSync.NEXT_DST -> outbox += endpoint to byteArrayOf(0x08, 0x01)
            }
            Endpoints.USER_INFO -> when (payload.u8(0)) {
                UserProfile.SET -> outbox += endpoint to byteArrayOf(0x02, 0x01)
            }
            Endpoints.OPEN_APP -> when (payload.u8(0)) {
                // Open (`04 02`) answers `05 03` on hardware, close (`04 01`) answers `05 02`.
                0x04 -> outbox += endpoint to byteArrayOf(0x05, if (payload.u8(1) == 0x02) 0x03 else 0x02)
            }
            Endpoints.HEART_RATE -> when (payload.u8(0)) {
                LiveHeartRate.SUBSCRIBE.u8(0) -> {
                    outbox += endpoint to byteArrayOf(0x04, 0x01)
                    // A subscription is answered with a first push, as on hardware.
                    outbox += endpoint to byteArrayOf(0x05, 0x48, 0x00)
                }
            }
            // GDSP: the history module answers on 0x004B, its data packets go out on 0005.
            Endpoints.HISTORY -> gdsp?.let { sim ->
                sim.handle(payload).forEach { outbox += endpoint to it }
                dataOutbox += sim.packets.toList()
                sim.packets.clear()
            }
        }
    }

    /** Packets waiting for the data characteristic, sent by [flushOutbox] after the control replies. */
    private val dataOutbox = mutableListOf<ByteArray>()

    /** A later push on 0x0048 (`05 hr u16`). */
    suspend fun pushHeartRate(bpm: Int) {
        outbox += Endpoints.HEART_RATE to ByteWriter().u8(0x05).u16(bpm).toByteArray()
        flushOutbox()
    }

    /** The endpoint list C3's engine needs: the channel, battery (encrypted), time, profile, HR. */
    private fun servicesReply(): ByteArray {
        val entries = listOf(
            Endpoints.SERVICES to false,
            Endpoints.AUTH to false,
            Endpoints.BATTERY to true,
            Endpoints.CONNECT to false,
            Endpoints.USER_INFO to false,
            Endpoints.TIME to false,
            Endpoints.HEART_RATE to false,
            Endpoints.OPEN_APP to false,
        ) + if (gdsp != null) listOf(Endpoints.HISTORY to false) else emptyList()
        return ByteWriter().u8(0x04).u16(entries.size).apply {
            for ((endpoint, encrypted) in entries) {
                u16(endpoint); u8(if (encrypted) 1 else 0)
            }
        }.toByteArray()
    }

    /** Device-side cipher: its tx counter is the phone's rx counter and vice versa. */
    private fun mirrored(phoneView: ChannelCipher): ChannelCipher {
        val s = ByteArray(24)
        val tx = phoneView.rxCounter
        val rx = phoneView.txCounter
        for (i in 0..3) {
            s[i] = (tx ushr (8 * i)).toByte()
            s[4 + i] = (rx ushr (8 * i)).toByte()
        }
        // Bytes 8..23 must reproduce the same session key: S[8+i] xor K[i] == sessionKey[i].
        for (i in 0..15) s[8 + i] = (phoneView.sessionKey[i].toInt() xor authKey[i].toInt()).toByte()
        return ChannelCipher(s, authKey)
    }

    /** ConnectModule keep-alive ping (02 §11). */
    suspend fun ping() {
        outbox += Endpoints.CONNECT to ConnectCodec.PING
        flushOutbox()
    }

    private suspend fun flushOutbox() {
        // Data packets go out on 0005 before the control reply that ends the transfer, as the strap sends them.
        while (dataOutbox.isNotEmpty()) {
            historyNotifications.emit(dataOutbox.removeAt(0))
        }
        while (outbox.isNotEmpty()) {
            val (endpoint, payload) = outbox.removeAt(0)
            val sid = deviceSession++ and 0xFF
            val c = cipher
            val encrypt = c != null && endpoint == Endpoints.BATTERY
            val body = if (c != null && encrypt) c.encrypt(sid, payload) else payload
            buildChunks(endpoint, sid, body, payload.size, encrypt, payloadMtu).forEach { dataNotifications.emit(it) }
        }
    }
}

/**
 * A [BleDriver] that puts [SimulatedStrap] behind a [BleBridge]: writes go to the strap, its
 * notifications come back as GATT notifications, reads answer from a table. One strap coroutine at a
 * time, like CoreBluetooth's serial queue.
 *
 * Ending a driver matters as much as driving it: [stopForwarding] and [quiesce] let a test finish
 * with nothing of the engine's still running, and [close] cancels the driver's own scope, so no work
 * from one test can run during the next one. [holdAnswers] parks a channel-data write (the reader's
 * ACK) inside the bridge on purpose, so a test can close the engine with an operation provably
 * in flight.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class FakeStrapDriver(private val strap: SimulatedStrap) : BleDriver {
    /** The strap's history-data and history-control characteristics, as `HistorySync` names them. */
    private val HISTORY_DATA = "00000005-0000-3512-2118-0009af100700"
    private val HISTORY_CONTROL = "00000004-0000-3512-2118-0009af100700"

    lateinit var bridge: BleBridge
    val calls = mutableListOf<String>()

    /** Writes still running on [scope]; 0 means the strap has nothing left to answer. */
    private val inFlight = AtomicInt(0)

    /** How many writes are still running; a test can assert this is 0 after teardown. */
    val writesInFlight: Int get() = inFlight.load()

    /** Set by [quiesce]: later writes answer at once instead of touching the strap. */
    @Volatile private var answering = true

    /**
     * While set, a write to the channel-data characteristic stops before answering, leaving the
     * caller parked in `BleBridge.op` with an operation pending. [quiesce] releases it.
     */
    @Volatile var holdAnswers = false

    /** Completed the first time a write is held, so a test can wait for that window. */
    val heldWrite = CompletableDeferred<Unit>()
    private val releaseHeld = CompletableDeferred<Unit>()

    /** Characteristic UUIDs the app would have discovered. */
    val discovered = listOf(
        StrapUuids.MAIN_SERVICE,
        StrapUuids.CHANNEL_CONTROL,
        StrapUuids.CHANNEL_DATA,
        StrapUuids.BATTERY,
        StrapUuids.CURRENT_TIME,
        StrapUuids.DEVICE_INFO_SERVICE,
        StrapUuids.SOFTWARE_REVISION,
        StrapUuids.HARDWARE_REVISION,
        StrapUuids.SERIAL_NUMBER,
        StrapUuids.HEART_RATE_SERVICE,
        StrapUuids.HR_MEASUREMENT,
        StrapUuids.HR_CONTROL,
        HISTORY_DATA,
        HISTORY_CONTROL,
    )

    private val reads = mapOf(
        StrapUuids.SOFTWARE_REVISION to "3.25.0.3".encodeToByteArray(),
        StrapUuids.HARDWARE_REVISION to "1.0".encodeToByteArray(),
        StrapUuids.SERIAL_NUMBER to "S1234567".encodeToByteArray(),
        StrapUuids.BATTERY to byteArrayOf(0x01, 0x50),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val forwarders = listOf(
        scope.launch { strap.controlNotifications.collect { bridge.onNotification(StrapUuids.CHANNEL_CONTROL, it) } },
        scope.launch { strap.dataNotifications.collect { bridge.onNotification(StrapUuids.CHANNEL_DATA, it) } },
        scope.launch { strap.historyNotifications.collect { bridge.onNotification(HISTORY_DATA, it) } },
    )

    /** Wait until every notification forwarder is subscribed, so no early chunk is dropped. */
    suspend fun ready() {
        strap.controlNotifications.subscriptionCount.first { it > 0 }
        strap.dataNotifications.subscriptionCount.first { it > 0 }
        strap.historyNotifications.subscriptionCount.first { it > 0 }
    }

    /**
     * Stops the three notification forwarders and waits for them, so the engine's reader can no
     * longer be handed a chunk while it is being closed.
     */
    suspend fun stopForwarding() {
        forwarders.forEach { it.cancel() }
        forwarders.forEach { it.join() }
    }

    /**
     * Ends the driver's traffic: later writes answer without touching the strap, a [holdAnswers]
     * write is released, and the driver's thread is waited out until nothing is in flight.
     */
    suspend fun quiesce() {
        answering = false
        holdAnswers = false
        releaseHeld.complete(Unit)
        if (!scope.isActive) return
        repeat(4) {
            // The driver's dispatcher is single-threaded: a no-op queued after the writes waits for
            // them, and the counter catches a write that was suspended while the no-op ran.
            val idle = CompletableDeferred<Unit>()
            scope.launch { idle.complete(Unit) }
            idle.await()
            if (inFlight.load() == 0) return
        }
    }

    override fun write(requestId: Long, characteristic: String, data: ByteArray, withResponse: Boolean) {
        calls += "write $characteristic ${data.size} $withResponse"
        // Quiescing: answer at once. Nothing is left suspended in BleBridge.op, so a later
        // onDisconnected cannot fail an operation the engine is still waiting on.
        if (!answering) {
            bridge.onComplete(requestId)
            return
        }
        scope.launch {
            inFlight.fetchAndAdd(1)
            try {
                if (holdAnswers && characteristic == StrapUuids.CHANNEL_DATA) {
                    heldWrite.complete(Unit)
                    releaseHeld.await()
                }
                when (characteristic) {
                    StrapUuids.CHANNEL_CONTROL -> strap.writeControl(data)
                    StrapUuids.CHANNEL_DATA -> strap.writeData(data)
                    StrapUuids.HR_CONTROL -> Unit
                    else -> {
                        bridge.onFailed(requestId, "unexpected write to $characteristic")
                        return@launch
                    }
                }
                bridge.onComplete(requestId)
            } finally {
                inFlight.fetchAndAdd(-1)
            }
        }
    }

    override fun read(requestId: Long, characteristic: String) {
        calls += "read $characteristic"
        val value = reads[characteristic]
        if (value == null) bridge.onFailed(requestId, "unexpected read of $characteristic")
        else bridge.onRead(requestId, value)
    }

    override fun setNotify(requestId: Long, characteristic: String, enabled: Boolean) {
        calls += "notify $characteristic $enabled"
        bridge.onComplete(requestId)
    }

    /** Ends the driver for good: release anything held, stop the forwarders and cancel the scope. */
    fun close() {
        releaseHeld.complete(Unit)
        forwarders.forEach { it.cancel() }
        scope.cancel()
    }
}
