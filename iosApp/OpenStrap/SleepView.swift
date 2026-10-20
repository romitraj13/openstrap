import Charts
import Foundation
import OpenStrapShared
import SwiftUI

/// Last night: the score, the sentence, the night as a hypnogram, and the stage totals.
struct SleepView: View {
    @ObservedObject var health: HealthModel

    var body: some View {
        NavigationStack {
            ScrollView {
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
                .padding()
            }
            .navigationTitle("Sleep")
            .onAppear {
                health.prepareDemoData()
                health.reload()
            }
            .refreshable { health.reload() }
        }
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
