import OpenStrapShared
import SwiftUI

/*
 * Today, ported from `GlowTodayScreen.kt`, `GlowTodayExtras.kt`, `GlowCheckIn.kt` and
 * `NapsPanel.kt` (card U5).
 *
 * The home screen answers "how am I, what changed, what should I do". Each thing appears once: the
 * verdict leads; in the morning last night comes next, later in the day the heart and movement do.
 * Only a few cards glow — the overview, the heart and activity, and the night — and the rest is
 * black with dots, so the glow keeps meaning "look here".
 *
 * Order, exactly as `TodayList` (GlowTodayScreen.kt:136) builds it: the overview (and the morning
 * check-in under it), the strap's alerts, Monday's recap, last night in the morning, What changed,
 * the heart, activity, the day's workouts, and last night again later in the day.
 *
 * The numbers, dates and sentences all come from `TodayGlow` in shared Kotlin, so both builds read
 * the same values.
 */

/// Where a metric or workout tap on Today goes. Card U9 replaces the placeholder these push and
/// keeps this route, as Android's `Detail.Metric` / `Detail.Workout` stay put.
enum TodayRoute: Hashable {
    case metric(String)
    case workout(Int64)
}

/// The clock Today judges "morning" and "later in the day" by, and which day counts as a Monday.
///
/// A normal launch reads the phone's clock. The UI test pins both states (`-todayMorning`,
/// `-todayEvening`, `-todayMonday`) so every run captures the morning order and the later order
/// rather than whichever the CI machine's clock happened to be at. Debug builds only.
enum TodayFlags {
    static var now: Date {
        #if DEBUG
        if CommandLine.arguments.contains("-todayMorning") { return at(hour: 8) }
        if CommandLine.arguments.contains("-todayEvening") { return at(hour: 20) }
        #endif
        return Date()
    }

    /// Treats the selected day as a Monday, so the recap shows in a run that is not on one.
    static var forcesRecapDay: Bool {
        #if DEBUG
        return CommandLine.arguments.contains("-todayMonday")
        #else
        return false
        #endif
    }

    #if DEBUG
    private static func at(hour: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: Date()) ?? Date()
    }
    #endif
}

