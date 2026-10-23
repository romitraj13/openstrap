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

    /// Debug only: `-glowStill` holds every glow core still for the whole life of the view.
    ///
    /// The UI tests must query a screen whose card animates for ever, and XCUITest waits for a
    /// quiescent app before every query: card F5 lost runs 39587579214 and 39594198894 to a 30 s
    /// query timeout on the design lab, and card U8's `testActivity` timed out the same way in run
    /// 39737184826 on a tree that had just passed. F5 answered it for the lab with `-designLabStill`;
    /// this is the same switch for the tab screens, and it also makes a run's screenshots the same
    /// picture every time.
    static var glowStill: Bool {
        #if DEBUG
        return CommandLine.arguments.contains("-glowStill")
        #else
        return false
        #endif
    }
}

/// The screens' data, read from the shared Kotlin `ScreenQueries` (U4's calendar, profile and goals;
/// U5-U8's day, workouts and PAI) and `HealthQueries` (the demo seed). One object for the app, made
/// once beside the controller so a tab switch does not reopen the database.
///
/// Kotlin calls back on the main thread, so every assignment here lands on the main thread and
/// SwiftUI can draw it without a hop.
final class HealthModel: ObservableObject {
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
    /// The strap's sport-effect records, newest last: Activity's load and fitness panels read them
    /// (`ScreenQueries.sportEffects`, Android's `WorkoutsViewModel.effects`).
    @Published private(set) var sportEffects: [SportEffectRow] = []
    /// Each day's PAI for the seven days ending on the selected one, oldest first
    /// (`ScreenQueries.paiWeek`; GlowActivityScreen.kt:84).
    @Published private(set) var paiWeek: [DailyPai] = []
    /// The workout the detail screen shows, with its whole heart-rate stream
    /// (`ScreenQueries.workout`). The list's copy carries only its last sample.
    @Published private(set) var openWorkout: WorkoutAnalysis?
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
        // The Activity tab's two reads (card U8): the seven days of PAI ending on this day, and the
        // strap's sport effects, which its load and fitness panels need.
        screens.sportEffects { [weak self] list in
            self?.sportEffects = list
        }
        screens.paiWeek(from: HealthModel.isoDaysBefore(date, 6), to: date) { [weak self] week in
            guard let self, self.date == date else { return }
            self.paiWeek = week
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

    /// The Sleep tab's own reads (card U7): the day, and Readiness behind the sleep-health panels.
    /// Today's own pass already fetches both; this only runs it when Sleep is the first tab drawn.
    func reloadSleep() {
        if dayState == nil { reloadToday() }
    }

    /// The Activity tab's own reads (card U8). Everything it shows - the day, Readiness, the
    /// workouts, the sport effects and the week of PAI - comes from Today's pass, which this runs
    /// when Activity is the first tab drawn.
    func reloadActivity() {
        if dayState == nil { reloadToday() }
    }

    /// One workout with its whole heart-rate stream, for the detail screen (card U8). Keeps the
    /// workout already loaded, so opening the same one twice does not read the database again.
    func loadWorkout(_ start: Int64) {
        if let open = openWorkout, open.workout.start == start { return }
        screens.workout(start: start) { [weak self] analysis in
            self?.openWorkout = analysis
        }
    }

    /// The metric detail's daily series (card U9): one value per day of [range] ending on [date], the
    /// same `history(m.daily, from, end)` Compose reads (GlowMetricDetail.kt:124-125). Days without
    /// data are absent, as they are there.
    func loadHistory(metric: Metric, range: Range, date: String, onResult: @escaping ([DayStat]) -> Void) {
        screens.history(metric: metric, range: range, date: date, onResult: onResult)
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
    ///
    /// Card F11: `-todayMonday` makes Today draw the recap on a day that is not a Monday (it exists so a
    /// UI test does not depend on the weekday it runs on), so the steps behind it follow the same flag.
    /// Without it the forced recap said "Steps a day —": the card was drawn with no history loaded.
    /// Release builds read `forcesRecapDay` as false and this is the plain Monday check.
    private func reloadRecapSteps(date: String) {
        guard TodayGlow.shared.isRecapDay(iso: date) || TodayFlags.forcesRecapDay else {
            recapSteps = []
            return
        }
        let selected = HealthModel.day(fromIso: date) ?? ScreenNow.startOfToday
        let yesterday = ScreenNow.calendar.date(byAdding: .day, value: -1, to: selected) ?? selected
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

    /// [iso] moved back by [days] local days, still as `yyyy-MM-dd`; [iso] itself when it will not parse.
    static func isoDaysBefore(_ iso: String, _ days: Int) -> String {
        guard let day = day(fromIso: iso),
              let earlier = ScreenNow.calendar.date(byAdding: .day, value: -days, to: day) else { return iso }
        return Self.iso(earlier)
    }

    private static func iso(_ day: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        // Card F9: format the day in the zone the screens measure it in, so the pinned demo day is
        // "2026-10-21" on a runner in any zone (the shared code reads it in UTC).
        formatter.timeZone = ScreenNow.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: day)
    }

    private static func day(fromIso value: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        // The same zone `iso` formats in, so a day round-trips whatever the runner's zone is.
        formatter.timeZone = ScreenNow.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: value)
    }

    /// The day the app opens on, at the start of that local day: the phone's today, or the fixed
    /// demo day when `-demoData` has pinned it (card F7). The header, the calendar's ring and the
    /// selected day all read this, so they agree with the data the shared code returns.
    ///
    /// Card F9: the instant and the zone both come from `ScreenNow`, so a demo run is pinned to
    /// `DemoDay` (09:30 UTC on 21 October 2026) in `DemoDay`'s zone. Before this, the start of the
    /// day came from the phone's clock and zone, so the header called the pinned day Yesterday.
    static var today: Date {
        ScreenNow.startOfToday
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
