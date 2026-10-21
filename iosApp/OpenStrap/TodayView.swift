import Charts
import Foundation
import OpenStrapShared
import SwiftUI

/// Today: Recovery's score and why, last night's sleep, the day's steps and the last sync — the
/// Android Today and Recovery cards, with the wording the shared Kotlin carries.
///
/// Card U4 puts this under the app shell's shared header and pull to sync, so the view is the
/// screen's content only; its cards change in card U5.
struct TodayView: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let recovery = health.recovery {
                RecoveryCard(recovery: recovery)
            } else {
                Text("Calculating…")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 60)
            }

            if let today = health.today {
                SleepSummary(today: today)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear { health.reload() }
    }
}

/// The Recovery score, its sentence, the week behind it, and the two folded panels that explain it.
private struct RecoveryCard: View {
    let recovery: RecoverySnapshot

    @State private var showDrivers = false
    @State private var showExplanation = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("Recovery").font(.headline)
                Spacer()
                if !recovery.stateLabel.isEmpty {
                    Text(recovery.stateLabel).font(.footnote).foregroundStyle(.secondary)
                }
            }

            if let score = recovery.score {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("\(score.intValue)")
                        .font(.system(size: 56, weight: .semibold, design: .rounded))
                        .accessibilityIdentifier("todayScore")
                    Text("/ 100").foregroundStyle(.secondary)
                }
                Text(recovery.headline).font(.title3)
                Text(recovery.body).foregroundStyle(.secondary)
            } else {
                Text(recovery.emptyText)
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("todayEmpty")
            }

            // The seven days ending today, as the Android card draws them.
            if recovery.trend.contains(where: { $0.value != nil }) {
                Chart(trendPoints, id: \.label) { point in
                    LineMark(x: .value("Day", point.label), y: .value("Score", point.value))
                        .foregroundStyle(.tint)
                    PointMark(x: .value("Day", point.label), y: .value("Score", point.value))
                        .foregroundStyle(.tint)
                }
                .chartYScale(domain: 0...100)
                .frame(height: 110)
                .accessibilityIdentifier("recoveryTrendChart")
            }

            if !recovery.contributors.isEmpty {
                DisclosureGroup(isExpanded: $showDrivers) {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(groups, id: \.self) { group in
                            Text(group.uppercased())
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            ForEach(rows(in: group), id: \.label) { contributor in
                                ContributorRow(contributor: contributor)
                            }
                        }
                        ForEach(recovery.notes, id: \.self) { note in
                            Text(note).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.top, 8)
                } label: {
                    Text("What drove it").font(.subheadline)
                }
            }

            DisclosureGroup(isExpanded: $showExplanation) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(recovery.explanation)
                    Text(recovery.chargeExplanation)
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 8)
            } label: {
                Text("How it works").font(.subheadline)
            }

            ChargeBlock(charge: recovery.charge)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }

    /// Only the days with a score: a line through holes would invent numbers.
    private var trendPoints: [TrendPoint] {
        recovery.trend.compactMap { day in
            day.value.map { TrendPoint(label: Self.shortDay(day.date), value: $0.intValue) }
        }
    }

    private var groups: [String] {
        var seen: [String] = []
        for contributor in recovery.contributors where !seen.contains(contributor.group) {
            seen.append(contributor.group)
        }
        return seen
    }

    private func rows(in group: String) -> [RecoveryContributor] {
        recovery.contributors
            .filter { $0.group == group }
            .sorted { $0.fraction > $1.fraction }
    }

    private static func shortDay(_ isoDate: String) -> String {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        guard let date = parser.date(from: isoDate) else { return isoDate }
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "EE"
        return String(formatter.string(from: date).prefix(2))
    }
}

private struct TrendPoint {
    let label: String
    let value: Int
}

private struct ContributorRow: View {
    let contributor: RecoveryContributor

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(contributor.label)
                Spacer()
                Text(contributor.rating)
                    .foregroundStyle(ScreenFormat.ratingColor(contributor.rating))
            }
            .font(.subheadline)
            Text(contributor.detail).font(.footnote).foregroundStyle(.secondary)
            ProgressView(value: Double(contributor.fraction))
                .tint(ScreenFormat.ratingColor(contributor.rating))
        }
    }
}

/// Charge, and where it went: the Android Charge card's numbers and its two hints.
private struct ChargeBlock: View {
    let charge: ChargeSummary

