import SwiftUI
import OpenStrapShared

/*
 * The night's stages in the glow hypnogram, ported from `GlowSleepScreen.kt` (card U5) because
 * Today's sleep card draws the same chart as the Sleep tab: `SleepGlow` (line 108) and
 * `GlowSleepStages` (line 175), which the Compose file marks "Shared with Today".
 *
 * The rows, names, fractions and readouts come from `TodayGlow.sleepStageChart` (shared Kotlin),
 * so both builds read the same numbers; this file only draws them.
 */

extension GlowStyle {
    /// Sleep glows midnight blue, breathing slowly. Shared with Today's sleep card.
    /// (`SleepGlow = GlowStyle.Midnight.copy(…)`, GlowSleepScreen.kt:108)
    static let sleep: GlowStyle = {
        var style = GlowStyle.midnight
        style.color = MetricColors.sleepDeep
        style.coreX = 0.5
        style.coreY = 0.6
        style.coreWidth = 1.1
        style.coreHeight = 0.9
        style.motion = .breath(perMinute: 5)
        return style
    }()
}

/// Stages as the glow hypnogram: rows Awake to Deep, the clocks under it, and a touch readout
/// naming the stage and its span. (`GlowSleepStages`, GlowSleepScreen.kt:175)
struct GlowSleepStages: View {
    var spans: [StageSpan]
    var height: CGFloat = GlowLayout.chartHeight

    var body: some View {
        let model = chart
        GlowHypnogram(
            stages: model.stages,
            rowNames: model.rowNames,
            height: height,
            labels: model.labels,
            describe: { stage in model.readouts[stage] ?? "" }
        )
    }

    /// One hypnogram stage per block, with the readout the shared chart computed for it. Two blocks
    /// with the same fractions and row would be one stage on the chart, so the first one's readout wins.
    private var chart: (stages: [GlowStage], rowNames: [String], labels: [String], readouts: [GlowStage: String]) {
        let shared = TodayGlow.shared.sleepStageChart(spans: spans)
        var stages: [GlowStage] = []
        var readouts: [GlowStage: String] = [:]
        for block in shared.blocks {
            let stage = GlowStage(from: block.from, to: block.to, row: Int(block.row))
            if readouts[stage] == nil {
                stages.append(stage)
                readouts[stage] = block.readout
            }
        }
        return (stages, shared.rowNames, shared.labels, readouts)
    }
}
