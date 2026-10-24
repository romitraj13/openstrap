package org.openstrap.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `String.format`'s `+` flag, which the Sleep tab's skin temperature asks for
 * (`"%+.1f °C"`, SleepPanels.kt:77): Java prints a plus on a non-negative number, and the Android
 * screen shows "+1.5 °C" where a plain `%.1f` would print "1.5 °C".
 */
class FormatTest {
    @Test
    fun aPlusFlagPutsTheSignOnANonNegativeNumber() {
        assertEquals("+1.5", "%+.1f".format(1.5f))
        assertEquals("+0.0", "%+.1f".format(0f))
        assertEquals("+13", "%+d".format(13L))
    }

    @Test
    fun aMinusNumberKeepsItsOwnSign() {
        assertEquals("-1.5", "%+.1f".format(-1.5f))
        assertEquals("-13", "%+d".format(-13L))
    }

    @Test
    fun withoutTheFlagNothingIsAdded() {
        assertEquals("1.5", "%.1f".format(1.5f))
        assertEquals("13", "%d".format(13L))
    }
}
