import Foundation
import OpenStrapShared
import SwiftUI

/// Debug only: `-demoData` on the command line seeds the database so the screens can be seen without
/// a strap. A release build ignores the argument however it is launched.
enum DemoData {
    static var isEnabled: Bool {
        #if DEBUG
        return CommandLine.arguments.contains("-demoData")
        #else
        return false
        #endif
    }
}

/// The four screens' data, read from the shared Kotlin `HealthQueries`. One object for the app, made
/// once beside the controller so a tab switch does not reopen the database.
///
/// Kotlin calls back on the main thread, so every assignment here lands on the main thread and
/// SwiftUI can draw it without a hop.
final class HealthModel: ObservableObject {
    @Published private(set) var today: TodaySnapshot?
    @Published private(set) var sleep: SleepSnapshot?
    @Published private(set) var heart: HeartSnapshot?
    @Published private(set) var recovery: RecoverySnapshot?
    /// The Diagnostics line the demo seed wrote; nil until `-demoData` has run.
    @Published private(set) var demoMessage: String?

    /// The local days the calendar may offer, oldest first (`ScreenQueries.daysWithData`).
    @Published private(set) var daysWithData: Set<Date> = []

    /// What the You tab edits: the same settings rows Android's `SettingsStore` reads.
    @Published private(set) var profile: UserProfile = ProfileRules.shared.blank()
    @Published private(set) var goals: Goals = GoalsRules.shared.defaults()

    /// The day every dated screen shows, `yyyy-MM-dd` in the phone's own time zone. Card U4 makes
    /// it the one selected day the four date tabs share, as Android's `DayViewModel` is shared.
    private(set) var date: String

    private let queries: HealthQueries
    /// The U3 object the screens' new state comes from: the calendar, the profile and the goals.
    /// Opened on first use, so a launch that never shows You (the design lab, a fresh install on
    /// Pair) does not pay for a second connection and its schema check.
    private var screensStorage: ScreenQueries?
    private var screens: ScreenQueries {
        if let open = screensStorage { return open }
        let open = ScreenQueries(databasePath: path)
        screensStorage = open
        return open
    }
    private let path: String
    private var seeded = false

    init(databasePath: String) {
        date = HealthModel.isoToday()
        path = databasePath
        queries = HealthQueries(databasePath: databasePath)
    }

    deinit {
        queries.close()
        screensStorage?.close()
    }

    /// Seeds the sample month once when the app was launched with `-demoData` in a debug build.
    func prepareDemoData() {
        guard DemoData.isEnabled, !seeded else { return }
        seeded = true
        queries.seedDemoData { [weak self] message in
            self?.demoMessage = message
            self?.reload()
        }
    }

    /// Asks the shared code for every screen's data. The four answers arrive separately.
    func reload() {
        queries.today(date: date) { [weak self] snapshot in
            self?.today = snapshot
        }
        queries.sleep(date: date) { [weak self] snapshot in
            self?.sleep = snapshot
        }
        queries.heart(date: date) { [weak self] snapshot in
            self?.heart = snapshot
        }
        queries.recovery(date: date) { [weak self] snapshot in
            self?.recovery = snapshot
        }
    }

    // MARK: - The one selected day (card U4)

    /// Moves every dated screen to [day], as `DayViewModel.select` does.
    func select(_ day: Date) {
        let iso = HealthModel.iso(day)
        guard iso != date else { return }
        date = iso
        reload()
    }

    /// The days the calendar lights up, as `Date`s at the start of each local day.
    func reloadDaysWithData() {
        screens.daysWithData { [weak self] list in
            self?.daysWithData = Set(list.compactMap { HealthModel.day(fromIso: $0) })
        }
    }

    // MARK: - The profile and goals (card U4)

    func reloadSettings() {
        screens.profile { [weak self] value in
            self?.profile = value
        }
        screens.goals { [weak self] value in
            self?.goals = value
        }
    }

    /// Saves and hands the write's own completion back, so a caller only reads after it landed.
    func saveProfile(_ value: UserProfile, onDone: @escaping () -> Void) {
        screens.saveProfile(profile: value) { [weak self] in
            self?.profile = value
            onDone()
        }
    }

    /// Saves the goals and calls [onDone] once the row is stored.
    func saveGoals(_ value: Goals, onDone: @escaping () -> Void) {
        screens.saveGoals(goals: value) { [weak self] in
            self?.goals = value
            onDone()
        }
    }

    private static func iso(_ day: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: day)
    }

    private static func day(fromIso value: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: value)
    }

    private static func isoToday() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}

// MARK: - Formatting shared by the screens

enum ScreenFormat {
    static let clock: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    static func time(_ epochSeconds: Int64) -> String {
        epochSeconds > 0 ? clock.string(from: Date(timeIntervalSince1970: TimeInterval(epochSeconds))) : "—"
    }

    static func date(_ epochSeconds: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(epochSeconds))
    }

    /// The same `duration` the Android app prints: "7h 20m", "7h", "45m".
    static func duration(minutes: Int) -> String {
        let hours = minutes / 60
        let rest = minutes % 60
        if hours > 0 && rest == 0 { return "\(hours)h" }
        if hours > 0 { return "\(hours)h \(rest)m" }
        return "\(rest)m"
    }

    /// The Android rating words' colours, keyed by the word `Narrative` returns.
    static func ratingColor(_ rating: String) -> Color {
        switch rating {
        case "Optimal": return .green
        case "Good": return .teal
        case "Fair": return .orange
        default: return .red
        }
    }

    /// The four stage names the shared code returns, as a colour and a short label.
    static func stageColor(_ stage: String) -> Color {
        switch stage {
        case "deep": return .indigo
        case "light": return .blue
        case "rem": return .purple
        case "awake": return .orange
        default: return .gray
        }
    }

    static func stageLabel(_ stage: String) -> String {
        switch stage {
        case "deep": return "Deep"
        case "light": return "Light"
        case "rem": return "REM"
        case "awake": return "Awake"
        default: return stage.capitalized
        }
    }
}
