import SwiftUI

/*
 * The quiet half of the glow language: plain black panels drawn with dots. Use these for lists and
 * secondary charts so the few glow cards on a screen stay special.
 *
 * Ported from `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowQuiet.kt`.
 */

private let panelCorner: CGFloat = 28
private let paneShape = RoundedRectangle(cornerRadius: panelCorner, style: .continuous)

/// Taps only when there is an action, so a panel that isn't pressable leaves its contents alone.
struct OptionalTap: ViewModifier {
    var action: (() -> Void)?
    var shape: AnyShape?

    @ViewBuilder
    func body(content: Content) -> some View {
        if let action {
            if let shape {
                content.contentShape(shape).onTapGesture { action() }
            } else {
                content.onTapGesture { action() }
            }
        } else {
            content
        }
    }
}

/// A black panel outlined in a row of faint dots instead of a border.
/// (`DotPanel`, GlowQuiet.kt:52)
struct DotPanel<Content: View>: View {
    @Environment(\.glowCardPadding) private var cardPadding
    private let onClick: (() -> Void)?
    private let content: () -> Content

    init(onClick: (() -> Void)? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.onClick = onClick
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(cardPadding)
        .background {
            // A clear step above the charts' dotted gridlines (16%), still well under the text.
            paneShape.strokeBorder(
                Color.white.opacity(0.3),
                style: StrokeStyle(lineWidth: 1.6, lineCap: .round, dash: [0.1, 7])
            )
            .padding(1)
        }
        .modifier(OptionalTap(action: onClick, shape: AnyShape(paneShape)))
    }
}

/// The glass surface every control inside a dotted panel wears (rows, pills, buttons): a faint white
/// fill and hairline, so it reads as something to press against the panel's bare black.
/// (`Modifier.glass`, GlowQuiet.kt:116)
struct GlassSurface: ViewModifier {
    var shape: AnyShape = AnyShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

    func body(content: Content) -> some View {
        content
            .background(Color.white.opacity(0.08), in: shape)
            .overlay(shape.stroke(Color.white.opacity(0.12), lineWidth: 1))
    }
}

extension View {
    func glass<S: Shape>(_ shape: S) -> some View {
        modifier(GlassSurface(shape: AnyShape(shape)))
    }
}

/// The shape of a glass row inside a dotted panel.
let glassRowShape = RoundedRectangle(cornerRadius: 16, style: .continuous)

/// The title row of a dotted panel: the title, an optional note on the right and an optional chevron.
/// No leading mark: these panels are the quiet layer, and colour belongs to the data inside them.
/// `warning` puts an amber warning icon in front; `expanded`, when set, shows a caret that turns as a
/// folding panel opens. (`DotHeader`, GlowQuiet.kt:88)
struct DotHeader: View {
    var title: String
    var trailing: String?
    var chevron: Bool
    var warning: Bool
    var expanded: Bool?

    init(
        _ title: String,
        trailing: String? = nil,
        chevron: Bool = false,
        warning: Bool = false,
        expanded: Bool? = nil
    ) {
        self.title = title
        self.trailing = trailing
        self.chevron = chevron
        self.warning = warning
        self.expanded = expanded
    }

    var body: some View {
        HStack(spacing: 0) {
            if warning {
                PhosphorIcon.warning.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .foregroundStyle(MetricColors.warn)
                Color.clear.frame(width: 10, height: 0)
            }
            Text(title)
                .appTextStyle(GlowType.title)
                .foregroundStyle(glowInk)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let trailing {
                Text(trailing)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(glowInk.opacity(0.7))
            }
            if chevron {
                Color.clear.frame(width: 4, height: 0)
                PhosphorIcon.caretRight.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 16, height: 16)
                    .foregroundStyle(glowInk.opacity(0.7))
            }
            if let expanded {
                // In a glass circle, like every other control inside a dotted panel. The turn is
                // instant: standing instruction 7 allows only offset, scale and opacity to animate.
                Color.clear.frame(width: 8, height: 0)
                ZStack {
                    PhosphorIcon.caretDown.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                        .foregroundStyle(glowInk)
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
                .frame(width: 32, height: 32)
                .glass(Circle())
                .accessibilityLabel(expanded ? "Hide" : "Show")
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// A dotted rule between rows. (`DotDivider`, GlowQuiet.kt:126)
struct DotDivider: View {
    var body: some View {
        Canvas { context, size in
            glowDottedLine(
                context,
                from: CGPoint(x: 0, y: size.height / 2),
                to: CGPoint(x: size.width, y: size.height / 2),
                color: .white.opacity(0.16),
                width: 1.5,
                gap: 6
            )
        }
        .frame(maxWidth: .infinity)
        .frame(height: 2)
        .accessibilityHidden(true)
    }
}

/// A small arrow made of five dots, pointing up (`up`) or down. (`DotArrow`, GlowQuiet.kt:138)
struct DotArrow: View {
    var up: Bool
    var color: Color

    init(up: Bool, color: Color) {
        self.up = up
        self.color = color
    }

    var body: some View {
        Canvas { context, size in
            let r: CGFloat = 1.3
            let sx = size.width / 4
            let sy = size.height / 2
            // A chevron: tip in the middle, two dots each side stepping away from it.
            let rows: [CGFloat] = up ? [0, 1, 2] : [2, 1, 0]
            let dots: [(CGFloat, CGFloat)] = [
                (-2, rows[2]), (-1, rows[1]), (0, rows[0]), (1, rows[1]), (2, rows[2]),
            ]
            for (dx, row) in dots {
                let centre = CGPoint(x: size.width / 2 + dx * sx * 0.95, y: r + row * (sy - r))
                context.fill(
                    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2)),
                    with: .color(color)
                )
            }
        }
        .frame(width: 11, height: 9)
        .accessibilityHidden(true)
    }
}

/// Three dots in a straight line, the `DotArrow` for "no change". Same footprint, so rows line up.
/// (`DotLevel`, GlowQuiet.kt:153)
struct DotLevel: View {
    var color: Color

    var body: some View {
        Canvas { context, size in
            let r: CGFloat = 1.3
            let sx = size.width / 4
            for dx in [-1, 0, 1] as [CGFloat] {
                let centre = CGPoint(x: size.width / 2 + dx * sx * 1.4, y: size.height / 2)
                context.fill(
                    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2)),
                    with: .color(color)
                )
            }
        }
        .frame(width: 11, height: 9)
        .accessibilityHidden(true)
    }
}

