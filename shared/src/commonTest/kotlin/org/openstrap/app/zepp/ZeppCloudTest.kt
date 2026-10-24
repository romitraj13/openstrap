package org.openstrap.app.zepp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Copied from `app/src/test/kotlin/org/openstrap/app/zepp/ZeppCloudTest.kt` @ 19f2865 (all 10
 * tests). Only the imports changed: `org.junit.Assert` became `kotlin.test`, the expected-throws
 * test became `assertFailsWith`, and `java.net.URLEncoder` was replaced by [urlEncode] below.
 */
class ZeppCloudTest {
    // Made-up values in the shape the device list returns.
    private val list = """
        {"items":[
          {"deviceId":"AA11","macAddress":"D1:22:33:44:55:66","deviceType":0,"deviceSource":8,"bindingStatus":1,
           "firmwareVersion":"1.0.0","additionalInfo":"{\"auth_key\":\"0x00112233445566778899aabbccddeeff\",\"sn\":\"OTHER1\",\"productId\":42}"},
          {"deviceId":"BB22","macAddress":"c7:2b:1f:00:00:01","deviceType":0,"deviceSource":10289411,"bindingStatus":0,"activeStatus":0,
           "additionalInfo":"{\"sn\":\"OLD\",\"productId\":157}"},
          {"deviceId":"CC33","macAddress":"C7:2B:1F:00:00:02","deviceType":0,"deviceSource":10289411,"bindingStatus":1,"activeStatus":1,
           "firmwareVersion":"3.1.5","additionalInfo":"{\"auth_key\":\"ffeeddccbbaa99887766554433221100\",\"sn\":\"HELIO1\",\"productId\":157,\"hardwareVersion\":\"0.1.0\"}"},
          {"deviceId":"DD44","macAddress":"not-a-mac","deviceSource":10289411,"additionalInfo":"{}"}
        ]}
    """.trimIndent()

    @Test
    fun parsesItemsAndAdditionalInfo() {
        val devices = ZeppCloud.parseDeviceList(list)
        assertEquals(3, devices.size) // the one without a valid MAC is dropped
        val other = devices[0]
        assertEquals("00112233445566778899aabbccddeeff", other.authKey)
        assertEquals(42, other.productId)
        assertFalse(other.isHelioStrap)

        val strap = devices[2]
        assertEquals("CC33", strap.deviceId)
        assertEquals("C7:2B:1F:00:00:02", strap.macAddress)
        assertEquals("ffeeddccbbaa99887766554433221100", strap.authKey)
        assertEquals("HELIO1", strap.serial)
        assertEquals("3.1.5", strap.firmwareVersion)
        assertEquals("0.1.0", strap.hardwareVersion)
        assertTrue(strap.isHelioStrap)
        assertFalse(strap.china)

        assertEquals("C7:2B:1F:00:00:01", devices[1].macAddress) // upper-cased
        assertNull(devices[1].authKey)
    }

    @Test
    fun picksBoundHelioStrapWithKey() {
        assertEquals("CC33", ZeppCloud.pickHelioStrap(ZeppCloud.parseDeviceList(list))?.deviceId)
    }

    @Test
    fun chinaSourceAndObjectAdditionalInfo() {
        val body = """{"items":[{"deviceId":"X","macAddress":"C7:2B:1F:55:24:AB","deviceSource":10289410,"bindingStatus":1,
            "additionalInfo":{"auth_key":"00112233445566778899AABBCCDDEEFF"}}]}"""
        val strap = ZeppCloud.pickHelioStrap(ZeppCloud.parseDeviceList(body))!!
        assertTrue(strap.china)
        assertEquals("00112233445566778899aabbccddeeff", strap.authKey)
    }

    @Test
    fun noStrapAndEmptyLists() {
        assertNull(ZeppCloud.pickHelioStrap(ZeppCloud.parseDeviceList("""{"items":[]}""")))
        assertTrue(ZeppCloud.parseDeviceList("{}").isEmpty())
        assertNull(ZeppCloud.pickHelioStrap(ZeppCloud.parseDeviceList(list).take(1)))
    }

    @Test
    fun rejectsNonJson() {
        assertFailsWith<ZeppCloudException> {
            ZeppCloud.parseDeviceList("<html>")
        }
    }

    @Test
    fun badKeysAreIgnored() {
        assertNull(ZeppCloud.normalizeKey("1234"))
        assertNull(ZeppCloud.normalizeKey("zz112233445566778899aabbccddeeff"))
        assertNull(ZeppCloud.normalizeKey(null))
    }

    @Test
    fun sessionFromLoginInfoCookie() {
        val info = """{"token_info":{"app_token":"tok-abc","user_id":"1234567"},"user_info":{}}"""
        val cookies = "_ga=GA1.2; hm-user-login-info=${urlEncode(info)}; lang=en"
        assertEquals(ZeppSession("tok-abc", "1234567"), ZeppCloud.sessionFromCookies(cookies))
    }

    @Test
    fun sessionFromPlainCookies() {
        assertEquals(ZeppSession("tok-xyz", "42"), ZeppCloud.sessionFromCookies("apptoken=tok-xyz; userid=42"))
    }

    @Test
    fun noSessionWithoutBoth() {
        assertNull(ZeppCloud.sessionFromCookies(null))
        assertNull(ZeppCloud.sessionFromCookies("apptoken=tok-xyz"))
        assertNull(ZeppCloud.sessionFromCookies("hm-user-login-info=%7B%7D"))
    }

    @Test
    fun toStringHidesSecrets() {
        assertFalse(ZeppSession("secret-token", "1").toString().contains("secret-token"))
        val strap = ZeppCloud.parseDeviceList(list)[2]
        assertFalse(strap.toString().contains(strap.authKey!!))
    }
}

private const val HEX = "0123456789ABCDEF"

/**
 * `java.net.URLEncoder.encode(s, "UTF-8")` from the Android test, which `shared/` cannot use.
 * The same unreserved characters are left alone and every other byte becomes `%XX`.
 */
private fun urlEncode(s: String): String = buildString {
    for (byte in s.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        val unreserved = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-_.!~*'()"
        if (unreserved) {
            append(char)
        } else {
            append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
        }
    }
}
