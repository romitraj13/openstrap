package org.openstrap.protocol

/** Little-endian helpers; the strap's wire format is little-endian throughout. */

fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF
fun ByteArray.u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)
fun ByteArray.u32(at: Int): Long = (u16(at).toLong()) or (u16(at + 2).toLong() shl 16)
fun ByteArray.s8(at: Int): Int = this[at].toInt()

/** Hex digits for [v], zero-padded to at least [width] characters (the old `%0<width>x`). */
fun hex(v: Long, width: Int = 8, upper: Boolean = false): String {
    val digits = if (upper) "0123456789ABCDEF" else "0123456789abcdef"
    val out = StringBuilder(width)
    var x = v
    do {
        out.append(digits[(x and 0xF).toInt()])
        x = x ushr 4
    } while (x != 0L)
    while (out.length < width) out.append('0')
    return out.reverse().toString()
}

/** [v] read as unsigned 32 bits, the way `%02x` formats an int. */
fun hex(v: Int, width: Int = 2, upper: Boolean = false): String = hex(v.toLong() and 0xFFFFFFFFL, width, upper)

fun ByteArray.toHex(): String = joinToString("") { hex(it.toInt() and 0xFF, 2) }

fun String.hexToBytes(): ByteArray {
    val clean = filter { !it.isWhitespace() && it != ':' }.removePrefix("0x")
    require(clean.length % 2 == 0) { "Odd hex length" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/** Small growable buffer for building little-endian payloads, and for collecting incoming bytes. */
class ByteWriter(initialCapacity: Int = 32) {
    private var buf = ByteArray(if (initialCapacity > 0) initialCapacity else 32)
    private var length = 0

    /** Bytes written so far. */
    val size: Int get() = length

    private fun ensure(n: Int) {
        if (length + n <= buf.size) return
        var cap = buf.size
        while (cap < length + n) cap *= 2
        buf = buf.copyOf(cap)
    }

    fun u8(v: Int) = apply { ensure(1); buf[length++] = v.toByte() }
    fun u16(v: Int) = apply { u8(v); u8(v ushr 8) }
    fun u32(v: Long) = apply { u16((v and 0xFFFF).toInt()); u16(((v ushr 16) and 0xFFFF).toInt()) }
    fun u64(v: Long) = apply { u32(v and 0xFFFFFFFFL); u32(v ushr 32) }
    fun bytes(b: ByteArray) = apply { write(b, 0, b.size) }

    /** Appends [count] bytes of [b] starting at [offset]. */
    fun write(b: ByteArray, offset: Int = 0, count: Int = b.size - offset) = apply {
        ensure(count)
        b.copyInto(buf, length, offset, offset + count)
        length += count
    }

    fun reset() {
        length = 0
    }

    fun toByteArray(): ByteArray = buf.copyOf(length)
}

/** Sequential little-endian reader. */
class ByteReader(private val data: ByteArray, var pos: Int = 0) {
    val remaining get() = data.size - pos
    fun u8(): Int = data.u8(pos).also { pos += 1 }
    fun s8(): Int = data.s8(pos).also { pos += 1 }
    fun u16(): Int = data.u16(pos).also { pos += 2 }
    fun u32(): Long = data.u32(pos).also { pos += 4 }
    fun bool(): Boolean = u8() != 0
    fun bytes(n: Int): ByteArray = data.copyOfRange(pos, pos + n).also { pos += n }
    fun rest(): ByteArray = bytes(remaining)
}