struct TodayScreen: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel
    /// Android's `onOpenRecovery` / `onOpenActivity`: a card's tap selects that tab.
    var onOpenTab: (AppTab) -> Void

    @State private var route: TodayRoute?
    @State private var choosingTiles = false

    var body: some View {
        Group {
            if let day = health.dayState {
                content(day)
            } else {
                // The day's first read is in flight. Nothing is invented for the wait.
                Color.clear.frame(height: 1)
            }
        }
        // Today is a `GlowScreenList` on Android, which at every size this app runs at (portrait
        // only: under 400 pt wide is the tight branch, 600 and up needs a landscape window the app
        // does not allow) pads its cards with the tight sides.
        .environment(\.glowCardPadding, GlowLayout.wideCardPadding)
        .navigationDestination(item: $route) { route in
            destination(for: route)
        }
        .sheet(isPresented: $choosingTiles) {
            TilesSheet(
                chosen: health.todayTiles,
                onSave: { tiles in
                    health.saveTodayTiles(tiles)
                    choosingTiles = false
                },
                onDismiss: { choosingTiles = false }
            )
            .presentationDetents([.height(420)])
            .presentationBackground(.clear)
        }
        .onAppear { health.reloadToday() }
    }

    // MARK: - The list

    private func content(_ day: DayUiState) -> some View {
        let overviewCard = overview(day)
        let morning = isMorning
        let hasSleep = day.asleepSeconds != nil || !day.naps.isEmpty
        let alerts = TodayGlow.shared.alertLines(alerts: day.alerts)
        let signals = TodayGlow.shared.signalTiles(state: day, tiles: health.todayTiles)
        let adviceText = advice()
        let workouts = TodayGlow.shared.dayWorkouts(
            workouts: health.workoutList, dayStart: day.dayStart, dayEnd: day.dayEnd
        )
        return VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
            OverviewCard(
                overview: overviewCard,
                onOpenLead: { onOpenTab(.recovery) },
                onOpenSecond: { onOpenTab(.recovery) },
                onOpenSleep: { onOpenTab(.sleep) }
            )

            // Mornings only (today, before noon): how you feel, under the score it's there to check.
            if morning {
                CheckInPanel(feeling: health.checkIn) { feeling in
                    health.saveCheckIn(feeling)
                }
            }

            // Quiet but first: anything the strap warned about this day.
            if !alerts.isEmpty {
                AlertsPanel(alerts: alerts) { metric in
                    open(metric: metric)
                }
            }

            // Mondays: the week just gone against the one before.
            if let week = recap(day) {
                WeeklyRecapPanel(recap: week)
            }

            // Dark grey glow: last night, naps included. Right after waking it leads.
            if hasSleep && morning {
                sleepCard(day)
            }

            if !signals.isEmpty || adviceText != nil {
                WhatChangedPanel(
                    signals: signals,
                    advice: adviceText,
                    onChooseTiles: { choosingTiles = true },
                    onOpenMetric: { metric in open(metric: metric) }
                )
            }

            HeartCard(
                day: day,
                live: controller.heartRate,
                liveEnabled: controller.state == "ready" ? controller.liveHeartRateEnabled : nil,
                onLiveToggle: { on in controller.setLiveHeartRate(on) },
                onClick: { open(metric: Metric.heartrate) } // Kotlin exports the entry all-lowercase.
            )

            ActivityCard(day: day, goals: health.goals) {
                onOpenTab(.activity)
            }

            if !workouts.isEmpty {
                TodayWorkouts(workouts: workouts) { start in
                    route = .workout(start)
                }
            }

            if hasSleep && !morning {
                sleepCard(day)
            }
        }
    }

    @ViewBuilder
    private func sleepCard(_ day: DayUiState) -> some View {
        SleepCard(day: day) { onOpenTab(.sleep) }
    }

    // MARK: - The conditions (shared Kotlin decides, GlowTodayScreen.kt:143-170)

    private var todayIso: String { HealthModel.isoDay(TodayFlags.now) }

    private var hour: Int { Calendar.current.component(.hour, from: TodayFlags.now) }

    private var isMorning: Bool {
        TodayGlow.shared.isMorning(iso: health.date, todayIso: todayIso, hour: Int32(hour))
    }

    private func chargeLeads(_ day: DayUiState) -> Bool {
        TodayGlow.shared.chargeLeads(
            state: day,
            todayIso: todayIso,
            chargeLatest: health.recoveryResult?.charge.latest,
            nowEpoch: Int64(TodayFlags.now.timeIntervalSince1970),
            hour: Int32(hour)
        )
    }

    private func overview(_ day: DayUiState) -> TodayOverview {
        let rec = health.recoveryResult
        return TodayGlow.shared.overview(
            chargeLatest: rec?.charge.latest,
            chargeAtWake: rec?.charge.atWake,
            readiness: rec?.readiness,
            readinessBefore: TodayGlow.shared.readinessBefore(rec: rec),
            strapScore: health.strapReadiness.map { KotlinInt(int: Int32($0)) },
            sleepScore: day.sleep?.score,
            previousSleepScore: day.previousSleepScore,
            chargeLeads: chargeLeads(day)
        )
    }

    /// The sentence under What changed's title: `Narrative.readinessBody`'s advice for the day.
    /// Empty (not nil) when there is no score, which is how Compose's `isNullOrEmpty` test drops it.
    private func advice() -> String? {
        guard let rec = health.recoveryResult else { return nil }
        let body = Narrative.shared.readinessBody(r: rec.readiness)
        return body.isEmpty ? nil : body
    }

    /// The week's recap: Mondays only, as `isRecapDay` decides (GlowTodayExtras.kt:126).
    private func recap(_ day: DayUiState) -> WeeklyRecap? {
        let monday = TodayGlow.shared.isRecapDay(iso: health.date) || TodayFlags.forcesRecapDay
        guard monday else { return nil }
        return TodayGlow.shared.weeklyRecap(
            state: day,
            recovery: health.recoveryResult,
            workouts: health.workoutList,
            steps: health.recapSteps
        )
    }

    private func open(metric: Metric) {
        route = .metric(metric.name)
    }

    // MARK: - The pushed detail (a placeholder until card U9)

    @ViewBuilder
    private func destination(for route: TodayRoute) -> some View {
        switch route {
        case .metric(let name):
            let metric = TodayGlow.shared.metric(name: name)
            MetricDetailPlaceholder(title: metric?.title ?? name, note: metric?.unit ?? "")
        case .workout(let start):
            let workout = health.workoutList.first { $0.workout.start == start }
            MetricDetailPlaceholder(title: workout?.sportName ?? "", note: "")
        }
    }
}

