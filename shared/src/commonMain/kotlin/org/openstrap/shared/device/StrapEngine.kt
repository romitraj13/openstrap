package org.openstrap.shared.device

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.concurrent.Volatile
import org.openstrap.app.ble.BridgeStrapLink
import org.openstrap.app.ble.StrapUuids
import org.openstrap.app.device.PairingErrors
import org.openstrap.app.zepp.ZeppCloud
import org.openstrap.protocol.ChannelSession
import org.openstrap.protocol.auth.AuthException
import org.openstrap.protocol.hexToBytes
import org.openstrap.protocol.services.BatteryInfo
import org.openstrap.protocol.services.ConnectCodec
import org.openstrap.protocol.services.Endpoints
import org.openstrap.protocol.services.LiveHeartRate
import org.openstrap.protocol.services.TimeSync
import org.openstrap.protocol.services.UserProfile
import org.openstrap.protocol.toHex
import org.openstrap.protocol.u8
import org.openstrap.shared.ble.BleBridge
import org.openstrap.shared.ble.BleDriver
import kotlin.time.Clock

/**
 * Everything the app hears from the strap. Delivered on the main thread, so SwiftUI can use it
 * directly. [onState] uses the strings `connecting`, `authenticating`, `ready`, `failed` and
 * `disconnected`; [onDevice] reports `batteryPercent` as -1 when the strap hasn't told us.
 */
interface EngineListener {
    fun onState(state: String, detail: String?)
    fun onDevice(firmware: String?, batteryPercent: Int, charging: Boolean)
    fun onHeartRate(bpm: Int)
    fun onLog(line: String)
}

/**
 * The strap, as the Swift app sees it: one object that owns the connection, the channel session and
 * live heart rate.
 *
 * Port of `StrapManager.startConnect` and `afterAuth`
 * (`app/src/main/kotlin/org/openstrap/app/device/StrapManager.kt` @ 19f2865, lines 136-270) and of
 * `startLive`/`pauseLive` (lines 513-598). What Android does with a foreground service, alarms,
 * settings, the database and history sync is not here: this card only pairs, authenticates and
 * shows battery, firmware and live heart rate.
 *
 * All engine work runs on one background thread ([Dispatchers.Default.limitedParallelism]); listener
 * callbacks are posted to [Dispatchers.Main]. Swift calls [linkUp], [linkDown] and
 * [setLiveHeartRate] from any thread, the way [BleBridge] allows.
 */
class StrapEngine(private val driver: BleDriver, private val listener: EngineListener) {
    val bridge: BleBridge = BleBridge(driver)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private var connectJob: Job? = null
    private var batteryJob: Job? = null
    private var liveJob: Job? = null
    private var disconnectWatcher: Job? = null
    private var session: ChannelSession? = null
    private var link: BridgeStrapLink? = null

    /** Set by [linkDown] before the bridge drops, so the disconnect watcher reports the same reason. */
    @Volatile private var downReason: String? = null

    private var battery = -1
    private var charging = false
    private var firmware: String? = null
    private var timeProtocol: Int? = null

    /** Set by the app when the profile changes; the strap is told after auth, as Android does (03 §3.5). */
    var profile: UserProfile = UserProfile()

    /** True between [linkUp] and [linkDown]; [close] ends the engine for good. */
    private var liveWanted = false

    /** One connect attempt: notifications, handshake, MTU, firmware, battery, time, profile. */
    fun linkUp(discoveredCharacteristics: List<String>, authKeyHex: String) {
        connectJob?.cancel()
        liveJob?.cancel()
        liveJob = null
        val key = ZeppCloud.normalizeKey(authKeyHex)
        connectJob = scope.launch {
            if (key == null) {
                log("auth key is not 32 hex characters")
                emitState("failed", "That isn't a valid auth key: it should be 32 hex characters")
                teardown()
                return@launch
            }
            try {
                emitState("connecting", null)
                bridge.onConnected(discoveredCharacteristics)
                log("connected, ${discoveredCharacteristics.size} characteristics")
                disconnectWatcher = watchDisconnect()

                val l = BridgeStrapLink(bridge).also { link = it }
                l.enableChannelNotifications()
                val s = ChannelSession(l, ::log).also { session = it }
                s.start(this)

                emitState("authenticating", null)
                s.handshake(key.hexToBytes())
                afterAuth(s, l)

                batteryJob?.cancel()
                batteryJob = watchBattery(s)
                emitState("ready", null)
                emitDevice()
                if (liveWanted) startLive()
            } catch (e: AuthException) {
                val failure = PairingErrors.authFailure(e)
                log("auth failed: ${e.message}")
                emitState("failed", failure.message)
                teardown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("connect failed: $e")
                emitState("failed", e.message ?: "Connection failed")
                teardown()
            }
        }
    }

