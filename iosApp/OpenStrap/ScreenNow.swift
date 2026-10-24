import Foundation
import OpenStrapShared

/*
 * Card F9: the one place the Swift screens read "now" and the zone a local day is measured in.
 *
 * The shared screens already read the pinned clock: `ScreenClock` (AppClock.kt) answers `DemoDay`
 * while `-demoData` is on — Wednesday 21 October 2026, 09:30 UTC — and the phone's own clock in
 * every other build. The Swift side kept reading the phone's, so a CI run on a later real day drew
 * the pinned day as "Yesterday" (`DateHeader.label` asked `Calendar.isDateInYesterday`) while the
 * data and every date the shared code formatted said Today, and `date.next` stayed enabled into
 * days with no data. On a phone both clocks agree, which is why only the screenshots were wrong.
 *
 * Every Swift read of the day now goes through here. A release build has no demo data, so `date` is
 * `Date()`, `timeZone` is the phone's and `calendar` is `Calendar.current`: the old code path,
 * unchanged.
 */
enum ScreenNow {
    /// The instant the Swift screens take as now.
    static var date: Date {
        guard DemoData.isEnabled else { return Date() }
        // `DemoDay.epochSeconds` is the same instant the shared code seeds and judges the day at.
        return Date(timeIntervalSince1970: TimeInterval(DemoDay.shared.epochSeconds))
    }

    /// The zone a local day is measured in: the phone's, or `DemoDay`'s (UTC) while `-demoData` is on.
    /// `DemoDay.zoneId` is the Kotlin zone's own identifier, so the two cannot drift apart.
    static var timeZone: TimeZone {
        guard DemoData.isEnabled else { return .current }
        return TimeZone(identifier: DemoDay.shared.zoneId) ?? .current
    }

    /// The calendar the Swift date arithmetic goes through. Outside a demo run this is exactly
    /// `Calendar.current`, the phone's own calendar and zone.
    static var calendar: Calendar {
        guard DemoData.isEnabled else { return .current }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }

    /// The start of the day `date` falls in, in [`timeZone`].
    static func startOfDay(_ date: Date) -> Date {
        calendar.startOfDay(for: date)
    }

    /// The day the app opens on: the phone's today, or the pinned demo day at 00:00 in its own zone.
    static var startOfToday: Date { startOfDay(date) }
}
