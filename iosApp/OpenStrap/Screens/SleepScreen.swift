import OpenStrapShared
import SwiftUI

/*
 * The Sleep tab, ported from `ui/glow/GlowSleepScreen.kt` (card U7), with the two panels it borrows
 * from `ui/dashboard/SleepPanels.kt` and `ui/dashboard/NapsPanel.kt`.
 *
 * "How did I sleep, and is it trending right": the night glows — its score inside the arc, the
 * sentence, and the stages as a hypnogram — and everything that explains it sits in quiet dot panels
 * underneath: the stages' minutes, what shaped the score, the vitals during sleep, the night's heart
 * rate and breathing, sleep health, breathing disturbances, naps and the week.
 *
 * Order, exactly as `GlowSleepScreen.kt:81-101` builds it. Compose splits that list into a left and a
 * right pane at tablet widths; at the size this app runs at (portrait only) `GlowScreenList` stacks
 * the panes, so the order below is the one a phone shows.
 *
 * Every number, sentence, share and word comes from `SleepGlow` in shared Kotlin, so both builds
 * read the same values; this file draws them.
 */

// MARK: - Colour names

/// The colours `SleepGlow`'s accents name, from `Theme.swift`'s tokens and nothing else
/// (standing instruction 6). Compose passes `Color` values into these panels
/// (GlowSleepScreen.kt:244-256, 208, 271-279, ScreenParts.kt:53-58); shared Kotlin has no colour
/// type, so it passes the name.
private enum SleepColors {
    static func of(_ accent: SleepAccent) -> Color {
        switch accent.name {
        case "Heart": return MetricColors.heart
        case "Hrv": return MetricColors.hrv
        case "Temperature": return MetricColors.temperature
        case "Spo2": return MetricColors.spo2
        case "SleepLight": return MetricColors.sleepLight
        case "SleepDeep": return MetricColors.sleepDeep
        case "SleepRem": return MetricColors.sleepRem
        case "SleepAwake": return MetricColors.sleepAwake
        case "Good": return MetricColors.good
        case "Warn": return MetricColors.warn
        default: return MetricColors.stress
        }
    }
}

// MARK: - The screen

struct SleepScreen: View {
    @ObservedObject var health: HealthModel

    /// A vital tile opens its metric, as Compose's `onOpenMetric` does (GlowSleepScreen.kt:94).
    /// Card U9 replaces the placeholder this pushes; the route is Today's, as Android's `Detail` is one.
    @State private var route: TodayRoute?

    var body: some View {
        Group {
            if let day = health.dayState {
                content(day)
            } else {
                // The day's first read is in flight. Nothing is invented for the wait.
                Color.clear.frame(height: 1)
            }
        }
        // Sleep is a `GlowScreenList` on Android, which at the sizes this app runs at (portrait only)
        // pads its cards with the tight sides.
        .environment(\.glowCardPadding, GlowLayout.wideCardPadding)
        .navigationDestination(item: $route) { route in
            switch route {
            case .metric(let name):
                let metric = TodayGlow.shared.metric(name: name)
                MetricDetailPlaceholder(title: metric?.title ?? name, note: metric?.unit ?? "")
            case .workout(let start):
                let workout = health.workoutList.first { $0.workout.start == start }
                MetricDetailPlaceholder(title: workout?.sportName ?? "", note: "")
            }
        }
        .onAppear { health.reloadSleep() }
    }

    @ViewBuilder
    private func content(_ day: DayUiState) -> some View {
        let goal = health.goals.sleepSeconds
        // `rec = recovery?.takeIf { it.date == state.date }` (GlowSleepScreen.kt:79), in shared Kotlin.
        let rec = recoveryForDay(day)
        VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
            NightCard(hero: SleepGlow.shared.hero(state: day, goalSeconds: goal), day: day)
            if let breakdown = day.sleepBreakdown {
                StagesPanel(rows: SleepGlow.shared.stageRows(b: breakdown))
            }
            if let model = SleepGlow.shared.contributors(state: day, recovery: health.recoveryResult) {
                SleepContributorsPanel(model: model)
            }
            let vitals = SleepGlow.shared.vitals(state: day)
            if !vitals.isEmpty {
                SleepVitalsPanel(vitals: vitals) { metric in
                    route = .metric(metric.name)
                }
            }
            if let charts = SleepGlow.shared.nightCharts(state: day) {
                NightChartsPanel(model: charts)
            }
            if let rec, let regularity = rec.regularity {
                SleepHealthPanel(model: SleepGlow.shared.sleepHealth(reg: regularity, health: rec.sleepHealth))
            }
            if let rec, let breathing = rec.breathingDisturbances {
                SleepBreathingPanel(model: SleepGlow.shared.breathing(b: breathing))
            }
            if !day.naps.isEmpty {
                SleepNapsPanel(day: day) { open in
                    SleepGlow.shared.naps(state: day, openStart: open)
                }
            }
            SleepWeekPanel(model: SleepGlow.shared.week(state: day, goalSeconds: goal))
        }
    }

    /// The day's own Recovery result, or nil while it belongs to another day (GlowSleepScreen.kt:79).
    private func recoveryForDay(_ day: DayUiState) -> RecoveryResult? {
        RecoveryGlow.shared.forDay(state: day, recovery: health.recoveryResult)
    }
}