    /** The link is gone (the peripheral disconnected, or the app stopped it). Ends the connection. */
    fun linkDown(reason: String) {
        downReason = reason
        bridge.onDisconnected(reason)
        scope.launch { finishConnection(reason) }
    }

    /** Streams the strap's heart rate (0x0048 and 2A37). [pauseLive]'s work happens either way. */
    fun setLiveHeartRate(enabled: Boolean) {
        liveWanted = enabled
        scope.launch { if (enabled) startLive() else pauseLive() }
    }

    /** Stops everything. The engine is not reusable afterwards. */
    fun close() {
        bridge.onDisconnected("closed")
        connectJob?.cancel()
        batteryJob?.cancel()
        liveJob?.cancel()
        disconnectWatcher?.cancel()
        session?.stop()
        session = null
        link = null
        scope.cancel()
    }

    // ---- Setup, as StrapManager.afterAuth ----

    private suspend fun afterAuth(s: ChannelSession, l: BridgeStrapLink) {
        // Payload MTU reported by the strap (§1.3).
        if (s.services.has(Endpoints.CONNECT)) {
            runCatching {
                val r = s.request(Endpoints.CONNECT, ConnectCodec.MTU_QUERY, 5_000) { it.u8(0) == 0x02 }
                val n = r.u8(1) or (r.u8(2) shl 8)
                if (n + 3 in 23..247) l.payloadMtu = n
                log("strap payload mtu=$n")
            }.onFailure { log("mtu query failed: $it") }
        }

        firmware = runCatching {
            bridge.read(StrapUuids.SOFTWARE_REVISION).decodeToString().trimEnd('\u0000')
        }.getOrNull()
        log("firmware=$firmware")

        runCatching {
            if (s.services.has(Endpoints.BATTERY)) {
                BatteryInfo.parseChannel(s.request(Endpoints.BATTERY, BatteryInfo.GET) { it.u8(0) == 0x04 })
            } else {
                BatteryInfo.parse(bridge.read(StrapUuids.BATTERY))
            }
        }.onFailure { log("battery read failed: $it") }.getOrNull()?.let {
            battery = it.level
            charging = it.charging == true
        }
        log("battery=$battery charging=$charging")

        timeProtocol = null
        if (s.services.has(Endpoints.TIME)) {
            timeProtocol = runCatching {
                TimeSync.parseVersion(s.request(Endpoints.TIME, TimeSync.versionRequest, 5_000) { it.isNotEmpty() && it.u8(0) == TimeSync.VERSION + 1 })
            }.onFailure { log("time version query failed: $it") }.getOrNull()
            log("time protocol $timeProtocol")
        }
        syncTime(s)
        sendProfile(s)
    }

    /** Sets the strap's clock and time zone, and tells it when DST next changes, as Android does. */
    private suspend fun syncTime(s: ChannelSession) {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        runCatching {
            if (s.services.has(Endpoints.TIME)) {
                val r = s.request(Endpoints.TIME, TimeSync.setRequest(now, zone)) { it.u8(0) == 0x06 }
                log("time set: ${r.toHex()}")
            } else {
                bridge.write(StrapUuids.CURRENT_TIME, TimeSync.struct(now, zone), withResponse = true)
                log("time set via 2A2B")
            }
        }.onFailure { log("time sync failed: $it") }
        if ((timeProtocol ?: 0) >= 2) runCatching {
            val request = TimeSync.nextDstRequest(now, zone)
            val r = s.request(Endpoints.TIME, request) { it.isNotEmpty() && it.u8(0) == TimeSync.NEXT_DST + 1 }
            log("next dst ${request.toHex()}: ${r.toHex()}")
        }.onFailure { log("dst hint failed: $it") }
    }

    /** Android sends the profile through 0x0017 whenever it changes; with nothing set there is nothing to send. */
    private suspend fun sendProfile(s: ChannelSession) {
        if (!s.services.has(Endpoints.USER_INFO)) return
        val payload = profile.channelPayload() ?: return
        runCatching {
            val r = s.request(Endpoints.USER_INFO, payload, 5_000) { it.isNotEmpty() && it.u8(0) == UserProfile.SET + 1 }
            log("profile sent: ${r.toHex()}")
        }.onFailure { log("profile send failed: $it") }
    }

    private fun watchBattery(s: ChannelSession) = scope.launch {
        s.messagesFor(Endpoints.BATTERY).collect { payload ->
            BatteryInfo.parseChannel(payload)?.let { info ->
                battery = info.level
                charging = info.charging == true
                log("battery update ${info.level}% charging=$charging")
                emitDevice()
            }
        }
    }

