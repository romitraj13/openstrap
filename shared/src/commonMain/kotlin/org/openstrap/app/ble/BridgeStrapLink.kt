package org.openstrap.app.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.openstrap.protocol.StrapLink
import org.openstrap.shared.ble.GattPort

/**
 * Strap GATT layout (docs/protocol/01 §1.1) as lowercase 128-bit UUID strings.
 *
 * Port of `app/src/main/kotlin/org/openstrap/app/ble/GattStrapLink.kt` @ 19f2865. Android passes
 * [java.util.UUID] around; [GattPort] takes strings, and CoreBluetooth reports short forms, so every
 * value here is the full lowercase 128-bit form. The engine and the Swift driver both compare against
 * these strings.
 */
object StrapUuids {
    private fun huami(id: Int): String = "0000" + id.toString(16).padStart(4, '0') + "-0000-3512-2118-0009af100700"
    private fun sig(id: Int): String = "0000" + id.toString(16).padStart(4, '0') + "-0000-1000-8000-00805f9b34fb"

    val MAIN_SERVICE = sig(0xFEE0)
    val CHANNEL_CONTROL = huami(0x0016)
    val CHANNEL_DATA = huami(0x0017)
    val BATTERY = huami(0x0006)
    val USER_SETTINGS = huami(0x0008)
    val CURRENT_TIME = sig(0x2A2B)

    val DEVICE_INFO_SERVICE = sig(0x180A)
    val SOFTWARE_REVISION = sig(0x2A28)
    val SERIAL_NUMBER = sig(0x2A25)

    val HARDWARE_REVISION = sig(0x2A27)

    val DFU_SERVICE = huami(0x1530)
    val DFU_CONTROL = huami(0x1531)
    val DFU_DATA = huami(0x1532)

    val HEART_RATE_SERVICE = sig(0x180D)
    val HR_MEASUREMENT = sig(0x2A37)
    val HR_CONTROL = sig(0x2A39)
}

/** Adapts [GattPort] to the protocol module's [StrapLink]. Port of Android's `GattStrapLink`. */
class BridgeStrapLink(private val gatt: GattPort) : StrapLink {
    override var payloadMtu: Int = 20

    override suspend fun writeControl(data: ByteArray) = gatt.write(StrapUuids.CHANNEL_CONTROL, data)

    override suspend fun writeData(data: ByteArray) = gatt.write(StrapUuids.CHANNEL_DATA, data)

    override val controlNotifications: Flow<ByteArray> =
        gatt.notifications.filter { it.characteristic == StrapUuids.CHANNEL_CONTROL }.map { it.value }

    override val dataNotifications: Flow<ByteArray> =
        gatt.notifications.filter { it.characteristic == StrapUuids.CHANNEL_DATA }.map { it.value }

    suspend fun enableChannelNotifications() {
        gatt.enableNotifications(StrapUuids.CHANNEL_CONTROL)
        gatt.enableNotifications(StrapUuids.CHANNEL_DATA)
    }
}
