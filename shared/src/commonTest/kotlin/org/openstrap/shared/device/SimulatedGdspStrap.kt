package org.openstrap.shared.device

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.openstrap.protocol.ByteReader
import org.openstrap.protocol.ByteWriter
import org.openstrap.protocol.crypto.crc32
import org.openstrap.protocol.history.FetchCodec
import org.openstrap.protocol.history.GdspStatus
import org.openstrap.protocol.history.HistoryDataType
import kotlin.time.Instant

/**
 * The device side of GDSP (04 §1), copied from
 * `protocol/src/jvmTest/kotlin/org/openstrap/protocol/history/GdspClientTest.kt` @ 19f2865 and given
 * two changes: it serves a window of per-minute activity records instead of a recorded workout, and
 * it hands its control replies back to the caller instead of writing them to a channel. That lets
 * the whole stack (BleBridge → channel session → GdspClient → Room) be driven by one simulated strap.
 *
 * A START from [since] serves the minutes of the window that are still to come: the window ends at
 * [firstMinute] + [minutes] × 60, and a START at or after that is answered with size 0, as a strap
 * with nothing newer does. Records are 8 bytes — the Helio Strap's real activity layout (02 §3.1) —
 * and the START size field counts records, not bytes, as the strap's does.
 */
internal class SimulatedGdspStrap(
    private val firstMinute: Long,
    private val minutes: Int,
) {
    /** (type, since epoch) of every START, in order. */
    val starts = mutableListOf<Pair<Int, Long>>()
    val acks = mutableListOf<Int?>()
    val transfers = mutableListOf<Int>()

    /** Data packets (index byte + up to 19 payload bytes) waiting to go out on HUAMI(0005). */
    val packets = mutableListOf<ByteArray>()

    private var pending: ByteArray? = null
    private var pendingRecords = 0

    /** The activity this strap holds: `mode, intensity, steps, hr` and the four unknown bytes, per minute. */
    private fun records(): ByteArray {
        val out = ByteWriter()
        for (i in 0 until minutes) {
            out.u8(1).u8(1).u8(10 + i).u8(90 + i).u8(0).u8(0).u8(0).u8(0)
        }
        return out.toByteArray()
    }

    /** One control command in, the replies to it out (`10 <op> ...`). */
    fun handle(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        return when (bytes[0].toInt() and 0xFF) {
            FetchCodec.OP_QUERY_VERSION -> listOf(versionReply())
            FetchCodec.OP_START -> {
                val type = bytes[1].toInt() and 0xFF
                val since = read(bytes, 2)
                starts += type to since
                val end = firstMinute + minutes * 60L
                val from = maxOf(since, firstMinute)
                val skip = ((from - firstMinute) / 60).toInt().coerceIn(0, minutes)
                val all = if (type == HistoryDataType.ACTIVITY.code) records() else ByteArray(0)
                val nothingNewer = from >= end
                pending = if (nothingNewer) ByteArray(0) else all.copyOfRange(skip * 8, all.size)
                pendingRecords = if (nothingNewer) 0 else (minutes - skip)
                val w = ByteWriter().u8(0x10).u8(FetchCodec.OP_START).u8(GdspStatus.SUCCESS.code)
                    .u32(pendingRecords.toLong())
                write(w, if (nothingNewer) since else from)
                listOf(w.toByteArray())
            }
            FetchCodec.OP_TRANSFER -> {
                transfers += 1
                val p = pending ?: ByteArray(0)
                if (p.isNotEmpty()) {
                    p.toList().chunked(19).forEachIndexed { i, c -> packets += byteArrayOf(i.toByte()) + c.toByteArray() }
                }
                listOf(
                    ByteWriter().u8(0x10).u8(FetchCodec.OP_TRANSFER).u8(GdspStatus.SUCCESS.code)
                        .u32(crc32(p)).toByteArray(),
                )
            }
            FetchCodec.OP_ACK -> {
                acks += bytes.getOrNull(1)?.toInt()
                pending = null
                listOf(byteArrayOf(0x10, FetchCodec.OP_ACK.toByte(), GdspStatus.SUCCESS.code.toByte()))
            }
            else -> emptyList()
        }
    }

    /**
     * `10 00 01 version supportDataUpload count u16 {type hasData}`: version 2, uploads supported,
     * one type — activity. Anything else would make `HistoryTypeSpecs.plan` fetch types this strap
     * doesn't serve.
     */
    private fun versionReply(): ByteArray = ByteWriter()
        .u8(0x10).u8(FetchCodec.OP_QUERY_VERSION).u8(GdspStatus.SUCCESS.code)
        .u8(2).u8(1).u16(1).u8(HistoryDataType.ACTIVITY.code).u8(1)
        .toByteArray()

    private companion object {
        /** GDSP wall clock (02 §1.3): `year u16, month u8, day u8, hour u8, minute u8, second u8, tz i8`. */
        fun write(w: ByteWriter, epochSeconds: Long) {
            val local = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC)
            w.u16(local.year).u8(local.month.number).u8(local.day).u8(local.hour).u8(local.minute)
                .u8(local.second).u8(0)
        }

        fun read(bytes: ByteArray, at: Int): Long {
            val r = ByteReader(bytes, at)
            val year = r.u16()
            val month = r.u8()
            val day = r.u8()
            val hour = r.u8()
            val minute = r.u8()
            val second = r.u8()
            val tzQuarters = r.s8()
            return LocalDateTime(year, month, day, hour, minute, second, 0).toInstant(TimeZone.UTC).epochSeconds -
                tzQuarters * 900L
        }
    }
}
