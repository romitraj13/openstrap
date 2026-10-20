import Charts
import Foundation
import OpenStrapShared
import SwiftUI

/// The day's heart rate, and the resting rate the strap reported to compare it with.
struct HeartView: View {
    @ObservedObject var health: HealthModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let heart = health.heart, !heart.samples.isEmpty {
                        Numbers(heart: heart)
                        Curve(heart: heart)
                    } else if health.heart != nil {
                        Text("Heart rate appears once the strap has synced a day.")
                            .foregroundStyle(.secondary)
                            .accessibilityIdentifier("heartEmpty")
                    } else {
                        Text("Calculating…")
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.top, 60)
                    }
                }
                .padding()
            }
            .navigationTitle("Heart")
            .onAppear {
                health.prepareDemoData()
                health.reload()
            }
            .refreshable { health.reload() }
        }
    }
}

private struct Numbers: View {
    let heart: HeartSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(heart.latestHr.map { "\($0.intValue)" } ?? "—")
                    .font(.system(size: 48, weight: .semibold, design: .rounded))
                    .accessibilityIdentifier("heartLatest")
                Text("bpm").foregroundStyle(.secondary)
            }
            Text("Latest today").font(.footnote).foregroundStyle(.secondary)
            HStack(spacing: 10) {
                Stat(
                    label: "Resting HR",
                    value: heart.restingHr.map { "\($0.intValue)" } ?? "—",
                    unit: heart.restingEstimated ? "our estimate" : "the strap's"
                )
                Stat(label: "Lowest", value: "\(lowest)", unit: "bpm")
                Stat(label: "Highest", value: "\(highest)", unit: "bpm")
            }
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }

    private var values: [Int] { heart.samples.map { Int($0.bpm) } }
    private var lowest: Int { values.min() ?? 0 }
    private var highest: Int { values.max() ?? 0 }
}

/// The day as one line, with the resting heart rate drawn across it for comparison.
private struct Curve: View {
    let heart: HeartSnapshot

    var body: some View {
        Chart {
            ForEach(heart.samples, id: \.timestamp) { sample in
                LineMark(
                    x: .value("Time", ScreenFormat.date(sample.timestamp)),
                    y: .value("Beats per minute", Int(sample.bpm))
                )
                .interpolationMethod(.catmullRom)
            }
            if let resting = heart.restingHr {
                RuleMark(y: .value("Resting", Int(resting.intValue)))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .foregroundStyle(.orange)
                    .annotation(position: .top, alignment: .leading) {
                        Text("Resting \(resting.intValue)")
                            .font(.caption2)
                            .foregroundStyle(.orange)
                    }
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 5)) { value in
                AxisValueLabel(format: .dateTime.hour())
                    .font(.caption2)
            }
        }
        .chartYAxis {
            AxisMarks { value in
                AxisGridLine()
                AxisValueLabel { Text("\(value.as(Int.self) ?? 0)") }
            }
        }
        .frame(height: 260)
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20))
        .accessibilityIdentifier("heartChart")
    }
}
