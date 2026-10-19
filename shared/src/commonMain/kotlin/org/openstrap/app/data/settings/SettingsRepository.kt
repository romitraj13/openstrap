package org.openstrap.app.data.settings

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.db.KeyValue
import org.openstrap.app.data.db.SettingsDao
import kotlin.time.Clock

/** The strap the user paired: its address and name. The auth key never lives in this table. */
data class PairedDevice(val address: String, val authKeyHex: String, val name: String)

/** What the user told us about themselves; every field is optional. Used by on-phone calculations and sent to the strap. */
data class UserProfile(
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val weightKg: Float? = null,
    val heightCm: Int? = null,
    /** Show pounds and feet/inches; null follows the phone's region. Values are always stored metric. */
    val imperial: Boolean? = null,
    /** The user's own sleep need, 420-540 min; null uses the default for their age. */
    val sleepNeedMinutes: Int? = null,
    /** A maximum heart rate from a test; overrides the age estimate. */
    val maxHeartRate: Int? = null,
    /** Usual physical activity, level 1-5 as index 0-4 (Jurca 2005's question), and when it was last answered. */
    val activityLevel: Int? = null,
    val activityAnsweredAt: Long? = null,
    /** When weight and height were last confirmed (epoch seconds); the VO2max estimate wants them under 90 days old. */
    val bodyMeasuredAt: Long? = null,
    /** Resting heart rate doesn't reflect fitness on beta-blockers or in pregnancy, so VO2max is withheld. */
    val betaBlocker: Boolean = false,
    val pregnant: Boolean = false,
) {
    enum class Sex { Female, Male }

    val usesImperial: Boolean
        get() = imperial ?: platformUsesImperial()

    companion object {
        /** Regions that use pounds and feet day to day. */
        val IMPERIAL_REGIONS = setOf("US", "LR", "MM")
        const val KG_PER_LB = 0.45359237f
        const val CM_PER_INCH = 2.54f
    }

    fun age(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Int? = birthYear?.let { today.year - it }
    val isEmpty get() = birthYear == null && sex == null && weightKg == null && heightCm == null
}

/**
 * The settings `HistorySync` and `StrapEngine` read, stored as rows of the `key_value` table
 * instead of Android's DataStore.
 *
 * Only what those two classes ask for is here. No secret is stored: the strap's auth key is in the
 * iOS Keychain (`KeychainSecretStore`, card C3), so [pairedDevice] only knows the address and name
 * — `authKeyHex` is empty and the caller supplies the key from the Keychain.
 */
class SettingsRepository(private val dao: SettingsDao) {

    /** Last synced timestamp (epoch seconds) for a history data type. */
    suspend fun lastSync(type: String): Long? = dao.value(LAST_SYNC_PREFIX + type)?.toLongOrNull()

    suspend fun setLastSync(type: String, epochSeconds: Long) {
        dao.put(KeyValue(LAST_SYNC_PREFIX + type, epochSeconds.toString()))
    }

    suspend fun profile(): UserProfile = UserProfile(
        birthYear = dao.value("profile_birth_year")?.toIntOrNull(),
        sex = dao.value("profile_sex")?.let { v -> UserProfile.Sex.entries.firstOrNull { it.name == v } },
        weightKg = dao.value("profile_weight_kg")?.toFloatOrNull(),
        heightCm = dao.value("profile_height_cm")?.toIntOrNull(),
        imperial = dao.value("profile_imperial")?.toBooleanStrictOrNull(),
        sleepNeedMinutes = dao.value("profile_sleep_need_minutes")?.toIntOrNull(),
        maxHeartRate = dao.value("profile_max_hr")?.toIntOrNull(),
        activityLevel = dao.value("profile_activity_level")?.toIntOrNull(),
        activityAnsweredAt = dao.value("profile_activity_answered_at")?.toLongOrNull(),
        bodyMeasuredAt = dao.value("profile_body_measured_at")?.toLongOrNull(),
        betaBlocker = dao.value("profile_beta_blocker")?.toBooleanStrictOrNull() ?: false,
        pregnant = dao.value("profile_pregnant")?.toBooleanStrictOrNull() ?: false,
    )

    /** Whether live heart rate starts automatically on connect. On by default. */
    suspend fun liveHeartRate(): Boolean = dao.value("live_hr")?.toBooleanStrictOrNull() ?: true

    suspend fun setLiveHeartRate(enabled: Boolean) {
        dao.put(KeyValue("live_hr", enabled.toString()))
    }

    /** Null until a strap has been paired. The key itself is not in this table. */
    suspend fun pairedDevice(): PairedDevice? {
        val address = dao.value("device_address") ?: return null
        return PairedDevice(address, authKeyHex = "", dao.value("device_name") ?: "Helio Strap")
    }

    private companion object {
        /** Android's key is `last_sync_<type>` in DataStore; the prefix carries over unchanged. */
        const val LAST_SYNC_PREFIX = "last_sync_"
    }
}