// MARK: - The night (NightCard, GlowSleepScreen.kt:122)

/// The score inside the arc, the headline, the night's facts and the stages as a hypnogram.
private struct NightCard: View {
    var hero: SleepHeroModel
    var day: DayUiState

    var body: some View {
        GlowCard(GlowStyle.sleep, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader("Sleep", trailing: hero.trailing)
                    .accessibilityIdentifier("sleep.title")
                if hero.missing {
                    Color.clear.frame(height: GlowLayout.titleGap)
                    Text(hero.emptyText ?? "")
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(.white.opacity(0.85))
                        .accessibilityIdentifier("sleep.empty")
                } else {
                    // The arc with the number standing in it, bottom-centred (:139-146).
                    ZStack(alignment: .bottom) {
                        // `progress: Float?` reaches Swift as `KotlinFloat?`.
                        GlowHorizon(progress: hero.progress.map { $0.floatValue }, color: .white, height: 100)
                        DotMatrixText(hero.value ?? "", dot: 5.5, gap: 2)
                            .padding(.bottom, 6)
                    }
                    .padding(.top, 6)
                    .frame(maxWidth: .infinity)
                    Text(hero.label ?? "")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(.white.opacity(0.6))
                        .frame(maxWidth: .infinity, alignment: .center)
                        // A scored night and a night without one each have an id of their own, so a
                        // test can find the state without matching the words.
                        .accessibilityIdentifier(hero.hasScore ? "sleep.score" : "sleep.noscore")
                    Color.clear.frame(height: 14)
                    Text(hero.headline ?? "")
                        .appTextStyle(AppTypography.titleMedium)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .center)
                    Text(hero.facts ?? "")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(.white.opacity(0.75))
                        .frame(maxWidth: .infinity, alignment: .center)
                    if !day.sleepStages.isEmpty {
                        GlowChartGap()
                        GlowSleepStages(spans: day.sleepStages, height: GlowLayout.chartHeight)
                    }
                }
            }
        }
    }
}

// MARK: - Stages (StagesPanel, GlowSleepScreen.kt:192)

/// Minutes and share of each stage, as dot bars in the stage's colour.
private struct StagesPanel: View {
    var rows: [SleepStageRow]

    var body: some View {
        DotPanel {
            DotHeader("Stages")
            ForEach(rows, id: \.label) { row in
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 0) {
                        Circle()
                            .fill(SleepColors.of(row.accent))
                            .frame(width: 7, height: 7)
                        Color.clear.frame(width: 8, height: 0)
                        Text(row.label)
                            .appTextStyle(AppTypography.bodyMedium)
                            .foregroundStyle(.white.opacity(0.85))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(row.value)
                            .appTextStyle(AppTypography.titleSmall)
                            .foregroundStyle(.white)
                        Text(row.percent)
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(.white.opacity(0.6))
                            .frame(width: 34, alignment: .leading)
                            .padding(.leading, 10)
                    }
                    GlowBar(fraction: row.fraction, color: SleepColors.of(row.accent))
                }
            }
        }
    }
}

// MARK: - What shaped the score (ContributorsPanel, GlowSleepScreen.kt:221)

private struct SleepContributorsPanel: View {
    var model: SleepContributorsModel