// MARK: - Overview

/// The leading score as one big gauge with its number and a one-line verdict inside, and the other
/// two scores as bars underneath that open their tabs. (`OverviewCard`, GlowTodayScreen.kt:205)
private struct OverviewCard: View {
    var overview: TodayOverview
    var onOpenLead: () -> Void
    var onOpenSecond: () -> Void
    var onOpenSleep: () -> Void

    /// The glow sits low and central, under the gauge (GlowTodayScreen.kt:236).
    private var style: GlowStyle {
        var style = GlowStyle.bloom
        style.color = MetricColors.overview(overview.tone)
        style.coreShape = .radial
        style.coreX = 0.5
        style.coreY = 0.6
        style.coreWidth = 0.95
        style.coreHeight = 0.75
        style.coreBlur = 30
        style.toneWidth = 16
        style.toneShift = -10
        style.glowStrength = 0.6
        style.bevel = 0.6
        style.motion = .breath(perMinute: 5)
        style.depth = 6
        return style
    }

    private var leadNumber: String {
        overview.lead.value.map { String($0.intValue) } ?? "-"
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader(overview.lead.label) {
                    Change(score: overview.lead, withNote: true)
                }
                GlowArcGauge(
                    progress: Float(overview.lead.value?.intValue ?? 0) / 100,
                    color: MetricColors.today(overview.lead.accent)
                ) {
                    VStack(spacing: 0) {
                        DotMatrixText(leadNumber, dot: 5.5, gap: 2)
                        if let verdict = overview.verdict {
                            Text(verdict)
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(.white.opacity(0.75))
                                .padding(.top, 8)
                        }
                        // Why there's no score yet, or that it's still learning: small, under the verdict.
                        if let note = overview.lead.noteLong {
                            Text(note)
                                .appTextStyle(AppTypography.labelSmall)
                                .foregroundStyle(.white.opacity(0.6))
                                .lineLimit(1)
                                .padding(.top, overview.verdict == nil ? 8 : 4)
                        }
                    }
                }
                .padding(.top, GlowLayout.titleGap)
                .padding(.horizontal, 12)

                HStack(alignment: .top, spacing: 20) {
                    ScoreBar(score: overview.second, onClick: onOpenSecond)
                        .frame(maxWidth: .infinity)
                    ScoreBar(score: overview.sleep, onClick: onOpenSleep)
                        .frame(maxWidth: .infinity)
                }
                .padding(.top, 18)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpenLead)
    }
}

/// How a score moved: a dot arrow (green up, amber down) and the amount; three level dots when it
/// didn't. (`Change`, GlowTodayScreen.kt:304)
private struct Change: View {
    var score: TodayScore
    var withNote: Bool

    var body: some View {
        if let change = score.change?.intValue {
            HStack(spacing: 0) {
                if change != 0 {
                    DotArrow(up: change > 0, color: change > 0 ? trendGood : trendBad)
                } else {
                    DotLevel(color: .white.opacity(0.6))
                }
                // No change has no amount: the level dots say it, so it never wraps.
                if !text.isEmpty {
                    Color.clear.frame(width: 6, height: 0)
                    Text(text)
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(.white.opacity(0.9))
                        .lineLimit(1)
                        .fixedSize()
                }
            }
        }
    }

