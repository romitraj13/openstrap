import SwiftUI

/*
 * The floating pill tab bar, ported from `app/src/main/kotlin/org/openstrap/app/ui/components/
 * Insights.kt`. Oura's floating pill in black glass: what sits under it shows through blurred and
 * darkened, and the edge catches light along the top.
 *
 * Compose blurs with the haze library. iOS blurs with `.ultraThinMaterial` over black, which is the
 * same effect: the content behind is diffused and then tinted near-black.
 */

/// One destination in `FloatingNavBar`. (`NavItem`, Insights.kt:52)
struct NavItem: Identifiable, Hashable {
    let label: String
    let icon: PhosphorIcon
    let selectedIcon: PhosphorIcon

    var id: String { label }

    init(label: String, icon: PhosphorIcon, selectedIcon: PhosphorIcon? = nil) {
        self.label = label
        self.icon = icon
        self.selectedIcon = selectedIcon ?? icon
    }
}

/// The near-black tint over the blur, and the chip that marks the selected tab (`Insights.kt:163-173`).
private let glassChip = Color.white.opacity(0.1)
private let glassEdge = LinearGradient(
    stops: [
        .init(color: Color.white.opacity(0.2), location: 0),
        .init(color: Color.white.opacity(0.05), location: 0.5),
        .init(color: Color.black.opacity(0.4), location: 1),
    ],
    startPoint: .top,
    endPoint: .bottom
)
private let glassChipEdge = LinearGradient(
    colors: [Color.white.opacity(0.16), Color.white.opacity(0)],
    startPoint: .top,
    endPoint: .bottom
)

/// The black glass surface: a blur the tint darkens until only colour shows through.
struct GlowGlassBackground: View {
    var body: some View {
        ZStack {
            Rectangle().fill(.ultraThinMaterial)
            Color(hex: 0xFF0B0B0D).opacity(0.7)
        }
    }
}

/// Compose's `Motion.tween(Motion.SWIFT)` (240 ms).
private let navMotion = Animation.easeInOut(duration: 0.24)

/// `FloatingNavBar`: the capsule bar with the Android look, the selected pill sliding to the tab.
/// (`Insights.kt:59`)
struct FloatingNavBar: View {
    var items: [NavItem]
    var selected: Int
    var onSelect: (Int) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let gap: CGFloat = 2

    init(items: [NavItem], selected: Int, onSelect: @escaping (Int) -> Void) {
        self.items = items
        self.selected = selected
        self.onSelect = onSelect
    }

    var body: some View {
        HStack(spacing: gap) {
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                NavCell(item: item, on: index == selected)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .contentShape(Capsule())
                    .onTapGesture { onSelect(index) }
            }
        }
        .padding(6)
        .background {
            GeometryReader { proxy in
                // One pill that slides to the selected tab, rather than each cell lighting up.
                let cell = (proxy.size.width - gap * CGFloat(items.count - 1)) / CGFloat(max(items.count, 1))
                Capsule()
                    .fill(glassChip)
                    .overlay(Capsule().strokeBorder(glassChipEdge, lineWidth: 1))
                    .frame(width: cell, height: proxy.size.height)
                    .offset(x: (cell + gap) * CGFloat(selected))
                    .animation(reduceMotion ? nil : navMotion, value: selected)
            }
        }
        .background {
            GlowGlassBackground()
                .clipShape(Capsule())
        }
        .overlay(Capsule().strokeBorder(glassEdge, lineWidth: 1))
        .frame(maxWidth: 440)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        // Card F4: the bar's top edge, as one 1 pt accessibility element, so a UI test can assert
        // that a scrolled row comes fully clear of the bar. A leaf of its own: an identifier on the
        // bar's container would land on every cell instead (the U4 note on containers).
        .overlay(alignment: .top) {
            Color.clear
                .frame(height: 1)
                .allowsHitTesting(false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Floating tab bar")
                .accessibilityIdentifier("navBar.top")
        }
    }
}

/// `FloatingNavBar` standing up along the left edge, for windows wider than tall: the same glass pill
/// and sliding highlight, giving the height back to the pages. (`Insights.kt:104`)
struct FloatingNavRail: View {
    var items: [NavItem]
    var selected: Int
    var onSelect: (Int) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let gap: CGFloat = 2
    private let railCorner: CGFloat = 40

    init(items: [NavItem], selected: Int, onSelect: @escaping (Int) -> Void) {
        self.items = items
        self.selected = selected
        self.onSelect = onSelect
    }

    var body: some View {
        VStack(spacing: gap) {
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                NavCell(item: item, on: index == selected, centred: true)
                    .frame(maxHeight: .infinity)
                    .frame(maxWidth: .infinity)
                    .contentShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .onTapGesture { onSelect(index) }
            }
        }
        .padding(6)
        .background {
            GeometryReader { proxy in
                let cell = (proxy.size.height - gap * CGFloat(items.count - 1)) / CGFloat(max(items.count, 1))
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(glassChip)
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .strokeBorder(glassChipEdge, lineWidth: 1)
                    )
                    .frame(width: proxy.size.width, height: cell)
                    .offset(y: (cell + gap) * CGFloat(selected))
                    .animation(reduceMotion ? nil : navMotion, value: selected)
            }
        }
        .background {
            GlowGlassBackground()
                .clipShape(RoundedRectangle(cornerRadius: railCorner, style: .continuous))
        }
        .overlay(
            RoundedRectangle(cornerRadius: railCorner, style: .continuous)
                .strokeBorder(glassEdge, lineWidth: 1)
        )
        .frame(width: NavRailWidth - 20)
        .frame(maxHeight: 440)
        .padding(.horizontal, 10)
        .padding(.vertical, 20)
    }
}

/// How much of the window's width `FloatingNavRail` takes, its margins included.
let NavRailWidth: CGFloat = 92

/// One destination's icon and label, tinted for whether it's `on`. (`NavCell`, Insights.kt:147)
private struct NavCell: View {
    var item: NavItem
    var on: Bool
    var centred = false

    var body: some View {
        VStack(spacing: 0) {
            (on ? item.selectedIcon : item.icon).image
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
                .scaleEffect(on ? 1.08 : 1)
                .foregroundStyle(on ? glowInk : glowInk.opacity(0.55))
            Text(item.label)
                .appTextStyle(AppTypography.labelSmall)
                .foregroundStyle(on ? glowInk : glowInk.opacity(0.55))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(maxHeight: centred ? .infinity : nil, alignment: centred ? .center : .top)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(item.label)
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
    }
}