    var body: some View {
        DotPanel {
            DotHeader("What shaped the score")
            ForEach(model.rows, id: \.label) { row in
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 0) {
                        Text(row.label)
                            .appTextStyle(AppTypography.bodyMedium)
                            .foregroundStyle(glowInk)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(row.rating)
                            .appTextStyle(AppTypography.titleSmall)
                            .foregroundStyle(MetricColors.rating(row.rating))
                    }
                    QuietNote(text: row.detail)
                    GlowBar(fraction: row.fraction, color: MetricColors.rating(row.rating))
                }
            }
            QuietNote(text: model.note)
        }
    }
}

// MARK: - Sleep vitals (VitalsPanel, GlowSleepScreen.kt:240)

/// The night's vitals as a two-column grid; each opens its detail.
private struct SleepVitalsPanel: View {
    var vitals: [SleepVital]
    var onOpenMetric: (Metric) -> Void

    /// `TileGrid` needs identity; a vital's metric names it, and each appears once.
    private var items: [VitalItem] {
        vitals.map { VitalItem(id: $0.label, vital: $0) }
    }

    var body: some View {
        DotPanel {
            DotHeader("Sleep vitals", trailing: "During sleep")
            TileGrid(items) { item in
                VitalTile(vital: item.vital) { onOpenMetric(item.vital.metric) }
            }
        }
    }
}

private struct VitalItem: Identifiable {
    let id: String
    let vital: SleepVital
}

/// One tile (VitalTile, GlowSleepScreen.kt:248): the value, and the note — or, when the vital is off
/// your usual range, the note's word as a pill.
private struct VitalTile: View {
    var vital: SleepVital
    var onOpen: () -> Void

    var body: some View {
        GlowTile(vital.label, accent: SleepColors.of(vital.accent), onClick: onOpen) {
            Text(vital.value)
                .appTextStyle(AppTypography.titleLarge)
                .foregroundStyle(glowInk)
                .padding(.top, 8)
            if let pill = vital.pill {
                StatusPill(pill, color: trendBad)
                    .padding(.top, 4)
            } else if let note = vital.note {
                Text(note)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(.white.opacity(0.55))
            }
            Spacer(minLength: 0)
        }
    }
}

// MARK: - The night's charts (NightChartsPanel, GlowSleepScreen.kt:259)

/// Heart rate and breathing across the sleep, each a line in its metric colour on the black panel.
private struct NightChartsPanel: View {
    var model: NightChartsModel

    var body: some View {
        DotPanel {
            ForEach(Array(model.rows.enumerated()), id: \.offset) { index, row in
                if index > 0 {
                    DotDivider()
                }
                DotHeader(row.title, trailing: row.trailing)
                // `List<Float?>` crosses as `[Any]`: each gap is the `NSNumber` Kotlin boxes a Float into.
                let values = row.values.map { ($0 as? NSNumber)?.floatValue }
                GlowLineChart(
                    values: values,
                    height: CGFloat(row.height),
                    labels: model.labels,
                    showAverage: false,
                    color: SleepColors.of(row.accent),
                    describe: { point, value in
                        SleepGlow.shared.readout(
                            from: model.start, index: Int32(point), value: value, unit: row.unit
                        )
                    }
                )
            }
        }
    }
}

// MARK: - Sleep health (SleepHealthPanel, GlowSleepScreen.kt:399)

private struct SleepHealthPanel: View {
    var model: SleepHealthModel

    var body: some View {
        DotPanel {
            DotHeader("Sleep health", trailing: model.trailing)
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "Regularity", value: model.regularityValue)
                Spacer(minLength: 0)
                Fact(label: "Band", value: model.bandValue)
            }
            QuietNote(text: model.note)
            if !model.rows.isEmpty {
                DotDivider()
                ForEach(model.rows, id: \.label) { row in
                    HStack(spacing: 0) {
                        Text(row.label)
                            .appTextStyle(AppTypography.bodyMedium)
                            .foregroundStyle(glowInk)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(row.value)
                            .appTextStyle(AppTypography.titleSmall)
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }
                if let midpoint = model.midpointNote {
                    QuietNote(text: midpoint)
                }
            }
        }
    }
}

// MARK: - Breathing disturbances (BreathingCard, GlowSleepScreen.kt:379)

private struct SleepBreathingPanel: View {
    var model: SleepBreathingModel

    var body: some View {
        DotPanel {
            DotHeader("Breathing", trailing: "vs. your nights")
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "Disturbances", value: model.disturbances)
                Spacer(minLength: 0)
                Fact(label: "Your usual", value: model.usual)
            }
            QuietNote(text: model.note)
        }
    }
}