    var body: some View {
        Divider()
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("Charge").font(.headline)
                Spacer()
                if let atWake = charge.atWake {
                    Text("\(atWake.intValue) at waking").font(.footnote).foregroundStyle(.secondary)
                }
            }
            if let latest = charge.latest {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("\(latest.intValue)")
                        .font(.system(size: 40, weight: .semibold, design: .rounded))
                        .accessibilityIdentifier("chargeValue")
                    Text("/ 100").foregroundStyle(.secondary)
                }
                Text(charge.headline).font(.subheadline)
                Text(charge.body).font(.footnote).foregroundStyle(.secondary)
                Chart(flows, id: \.label) { flow in
                    BarMark(
                        x: .value("Amount", flow.fraction),
                        y: .value("Where", flow.label)
                    )
                    .foregroundStyle(flow.color)
                    .annotation(position: .trailing) {
                        Text(flow.text).font(.caption2)
                    }
                }
                .chartXAxis(.hidden)
                .chartYAxis(.hidden)
                .frame(height: 90)
                .accessibilityIdentifier("chargeChart")
                if let highest = charge.highest, let lowest = charge.lowest {
                    Text("Highest \(highest.intValue) · lowest \(lowest.intValue) this day")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            } else {
                Text(charge.hint).foregroundStyle(.secondary)
            }
            if !charge.stressThresholdText.isEmpty {
                Text(charge.stressThresholdText).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    /// The four parts of the day's charge, as shares of its biggest move.
    private var flows: [ChargeFlow] {
        let drained = Double(charge.drainedExercise + charge.drainedStress + charge.drainedLiving)
        let total = max(max(Double(charge.recharged), drained), 1)
        func entry(_ label: String, _ value: Int32, _ positive: Bool, _ color: Color) -> ChargeFlow {
            ChargeFlow(
                label: label,
                fraction: Double(value) / total,
                color: color,
                text: value == 0 ? "0" : "\(positive ? "+" : "−")\(abs(value))"
            )
        }
        return [
            entry("Sleep and calm rest", charge.recharged, true, .green),
            entry("Movement and exercise", charge.drainedExercise, false, .blue),
            entry("Physiological stress (estimate)", charge.drainedStress, false, .orange),
            entry("Being awake", charge.drainedLiving, false, .gray),
        ]
    }
}

private struct ChargeFlow {
    let label: String
    let fraction: Double
    let color: Color
    let text: String
}

/// Last night in one line: the score, the hours, and the stage split.
private struct SleepSummary: View {
    let today: TodaySnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Sleep").font(.headline)
                Spacer()
                if let score = today.sleepScore {
                    Text("\(score.intValue)").font(.title3).accessibilityIdentifier("todaySleepScore")
                }
            }
            Text("You slept \(ScreenFormat.duration(minutes: Int(today.sleepMinutes)))")
                .font(.title3)
            StageBar(stages: today.stages)
            HStack(spacing: 12) {
                Stat(label: "Resting HR", value: today.restingHr.map { "\($0.intValue)" } ?? "—", unit: "bpm")
                Stat(label: "Blood oxygen", value: today.averageSpo2.map { String(format: "%.0f", Double($0.floatValue)) } ?? "—", unit: "%")
                Stat(label: "Steps", value: "\(today.steps)", unit: "")
            }
            Text("Last sync \(ScreenFormat.time(today.lastSync))")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
}

/// The night's four stages as one bar, with the minutes the Sleep screen totals.
struct StageBar: View {
    let stages: StageTotals

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            GeometryReader { geometry in
                HStack(spacing: 1) {
                    ForEach(slices) { slice in
                        Rectangle()
                            .fill(ScreenFormat.stageColor(slice.name))
                            .frame(width: geometry.size.width * slice.fraction)
                    }
                }
            }
            .frame(height: 12)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            HStack(spacing: 12) {
                ForEach(slices) { slice in
                    HStack(spacing: 3) {
                        Circle().fill(ScreenFormat.stageColor(slice.name)).frame(width: 6, height: 6)
                        Text("\(ScreenFormat.stageLabel(slice.name)) \(slice.minutes)m").font(.caption2)
                    }
                }
            }
        }
    }

    private var slices: [StageSlice] {
        let values = [
            StageSlice(name: "deep", minutes: Int(stages.deepMinutes)),
            StageSlice(name: "light", minutes: Int(stages.lightMinutes)),
            StageSlice(name: "rem", minutes: Int(stages.remMinutes)),
            StageSlice(name: "awake", minutes: Int(stages.awakeMinutes)),
        ]
        let total = max(values.reduce(0) { $0 + $1.minutes }, 1)
        return values.map { slice in
            StageSlice(name: slice.name, minutes: slice.minutes, fraction: Double(slice.minutes) / Double(total))
        }
    }
}

private struct StageSlice: Identifiable {
    var id: String { name }
    let name: String
    let minutes: Int
    var fraction: Double = 0
}

struct Stat: View {
    let label: String
    let value: String
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased()).font(.caption2).foregroundStyle(.secondary)
            Text(value).font(.title3).monospacedDigit()
            if !unit.isEmpty {
                Text(unit).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