    private var text: String {
        guard let change = score.change?.intValue else { return "" }
        var parts: [String] = []
        // `listOfNotNull(if (d == 0) null else "${abs(d)}", if (withNote) score.against else null)`.
        if change != 0 { parts.append(String(abs(change))) }
        if withNote { parts.append(score.against) }
        return parts.joined(separator: " ")
    }
}

/// One of the other two scores under the gauge, tappable: the label with a chevron and the value
/// with its change on one line, and below them a thin bar filled to the score in its colour.
/// (`ScoreBar`, GlowTodayScreen.kt:323)
private struct ScoreBar: View {
    var score: TodayScore
    var onClick: () -> Void

    private var value: String {
        score.value.map { String($0.intValue) } ?? "-"
    }

    private var barColor: Color {
        let color = MetricColors.today(score.accent)
        // The strap's estimate draws fainter, so it doesn't read as OpenStrap's own score.
        return score.estimate ? color.opacity(0.5) : color
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                // The chevron right after the label marks the half as something to open.
                HStack(spacing: 0) {
                    Text(score.label)
                        .appTextStyle(AppTypography.labelSmall)
                        .foregroundStyle(.white.opacity(0.75))
                        .lineLimit(1)
                    Color.clear.frame(width: 2, height: 0)
                    PhosphorIcon.caretRight.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 12, height: 12)
                        .foregroundStyle(.white.opacity(0.6))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(value)
                    .appTextStyle(AppTypography.titleLarge)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .fixedSize()
                if score.change != nil {
                    Color.clear.frame(width: 6, height: 0)
                    Change(score: score, withNote: false)
                }
            }
            Color.clear.frame(height: 6)
            GlowBar(fraction: Float(score.value?.intValue ?? 0) / 100, color: barColor)
            if let note = score.note {
                Text(note)
                    .appTextStyle(AppTypography.labelSmall)
                    .foregroundStyle(.white.opacity(0.6))
                    .lineLimit(1)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .contentShape(glassRowShape)
        .onTapGesture(perform: onClick)
    }
}

// MARK: - Check-in

/// The morning check-in: how you feel, 1 to 5. (`CheckInPanel`, GlowCheckIn.kt:24)
private struct CheckInPanel: View {
    var feeling: Int?
    var onCheckIn: (Int?) -> Void

