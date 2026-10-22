import SwiftUI

/*
 * The title row a pushed glow screen wears: the back arrow, the screen's title in the glow title
 * face at 1.25×, and the day (or another subtitle) under it. Ported from `GlowDetailHeader`
 * (`ui/glow/GlowMetricDetail.kt:59-67`, card U8), which `GlowWorkoutScreen.kt:51` and
 * `GlowWorkoutsScreen.kt:201` both use; card U9's metric detail uses the same row.
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
