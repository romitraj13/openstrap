package org.openstrap.protocol.crypto

/**
 * CRC-32 (the zlib polynomial, reflected), the checksum the strap puts on every chunk and on a
 * whole fetch. Returns the same value as `java.util.zip.CRC32.value`: unsigned 32 bits, so the
 * result is a non-negative Long.
 */
private val CRC32_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

fun crc32(data: ByteArray): Long {
    var c = -1
    for (b in data) c = CRC32_TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
    return c.inv().toLong() and 0xFFFFFFFFL
}
