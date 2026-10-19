package org.openstrap.app.zepp

/**
 * The Zepp code the iOS app needs so far.
 *
 * Only [normalizeKey] was copied from `app/src/main/kotlin/org/openstrap/app/zepp/ZeppCloud.kt`
 * @ 19f2865. The sign-in session, the device-list parsing and the networking stay on Android until
 * card S1 ports them; nothing else from the Zepp cloud code is here.
 */
object ZeppCloud {
    /** "0x" prefix, separators and case are dropped; anything not 16 bytes of hex is ignored. */
    fun normalizeKey(raw: String?): String? {
        val hex = raw.orEmpty().trim().removePrefix("0x").removePrefix("0X").filter { it.isLetterOrDigit() }.lowercase()
        return hex.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
}
