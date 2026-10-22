import SwiftUI

/*
 * The two pieces every pushed glow screen shares, from `GlowMetricDetail.kt`'s "Shared pieces"
 * section (`GlowDetailHeader`, :59-67; `GlowSegments`, :77-96), which `GlowWorkoutScreen.kt:51`,
 * `GlowWorkoutsScreen.kt:201` and the metric detail all use.
 */

/// Back arrow, title and subtitle, at the top of a pushed screen. (`GlowDetailHeader`, GlowMetricDetail.kt:61)
struct GlowDetailHeader: View {
    var title: String
    var subtitle: String?
    var onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                PhosphorIcon.arrowLeft.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .foregroundStyle(glowInk)
                    // Compose's IconButton keeps a 48 dp touch target round a 24 dp icon.
                    .frame(width: 48, height: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .appTextStyle(titleStyle)
                    .foregroundStyle(glowInk)
                if let subtitle {
                    Text(subtitle)
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(glowInk.opacity(0.6))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
    }

    /// `GlowType.title.copy(fontSize = GlowType.title.fontSize * 1.25f)` (GlowMetricDetail.kt:64):
    /// the same face, a quarter larger, so the pushed screen's title leads its subtitle.
    private var titleStyle: AppTextStyle {
        let base = GlowType.title
        return AppTextStyle(
            name: base.name + " x1.25",
            family: base.family,
            size: base.size * 1.25,
            weight: base.weight,
            tracking: base.tracking,
            lineHeight: base.lineHeight,
            tabularFigures: base.tabularFigures
        )
    }
}

/// A row of choices on a dotted pill; the chosen one is a soft white pill.
/// (`GlowSegments`, GlowMetricDetail.kt:79)
///
/// Compose's `pressClickable(..., scale = 0.97f)` squashes the chosen pill under the finger. That is
/// a scale, which standing instruction 7 allows; with Reduce Motion on it does not move.
struct GlowSegments: View {
    var options: [String]
    var selected: Int
    var onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                let on = index == selected
                Button {
                    onSelect(index)
                } label: {
                    Text(label)
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(on ? Color.white : Color.white.opacity(0.6))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(on ? Color.white.opacity(0.16) : Color.clear, in: Capsule())
                        .contentShape(Capsule())
                }
                .buttonStyle(GlowSegmentStyle())
                .accessibilityIdentifier("detail.range.\(label)")
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(4)
        .background(Color.white.opacity(0.06), in: Capsule())
    }
}

/// The press squash of one segment: 0.97 while the finger is down, and still under Reduce Motion.
private struct GlowSegmentStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(!reduceMotion && configuration.isPressed ? 0.97 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
