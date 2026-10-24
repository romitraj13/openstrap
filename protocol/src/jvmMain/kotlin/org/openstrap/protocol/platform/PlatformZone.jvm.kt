package org.openstrap.protocol.platform

import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * `java.util.TimeZone` / `java.time.ZoneRules`, i.e. exactly what `TimeSync` used before the module
 * became multiplatform, so the Android app keeps sending the same bytes.
 */
actual fun isDaylightSaving(timeZoneId: String, epochSecond: Long): Boolean =
    ZoneId.of(timeZoneId).rules.isDaylightSavings(Instant.ofEpochSecond(epochSecond))

actual fun nextDstTransition(timeZoneId: String, epochSecond: Long): DstTransition? {
    val t = ZoneId.of(timeZoneId).rules.nextTransition(Instant.ofEpochSecond(epochSecond)) ?: return null
    return DstTransition(t.instant.epochSecond, t.offsetBefore.totalSeconds, t.offsetAfter.totalSeconds)
}

actual fun zoneUsesDaylightTime(timeZoneId: String): Boolean =
    TimeZone.getTimeZone(timeZoneId).useDaylightTime()
