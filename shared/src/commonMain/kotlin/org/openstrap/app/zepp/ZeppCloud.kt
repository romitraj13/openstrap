package org.openstrap.app.zepp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The user's own Zepp sign-in, read from the watchface.zepp.com cookies after they signed in there. */
data class ZeppSession(val appToken: String, val userId: String) {
    override fun toString() = "ZeppSession(userId=$userId, appToken=…)"
}

/** One device bound to the account, as the device list reports it. */
data class BoundDevice(
    val deviceId: String,
    val macAddress: String,
    val authKey: String?,
    val deviceSource: Int,
    val productId: Int?,
    val serial: String?,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val bindingStatus: Int,
    val activeStatus: Int?,
) {
    val isHelioStrap: Boolean
        get() = deviceSource == DEVICE_SOURCE_GLOBAL || deviceSource == DEVICE_SOURCE_CHINA ||
            productId == PRODUCT_ID
    val china: Boolean get() = deviceSource == DEVICE_SOURCE_CHINA

    override fun toString() = "BoundDevice(deviceId=$deviceId, mac=$macAddress, source=$deviceSource, authKey=${if (authKey == null) "none" else "…"})"

    private companion object {
        // StrapIdentity in `app/src/main/kotlin/org/openstrap/app/firmware/FirmwareCloud.kt` @ 19f2865.
        // Inlined because firmware checks are out of S1's scope and that file also does networking.
        const val DEVICE_SOURCE_GLOBAL = 10289411
        const val DEVICE_SOURCE_CHINA = 10289410
        const val PRODUCT_ID = 157
    }
}

/** A device list fetched with [session] from [apiBase]. */
data class DeviceListing(val session: ZeppSession, val apiBase: String, val devices: List<BoundDevice>) {
    /** The Helio Strap to use: a currently bound one with a key first, then the most recent. */
    val helioStrap: BoundDevice? get() = ZeppCloud.pickHelioStrap(devices)
}

/**
 * Android's `IOException` has no counterpart in common code; this is the one failure the parsers
 * raise. [rejected] when Zepp refused the sign-in itself, which another host will not fix.
 */
class ZeppCloudException(message: String, val rejected: Boolean = false) : Exception(message)

/**
 * The pure half of `app/src/main/kotlin/org/openstrap/app/zepp/ZeppCloud.kt` @ 19f2865: the sign-in
 * constants, the cookie and device-list parsing, and the strap pick. The HTTP request itself is
 * `ZeppCloud.listDevices`/`get`, which card S1 writes in Swift
 * (`iosApp/OpenStrap/ZeppSignIn/ZeppDeviceListClient.swift`) using these constants.
 */
object ZeppCloud {
    // Same identity as the firmware check. Public because the Swift request sends them as headers.
    val APP_ID = "428135909242707968"
    val APP_VERSION_NAME = "10.9.0-play"
    val APP_VERSION_CODE = "151983"

    /**
     * Zepp's universal login, set up as watchface.zepp.com's sign-in: after the password it returns to that
     * site, whose cookies hold the token we need. The page follows the phone's language.
     */
    val SIGN_IN_URL = "https://user.zepp.com/universalLogin/index.html#/login" +
        "?project_name=watchface" +
        "&project_redirect_uri=https%3A%2F%2Fwatchface.zepp.com%2Fcreate" +
        "&platform_app=com.huami.webapp"
    val COOKIE_URLS = listOf("https://watchface.zepp.com", "https://zepp.com", "https://user.zepp.com", "https://user.huami.com")

    /** Accounts live in one region; the global host usually answers for all, the others are tried if it doesn't. */
    val API_HOSTS = listOf(
        "https://api-mifit.zepp.com/",
        "https://api-mifit-us2.zepp.com/",
        "https://api-mifit-us3.zepp.com/",
        "https://api-mifit-de2.zepp.com/",
        "https://api-mifit-sg2.zepp.com/",
    )

    /**
     * Finds the app token and user ID in a `Cookie`-style string ("a=b; c=d"). watchface.zepp.com keeps them in
     * `hm-user-login-info` (URL-encoded JSON with `token_info.app_token` / `token_info.user_id`), or in plain
     * `apptoken` / `userid` cookies.
     */
    fun sessionFromCookies(cookies: String?): ZeppSession? {
        if (cookies.isNullOrBlank()) return null
        val map = cookies.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
        }.toMap()

