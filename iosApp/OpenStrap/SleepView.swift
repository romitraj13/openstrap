import Charts
import Foundation
import OpenStrapShared
import SwiftUI

/// The night's four stages as one bar. Card U5 deleted `TodayView.swift`, which owned this and
/// `Stat`; they stay here because the Sleep screen (until card U7 ports `GlowSleepScreen.kt`) and
/// the unused Heart screen still draw them.
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

/// A label, a value and a unit, in a row of three (the old cards' statistic).
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

/// Last night: the score, the sentence, the night as a hypnogram, and the stage totals.
///
/// Card U4 puts this under the app shell's shared header and pull to sync, so the view is the
/// screen's content only; its cards change in card U7.
struct SleepView: View {
    @ObservedObject var health: HealthModel

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let sleep = health.sleep {
                if sleep.end > sleep.start {
                    Headline(sleep: sleep)
                    Hypnogram(sleep: sleep)
                    Breakdown(sleep: sleep)
                } else {
                    Text("Recovery is scored each morning from the night's sleep. Wear the strap to bed.")
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("sleepEmpty")
                }
            } else {
                Text("Calculating…")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 60)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear { health.reload() }
    }
}

private struct Headline: View {
    let sleep: SleepSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(sleep.score.map { "\($0.intValue)" } ?? "—")
                    .font(.system(size: 48, weight: .semibold, design: .rounded))
                    .accessibilityIdentifier("sleepScore")
                Text("/ 100").foregroundStyle(.secondary)
                Spacer()
                Text("\(ScreenFormat.time(sleep.start)) – \(ScreenFormat.time(sleep.end))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Text(sleep.headline).font(.title3)
            Text(sleep.body).foregroundStyle(.secondary)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
}

/// The night as the strap staged it: awake at the top, deep at the bottom, one block per stage.
private struct Hypnogram: View {
    let sleep: SleepSnapshot

    var body: some View {
        Chart(sleep.segments, id: \.start) { segment in
            RectangleMark(
                xStart: .value("Start", ScreenFormat.date(segment.start)),
                xEnd: .value("End", ScreenFormat.date(segment.end)),
                yStart: .value("Stage", Double(Self.row(segment.stage))),
                yEnd: .value("Stage", Double(Self.row(segment.stage)) + 0.9)
            )
            .foregroundStyle(ScreenFormat.stageColor(segment.stage))
        }
        .chartYScale(domain: 0...4)
        .chartYAxis {
            AxisMarks(values: [0.45, 1.45, 2.45, 3.45]) { value in
                AxisValueLabel {
                    Text(Self.label(value.as(Double.self)))
                        .font(.caption2)
                }
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisValueLabel(format: .dateTime.hour().minute())
                    .font(.caption2)
            }
        }
        .frame(height: 190)
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
        .accessibilityIdentifier("sleepHypnogram")
    }

    private static func row(_ stage: String) -> Int {
        switch stage {
        case "deep": return 0
        case "light": return 1
        case "rem": return 2
        default: return 3
        }
    }

    private static func label(_ value: Double?) -> String {
        switch Int((value ?? -1).rounded()) {
        case 0: return "Deep"
        case 1: return "Light"
        case 2: return "REM"
        case 3: return "Awake"
        default: return ""
        }
    }
}

/// The night's minutes by stage, and how much of it was spent asleep.
private struct Breakdown: View {
    let sleep: SleepSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Asleep").font(.headline)
            Text(ScreenFormat.duration(minutes: Int(sleep.sleepMinutes)))
                .font(.title2)
            StageBar(stages: sleep.stages)
            HStack(spacing: 10) {
                Stat(label: "Deep", value: "\(sleep.stages.deepMinutes)", unit: "min")
                Stat(label: "Light", value: "\(sleep.stages.lightMinutes)", unit: "min")
                Stat(label: "REM", value: "\(sleep.stages.remMinutes)", unit: "min")
                Stat(label: "Awake", value: "\(sleep.stages.awakeMinutes)", unit: "min")
            }
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
}
