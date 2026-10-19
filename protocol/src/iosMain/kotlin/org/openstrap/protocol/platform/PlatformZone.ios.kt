package org.openstrap.protocol.platform

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/**
 * `NSTimeZone`, the counterpart of `java.util.TimeZone`. Untestable on this server (it needs a
 * simulator); the JVM path is what the Android app and the JVM tests exercise.
 *
 * The zone object comes from kotlinx-datetime's `toNSTimeZone()` so only instance methods of
 * `NSTimeZone` are used; its ObjC class methods are not bound in this Kotlin/Native version.
 */
private fun zoneOrNull(timeZoneId: String): NSTimeZone? =
    runCatching { TimeZone.of(timeZoneId).toNSTimeZone() }.getOrNull()

actual fun isDaylightSaving(timeZoneId: String, epochSecond: Long): Boolean {
    val zone = zoneOrNull(timeZoneId) ?: return false
    return zone.isDaylightSavingTimeForDate(NSDate.dateWithTimeIntervalSince1970(epochSecond.toDouble()))
}

actual fun nextDstTransition(timeZoneId: String, epochSecond: Long): DstTransition? {
    val zone = zoneOrNull(timeZoneId) ?: return null
    val at = zone.nextDaylightSavingTimeTransitionAfterDate(
        NSDate.dateWithTimeIntervalSince1970(epochSecond.toDouble()),
    ) ?: return null
    val transition = at.timeIntervalSince1970.toLong()
    val after = zone.secondsFromGMTForDate(at).toInt()
    val before = zone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970((transition - 1).toDouble())).toInt()
    return DstTransition(transition, before, after)
}

actual fun zoneUsesDaylightTime(timeZoneId: String): Boolean {
    val zone = zoneOrNull(timeZoneId) ?: return false
    val now = NSDate()
    // Either a transition is still ahead, or the zone is in DST right now.
    return zone.nextDaylightSavingTimeTransitionAfterDate(now) != null ||
        zone.daylightSavingTimeOffsetForDate(now) > 0.0
}
