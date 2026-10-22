import OpenStrapShared
import SwiftUI

/*
 * The Recovery tab, ported from `ui/glow/GlowRecoveryScreen.kt` (card U6), with the quiet parts of
 * `ui/glow/GlowQuiet.kt` it draws its tiles with.
 *
 * "Can I push today, and why": two cards glow, the score and Charge; everything that explains them
 * sits in quiet dot panels underneath, and each body signal opens its detail.
 *
 * Order, exactly as `GlowRecoveryScreen.kt:76-95` builds it: the strap's body-signal alert when the
 * night is off baseline (it matters more than either score), the Recovery card with its
 * contributors under it, the Charge card with where the charge went under that, then the body-signal
 * tiles and how it works, both spanning the width.
 *
 * The numbers, sentences and fold rules all come from `RecoveryGlow` in shared Kotlin, so both
 * builds read the same values; this file draws them.
 */

// MARK: - Colour names

/// The colours `RecoveryGlow`'s accents name, from `Theme.swift`'s tokens and nothing else
/// (standing instruction 6). Compose passes `Color` values into these panels
/// (GlowRecoveryScreen.kt:241-244, 374-382); shared Kotlin has no colour type, so it passes the name.
private enum RecoveryColors {
    static func tile(_ accent: RecoveryAccent) -> Color {
        switch accent.name {
        case "Heart": return MetricColors.heart
        case "Hrv": return MetricColors.hrv
        case "Temperature": return MetricColors.temperature
        case "SleepLight": return MetricColors.sleepLight
        case "Spo2": return MetricColors.spo2
        default: return MetricColors.stress
        }
    }

    static func flow(_ accent: ChargeFlowAccent) -> Color {
        switch accent.name {
        case "Good": return MetricColors.good
        case "Steps": return MetricColors.steps
        case "Stress": return MetricColors.stress
        default: return MetricColors.sleepLight
        }
    }

    /// `Narrative.Rating`'s colours (Narrative.kt:14-19): Optimal good, Good hrv, Fair warn,
    /// Pay attention stress.
    static func rating(_ label: String) -> Color {
        switch label {
        case "Optimal": return MetricColors.good
        case "Good": return MetricColors.hrv
        case "Fair": return MetricColors.warn
        default: return MetricColors.stress
        }
    }
}

// MARK: - The screen

struct RecoveryScreen: View {
    @ObservedObject var health: HealthModel

    /// A body-signal tile opens its metric, as Compose's `onOpenMetric` does (GlowRecoveryScreen.kt:93).
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
        // Recovery is a `GlowScreenList` on Android, which at the sizes this app runs at (portrait
        // only) pads its cards with the tight sides.
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
        .onAppear { health.reloadRecovery() }
    }

    @ViewBuilder
    private func content(_ day: DayUiState) -> some View {
        let rec = RecoveryGlow.shared.forDay(state: day, recovery: health.recoveryResult)
        VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
            if let rec {
                let ready = rec.readiness
                // A body-signal alert leads: it matters more than either score (:87-88).
                if let body = ready.bodySignals, body.state.name != "Normal" {
                    BodySignalsAlertPanel(model: RecoveryGlow.shared.bodySignals(body: body))
                }
                RecoveryScoreCard(model: RecoveryGlow.shared.card(r: rec, strap: health.strapReadinessRow))
                if !ready.contributors.isEmpty {
                    ContributorsPanel { unfolded in
                        RecoveryGlow.shared.contributors(ready: ready, open: unfolded)
                    }
                }
                RecoveryChargeCard(
                    model: RecoveryGlow.shared.chargeCard(c: rec.charge, dayStart: day.dayStart, dayEnd: day.dayEnd),
                    dayStart: day.dayStart
                )
                if rec.charge.latest != nil {
                    ChargeFlowPanel(model: RecoveryGlow.shared.flow(r: rec))
                }
                SignalTilesPanel(
                    model: RecoveryGlow.shared.signals(state: day, histories: health.signalHistories)
                ) { metric in
                    route = .metric(metric.name)
                }
                HowItWorksPanel(model: RecoveryGlow.shared.howItWorks(ready: ready, strap: health.strapReadinessRow))
            } else {
                // Nothing scored yet for this day (:79-81).
                DotPanel {
                    Text("Calculating…")
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(.white.opacity(0.75))
                }
            }
        }
    }
}

// MARK: - The score card (RecoveryCard, GlowRecoveryScreen.kt:105)

/// The number, its sentence and the last seven days underneath, coloured by how the day looks.
private struct RecoveryScoreCard: View {
    var model: RecoveryCardModel

