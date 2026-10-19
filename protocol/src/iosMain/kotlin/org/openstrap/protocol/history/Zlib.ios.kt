package org.openstrap.protocol.history

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_OK
import platform.zlib.uncompress

/**
 * zlib through the system library. `uncompress` needs the inflated size up front, so the buffer
 * starts at eight times the compressed size and doubles on `Z_BUF_ERROR`.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun zlibInflate(b: ByteArray): ByteArray {
    require(b.isNotEmpty()) { "truncated zlib stream" }
    val src = b.toUByteArray()
    var capacity = maxOf(256, b.size * 8)
    while (true) {
        val out = UByteArray(capacity)
        var produced = 0
        val rc = memScoped {
            val len = alloc<ULongVar>()
            len.value = capacity.toULong()
            val r = src.usePinned { s ->
                out.usePinned { o ->
                    uncompress(o.addressOf(0), len.ptr, s.addressOf(0), b.size.convert())
                }
            }
            produced = len.value.toInt()
            r
        }
        if (rc == Z_OK) return out.toByteArray().copyOf(produced)
        if (rc != Z_BUF_ERROR || capacity > 64 * 1024 * 1024) error("zlib inflate failed with status $rc")
        capacity *= 2
    }
}
