package org.openstrap.protocol.platform

/**
 * The three time-zone facts the strap's time sync needs and kotlinx-datetime does not expose: whether
 * a moment is in daylight saving, the zone's next DST transition, and whether the zone uses DST at all.
 *
 * The JVM implementation delegates to `java.util.TimeZone`, so the Android app sends exactly the bytes
 * it sent before the multiplatform move; iOS uses `NSTimeZone`.
 */
data class DstTransition(
    /** The instant the offset changes, in epoch seconds. */
    val epochSecond: Long,
    /** Zone offset just before the transition, in seconds east of UTC. */
    val offsetBeforeSeconds: Int,
    /** Zone offset from the transition on, in seconds east of UTC. */
    val offsetAfterSeconds: Int,
)

/** True when [epochSecond] falls in daylight saving time according to [timeZoneId] (an IANA id). */
expect fun isDaylightSaving(timeZoneId: String, epochSecond: Long): Boolean

/** The first DST transition after [epochSecond], or null when the zone has none (or never ends DST). */
expect fun nextDstTransition(timeZoneId: String, epochSecond: Long): DstTransition?

/** True when the zone uses daylight saving time at all (not for a one-off standard-offset change). */
expect fun zoneUsesDaylightTime(timeZoneId: String): Boolean