// MARK: - Naps (NapsPanel, GlowSleepScreen.kt:298)

/// The day's naps, one glass row each; tap one to see its stages and heart rate. A lone nap starts
/// open, which the shared code decides (`defaultOpenNap`).
private struct SleepNapsPanel: View {
    var day: DayUiState
    var build: (Int64) -> SleepNapsModel?

    /// nil until the reader taps a row, so the first draw is Compose's default.
    @State private var chosen: Int64?

    var body: some View {
        let open = chosen ?? SleepGlow.shared.defaultOpenNap(state: day)
        let model = build(open)
        DotPanel {
            DotHeader("Naps", trailing: model?.total)
            if let model {
                ForEach(Array(model.rows.enumerated()), id: \.offset) { index, nap in
                    if index > 0 {
                        DotDivider()
                    }
                    let expanded = open == nap.start
                    Button {
                        chosen = expanded ? -1 : nap.start
                    } label: {
                        HStack(spacing: 0) {
                            Text(nap.line)
                                .appTextStyle(AppTypography.bodyMedium)
                                .foregroundStyle(glowInk)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            PhosphorIcon.caretDown.image
                                .resizable()
                                .scaledToFit()
                                .frame(width: 16, height: 16)
                                .foregroundStyle(.white.opacity(0.8))
                                .rotationEffect(.degrees(expanded ? 180 : 0))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .contentShape(glassRowShape)
                    }
                    .buttonStyle(.plain)
                    .glass(glassRowShape)
                    .accessibilityLabel(expanded ? "Hide" : "Details")
                    if expanded {
                        if !nap.stages.isEmpty {
                            GlowSleepStages(spans: nap.stages, height: 110)
                        }
                        if !nap.stageRows.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(nap.stageRows, id: \.label) { row in
                                    HStack(spacing: 0) {
                                        Text(row.label)
                                            .appTextStyle(AppTypography.bodyMedium)
                                            .foregroundStyle(.white.opacity(0.85))
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                        Text(row.value)
                                            .appTextStyle(AppTypography.titleSmall)
                                            .foregroundStyle(.white)
                                        Text(row.percent)
                                            .appTextStyle(AppTypography.bodySmall)
                                            .foregroundStyle(.white.opacity(0.6))
                                            .frame(width: 34, alignment: .leading)
                                            .padding(.leading, 10)
                                    }
                                }
                            }
                        }
                        if let note = nap.stagesNote {
                            QuietNote(text: note)
                        }
                        // The strap reports no average for naps, so it comes from the day's heart rate (:325-331).
                        if let note = nap.hrNote {
                            QuietNote(text: note)
                            let values = nap.hrValues.map { ($0 as? NSNumber)?.floatValue }
                            GlowLineChart(
                                values: values,
                                height: 80,
                                labels: nap.hrLabels,
                                showAverage: false,
                                color: MetricColors.heart,
                                describe: { point, value in
                                    SleepGlow.shared.readout(
                                        from: nap.start, index: Int32(point), value: value, unit: "bpm"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - This week (WeekPanel, GlowSleepScreen.kt:337)

private struct SleepWeekPanel: View {
    var model: SleepWeekModel

    var body: some View {
        DotPanel {
            DotHeader("This week", trailing: model.trailing)
            if !model.hasWeek {
                QuietNote(text: model.emptyNote ?? "")
            } else {
                if let windows = model.windows {
                    GlowSleepWindows(model: windows)
                }
                HStack(alignment: .top, spacing: 0) {
                    ForEach(model.facts, id: \.label) { fact in
                        Fact(label: fact.label, value: fact.value)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                if let regularity = model.regularity {
                    DotDivider()
                    HStack(alignment: .bottom, spacing: 0) {
                        VStack(alignment: .leading, spacing: 0) {
                            Fact(label: "Regularity", value: regularity.score)
                            Text(regularity.band)
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(SleepColors.of(regularity.accent))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        VStack(alignment: .trailing, spacing: 0) {
                            QuietNote(text: regularity.fallAsleep)
                            QuietNote(text: regularity.wakeUp)
                        }
                    }
                    GlowBar(fraction: regularity.fraction, color: SleepColors.of(regularity.accent))
                    QuietNote(text: regularity.note)
                } else if let note = model.learningNote {
                    QuietNote(text: note)
                }
            }
        }
    }
}
