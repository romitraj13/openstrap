package org.openstrap.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals

class BytesTest {
    private fun roundTrip(size: Int) {
        val bytes = ByteArray(size) { ((it * 31 + 7) and 0xff).toByte() }
        assertContentEquals(bytes, bytes.toNSData().toKotlinByteArray())
    }

    @Test
    fun zeroBytesRoundTrip() = roundTrip(0)

    @Test
    fun oneByteRoundTrips() = roundTrip(1)

    @Test
    fun fourThousandNinetySixBytesRoundTrip() = roundTrip(4096)
}
