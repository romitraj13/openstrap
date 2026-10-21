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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                Text("Design lab")
                    .appTextStyle(AppTypography.headlineSmall)
                    .foregroundStyle(ThemeColors.onBackground)
                    .accessibilityIdentifier("lab.title")

                typeSection
                colourSection
                cardSection
                motionSection
                dotMatrixSection
                iconSection
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .glowScrollPhasePause()
        .background(ThemeColors.background)
        .accessibilityIdentifier("lab.scroll")
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
            VStack(spacing: 14) {
                ForEach(GlowStyle.presets) { preset in
                    glowCard(preset.style, title: preset.name)
                }
                ForEach(chartStyles) { chart in
                    glowCard(chart.style, title: chart.name)
                }
            }
        }
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
            VStack(spacing: 14) {
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
#endif
