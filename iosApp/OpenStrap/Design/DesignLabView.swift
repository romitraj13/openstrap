#if DEBUG
import SwiftUI

/// The glow design system on one screen, for debug builds only, opened with the launch argument
/// `-designLab` (card U1). Nothing here is product UI: it exists so the type, the colours, the
/// glow cards, their motions, the dot-matrix numerals and the icon set can be seen, screenshotted
/// and compared against the Android build.
struct DesignLabView: View {
    /// Whether the app was launched with `-designLab`. Debug builds only: the whole type is inside
    /// `#if DEBUG`, so a release build has no design lab to open.
    static var isEnabled: Bool {
        ProcessInfo.processInfo.arguments.contains("-designLab")
    }

    /// Set by the UI test (`-designLabStill`): the glow cores hold still. A lab whose cards animate
    /// for ever never goes quiescent, and XCUITest waits for quiescence before every query and swipe
    /// — one of those waits outlasting XCUITest's 30 s query timeout is what failed runs 39587579214
    /// and 39594198894. Holding still also makes the screenshots the same picture every run.
    static var still: Bool {
        ProcessInfo.processInfo.arguments.contains("-designLabStill")
    }

    /// Set by the UI test (`-designLabSection <name>`): draw one section instead of thirteen. Card F5
    /// split the lab this way because one screen holding the whole tree — thirteen glow cards, 46
    /// icons, five tab bars, two calendar sheets and every type row — made XCUITest's queries time
    /// out on a lab that was otherwise correct: three green-again reruns of the same tree since F2
    /// (runs 39647481836, 39664474482 and the retries of 883bb5b). Each launch now walks about a
    /// screenful of tree, which is a query the accessibility server answers.
    static var requestedSection: LabSection? {
        let arguments = ProcessInfo.processInfo.arguments
        guard
            let flag = arguments.firstIndex(of: "-designLabSection"),
            arguments.indices.contains(flag + 1)
        else { return nil }
        return LabSection(rawValue: arguments[flag + 1])
    }

    /// Whether `-designLabSection` was passed at all. A name the enum does not know is a test bug,
    /// and draws `lab.unknown` rather than the whole lab, so it cannot pass for the wanted section.
    private static var askedForSection: Bool {
        ProcessInfo.processInfo.arguments.contains("-designLabSection")
    }

    // The switches' state lives here so the lab can show both of them.
    @State private var switchOn = true
    @State private var switchOff = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                Text("Design lab")
                    .appTextStyle(AppTypography.headlineSmall)
                    .foregroundStyle(ThemeColors.onBackground)
                    .accessibilityIdentifier("lab.title")

                if Self.askedForSection && Self.requestedSection == nil {
                    Text("Unknown -designLabSection")
                        .appTextStyle(AppTypography.bodyLarge)
                        .foregroundStyle(ThemeColors.error)
                        .accessibilityIdentifier("lab.unknown")
                } else {
                    sections(Self.requestedSection)
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .glowScrollPhasePause(always: Self.still)
        .background(ThemeColors.background)
        .accessibilityIdentifier("lab.scroll")
    }

    /// Every section, in layout order, or only `only` when the test asked for one.
    @ViewBuilder
    private func sections(_ only: LabSection?) -> some View {
        if only == nil || only == .type { typeSection }
        if only == nil || only == .colours { colourSection }
        if only == nil || only == .cards { cardSection }
        if only == nil || only == .motions { motionSection }
        if only == nil || only == .dotmatrix { dotMatrixSection }
        if only == nil || only == .icons { iconSection }
        if only == nil || only == .charts { chartSection }
        if only == nil || only == .gauges { gaugeSection }
        if only == nil || only == .hypnogram { hypnogramSection }
        if only == nil || only == .parts { partsSection }
        if only == nil || only == .quiet { quietSection }
        if only == nil || only == .navbar { navSection }
        if only == nil || only == .calendar { calendarSection }
    }

    /// The lab's sections, in layout order. The raw value is the name the UI test passes to
    /// `-designLabSection`; `identifier` is what the section's own view sets (`lab.quiet`), which is
    /// also the name the frame assertion prints.
    enum LabSection: String, CaseIterable {
        case type, colours, cards, motions, dotmatrix, icons, charts
        case gauges, hypnogram, parts, quiet, navbar, calendar

        var identifier: String { "lab.\(rawValue)" }
    }

    // MARK: Type