        map["hm-user-login-info"]?.let { raw ->
            val json = decodeJson(raw) ?: return@let
            val info = json["token_info"] as? JsonObject ?: json
            val token = info.text("app_token").ifBlank { info.text("apptoken") }
            val user = info.text("user_id").ifBlank { info.text("userid") }
            if (token.isNotBlank() && user.isNotBlank()) return ZeppSession(token, user)
        }
        val token = map["apptoken"]?.let(::urlDecode).orEmpty()
        val user = map["userid"]?.let(::urlDecode).orEmpty()
        return if (token.isNotBlank() && user.isNotBlank()) ZeppSession(token, user) else null
    }

    /** Parses the `users/{id}/devices` reply: `{"items":[{…, "additionalInfo":"{\"auth_key\":…}"}]}`. */
    @Throws(ZeppCloudException::class)
    fun parseDeviceList(body: String): List<BoundDevice> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw ZeppCloudException("Unexpected reply from Zepp's device list")
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val mac = item.text("macAddress").uppercase()
            if (!MAC.matches(mac)) return@mapNotNull null
            // additionalInfo is a JSON string; tolerate an object too.
            val extra = when (val raw = item["additionalInfo"]) {
                is JsonObject -> raw
                is JsonPrimitive -> raw.contentOrNull?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() as? JsonObject }
                else -> null
            } ?: JsonObject(emptyMap())
            BoundDevice(
                deviceId = item.text("deviceId"),
                macAddress = mac,
                authKey = normalizeKey(extra.text("auth_key")),
                deviceSource = item.int("deviceSource"),
                productId = extra.intOrNull("productId")?.takeIf { it >= 0 },
                serial = extra.text("sn").ifBlank { extra.text("SN") }.ifBlank { null },
                firmwareVersion = item.text("firmwareVersion").ifBlank { null },
                hardwareVersion = extra.text("hardwareVersion").ifBlank { null },
                bindingStatus = item.int("bindingStatus"),
                activeStatus = if (item.containsKey("activeStatus")) item.int("activeStatus") else null,
            )
        }
    }

    /** Prefers a bound strap with a usable key, then the most recently active one. */
    fun pickHelioStrap(devices: List<BoundDevice>): BoundDevice? =
        devices.filter { it.isHelioStrap }
            .sortedWith(compareByDescending<BoundDevice> { it.authKey != null }.thenByDescending { it.bindingStatus == 1 }.thenByDescending { it.activeStatus == 1 })
            .firstOrNull()

    /** "0x" prefix, separators and case are dropped; anything not 16 bytes of hex is ignored. */
    fun normalizeKey(raw: String?): String? {
        val hex = raw.orEmpty().trim().removePrefix("0x").removePrefix("0X").filter { it.isLetterOrDigit() }.lowercase()
        return hex.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    private fun decodeJson(raw: String): JsonObject? {
        var s = raw.trim().removeSurrounding("\"")
        repeat(3) { // Sometimes encoded more than once.
            val element = runCatching { Json.parseToJsonElement(s) }.getOrNull()
            if (element is JsonObject) return element
            val next = urlDecode(s)
            if (next == s) return null
            s = next
        }
        return null
    }

    /** `java.net.URLDecoder.decode(s, "UTF-8")` from the Android file: `+` is a space, `%XX` a byte. */
    private fun urlDecode(s: String): String = runCatching {
        if ('%' !in s && '+' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                '%' -> {
                    val code = if (i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
                    if (code == null) {
                        bytes.add(s[i].code.toByte())
                        i++
                    } else {
                        bytes.add(code.toByte())
                        i += 3
                    }
                }
                else -> {
                    val start = i
                    while (i < s.length && s[i] != '%' && s[i] != '+') i++
                    s.substring(start, i).encodeToByteArray().forEach { bytes.add(it) }
                }
            }
        }
        bytes.toByteArray().decodeToString()
    }.getOrDefault(s)

    // org.json's optString/optInt, which the copied parser used, over kotlinx-serialization-json.
    private fun JsonObject.text(key: String): String = when (val value = this[key]) {
        null, JsonNull -> ""
        is JsonPrimitive -> value.contentOrNull ?: value.toString()
        else -> value.toString()
    }

    private fun JsonObject.int(key: String): Int = intOrNull(key) ?: 0

    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}
