import OpenStrapShared
import SwiftUI

/*
 * One workout, ported from `GlowWorkoutScreen` in `ui/glow/GlowWorkoutScreen.kt` (card U8).
 *
 * Its heart rate glows: the average, duration and peak, and every second of it. How fast you
 * recovered, time in each zone and the strap's training effect sit in dot panels underneath, in the
 * order `GlowWorkoutScreen.kt:50-61` builds them.
 *
 * Every number and sentence comes from `WorkoutGlow` in shared Kotlin, so both builds read the same
 * values; this file draws them. The screen reads the workout with its whole heart-rate stream from
 * `HealthModel.loadWorkout` - the list's copy carries only its last sample.
 */

/// The back arrow, then the sport, the day it ran, the heart-rate card and the four panels.
struct WorkoutScreen: View {
    @ObservedObject var health: HealthModel
    var start: Int64

    @Environment(\.dismiss) private var dismiss

    private var analysis: WorkoutAnalysis? {
        guard let open = health.openWorkout, open.workout.start == start else { return nil }
        return open
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
                // `GlowDetailHeader(a?.sportName ?: "Workout", a?.let { dateLine(it.workout.start) }, onBack)` (:51).
                GlowDetailHeader(
                    title: analysis?.sportName ?? "Workout",
                    subtitle: analysis.map { WorkoutGlow.shared.dateLine(start: $0.workout.start) }
                ) {
                    dismiss()
                }
                if let a = analysis {
                    HeartCard(model: WorkoutGlow.shared.heart(a: a))
                    SummaryPanel(model: WorkoutGlow.shared.summary(a: a))
                    if let recovery = WorkoutGlow.shared.recovery(a: a) {
                        RecoveryPanel(model: recovery)
                    }
                    let zones = WorkoutGlow.shared.zones(a: a)
                    if zones.hasZones {
                        ZonesPanel(model: zones)
                    }
                    EffectPanel(model: WorkoutGlow.shared.effect(a: a))
                } else {
                    // `if (a == null) item { DotPanel { Note("Loading…") } }` (:52-55).
                    DotPanel {
                        QuietNote(text: "Loading…")
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        // Card F9: this screen was built after F4 and never took the bar's clearance, so the end of
        // its scroll (the training effect's footnote) sat under the floating bar.
        .glowClearsNavBar()
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { health.loadWorkout(start) }
    }
}

// MARK: - The heart rate (HeartCard, GlowWorkoutScreen.kt:66)

/// The average inside the glow, the peak and the totals beside it, and every second of the workout.
private struct HeartCard: View {
    var model: WorkoutHeartModel

    private var style: GlowStyle {
        var style = GlowStyle.chart(MetricColors.heart)
        // `GlowMotion.Heartbeat(w.averageHeartRate ?: 100)` (:68); Kotlin's Int arrives as Int32.
        style.motion = .heartbeat(bpm: Int(model.heartbeat))
        return style
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader("Heart rate", trailing: model.trailing)
                GlowHeroNumber(model.average, unit: "bpm avg", facts: model.facts)
                if !model.hasHeartRate {
                    Color.clear.frame(height: GlowLayout.titleGap)
                    QuietNote(text: model.emptyNote ?? "")
                } else {
                    GlowChartGap()
                    // No average line: it would count the recovery tail and disagree with the strap's
                    // average above (:96-99).
                    GlowLineChart(
                        values: model.values.map { ($0 as? NSNumber)?.floatValue },
                        height: GlowLayout.chartHeight,
                        labels: model.labels,
                        showAverage: false,
                        color: MetricColors.heart,
                        describe: { index, value in
                            WorkoutGlow.shared.heartReadout(
                                start: model.start, index: Int32(index), value: value
                            )
                        }
                    )
                    if let note = model.tailNote {
                        Color.clear.frame(height: 10)
                        QuietNote(text: note)
                    }
                }
            }
        }
    }
}

// MARK: - How the time split (SummaryPanel, GlowWorkoutScreen.kt:108)

/// Duration, calories and peak are on the card; this is how the time split and what else the strap
/// counted.
private struct SummaryPanel: View {
    var model: WorkoutSummaryModel

    var body: some View {
        DotPanel {
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "Active", value: model.active)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Paused", value: model.paused)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Lowest HR", value: model.lowestHr)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if let note = model.stepsNote {
                QuietNote(text: note)
            }
        }
    }
}

// MARK: - Recovery heart rate (RecoveryPanel, GlowWorkoutScreen.kt:123)

/// How fast your heart rate fell from the end of the workout, rated only when the effort was hard
/// enough for the drop to mean something.
private struct RecoveryPanel: View {
    var model: WorkoutRecoveryModel

    var body: some View {
        DotPanel {
            DotHeader("Recovery heart rate", trailing: model.label)
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "After 1 min", value: model.afterOne)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let afterTwo = model.afterTwo {
                    Fact(label: "After 2 min", value: afterTwo)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            if model.rated, let accent = model.accent {
                GlowBar(fraction: model.fraction, color: ActivityColors.of(accent))
            }
            QuietNote(text: model.note)
        }
    }
}

// MARK: - Zones (ZonesPanel, GlowWorkoutScreen.kt:153)

/// Time in each of the five zones, hottest first, each with its range and a bar of its share.
private struct ZonesPanel: View {
    var model: WorkoutZonesModel

    var body: some View {
        DotPanel {
            DotHeader("Heart-rate zones", trailing: model.trailing)
            ForEach(Array(model.rows.enumerated()), id: \.offset) { _, zone in
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 0) {
                        Circle()
                            .fill(ActivityColors.of(zone.accent))
                            .frame(width: 7, height: 7)
                        Color.clear.frame(width: 8, height: 0)
                        Text(zone.name)
                            .appTextStyle(AppTypography.bodyMedium)
                            .foregroundStyle(glowInk.opacity(0.85))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        QuietNote(text: zone.range)
                        Text(zone.time)
                            .appTextStyle(AppTypography.titleSmall)
                            .foregroundStyle(glowInk)
                            .padding(.leading, 12)
                            .frame(width: 68, alignment: .leading)
                    }
                    GlowBar(fraction: zone.fraction, color: ActivityColors.of(zone.accent))
                }
            }
        }
    }
}

// MARK: - Training effect (EffectPanel, GlowWorkoutScreen.kt:175)

/// The strap's own training effect, VO2 max and recovery time for the workout.
private struct EffectPanel: View {
    var model: WorkoutEffectModel

    var body: some View {
        DotPanel {
            DotHeader("Training effect", trailing: model.trailing)
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "Aerobic", value: model.aerobic)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Anaerobic", value: model.anaerobic)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Load", value: model.load)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "VO₂ max", value: model.vo2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Recovery time", value: model.recoveryTime)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            // The last row of the screen, and the row card F9's end-of-scroll walk measures: the
            // footnote's second line is what U8-workout-3.png showed behind the floating bar.
            QuietNote(text: model.note)
                .accessibilityIdentifier("workout.footnote")
        }
    }
}