    /// `GlowStyle.Bloom.copy(...)` (:109-114): the glow sits low and central in the card.
    private var style: GlowStyle {
        var style = GlowStyle.bloom
        style.color = MetricColors.overview(model.tone)
        style.coreShape = .radial
        style.coreX = 0.5
        style.coreY = 0.8
        style.coreWidth = 0.95
        style.coreHeight = 0.75
        style.coreBlur = 30
        style.toneWidth = 16
        style.toneShift = -10
        style.glowStrength = 0.6
        style.bevel = 0.6
        style.motion = .breath(perMinute: 5)
        // The slab: card F6 draws it in GlowCard, so the style only asks for it, as Compose does.
        style.depth = 6
        return style
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader("Recovery", trailing: model.trailing)
                    .accessibilityIdentifier("recovery.title")
                if let score = model.score?.intValue {
                    GlowHeroNumber("\(score)", unit: "/ 100", facts: model.heroFacts)
                    Color.clear.frame(height: 12)
                    if let body = model.heroBody {
                        Text(body)
                            .appTextStyle(AppTypography.bodyMedium)
                            .foregroundStyle(.white.opacity(0.85))
                    }
                    if model.hasTrend {
                        GlowChartGap()
                        GlowTrend(
                            days: model.trend.map { GlowDay(label: $0.label, value: $0.value?.floatValue) },
                            height: GlowLayout.chartHeight
                        )
                    }
                } else {
                    // No score: why, what the strap says instead, and its fallbacks (:129-155).
                    Color.clear.frame(height: GlowLayout.titleGap)
                    Text(model.body ?? "")
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(.white.opacity(0.85))
                        // The learning state has an id of its own, so a test can find the state
                        // without matching the sentence's words.
                        .accessibilityIdentifier(model.learning ? "recovery.learning" : "recovery.body")
                    if let sleep = model.sleepNote {
                        QuietNote(text: sleep)
                    }
                    if let strap = model.strapValue {
                        Color.clear.frame(height: 12)
                        Fact(label: "Strap's estimate", value: strap)
                        if let note = model.strapNote {
                            QuietNote(text: note)
                        }
                    }
                    ForEach(model.notes, id: \.self) { note in
                        QuietNote(text: note)
                    }
                }
            }
        }
    }
}

// MARK: - The body-signal alert (BodySignalsPanel, GlowRecoveryScreen.kt:175)

/// Off baseline: Watch is a quiet note, Alert a warning (and a push, sent once from the sync).
private struct BodySignalsAlertPanel: View {
    var model: BodySignalsModel

    var body: some View {
        DotPanel {
            DotHeader(model.title, trailing: model.trailing, warning: model.alert)
            Text(model.text)
                .appTextStyle(AppTypography.bodyMedium)
                .foregroundStyle(.white.opacity(0.85))
            if let cause = model.cause {
                QuietNote(text: cause)
            }
            QuietNote(text: model.disclaimer)
        }
    }
}

// MARK: - What drove it (ContributorsPanel, GlowRecoveryScreen.kt:296)

/// The score's contributors, grouped by what they are about, folded to the heaviest few until the
/// panel is tapped. The model is rebuilt for the open state, so the fold rule stays Kotlin's; the
/// builder keeps `Recovery.Readiness`, whose exported Swift name this file would otherwise have to
/// spell, out of the signature (Today's screens pass it through the same way).
private struct ContributorsPanel: View {
    var build: (Bool) -> ContributorsModel

    @State private var open = false

    var body: some View {
        let model = build(open)
        let unfold: (() -> Void)? = model.foldable ? { open = !open } : nil
        let expanded: Bool? = model.foldable ? open : nil
        return DotPanel(onClick: unfold) {
            DotHeader("What drove it", trailing: model.trailing, expanded: expanded)
            ForEach(Array(model.groups.enumerated()), id: \.offset) { _, group in
                if group.afterDivider {
                    DotDivider()
                }
                Text(group.name)
                    .appTextStyle(AppTypography.labelSmall)
                    .foregroundStyle(.white.opacity(0.55))
                ForEach(group.rows, id: \.label) { row in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 0) {
                            Text(row.label)
                                .appTextStyle(AppTypography.bodyMedium)
                                .foregroundStyle(glowInk)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(row.rating)
                                .appTextStyle(AppTypography.titleSmall)
                                .foregroundStyle(RecoveryColors.rating(row.rating))
                        }
                        QuietNote(text: row.detail)
                        GlowBar(fraction: row.fraction, color: RecoveryColors.rating(row.rating))
                    }
                }
            }
            ForEach(model.notes, id: \.self) { note in
                QuietNote(text: note)
            }
        }
    }
}

// MARK: - Charge (ChargeCard, GlowRecoveryScreen.kt:191)

/// Charge: the battery now and the day's curve, in the ember glow tinted by the level.
private struct RecoveryChargeCard: View {
    var model: ChargeCardModel
    /// The day's first second, so a point's touch readout can say its clock time (:211-213).
    var dayStart: Int64

    private var style: GlowStyle {
        var style = GlowStyle.ember
        style.color = MetricColors.chargeAt(model.level?.doubleValue)
        style.motion = .breath(perMinute: 4)
        return style
    }

