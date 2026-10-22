import XCTest
@testable import OpenStrap

/// Card F9: the Swift side's clock, and the count it prints.
///
/// Both defects were invisible on a phone and only wrong on the CI runner. The header asked the
/// system clock whether a day was today, so the pinned demo day read "Yesterday" on every run after
/// 21 October 2026; and the log's length was printed with Android's `"${lines.size} lines"`, which
/// says "1 lines". These pin the two rules without needing a `-demoData` launch, so a regression
/// fails here and not only in a screenshot.
final class ScreenNowTests: XCTestCase {
    /// The zone the demo day is measured in (AppClock.kt `DemoDay.ZONE`).
    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    /// `yyyy-MM-dd` at midnight UTC.
    private func day(_ iso: String) -> Date {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: iso)!
    }

    // MARK: - The header's two words

    /// The demo day is 21 October 2026 (AppClock.kt `DemoDay`). Handing that day in as today must read
    /// "Today" whatever the host clock says: before card F9 `label(for:)` called
    /// `Calendar.isDateInToday`, which asked the runner's real date and answered "Yesterday".
    func testThePinnedDemoDayReadsToday() {
        let pinned = day("2026-10-21")
        XCTAssertEqual(
            DateHeader.label(for: pinned, today: pinned, calendar: utc),
            "Today",
            "the day passed as today is not called Today, so the label still asks the system clock"
        )
        XCTAssertEqual(
            DateHeader.label(for: day("2026-10-20"), today: pinned, calendar: utc),
            "Yesterday",
            "the day before the pinned day is not called Yesterday"
        )
        let after = DateHeader.label(for: day("2026-10-22"), today: pinned, calendar: utc)
        XCTAssertNotEqual(after, "Today", "a day after the pinned day was called Today")
        XCTAssertNotEqual(after, "Yesterday", "a day after the pinned day was called Yesterday")
    }

    // MARK: - The release path

    /// Without `-demoData` the accessor is exactly what the screens read before card F9: the phone's
    /// clock, the phone's zone and `Calendar.current`. The unit-test host never carries the flag, so
    /// this is the release path.
    func testWithoutDemoDataTheClockIsThePhones() {
        XCTAssertFalse(DemoData.isEnabled, "the unit-test host was launched with -demoData")
        XCTAssertEqual(ScreenNow.timeZone, TimeZone.current)
        let now = Date()
        XCTAssertEqual(
            ScreenNow.calendar.startOfDay(for: now),
            Calendar.current.startOfDay(for: now),
            "the release path's start of day is not Calendar.current's"
        )
    }

    // MARK: - The log's length

    /// Card F9's product decision: "1 line" for one, "N lines" otherwise, in both places the count is
    /// shown. Android's `"${lines.size} lines"` is the plural bug the exception exists for.
    func testTheLogCountIsSingularForOneLine() {
        XCTAssertEqual(StrapController.lineCountText(0), "0 lines")
        XCTAssertEqual(StrapController.lineCountText(1), "1 line")
        XCTAssertEqual(StrapController.lineCountText(2), "2 lines")
        XCTAssertEqual(StrapController.lineCountText(2000), "2000 lines")
    }
}
