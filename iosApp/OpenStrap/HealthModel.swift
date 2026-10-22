import Foundation
import OpenStrapShared
import SwiftUI

/// Debug only: `-demoData` on the command line seeds the database so the screens can be seen without
/// a strap. A release build ignores the argument however it is launched.
///
/// Card F7: demo mode also pins the shared clock to one fixed day (`DemoDay`), so a CI run at 03:00
/// and one at 13:00 seed the same month and draw the same verdicts. Release builds and real data
/// never pin it.
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

    /// The selected day as `DayViewModel.state` builds it (`ScreenQueries.day`). Card U5's Today
    /// screen reads this; nil until that screen first asks, so a launch that never shows Today
    /// pays for none of the day's dozen reads.
    @Published private(set) var dayState: DayUiState?
    /// Readiness and Charge for the selected day, as the Recovery tab reads them.
    @Published private(set) var recoveryResult: RecoveryResult?
    /// Every workout OpenStrap knows, newest first: Today picks the selected day's out of it.
    @Published private(set) var workoutList: [WorkoutAnalysis] = []
    /// The selected day's morning check-in, 1-5; nil until it is answered.
    @Published private(set) var checkIn: Int?
    /// The strap's own Readiness score for the selected day, when it reported one.
    @Published private(set) var strapReadiness: Int?
    /// The whole of the strap's Readiness row for the selected day: Recovery's "Strap's estimate"
    /// and the comparison in How it works read its parts, not just the score.
    @Published private(set) var strapReadinessRow: StrapReadinessRow?
    /// The month behind each of Recovery's six body signals (`ScreenQueries.signalHistories`).
    @Published private(set) var signalHistories = RecoveryGlow.shared.noSignalHistories()
    /// A month of daily steps ending yesterday, for Monday's recap (`ScreenQueries.monthHistory`).
    @Published private(set) var recapSteps: [DayStat] = []
    /// The statistics What changed shows, in `TODAY_TILES`' order (Android's `todayTiles`).
    @Published private(set) var todayTiles: [Metric] = TodayGlow.shared.allTodayTiles()

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
    /// Where the tiles picker's answer is kept. Android keeps it in DataStore; iOS in the app's own
    /// defaults, like the Apple Health switch, because it is this phone's preference.
    private static let tilesKey = "today.tiles"

    init(databasePath: String) {
        // Card F7: a demo launch pins the one clock the shared code reads, before the first query and
        // before the seed, so nothing on any screen follows the runner's wall clock.
        if DemoData.isEnabled { ScreenClock.shared.pinDemo() }
        date = HealthModel.todayIso
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
        // Today's own reads, once that screen has asked for them: a launch that never shows Today
        // (the design lab, a fresh install on Pair) pays for none of them.
        if dayState != nil { reloadToday() }
    }

    // MARK: - Today's day (card U5)

    /// Everything card U5's Today screen shows for the selected day: the day itself, Readiness and
    /// Charge, the workouts, the check-in, the strap's own score and the recap's step history.
    func reloadToday() {
        let date = self.date
        screens.day(date: date) { [weak self] state in
            guard let self else { return }
            // A slower answer for a day the user has already left must not overwrite the new one.
            guard self.date == date else { return }
            self.dayState = state
            self.reloadRecapSteps(date: date)
        }
        screens.recovery(date: date) { [weak self] result in
            guard let self, self.date == date else { return }
            self.recoveryResult = result
        }
        screens.workouts { [weak self] list in
            self?.workoutList = list
        }
        screens.checkIn(date: date) { [weak self] answer in
            guard let self, self.date == date else { return }
            self.checkIn = answer.feeling?.intValue
        }
        screens.strapReadiness(date: date) { [weak self] row in
            guard let self, self.date == date else { return }
            self.strapReadiness = row?.score?.intValue
            self.strapReadinessRow = row
        }
        reloadTodayTiles()
        // Recovery reads the same strap row, so its screen gets it without a second read when it has
        // been opened (see `reloadRecovery`).
        if recoveryOpened { reloadRecovery() }
    }

    // MARK: - Recovery's own reads (card U6)

    /// True once the Recovery tab has been drawn: its signal histories and strap row are read only
    /// then, as Today's own reads wait for Today (card U5).
    private var recoveryOpened = false

    /// The two reads Recovery adds to the day: the strap's whole Readiness row and a month of each
    /// body signal (`rememberSignals`, GlowRecoveryScreen.kt:360-384).
    func reloadRecovery() {
        recoveryOpened = true
        let date = self.date
        screens.strapReadiness(date: date) { [weak self] row in
            guard let self, self.date == date else { return }
            self.strapReadinessRow = row
        }
        screens.signalHistories(date: date) { [weak self] histories in
            guard let self, self.date == date else { return }
            self.signalHistories = histories
        }
    }

    /// Answers - or clears, with nil - the selected day's check-in (`CheckIn.save`, GlowCheckIn.kt).
    func saveCheckIn(_ feeling: Int?) {
        let date = self.date
        screens.saveCheckIn(date: date, feeling: feeling.map { KotlinInt(int: Int32($0)) }) { [weak self] in
            guard let self, self.date == date else { return }
            self.checkIn = feeling
        }
    }

    /// The statistics What changed shows, as `MainActivity`'s `todayTiles` reads them: the stored
    /// names in their order, else all six.
    func reloadTodayTiles() {
        let names = UserDefaults.standard.string(forKey: Self.tilesKey)?.split(separator: ",").map(String.init)
        todayTiles = TodayGlow.shared.todayTiles(names: names)
    }

    /// Stores the picker's answer. Compose only saves a non-empty choice.
    func saveTodayTiles(_ tiles: [Metric]) {
        let names = tiles.map { $0.name }
        UserDefaults.standard.set(names.joined(separator: ","), forKey: Self.tilesKey)
        todayTiles = TodayGlow.shared.todayTiles(names: names)
    }

    /// A Monday's recap compares the seven days before the selected day with the seven before them,
    /// so it reads a month of steps: the same two weeks Compose's history call covers.
    private func reloadRecapSteps(date: String) {
        guard TodayGlow.shared.isRecapDay(iso: date) else {
            recapSteps = []
            return
        }
        let selected = HealthModel.day(fromIso: date) ?? Date()
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: selected) ?? selected
        screens.monthHistory(metric: Metric.steps, date: HealthModel.iso(yesterday)) { [weak self] list in
            guard let self, self.date == date else { return }
            self.recapSteps = list
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

    /// The day's `yyyy-MM-dd` in the phone's own zone: the currency the shared screens read.
    static func isoDay(_ day: Date) -> String { iso(day) }

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

    /// The day the app opens on, at the start of that local day: the phone's today, or the fixed
    /// demo day when `-demoData` has pinned it (card F7). The header, the calendar's ring and the
    /// selected day all read this, so they agree with the data the shared code returns.
    static var today: Date {
        if DemoData.isEnabled, let day = day(fromIso: DemoDay.shared.isoDate) { return day }
        return Calendar.current.startOfDay(for: Date())
    }

    /// [today] as the `yyyy-MM-dd` the shared screens take.
    static var todayIso: String { iso(today) }
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