    /** A link lost without [linkDown] (a failed write, say) still ends the connection. */
    private fun watchDisconnect() = scope.launch {
        bridge.connected.filter { !it }.first()
        finishConnection(downReason ?: "link lost")
    }

    /** The one exit from a live connection; whichever path gets here first reports it. */
    private fun finishConnection(reason: String) {
        if (session == null && connectJob?.isActive != true) return
        connectJob?.cancel()
        connectJob = null
        log("link down: $reason")
        teardown()
        emitState("disconnected", reason)
    }

    // ---- Live heart rate, as StrapManager.startLive / pauseLive ----

    private fun startLive() {
        liveJob?.cancel()
        val s = session ?: return
        if (!bridge.connected.value) return
        liveJob = scope.launch {
            try {
                launch { s.messagesFor(Endpoints.HEART_RATE).collect { p -> LiveHeartRate.parsePush(p)?.let { emitHeartRate(it) } } }
                // Each keep-alive is answered with `05 02`; only log anything else.
                launch {
                    s.messagesFor(Endpoints.OPEN_APP).collect { p ->
                        if (!p.contentEquals(KEEP_ALIVE_REPLY)) log("live hr 001d < ${p.toHex()}")
                    }
                }
                launch {
                    bridge.notifications.filter { it.characteristic == StrapUuids.HR_MEASUREMENT }
                        .collect { n -> LiveHeartRate.parseMeasurement(n.value)?.let { emitHeartRate(it) } }
                }
                runCatching { bridge.enableNotifications(StrapUuids.HR_MEASUREMENT) }
                    .onFailure { log("2A37 subscribe failed: $it") }

                val channel = s.services.has(Endpoints.HEART_RATE)
                if (channel) {
                    log("live hr open: ${s.request(Endpoints.OPEN_APP, LiveHeartRate.OPEN, 5_000) { it.u8(0) == 0x05 }.toHex()}")
                    log("live hr subscribe: ${s.request(Endpoints.HEART_RATE, LiveHeartRate.SUBSCRIBE, 5_000) { it.u8(0) == 0x04 }.toHex()}")
                } else {
                    bridge.write(StrapUuids.HR_CONTROL, LiveHeartRate.LEGACY_START, withResponse = true)
                    log("live hr started via 2A39")
                }
                while (isActive) {
                    delay(LiveHeartRate.KEEP_ALIVE_MS)
                    if (channel) s.send(Endpoints.OPEN_APP, LiveHeartRate.KEEP_ALIVE)
                    else bridge.write(StrapUuids.HR_CONTROL, LiveHeartRate.LEGACY_KEEP_ALIVE, withResponse = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("live hr failed: $e")
            }
        }
    }

    /** Stops streaming without changing the user's preference. */
    private fun pauseLive() {
        liveJob?.cancel()
        liveJob = null
        val s = session
        scope.launch { stopLive(s) }
    }

    private suspend fun stopLive(s: ChannelSession?) {
        runCatching {
            if (s != null && s.services.has(Endpoints.HEART_RATE)) {
                s.send(Endpoints.OPEN_APP, LiveHeartRate.CLOSE)
                s.send(Endpoints.HEART_RATE, LiveHeartRate.UNSUBSCRIBE)
            } else if (bridge.connected.value) {
                bridge.write(StrapUuids.HR_CONTROL, LiveHeartRate.LEGACY_STOP, withResponse = true)
            }
        }.onFailure { log("live hr stop failed: $it") }
    }

    // ---- Plumbing ----

    /** Ends the current connection without cancelling [linkUp]'s own coroutine. */
    private fun teardown() {
        batteryJob?.cancel()
        batteryJob = null
        liveJob?.cancel()
        liveJob = null
        session?.stop()
        session = null
        link = null
    }

    private fun log(message: String) {
        scope.launch(Dispatchers.Main) { listener.onLog(message) }
    }

    private fun emitState(state: String, detail: String?) {
        scope.launch(Dispatchers.Main) { listener.onState(state, detail) }
    }

    private fun emitDevice() {
        val f = firmware
        val b = battery
        val c = charging
        scope.launch(Dispatchers.Main) { listener.onDevice(f, b, c) }
    }

    private fun emitHeartRate(bpm: Int) {
        scope.launch(Dispatchers.Main) { listener.onHeartRate(bpm) }
    }

    private companion object {
        val KEEP_ALIVE_REPLY = byteArrayOf(0x05, 0x02)
    }
}