/// A tappable tile for a grid of statistics: a soft raised surface, the metric's dot and label, and a
/// chevron that says it opens the metric's detail. `content` sits underneath.
/// (`GlowTile`, GlowQuiet.kt:170)
struct GlowTile<Content: View>: View {
    var label: String
    var accent: Color
    var onClick: (() -> Void)?
    private let content: () -> Content

    private let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)

    init(
        _ label: String,
        accent: Color,
        onClick: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.label = label
        self.accent = accent
        self.onClick = onClick
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Circle()
                    .fill(accent)
                    .frame(width: 6, height: 6)
                Color.clear.frame(width: 8, height: 0)
                Text(label)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(glowInk.opacity(0.75))
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if onClick != nil {
                    PhosphorIcon.caretRight.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 14, height: 14)
                        .foregroundStyle(glowInk.opacity(0.55))
                }
            }
            content()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(Color.white.opacity(0.05), in: shape)
        .overlay(shape.stroke(Color.white.opacity(0.08), lineWidth: 1))
        .contentShape(shape)
        .onTapGesture { onClick?() }
    }
}

/// Two tiles a row, evenly spaced; an odd last tile keeps its half width.
/// (`TileGrid`, GlowQuiet.kt:204)
struct TileGrid<Item: Identifiable, Tile: View>: View {
    var items: [Item]
    private let tile: (Item) -> Tile

    init(_ items: [Item], @ViewBuilder tile: @escaping (Item) -> Tile) {
        self.items = items
        self.tile = tile
    }

    private var rows: [[Item]] {
        stride(from: 0, to: items.count, by: 2).map { start in
            Array(items[start..<min(start + 2, items.count)])
        }
    }

    var body: some View {
        Grid(horizontalSpacing: 10, verticalSpacing: 10) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, pair in
                GridRow {
                    ForEach(pair) { item in
                        tile(item)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    }
                    if pair.count == 1 {
                        Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
                    }
                }
            }
        }
    }
}

/// A small rounded tag for a status word ("Normal", "Higher"), tinted in `color`.
/// (`StatusPill`, GlowQuiet.kt:195)
struct StatusPill: View {
    var text: String
    var color: Color

    init(_ text: String, color: Color) {
        self.text = text
        self.color = color
    }

    var body: some View {
        Text(text)
            .appTextStyle(AppTypography.labelSmall)
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.14), in: Capsule())
    }
}

/// One key statistic as a grid tile: the value with its trend arrow, and its usual value underneath.
/// (`DotStatTile`, GlowQuiet.kt:219)
struct DotStatTile: View {
    var label: String
    var accent: Color
    var value: String?
    var unit: String
    var baseline: String?
    /// +1 above baseline, -1 below, 0 or nil when about the same or unknown.
    var direction: Int?
    /// Whether going up is good; colours the arrow.
    var higherIsBetter: Bool?
    var onClick: (() -> Void)?

    private var trendTint: Color {
        guard let higherIsBetter, let direction else { return glowInk.opacity(0.6) }
        return (direction > 0) == higherIsBetter ? trendGood : trendBad
    }

    var body: some View {
        GlowTile(label, accent: accent, onClick: onClick) {
            HStack(spacing: 0) {
                Text(value ?? "—")
                    .appTextStyle(AppTypography.titleLarge)
                    .foregroundStyle(glowInk)
                if value != nil && !unit.isEmpty {
                    Text(unit)
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(glowInk.opacity(0.6))
                        .padding(.leading, 3)
                }
                if value != nil, let direction, direction != 0 {
                    Color.clear.frame(width: 8, height: 0)
                    DotArrow(up: direction > 0, color: trendTint)
                }
            }
            .padding(.top, 8)
            if let baseline {
                Text("usually \(baseline)")
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(glowInk.opacity(0.5))
            }
        }
    }
}

/// The colours a trend arrow takes, as `TrendGood`/`TrendBad` in GlowQuiet.kt:249-250.
let trendGood = MetricColors.good
let trendBad = MetricColors.warn
