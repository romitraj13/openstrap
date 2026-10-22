package org.openstrap.shared.clock

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Where the shared code reads the time and the local zone.
 *
 * The Android app reads `LocalDate.now()`, `ZoneId.systemDefault()` and `System.currentTimeMillis()`
 * directly, which is right for a phone whose owner is wearing a strap. The iPhone screens are also
 * photographed by a CI runner, and a screenshot that changes with the hour of the run cannot be
 * compared with the Android one — card F7 measured Today's leading score at 29, 50 and 72 across
 * three runs of near-identical code, because the sample month was seeded up to whatever "now" the
 * runner happened to be at. [ScreenClock] is the one place those reads now go, and a `-demoData`
 * launch pins it with [DemoDay] (see `ScreenClock.pinDemo`). Release builds and real data never pin
 * it, so they keep the phone's own clock.
 */
interface AppClock {
    /** Epoch seconds now, the currency the database rows are stamped with. */
    fun nowEpochSeconds(): Long

    /** The zone a local day is measured in. */
    fun zone(): TimeZone

    /** Today's date in [zone]. */
    fun today(): LocalDate
}

/** The phone's own clock and zone: every build that is not showing demo data. */
object PhoneClock : AppClock {
    override fun nowEpochSeconds(): Long = Clock.System.now().epochSeconds
    override fun zone(): TimeZone = TimeZone.currentSystemDefault()
    override fun today(): LocalDate = Clock.System.todayIn(zone())
}

/**
 * The day `-demoData` shows, fixed: Wednesday 21 October 2026, 09:30 UTC.
 *
 * Mid-morning on a weekday, so the sample month has the morning behind it (the check-in is due, the
 * night is the newest thing that happened) and the day is old enough for Charge to have moved from
 * its waking value. The instant and the zone are constants so the same commit seeds the same rows
 * and draws the same verdicts on any runner, at any hour, in any runner time zone.
 */
object DemoDay : AppClock {
    /** The demo day as the screens read it, `yyyy-MM-dd`. */
    val isoDate: String = "2026-10-21"

    /** [isoDate] as the shared code's own date type. */
    val TODAY: LocalDate = LocalDate(2026, 10, 21)

    /** 2026-10-21T09:30:00Z. */
    val epochSeconds: Long = 1_792_575_000L

    /** UTC: the one zone the sample month is written and read in, whatever TZ the runner sets. */
    val ZONE: TimeZone = TimeZone.UTC

    override fun nowEpochSeconds(): Long = epochSeconds
    override fun zone(): TimeZone = ZONE
    override fun today(): LocalDate = TODAY
}

/**
 * The clock the shared screens read: the phone's, unless a demo run has pinned [DemoDay].
 *
 * [phone] is what the device says. A test replaces it to stand in for two CI runs at different
 * hours; the app never sets it. [demo] is non-null only while `-demoData` is on, and while it is,
 * every read answers [DemoDay] instead — so nothing in the seed or in a screen can follow the wall
 * clock.
 */
object ScreenClock : AppClock {
    var phone: AppClock = PhoneClock
    var demo: AppClock? = null

    override fun nowEpochSeconds(): Long = (demo ?: phone).nowEpochSeconds()
    override fun zone(): TimeZone = (demo ?: phone).zone()
    override fun today(): LocalDate = (demo ?: phone).today()

    /** `-demoData`: from here on the screens read the fixed demo day. Release builds never call this. */
    fun pinDemo() {
        demo = DemoDay
    }

    /** Back to the phone's clock, as a test's cleanup or a relaunch without `-demoData` wants. */
    fun unpin() {
        demo = null
    }
}