    private var typeSection: some View {
        section("Type", identifier: "lab.type") {
            VStack(alignment: .leading, spacing: 18) {
                ForEach(AppTypography.all, id: \.name) { style in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(style.name)
                            .appTextStyle(AppTypography.labelMedium)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                        Text("Recovery 73 Ready")
                            .appTextStyle(style)
                            .foregroundStyle(ThemeColors.onBackground)
                    }
                }
                ForEach(GlowType.all, id: \.name) { style in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(style.name)
                            .appTextStyle(AppTypography.labelMedium)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                        Text("Recovery 73 Ready")
                            .appTextStyle(style)
                            .foregroundStyle(ThemeColors.onBackground)
                    }
                }
                // Every weight Theme.kt instantiates, so a weight that fails to render is visible.
                ForEach(DesignFontFamily.allCases, id: \.rawValue) { family in
                    ForEach(family.weights, id: \.self) { weight in
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(family.familyName) \(DesignFontFamily.weightName(weight)) \(Int(weight))")
                                .appTextStyle(AppTypography.labelMedium)
                                .foregroundStyle(ThemeColors.onSurfaceVariant)
                            Text("Recovery 73 Ready")
                                .font(family.font(size: 24, weight: weight, tabularFigures: true))
                                .foregroundStyle(ThemeColors.onBackground)
                        }
                    }
                }
            }
        }
    }

    // MARK: Colours

    private var colourSection: some View {
        section("Metric colours", identifier: "lab.colours") {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                ForEach(MetricColors.allAccents) { accent in
                    VStack(alignment: .leading, spacing: 4) {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(accent.color)
                            .frame(height: 44)
                        Text(accent.name)
                            .appTextStyle(AppTypography.labelSmall)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                }
            }
        }
    }

    // MARK: Glow cards

    private var cardSection: some View {
        section("Glow cards", identifier: "lab.cards") {
            // Card F5: lazy, so a launch renders the two or three cards the window shows instead of
            // all nine at once. An XCUITest query has to wait for the app to answer, and the whole
            // section's Core Image renders held that answer for 9.6 s in one green run (`F2
            // lab.cards` arrived 9.6 s after the query started) and past the 30 s snapshot timeout
            // in run 39706541852. The cards are the same; only when a set of layers is rendered is
            // not.
            LazyVStack(spacing: 14) {
                ForEach(GlowStyle.presets) { preset in
                    glowCard(preset.style, title: preset.name)
                }
                // The 6 pt slab edge Today's overview card wears (`GlowCard.kt:144-147`, card F6).
                glowCard(depthStyle, title: "depth 6")
                ForEach(chartStyles) { chart in
                    glowCard(chart.style, title: chart.name)
                }
            }
        }
    }

    /// `Bloom` with the slab edge Today's overview card draws under itself (`depth = 6.dp`,
    /// GlowTodayScreen.kt:240). Nothing else in the lab has a `depth`, so this is the one card that
    /// shows what the field does.
    private var depthStyle: GlowStyle {
        var style = GlowStyle.bloom
        style.depth = 6
        return style
    }

    /// `chart(color:)` for the accents the screens draw charts with.
    private var chartStyles: [LabCard] {
        [
            LabCard(name: "heart", style: GlowStyle.chart(MetricColors.heart)),
            LabCard(name: "sleepDeep", style: GlowStyle.chart(MetricColors.sleepDeep)),
            LabCard(name: "steps", style: GlowStyle.chart(MetricColors.steps)),
            LabCard(name: "readiness", style: GlowStyle.chart(MetricColors.readiness)),
            LabCard(name: "charge", style: GlowStyle.chart(MetricColors.charge)),
        ]
    }

    private var motionSection: some View {
        section("Motion", identifier: "lab.motions") {
            // Lazy for the reason `cardSection` is (card F5): five glow renders at once is five
            // times the wait before a query is answered.
            LazyVStack(spacing: 14) {
                ForEach(motions) { motion in
                    glowCard(motion.style, title: motion.name)
                }
            }
        }
    }

    private var motions: [LabCard] {
        let pairs: [(String, GlowMotion)] = [
            ("Drift", GlowMotion.drift),
            ("Heartbeat", GlowMotion.heartbeatDefault),
            ("Breath", GlowMotion.breathDefault),
            ("Stride", GlowMotion.strideDefault),
            ("Still", GlowMotion.still),
        ]
        return pairs.map { name, motion in
            var style = GlowStyle.bloom
            style.motion = motion
            return LabCard(name: name, style: style)
        }
    }

    private func glowCard(_ style: GlowStyle, title: String) -> some View {
        GlowCard(style, contentPadding: 16) {
            Text(title)
                .appTextStyle(GlowType.title)
                .foregroundStyle(ThemeColors.onBackground)
        }
        .frame(height: 160)
    }

    // MARK: Dot matrix

    private var dotMatrixSection: some View {
        section("Dot matrix", identifier: "lab.dotmatrix") {
            DotMatrixText("0123456789", color: ThemeColors.onBackground)
        }
    }

    // MARK: Icons

    private var iconSection: some View {
        section("Icons", identifier: "lab.icons") {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 5), spacing: 16) {
                ForEach(PhosphorIcon.allCases) { icon in
                    VStack(spacing: 6) {
                        icon.image
                            .resizable()
                            .scaledToFit()
                            .frame(width: 26, height: 26)
                            .foregroundStyle(ThemeColors.onBackground)
                        Text(icon.name)
                            .appTextStyle(AppTypography.labelSmall)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                    }
                }
            }
        }
    }

    // MARK: Charts (GlowCharts.kt)

    private var chartSection: some View {
        section("Charts", identifier: "lab.charts") {
            VStack(alignment: .leading, spacing: 14) {
                DotPanel {
                    DotHeader("Heart rate", trailing: "62 bpm", chevron: true)
                    GlowChartGap()
                    GlowLineChart(
                        values: Self.dayHours,
                        height: GlowLayout.chartHeight,
                        labels: Self.dayLabels,
                        color: MetricColors.heart,
                        describe: { index, value in "\(Int(value)) bpm at \(Self.hourLabel(index))" }
                    )
                }
                DotPanel {
                    DotHeader("Steps", trailing: "8,240")
                    GlowChartGap()
                    GlowDotBars(
                        values: Self.hourlySteps,
                        height: GlowLayout.chartHeight,
                        rows: 10,
                        labels: Self.dayLabels,
                        accent: MetricColors.steps,
                        describe: { index, value in "\(Int(value)) steps at \(Self.hourLabel(index))" }
                    )
                }
                DotPanel {
                    DotHeader("Resting heart rate", trailing: "Last 7 days")
                    GlowChartGap()
                    GlowTrend(days: Self.week, height: 130, average: 61)
                }
            }
        }
    }

    // MARK: Gauges (GlowArcGauge, GlowHorizon)

    private var gaugeSection: some View {
        section("Gauges", identifier: "lab.gauges") {
            VStack(alignment: .leading, spacing: 14) {
                DotPanel {
                    DotHeader("Recovery")
                    GlowArcGauge(progress: 0.73, color: MetricColors.readiness) {
                        VStack(spacing: 4) {
                            DotMatrixText("73", dot: GlowLayout.heroDot, gap: GlowLayout.heroGap)
                            Text("Ready")
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(glowInk.opacity(0.7))
                        }
                        .padding(.bottom, 8)
                    }
                }
                DotPanel {
                    DotHeader("Sleep score", trailing: "84")
                    GlowHorizon(progress: 0.84, color: MetricColors.sleepDeep)
                }
            }
        }
    }

    // MARK: Hypnogram (GlowHypnogram)

    private var hypnogramSection: some View {
        section("Hypnogram", identifier: "lab.hypnogram") {
            DotPanel {
                DotHeader("Sleep stages", trailing: "7h 42m")
                GlowChartGap()
                GlowHypnogram(
                    stages: Self.night,
                    rowNames: ["Awake", "REM", "Light", "Deep"],
                    height: 130,
                    labels: ["23:00", "03:00", "07:00"],
                    describe: { stage in Self.stageName(stage) }
                )
            }
        }
    }

    // MARK: Parts (GlowParts.kt)

    private var partsSection: some View {
        section("Parts", identifier: "lab.parts") {
            VStack(alignment: .leading, spacing: 14) {
                DotPanel {
                    DotHeader("Breath volume", trailing: "12.4 L")
                    ArcTimeline(
                        marks: [
                            ArcMark(at: 0.2, color: MetricColors.heart),
                            ArcMark(at: 0.55, color: MetricColors.sleepDeep),
                            ArcMark(at: 0.8, color: MetricColors.steps),
                        ],
                        arcs: [(from: 0.1, to: 0.3), (from: 0.45, to: 0.65), (from: 0.7, to: 0.9)],
                        height: 44
                    )
                }
                DotPanel {
                    HStack {
                        Text("Training")
                            .appTextStyle(GlowType.title)
                            .foregroundStyle(glowInk)
                        Spacer(minLength: 0)
                        GlowSwitch(checked: $switchOn)
                    }
                    HStack {
                        Text("Training (off)")
                            .appTextStyle(GlowType.title)
                            .foregroundStyle(glowInk)
                        Spacer(minLength: 0)
                        GlowSwitch(checked: $switchOff)
                    }
                }
            }
        }
    }

    // MARK: Quiet panels (GlowQuiet.kt)

    private var quietSection: some View {
        section("Quiet panels", identifier: "lab.quiet") {
            VStack(alignment: .leading, spacing: 14) {
                DotPanel {
                    DotHeader("Heart rate", trailing: "62 bpm", chevron: true)
                    DotDivider()
                    HStack(alignment: .center, spacing: 8) {
                        Text("Resting")
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(glowInk.opacity(0.7))
                        Spacer(minLength: 0)
                        Text("58")
                            .appTextStyle(AppTypography.titleLarge)
                            .foregroundStyle(glowInk)
                        DotArrow(up: true, color: trendGood)
                    }
                    HStack(alignment: .center, spacing: 8) {
                        Text("HRV")
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(glowInk.opacity(0.7))
                        Spacer(minLength: 0)
                        Text("42")
                            .appTextStyle(AppTypography.titleLarge)
                            .foregroundStyle(glowInk)
                        DotArrow(up: false, color: trendBad)
                        DotLevel(color: glowInk.opacity(0.6))
                    }
                    HStack(spacing: 8) {
                        StatusPill("Normal", color: MetricColors.good)
                        StatusPill("Higher", color: MetricColors.warn)
                        StatusPill("Lower", color: MetricColors.bad)
                    }
                }
                DotPanel {
                    DotHeader("Skin temperature", trailing: "36.4°C", warning: true)
                    DotDivider()
                    HStack(alignment: .center, spacing: 8) {
                        Text("36.4")
                            .appTextStyle(AppTypography.titleLarge)
                            .foregroundStyle(glowInk)
                        DotArrow(up: true, color: trendBad)
                        Spacer(minLength: 0)
                        StatusPill("Higher", color: MetricColors.warn)
                    }
                    Text("usually 36.2°C")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(glowInk.opacity(0.5))
                }
                DotPanel {
                    DotHeader("Recovery", chevron: true)
                    DotPanel {
                        DotHeader("Sleep", trailing: "7h 42m", expanded: true)
                        DotDivider()
                        HStack(spacing: 8) {
                            StatusPill("Awake", color: MetricColors.sleepAwake)
                            StatusPill("REM", color: MetricColors.sleepRem)
                            StatusPill("Light", color: MetricColors.sleepLight)
                            StatusPill("Deep", color: MetricColors.sleepDeep)
                        }
                    }
                }
                TileGrid(Self.statTiles) { tile in
                    DotStatTile(
                        label: tile.label,
                        accent: tile.accent,
                        value: tile.value,
                        unit: tile.unit,
                        baseline: tile.baseline,
                        direction: tile.direction,
                        higherIsBetter: tile.higherIsBetter,
                        onClick: {}
                    )
                }
                GlowTile("Sleep", accent: MetricColors.sleepDeep, onClick: {}) {
                    Text("7h 42m")
                        .appTextStyle(AppTypography.titleLarge)
                        .foregroundStyle(glowInk)
                        .padding(.top, 8)
                }
            }
        }
    }

    // MARK: Tab bar (Insights.kt)

    private var navSection: some View {
        section("Tab bar", identifier: "lab.navbar") {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(Self.navItems.indices), id: \.self) { selected in
                    FloatingNavBar(items: Self.navItems, selected: selected, onSelect: { _ in })
                        .padding(.horizontal, 0)
                }
                HStack {
                    FloatingNavRail(items: Self.navItems, selected: 1, onSelect: { _ in })
                    Spacer(minLength: 0)
                }
            }
        }
    }

    // MARK: Calendar (DayCalendar.kt)

    private var calendarSection: some View {
        section("Calendar", identifier: "lab.calendar") {
            VStack(alignment: .leading, spacing: 14) {
                DayCalendarSheet(
                    selected: Calendar.current.startOfDay(for: Date()),
                    available: Self.sampleAvailable,
                    onPick: { _ in },
                    onDismiss: {}
                )
                DayCalendarSheet(
                    selected: Self.sampleSelected,
                    available: Self.sampleAvailable,
                    onPick: { _ in },
                    onDismiss: {}
                )
            }
        }
    }

    // MARK: Sample data

    /// A day of heart rate, one reading an hour, with a gap where the strap was off.
    private static let dayHours: [Float?] = [
        58, 56, 54, 53, 52, 53, 55, 57, 61, 68, 74, 79,
        83, 86, 88, 85, 81, nil, 76, 74, 72, 68, 64, 61,
    ]

    /// Steps per hour, for the dot-bar grid.
    private static let hourlySteps: [Float] = [
        0, 0, 0, 0, 0, 0, 20, 180, 600, 900, 1100, 700,
        500, 650, 900, 1200, 800, 400, 300, 900, 1400, 600, 120, 0,
    ]

    private static let dayLabels = ["00:00", "06:00", "12:00", "18:00", "23:00"]

    private static let week: [GlowDay] = [
        GlowDay(label: "Mon", value: 58),
        GlowDay(label: "Tue", value: 55),
        GlowDay(label: "Wed", value: 61),
        GlowDay(label: "Thu", value: 57),
        GlowDay(label: "Fri", value: 64),
        GlowDay(label: "Sat", value: 62),
        GlowDay(label: "Sun", value: 60),
    ]

    /// One night, as stretches of a fraction of it. Row 0 is awake, then REM, light and deep.
    private static let night: [GlowStage] = [
        GlowStage(from: 0.00, to: 0.05, row: 0),
        GlowStage(from: 0.05, to: 0.12, row: 2),
        GlowStage(from: 0.12, to: 0.20, row: 3),
        GlowStage(from: 0.20, to: 0.26, row: 2),
        GlowStage(from: 0.26, to: 0.34, row: 1),
        GlowStage(from: 0.34, to: 0.42, row: 2),
        GlowStage(from: 0.42, to: 0.50, row: 3),
        GlowStage(from: 0.50, to: 0.56, row: 2),
        GlowStage(from: 0.56, to: 0.62, row: 1),
        GlowStage(from: 0.62, to: 0.70, row: 2),
        GlowStage(from: 0.70, to: 0.78, row: 3),
        GlowStage(from: 0.78, to: 0.84, row: 2),
        GlowStage(from: 0.84, to: 0.90, row: 1),
        GlowStage(from: 0.90, to: 0.96, row: 0),
        GlowStage(from: 0.96, to: 1.00, row: 0),
    ]

    private static let stageNames = ["Awake", "REM", "Light", "Deep"]

    private static func stageName(_ stage: GlowStage) -> String {
        let name = stageNames.indices.contains(stage.row) ? stageNames[stage.row] : "Light"
        let minutes = Int(((stage.to - stage.from) * 8 * 60).rounded())
        return "\(name) \(minutes) min"
    }

    private static func hourLabel(_ index: Int) -> String {
        String(format: "%02d:00", index)
    }

    private static let navItems: [NavItem] = [
        NavItem(label: "Today", icon: .house, selectedIcon: .houseFill),
        NavItem(label: "Recovery", icon: .plant),
        NavItem(label: "Sleep", icon: .moon, selectedIcon: .moonFill),
        NavItem(label: "Activity", icon: .footprints),
        NavItem(label: "You", icon: .user),
    ]

    private static var sampleAvailable: Set<Date> {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return Set((1...14).compactMap { calendar.date(byAdding: .day, value: -$0, to: today) })
    }

    private static var sampleSelected: Date {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return calendar.date(byAdding: .day, value: -5, to: today) ?? today
    }

    /// Four statistics for the grid, in the shape `DotStatTile` takes.
    private static let statTiles: [LabStatTile] = [
        LabStatTile(label: "Resting heart rate", accent: MetricColors.heart, value: "58", unit: "bpm",
                    baseline: "56 bpm", direction: 1, higherIsBetter: false),
        LabStatTile(label: "HRV", accent: MetricColors.hrv, value: "42", unit: "ms",
                    baseline: "38 ms", direction: 1, higherIsBetter: true),
        LabStatTile(label: "Skin temperature", accent: MetricColors.temperature, value: "36.4", unit: "°C",
                    baseline: "36.2°C", direction: 0, higherIsBetter: nil),
        LabStatTile(label: "Blood oxygen", accent: MetricColors.spo2, value: nil, unit: "%",
                    baseline: nil, direction: nil, higherIsBetter: nil),
    ]

    // MARK: Section frame
    private func section<Content: View>(
        _ title: String,
        identifier: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .appTextStyle(AppTypography.labelMedium)
                .foregroundStyle(ThemeColors.onSurfaceVariant)
                // A plain Text is always an accessibility element, so the UI test can scroll to
                // the last section by asking for this.
                .accessibilityIdentifier("\(identifier).title")
            content()
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }
}

/// One labelled glow card in the lab.
private struct LabCard: Identifiable {
    let name: String
    let style: GlowStyle
    var id: String { name }
}

/// One statistic in the lab's tile grid (card U2).
private struct LabStatTile: Identifiable {
    let label: String
    let accent: Color
    let value: String?
    let unit: String
    let baseline: String?
    let direction: Int?
    let higherIsBetter: Bool?
    var id: String { label }
}
#endif