    var body: some View {
        DotPanel {
            DotHeader("Morning check-in", trailing: feeling.map { CheckIn.shared.label(feeling: Int32($0)) })
            Text("How do you feel this morning?")
                .appTextStyle(AppTypography.bodyMedium)
                .foregroundStyle(.white.opacity(0.85))
            HStack(spacing: 8) {
                ForEach(1...5, id: \.self) { value in
                    ChoicePill(text: "\(value)", selected: feeling == value) {
                        // Tapping the chosen pill clears the answer.
                        onCheckIn(feeling == value ? nil : value)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            QuietNote(text: TodayGlow.shared.checkInNote())
        }
    }
}

/// A glass pill that can be switched on, filled in white when it is. (`ChoicePill`, GlowCheckIn.kt:39)
private struct ChoicePill: View {
    var text: String
    var selected: Bool
    var onClick: () -> Void

    var body: some View {
        Text(text)
            .appTextStyle(AppTypography.labelLarge)
            .foregroundStyle(selected ? Color.black : glowInk)
            .lineLimit(1)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .padding(.horizontal, 6)
            .background {
                if selected { Capsule().fill(Color.white.opacity(0.85)) }
            }
            .glass(Capsule())
            .contentShape(Capsule())
            .onTapGesture(perform: onClick)
    }
}

// MARK: - Alerts

/// The strap's alerts this day, newest first. Shown only when there are some.
/// (`AlertsPanel`, GlowTodayExtras.kt:51)
private struct AlertsPanel: View {
    var alerts: [AlertLine]
    var onOpenMetric: (Metric) -> Void

    var body: some View {
        DotPanel {
            DotHeader(
                "Strap alerts",
                trailing: alerts.count == 1 ? "1 today" : "\(alerts.count) today",
                warning: true
            )
            ForEach(alerts, id: \.epoch) { alert in
                HStack(spacing: 0) {
                    Text(alert.text)
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(glowInk)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    QuietNote(text: alert.clock)
                }
                .contentShape(Rectangle())
                .onTapGesture { onOpenMetric(alert.metric) }
            }
            QuietNote(text: TodayGlow.shared.alertsDisclaimer())
        }
    }
}

// MARK: - Weekly recap

/// Monday's look back: steps per day, time asleep, the average Recovery and the workouts done.
/// (`WeeklyRecapPanel`, GlowTodayExtras.kt:74)
private struct WeeklyRecapPanel: View {
    var recap: WeeklyRecap

    var body: some View {
        DotPanel {
            DotHeader("Your week", trailing: recap.range)
            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(Array(recap.facts.prefix(2)), id: \.label) { fact in
                        Fact(label: fact.label, value: fact.value)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(Array(recap.facts.dropFirst(2)), id: \.label) { fact in
                        Fact(label: fact.label, value: fact.value)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            QuietNote(text: recap.note)
        }
    }
}

// MARK: - What changed

/// The statistics What changed shows, in the chosen order. (`WhatChanged`, GlowTodayScreen.kt:413)
private struct WhatChangedPanel: View {
    var signals: [SignalTile]
    var advice: String?
    var onChooseTiles: () -> Void
    var onOpenMetric: (Metric) -> Void

    var body: some View {
        DotPanel {
            // No leading dot: editing the tiles is a proper button where you'd look for it, so the
            // 7-day note moves under the title.
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 0) {
                    Text("What changed")
                        .appTextStyle(GlowType.title)
                        .foregroundStyle(glowInk)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    EditPill(onClick: onChooseTiles)
                }
                Text("vs. your last 7 days")
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(.white.opacity(0.6))
                    .padding(.top, 2)
            }
            if let advice {
                Text(advice)
                    .appTextStyle(AppTypography.bodyMedium)
                    .foregroundStyle(.white.opacity(0.85))
            }
            // Two columns, so six statistics take three rows instead of a screenful.
            TileGrid(items) { item in
                DotStatTile(
                    label: item.signal.label,
                    accent: MetricColors.signal(item.signal.accent),
                    value: item.signal.value,
                    unit: item.signal.unit,
                    baseline: item.signal.baseline,
                    direction: item.signal.direction.map { Int($0.intValue) },
                    higherIsBetter: item.signal.higherIsBetter?.boolValue,
                    onClick: { onOpenMetric(item.signal.target) }
                )
            }
            if !signals.isEmpty && signals.allSatisfy({ $0.baseline == nil }) {
                Text("Your normal builds over the first week of wear.")
                    .appTextStyle(AppTypography.bodyMedium)
                    .foregroundStyle(.white.opacity(0.65))
            }
        }
    }

    /// `TileGrid` needs identity; a statistic's metric names it, and each appears once.
    private var items: [SignalItem] {
        signals.map { SignalItem(id: $0.target.name, signal: $0) }
    }
}

private struct SignalItem: Identifiable {
    let id: String
    let signal: SignalTile
}

/// A small glass pill with the sliders icon: opens the chooser for which statistics show.
/// (`EditPill`, GlowTodayScreen.kt:452)
private struct EditPill: View {
    var onClick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            PhosphorIcon.slidersHorizontal.image
                .resizable()
                .scaledToFit()
                .frame(width: 14, height: 14)
                .foregroundStyle(glowInk)
            Color.clear.frame(width: 6, height: 0)
            Text("Edit")
                .appTextStyle(AppTypography.bodySmall)
                .foregroundStyle(glowInk)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .glass(Capsule())
        .contentShape(Capsule())
        .onTapGesture(perform: onClick)
    }
}

// MARK: - Heart

/// Heart rate, beating at the latest reading, with the Live switch when the strap is connected.
/// (`HeartGlow`, GlowTodayScreen.kt:366)
private struct HeartCard: View {
    var day: DayUiState
    var live: Int?
    var liveEnabled: Bool?
    var onLiveToggle: (Bool) -> Void
    var onClick: () -> Void

    private static let hours = ["00", "06", "12", "18", "24"]

    private var now: Int? {
        if let live { return live }
        return day.latestHeartRate.map { Int($0.intValue) }
    }

    private var style: GlowStyle {
        var style = GlowStyle.chart(MetricColors.heart)
        style.motion = .heartbeat(bpm: now ?? 62)
        return style
    }

    private var buckets: [Float?] {
        TodayGlow.shared.heartBuckets(state: day).map { ($0 as? NSNumber)?.floatValue } // [Any] gaps
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                // A live reading needs no label (the beating glow says it). An older one says
                // "Latest" only when there's no Live switch.
                GlowHeader(
                    "Heart rate",
                    trailing: now == nil && liveEnabled == nil ? "Latest" : nil
                ) {
                    if let on = liveEnabled {
                        HStack(spacing: 0) {
                            Text("Live")
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(.white.opacity(0.85))
                            Color.clear.frame(width: 8, height: 0)
                            GlowSwitch(
                                checked: Binding(get: { on }, set: { onLiveToggle($0) }),
                                accent: MetricColors.heart
                            )
                        }
                    }
                }
                if let now {
                    GlowHeroNumber("\(now)", unit: "bpm", facts: TodayGlow.shared.heartFacts(state: day))
                }
                GlowChartGap()
                if day.heartRate.isEmpty {
                    Text("No heart-rate data yet. Sync your strap.")
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(.white.opacity(0.75))
                } else {
                    GlowLineChart(
                        values: buckets,
                        height: GlowLayout.chartHeight,
                        labels: Self.hours,
                        describe: { index, value in
                            TodayGlow.shared.heartReadout(
                                dayStart: day.dayStart, index: Int32(index), value: value
                            )
                        }
                    )
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClick)
    }
}

// MARK: - Activity

/// Activity, moving with the day's steps: the hero total, the goal, and the hours it happened in.
/// (`ActivityGlow`, GlowTodayScreen.kt:466)
private struct ActivityCard: View {
    var day: DayUiState
    var goals: Goals
    var onClick: () -> Void

    private static let hours = ["00", "06", "12", "18", "24"]

    private var progress: Float {
        goals.steps > 0 ? Float(day.steps) / Float(goals.steps) : 0
    }

    private var style: GlowStyle {
        var style = GlowStyle.chart(MetricColors.steps)
        style.motion = .stride(cadence: 110, energy: CGFloat(min(max(progress, 0), 1)))
        return style
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader("Activity", chevron: true)
                GlowHeroNumber(
                    "\(day.steps)",
                    unit: "steps",
                    facts: TodayGlow.shared.activityFacts(state: day, goals: goals)
                )
                GlowChartGap()
                GlowDotBars(
                    values: day.hourlySteps.map { $0.floatValue },
                    height: GlowLayout.chartHeight,
                    labels: Self.hours,
                    accent: MetricColors.steps,
                    describe: { hour, value in
                        TodayGlow.shared.hourStepReadout(hour: Int32(hour), value: value)
                    }
                )
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClick)
    }
}

// MARK: - Sleep

/// Last night: the score's headline, the time asleep, the hypnogram and the naps under it.
/// (`SleepPanel`, GlowTodayScreen.kt:488)
private struct SleepCard: View {
    var day: DayUiState
    var onClick: () -> Void

