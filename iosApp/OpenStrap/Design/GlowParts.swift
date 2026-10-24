import SwiftUI

/*
 * Decorations and controls that sit on the glow cards, ported from
 * `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowParts.kt`.
 *
 * `DotMatrixText` (GlowParts.kt:64) is already in `DotMatrixText.swift`, ported by card U1 with the
 * same glyphs and the same pitch.
 */

/// A marker on an `ArcTimeline`: a position along the line (0...1) and its colour.
/// (`ArcMark`, GlowParts.kt:105)
struct ArcMark: Hashable {
    var at: Float
    var color: Color

    init(at: Float, color: Color) {
        self.at = at
        self.color = color
    }
}

/// A dotted baseline with arcs bridging consecutive `arcs` marks ("Breath Volume"). Marks are drawn
/// as coloured dots; `arcs` are pairs of positions (0...1) to bridge.
/// (`ArcTimeline`, GlowParts.kt:112)
struct ArcTimeline: View {
    var marks: [ArcMark]
    var arcs: [(from: Float, to: Float)]
    var color: Color = .white
    var height: CGFloat = 40

    var body: some View {
        Canvas { context, size in
            let y = size.height - 6
            glowDottedLine(
                context,
                from: CGPoint(x: 0, y: y),
                to: CGPoint(x: size.width, y: y),
                color: color.opacity(0.65),
                width: 2,
                gap: 9
            )
            for (index, arc) in arcs.enumerated() {
                let left = CGFloat(arc.from) * size.width
                let diameter = CGFloat(arc.to - arc.from) * size.width
                var path = Path()
                path.addArc(
                    center: CGPoint(x: left + diameter / 2, y: y),
                    radius: diameter / 2,
                    startAngle: .degrees(180),
                    endAngle: .degrees(360),
                    clockwise: false
                )
                context.stroke(
                    path,
                    with: .color(color.opacity(index == arcs.count - 1 ? 1 : 0.65)),
                    style: StrokeStyle(lineWidth: 1.5)
                )
            }
            for mark in marks {
                let centre = CGPoint(x: CGFloat(mark.at) * size.width, y: y)
                context.fill(
                    Path(ellipseIn: CGRect(x: centre.x - 4, y: centre.y - 4, width: 8, height: 8)),
                    with: .color(mark.color)
                )
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

/// The glassy pill switch from the "Training" card. (`GlowSwitch`, GlowParts.kt:145)
struct GlowSwitch: View {
    @Binding var checked: Bool
    var accent: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(checked: Binding<Bool>, accent: Color = Color(hex: 0xFF6D7CFF)) {
        self._checked = checked
        self.accent = accent
    }

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule()
                .fill(checked ? accent.opacity(0.55) : Color.white.opacity(0.08))
            Circle()
                .fill(
                    RadialGradient(
                        colors: [.white, Color(hex: 0xFFDCDCEA)],
                        center: .center,
                        startRadius: 0,
                        endRadius: 14
                    )
                )
                .frame(width: 28, height: 28)
                .padding(3)
                .shadow(color: .black.opacity(0.35), radius: 3)
                .offset(x: checked ? 26 : 0)
                .animation(reduceMotion ? nil : .spring(response: 0.25, dampingFraction: 0.6), value: checked)
        }
        .frame(width: 60, height: 34)
        .padding(0)
        .contentShape(Capsule())
        .onTapGesture { checked.toggle() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(checked ? "On" : "Off")
        .accessibilityAddTraits(.isButton)
    }
}
