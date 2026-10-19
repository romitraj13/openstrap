package org.openstrap.app.zepp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Copied from `app/src/test/kotlin/org/openstrap/app/zepp/ZeppCloudTest.kt` @ 19f2865. Only the
 * key tests: C3 copies `normalizeKey` and nothing else from the Zepp code.
 */
class ZeppCloudTest {
    @Test
    fun badKeysAreIgnored() {
        assertNull(ZeppCloud.normalizeKey("1234"))
        assertNull(ZeppCloud.normalizeKey("zz112233445566778899aabbccddeeff"))
        assertNull(ZeppCloud.normalizeKey(null))
    }

    /** New in C3: the shapes the Pair screen may receive from a paste. */
    @Test
    fun prefixedSeparatedUpperCaseKeyYieldsThirtyTwoLowercaseHexCharacters() {
        val normalised = ZeppCloud.normalizeKey("0x3C:4A:6F:0E:9B:1D:22:E7:A8:C5:F0:1B:6D:9E:4A:77")
        assertEquals("3c4a6f0e9b1d22e7a8c5f01b6d9e4a77", normalised)
        assertEquals(32, normalised?.length)
        assertEquals(normalised?.lowercase(), normalised)
    }
}
