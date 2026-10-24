import XCTest
import OpenStrapShared
@testable import OpenStrap

/// Card U4: the profile and the goals go through `ScreenQueries`, and only after the write's own
/// `onDone` does the test read them back. Room writes on its own context, so a fire-and-forget
/// save can be read before it lands (the board's note from U3).
final class ScreenQueriesTests: XCTestCase {
    private var folder: URL?
    private var path: String?
    private var queries: ScreenQueries?

    override func setUpWithError() throws {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("openstrap-u4-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        self.folder = folder
        let path = folder.appendingPathComponent("openstrap.db").path
        self.path = path
        queries = ScreenQueries(databasePath: path)
    }

    override func tearDownWithError() throws {
        queries?.close()
        queries = nil
        if let folder { try? FileManager.default.removeItem(at: folder) }
    }

    func testAProfileAndGoalsGoInAndComeBackOut() throws {
        let queries = try XCTUnwrap(queries)

        // A fresh install: the settings table is empty, so both read as their defaults.
        var freshProfile: UserProfile?
        let readFresh = expectation(description: "profile read")
        queries.profile { value in
            freshProfile = value
            readFresh.fulfill()
        }
        wait(for: [readFresh], timeout: 10)
        XCTAssertNil(try XCTUnwrap(freshProfile).birthYear)

        // Build what the Profile screen builds on Save, through the same rules the screen calls.
        let today = DateFormatter()
        today.locale = Locale(identifier: "en_US_POSIX")
        today.calendar = Calendar(identifier: .gregorian)
        today.dateFormat = "yyyy-MM-dd"
        let saved = ProfileRules.shared.toProfile(
            profile: ProfileRules.shared.blank(),
            age: "34",
            sex: 1,
            imperial: false,
            weight: "72",
            heightCm: "178",
            feet: "",
            inches: "",
            activity: 2,
            betaBlocker: true,
            pregnant: false,
            maxHr: "180",
            sleepNeed: "7.5",
            todayIso: today.string(from: Date()),
            nowEpochSeconds: Int64(Date().timeIntervalSince1970)
        )

        let wroteProfile = expectation(description: "profile written")
        queries.saveProfile(profile: saved) { wroteProfile.fulfill() }
        wait(for: [wroteProfile], timeout: 10)

        var readProfile: UserProfile?
        let readBack = expectation(description: "profile read back")
        queries.profile { value in
            readProfile = value
            readBack.fulfill()
        }
        wait(for: [readBack], timeout: 10)
        let stored = try XCTUnwrap(readProfile)
        // Every field the screen edits, through the same summary line You shows.
        XCTAssertEqual(ProfileRules.shared.summary(profile: stored), "34 years, male, 72 kg, 178 cm")
        XCTAssertEqual(stored.sleepNeedMinutes?.int32Value, 450)
        XCTAssertEqual(stored.maxHeartRate?.int32Value, 180)
        XCTAssertEqual(stored.activityLevel?.int32Value, 2)
        XCTAssertTrue(stored.betaBlocker)
        XCTAssertNotNil(stored.activityAnsweredAt)
        XCTAssertNotNil(stored.bodyMeasuredAt)

        // The goals, the way the goals sheet saves them.
        let goals = GoalsRules.shared.toGoals(
            steps: "9000",
            sleepHours: "7",
            sleepMinutes: "30",
            activeHours: "12",
            current: GoalsRules.shared.defaults()
        )
        let wroteGoals = expectation(description: "goals written")
        queries.saveGoals(goals: goals) { wroteGoals.fulfill() }
        wait(for: [wroteGoals], timeout: 10)

        var readGoals: Goals?
        let readGoalsBack = expectation(description: "goals read back")
        queries.goals { value in
            readGoals = value
            readGoalsBack.fulfill()
        }
        wait(for: [readGoalsBack], timeout: 10)
        let storedGoals = try XCTUnwrap(readGoals)
        XCTAssertEqual(storedGoals.steps, 9000)
        XCTAssertEqual(storedGoals.sleepMinutes, 450)
        XCTAssertEqual(storedGoals.activeHours, 12)
    }

    /// The calendar's list is ISO days, and the screen turns them into `Date`s: today is always in
    /// it once the sample month is seeded (the UI test runs with `-demoData`).
    func testTheDayListIsReadableAcrossTheBoundary() throws {
        let queries = try XCTUnwrap(queries)
        var days: [String]?
        let answered = expectation(description: "days read")
        queries.daysWithData { list in
            days = list
            answered.fulfill()
        }
        wait(for: [answered], timeout: 10)
        // An empty database still answers, with an empty list, rather than failing the call.
        XCTAssertEqual(try XCTUnwrap(days), [])
    }
}
