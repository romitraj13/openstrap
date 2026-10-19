package org.openstrap.protocol.history

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/** The pre-multiplatform `Inflater` loop, unchanged. */
internal actual fun zlibInflate(b: ByteArray): ByteArray {
    val inflater = Inflater()
    try {
        inflater.setInput(b)
        val out = ByteArrayOutputStream(b.size * 8)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) error("truncated zlib stream")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}