    /// `List<Float?>` crosses as `[Any]`: each gap is the `NSNumber` Kotlin boxes a Float into.
    private var values: [Float?] {
        model.values.map { ($0 as? NSNumber)?.floatValue }
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader("Charge", trailing: model.trailing)
                    .accessibilityIdentifier("recovery.charge")
                if model.hint != nil || model.value == nil {
                    Color.clear.frame(height: GlowLayout.titleGap)
                    Text(model.hint ?? "")
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(.white.opacity(0.85))
                } else {
                    GlowHeroNumber(model.value ?? "", unit: model.unit, facts: model.facts)
                    GlowChartGap()
                    GlowLineChart(
                        values: values,
                        height: GlowLayout.chartHeight,
                        labels: model.labels,
                        showAverage: false,
                        yRange: 0...100,
                        describe: { index, value in
                            RecoveryGlow.shared.chargeReadout(
                                dayStart: dayStart, index: Int32(index), value: value
                            )
                        }
                    )
                }
            }
        }
    }
}

// MARK: - Where your charge went (ChargeFlowPanel, GlowRecoveryScreen.kt:237)

private struct ChargeFlowPanel: View {
    var model: ChargeFlowModel

    var body: some View {
        DotPanel {
            DotHeader("Where your charge went")
            ForEach(model.rows, id: \.label) { row in
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 0) {
                        Text(row.label)
                            .appTextStyle(AppTypography.bodyMedium)
                            .foregroundStyle(.white.opacity(0.85))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(row.value)
                            .appTextStyle(AppTypography.titleSmall)
                            .foregroundStyle(glowInk)
                    }
                    GlowBar(fraction: row.fraction, color: RecoveryColors.flow(row.accent))
                }
            }
            if let highLow = model.highLow {
                QuietNote(text: highLow)
            }
            QuietNote(text: model.stressNote)
        }
    }
}

// MARK: - Body signals (SignalsPanel, GlowRecoveryScreen.kt:388)

/// The six body signals as a two-column grid: value, a word against your normal, and the week.
private struct SignalTilesPanel: View {
    var model: SignalsModel
    var onOpenMetric: (Metric) -> Void

    var body: some View {
        DotPanel {
            DotHeader("Body signals", trailing: "vs. your normal")
            // `TileGrid` needs identity; a statistic's metric names it, and each appears once.
            TileGrid(items) { item in
                SignalTileView(tile: item.tile, onOpen: { onOpenMetric(item.tile.metric) })
            }
            if let note = model.note {
                QuietNote(text: note)
            }
        }
    }

    private var items: [SignalItem] {
        model.tiles.map { SignalItem(id: $0.metric.name, tile: $0) }
    }
}

private struct SignalItem: Identifiable {
    let id: String
    let tile: BodySignalTile
}

/// One tile (SignalTile, GlowRecoveryScreen.kt:397): the value with its unit, the status pill, and
/// the week as a sparkline.
private struct SignalTileView: View {
    var tile: BodySignalTile
    var onOpen: () -> Void

    private var accent: Color { RecoveryColors.tile(tile.accent) }

    /// The pill keeps its slot even with no word yet, so side-by-side tiles share a line (:422-426).
    private var pillColor: Color {
        tile.word == nil || tile.word == "Normal" ? trendGood : trendBad
    }

    private var week: [Float?] {
        tile.week.map { ($0 as? NSNumber)?.floatValue }
    }

    var body: some View {
        GlowTile(tile.label, accent: accent, onClick: onOpen) {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(tile.value ?? "—")
                    .appTextStyle(AppTypography.titleLarge)
                    .foregroundStyle(glowInk)
                    .lineLimit(1)
                if tile.value != nil && !tile.unit.isEmpty {
                    Text(tile.unit)
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(.white.opacity(0.6))
                        .lineLimit(1)
                        .padding(.leading, 3)
                }
            }
            .padding(.top, 8)
            StatusPill(tile.word ?? "Normal", color: pillColor)
                .padding(.top, 4)
                .opacity(tile.word == nil ? 0 : 1)
            Spacer(minLength: 0)
            Sparkline(values: week, color: accent)
                .padding(.top, 10)
        }
    }
}

// MARK: - How it works (HowItWorksPanel, GlowRecoveryScreen.kt:466)

/// Folded away, with the strap's own score inside as a comparison.
private struct HowItWorksPanel: View {
    var model: HowItWorksModel

    @State private var open = false

    var body: some View {
        DotPanel(onClick: { open.toggle() }) {
            DotHeader("How it works", expanded: open)
            if open {
                Text(model.explainer)
                    .appTextStyle(AppTypography.bodyMedium)
                    .foregroundStyle(.white.opacity(0.85))
                Text(model.chargeExplainer)
                    .appTextStyle(AppTypography.bodyMedium)
                    .foregroundStyle(.white.opacity(0.85))
                if let strapValue = model.strapValue {
                    DotDivider()
                    HStack(alignment: .bottom) {
                        Fact(label: model.strapLabel ?? "", value: strapValue)
                        Spacer(minLength: 0)
                        if let ours = model.oursValue {
                            Fact(label: model.oursLabel ?? "", value: ours)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    if let note = model.strapNote {
                        QuietNote(text: note)
                    }
                }
                QuietNote(text: model.disclaimer)
            }
        }
    }
}