    private var session: SleepSession? { day.sleep }

    var body: some View {
        GlowCard(GlowStyle.sleep, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader(
                    "Sleep",
                    trailing: session.map { TodayGlow.shared.sleepRange(session: $0) },
                    chevron: true
                )
                if let asleep = day.asleepSeconds?.int64Value {
                    GlowHeroNumber(
                        TodayGlow.shared.asleepClock(seconds: asleep),
                        unit: "asleep",
                        facts: TodayGlow.shared.sleepFacts(score: session?.score)
                    )
                }
                if !day.sleepStages.isEmpty {
                    GlowChartGap()
                    GlowSleepStages(spans: day.sleepStages, height: GlowLayout.chartHeight)
                }
                if !day.naps.isEmpty {
                    Color.clear.frame(height: 14)
                    DotDivider()
                    Color.clear.frame(height: 14)
                    Text(TodayGlow.shared.napsLine(naps: day.naps))
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(.white.opacity(0.75))
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClick)
    }
}

// MARK: - Workouts

/// The selected day's workouts, in the same glass rows as the Activity screen's list.
/// (`TodayWorkouts`, GlowTodayScreen.kt:174)
private struct TodayWorkouts: View {
    var workouts: [WorkoutAnalysis]
    var onOpen: (Int64) -> Void

