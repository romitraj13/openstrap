package org.openstrap.protocol.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {
    @Test
    fun matchesTheZlibCheckValue() {
        // The check value every CRC-32 (zlib polynomial) implementation is tested against.
        assertEquals(0xCBF43926L, crc32("123456789".encodeToByteArray()))
    }
}
