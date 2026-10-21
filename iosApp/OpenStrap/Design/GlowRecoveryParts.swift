import SwiftUI
import OpenStrapShared

/*
 * The parts of `GlowRecoveryScreen.kt` that other screens share, ported by card U5 because Today's
 * top card uses them: the quiet progress bar under a score, the small note, and the label-and-value
 * pair the weekly recap is built from. Card U6 ports the rest of that file.
 */

/// A faint solid track with the first `fraction` of it a glowing line in `color`, and a dot where
/// it ends, like the knob on a sleep score's arc. (`GlowBar`, GlowRecoveryScreen.kt:272)
struct GlowBar: View {
    var fraction: Float
    var color: Color

    var body: some View {
        Canvas { context, size in
            let y = size.height / 2
            let r: CGFloat = 5
            // Inset by the dot's halo, so a full or empty bar keeps its dot inside the panel.
            let x0 = r
            let x1 = size.width - r
            let track = Path { path in
                path.move(to: CGPoint(x: x0, y: y))
                path.addLine(to: CGPoint(x: x1, y: y))
            }
            context.stroke(
                track,
                with: .color(.white.opacity(0.10)),
                style: StrokeStyle(lineWidth: 2, lineCap: .round)
            )
            let f = min(max(fraction, 0), 1)
            guard f > 0 else { return }
            let end = CGPoint(x: x0 + (x1 - x0) * CGFloat(f), y: y)
            let filled = Path { path in
                path.move(to: CGPoint(x: x0, y: y))
                path.addLine(to: end)
            }
            context.stroke(
                filled,
                with: .color(color.opacity(0.22)),
                style: StrokeStyle(lineWidth: 6, lineCap: .round)
            )
            context.stroke(
                filled,
                with: .color(color),
                style: StrokeStyle(lineWidth: 2.2, lineCap: .round)
            )
            context.fill(
                Path(ellipseIn: CGRect(x: end.x - r, y: end.y - r, width: r * 2, height: r * 2)),
                with: .color(color.opacity(0.30))
            )
            context.fill(
                Path(ellipseIn: CGRect(x: end.x - 3, y: end.y - 3, width: 6, height: 6)),
                with: .color(color)
            )
            context.fill(
                Path(ellipseIn: CGRect(x: end.x - 1.2, y: end.y - 1.2, width: 2.4, height: 2.4)),
                with: .color(.white)
            )
        }
        .frame(maxWidth: .infinity)
        .frame(height: 12)
        .accessibilityHidden(true)
    }
}

/// The small muted line under a panel's title or rows. (`Note`, GlowRecoveryScreen.kt:295)
struct QuietNote: View {
    var text: String

    var body: some View {
        Text(text)
            .appTextStyle(AppTypography.bodySmall)
            .foregroundStyle(glowInk.opacity(0.6))
    }
}

/// A label with its value underneath: the weekly recap's four figures.
/// (`Fact`, GlowRecoveryScreen.kt:457)
struct Fact: View {
    var label: String
    var value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .appTextStyle(AppTypography.bodySmall)
                .foregroundStyle(glowInk.opacity(0.6))
            Text(value)
                .appTextStyle(AppTypography.titleLarge)
                .foregroundStyle(glowInk)
        }
    }
}