    var body: some View {
        DotPanel {
            DotHeader("Workouts")
            ForEach(workouts, id: \.workout.start) { analysis in
                WorkoutLine(analysis: analysis, showDate: false) {
                    onOpen(analysis.workout.start)
                }
            }
        }
    }
}

// MARK: - The tile chooser

/// Which statistics What changed shows. At least one stays on.
/// (`TilesDialog`, GlowTodayExtras.kt:135)
private struct TilesSheet: View {
    var chosen: [Metric]
    var onSave: ([Metric]) -> Void
    var onDismiss: () -> Void

    @State private var on: Set<String> = []

    private var tiles: [Metric] { TodayGlow.shared.allTodayTiles() }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("What changed shows")
                .appTextStyle(AppTypography.headlineSmall)
                .foregroundStyle(glowInk)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(tiles, id: \.name) { metric in
                    HStack(spacing: 10) {
                        CheckBox(on: on.contains(metric.name))
                        Text(metric.title)
                            .appTextStyle(AppTypography.bodyLarge)
                            .foregroundStyle(glowInk)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                    .onTapGesture { toggle(metric.name) }
                }
            }
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                Button {
                    onDismiss()
                } label: {
                    Text("Cancel")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("tiles.cancel")
                Button {
                    onSave(tiles.filter { on.contains($0.name) })
                } label: {
                    Text("Save")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(on.isEmpty ? ThemeColors.onSurfaceVariant : ThemeColors.primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
                .disabled(on.isEmpty)
                .accessibilityIdentifier("tiles.save")
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack {
                GlowGlassBackground()
                Color(hex: 0xFF0B0B0D).opacity(0.72)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
        )
        .padding(12)
        .onAppear { on = Set(chosen.map { $0.name }) }
    }

    private func toggle(_ name: String) {
        if on.contains(name) {
            on.remove(name)
        } else {
            on.insert(name)
        }
    }
}

/// Compose's `Checkbox`, drawn: an empty rounded box, or a filled one with a tick.
private struct CheckBox: View {
    var on: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(on ? Color.white : Color.clear)
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .strokeBorder(on ? Color.white : Color.white.opacity(0.55), lineWidth: 2)
            if on {
                Path { path in
                    path.move(to: CGPoint(x: 4, y: 10))
                    path.addLine(to: CGPoint(x: 8, y: 14))
                    path.addLine(to: CGPoint(x: 16, y: 5))
                }
                .stroke(Color.black, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: 20, height: 20)
        .accessibilityHidden(true)
    }
}

// MARK: - The pushed detail, until card U9

/// Where a metric or workout tap lands until card U9 builds the detail screens: the thing's own
/// name, and nothing invented. `TodayRoute` stays as the route U9 fills in. Card U6's Recovery tab
/// pushes the same route for its body-signal tiles, so this is shared, not private.
struct MetricDetailPlaceholder: View {
    var title: String
    var note: String

    var body: some View {
        ScrollView {
            DotPanel {
                Text(title)
                    .appTextStyle(GlowType.title)
                    .foregroundStyle(glowInk)
                    .accessibilityIdentifier("today.detail")
                if !note.isEmpty {
                    Text(note)
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(glowInk.opacity(0.7))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
        .background(ThemeColors.background.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
