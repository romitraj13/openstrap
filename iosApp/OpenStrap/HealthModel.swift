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

    /// The day every screen shows, `yyyy-MM-dd` in the phone's own time zone.
    let date: String

    private let queries: HealthQueries
    private var seeded = false

    init(databasePath: String) {
        date = HealthModel.isoToday()
        queries = HealthQueries(databasePath: databasePath)
    }

    deinit {
        queries.close()
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
