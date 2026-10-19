package org.openstrap.app.ble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.openstrap.shared.ble.GattNotification
import org.openstrap.shared.ble.GattPort
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A [GattPort] that records what the link asks of it. */
private class RecordingGatt : GattPort {
    val writes = mutableListOf<Triple<String, ByteArray, Boolean>>()
    val notifyEnabled = mutableListOf<String>()
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)
    private val _notifications = MutableSharedFlow<GattNotification>(extraBufferCapacity = 16)
    override val notifications: SharedFlow<GattNotification> = _notifications.asSharedFlow()
    override fun hasCharacteristic(characteristic: String) = true
    override suspend fun write(characteristic: String, data: ByteArray, withResponse: Boolean) {
        writes += Triple(characteristic, data, withResponse)
    }
    override suspend fun read(characteristic: String): ByteArray = ByteArray(0)
    override suspend fun enableNotifications(characteristic: String) { notifyEnabled += characteristic }
    suspend fun push(characteristic: String, value: ByteArray) = _notifications.emit(GattNotification(characteristic, value))
    suspend fun awaitSubscribers(count: Int) = _notifications.subscriptionCount.first { it >= count }
}

class BridgeStrapLinkTest {
    @Test
    fun uuidsAreLowercase128BitStrings() {
        // docs/protocol/01 §1.1. CoreBluetooth reports the short forms, so every UUID the link
        // compares against must already be the 128-bit lowercase form.
        assertEquals("0000fee0-0000-1000-8000-00805f9b34fb", StrapUuids.MAIN_SERVICE)
        assertEquals("00000016-0000-3512-2118-0009af100700", StrapUuids.CHANNEL_CONTROL)
        assertEquals("00000017-0000-3512-2118-0009af100700", StrapUuids.CHANNEL_DATA)
        assertEquals("00000006-0000-3512-2118-0009af100700", StrapUuids.BATTERY)
        assertEquals("00000008-0000-3512-2118-0009af100700", StrapUuids.USER_SETTINGS)
        assertEquals("00002a2b-0000-1000-8000-00805f9b34fb", StrapUuids.CURRENT_TIME)
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", StrapUuids.DEVICE_INFO_SERVICE)
        assertEquals("00002a28-0000-1000-8000-00805f9b34fb", StrapUuids.SOFTWARE_REVISION)
        assertEquals("00002a25-0000-1000-8000-00805f9b34fb", StrapUuids.SERIAL_NUMBER)
        assertEquals("00002a27-0000-1000-8000-00805f9b34fb", StrapUuids.HARDWARE_REVISION)
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", StrapUuids.HEART_RATE_SERVICE)
        assertEquals("00002a37-0000-1000-8000-00805f9b34fb", StrapUuids.HR_MEASUREMENT)
        assertEquals("00002a39-0000-1000-8000-00805f9b34fb", StrapUuids.HR_CONTROL)

        val all = listOf(
            StrapUuids.MAIN_SERVICE, StrapUuids.CHANNEL_CONTROL, StrapUuids.CHANNEL_DATA,
            StrapUuids.BATTERY, StrapUuids.USER_SETTINGS, StrapUuids.CURRENT_TIME,
            StrapUuids.DEVICE_INFO_SERVICE, StrapUuids.SOFTWARE_REVISION, StrapUuids.SERIAL_NUMBER,
            StrapUuids.HARDWARE_REVISION, StrapUuids.HEART_RATE_SERVICE, StrapUuids.HR_MEASUREMENT,
            StrapUuids.HR_CONTROL, StrapUuids.DFU_SERVICE, StrapUuids.DFU_CONTROL, StrapUuids.DFU_DATA,
        )
        for (uuid in all) {
            assertEquals(36, uuid.length, uuid)
            assertEquals(uuid.lowercase(), uuid, uuid)
        }
    }

    @Test
    fun writesGoToTheChannelCharacteristics() = runTest {
        val gatt = RecordingGatt()
        val link = BridgeStrapLink(gatt)
        assertEquals(20, link.payloadMtu)

        link.writeControl(byteArrayOf(1))
        link.writeData(byteArrayOf(2))
        link.enableChannelNotifications()

        assertEquals(StrapUuids.CHANNEL_CONTROL, gatt.writes[0].first)
        assertContentEquals(byteArrayOf(1), gatt.writes[0].second)
        // 0016 writes are phone→device chunks, 0017 carries the phone's ACKs: no response, like Android.
        assertEquals(false, gatt.writes[0].third)
        assertEquals(StrapUuids.CHANNEL_DATA, gatt.writes[1].first)
        assertEquals(false, gatt.writes[1].third)
        assertEquals(listOf(StrapUuids.CHANNEL_CONTROL, StrapUuids.CHANNEL_DATA), gatt.notifyEnabled)
    }

    @Test
    fun notificationsAreSplitByCharacteristic() = runTest {
        val gatt = RecordingGatt()
        val link = BridgeStrapLink(gatt)
        val control = mutableListOf<ByteArray>()
        val data = mutableListOf<ByteArray>()
        val jobs = listOf(
            launch { link.controlNotifications.collect { control += it } },
            launch { link.dataNotifications.collect { data += it } },
        )
        // Let both collectors subscribe before anything is pushed.
        gatt.awaitSubscribers(2)

        gatt.push(StrapUuids.CHANNEL_CONTROL, byteArrayOf(0x02, 0x02, 0x01))
        gatt.push(StrapUuids.CHANNEL_DATA, byteArrayOf(0x04, 0x00))
        gatt.push(StrapUuids.SOFTWARE_REVISION, byteArrayOf(0x33))

        while (control.size + data.size < 2) kotlinx.coroutines.yield()
        assertEquals(1, control.size)
        assertEquals(1, data.size)
        assertContentEquals(byteArrayOf(0x02, 0x02, 0x01), control[0])
        // The DIS characteristic is not a channel characteristic: it must not reach either flow.
        assertTrue(data.none { it.size == 1 })
        jobs.forEach { it.cancel() }
    }
}
