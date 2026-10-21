import SwiftUI

/// The spacing rules every glow card and dot panel shares, ported from `GlowLayout` in
/// `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowLayout.kt`: same gutter, same inset from the
/// rounded edge, same rhythm from title to number to chart. Dp values are points.
enum GlowLayout {
    /// Space between the screen edge and a card.
    static let gutter: CGFloat = 20
    /// Inside a card: clear of the big rounded corners and the rim light, so text sits on the calm
    /// middle. Dot panels use the same, so text columns align.
    static let cardPadding = EdgeInsets(top: 32, leading: 30, bottom: 26, trailing: 30)
    /// Title row to the hero number.
    static let titleGap: CGFloat = 14
    /// Hero number (or the last line of text) to the chart.
    static let chartGap: CGFloat = 20
    /// Every chart in a card has the same height, axis labels included.
    static let chartHeight: CGFloat = 116
    /// Dot-matrix hero numbers.
    static let heroDot: CGFloat = 4.5
    static let heroGap: CGFloat = 1.8
    /// From this width (an unfolded foldable, a tablet, a phone on its side) the dashboards use two columns.
    static let twoColumnWidth: CGFloat = 600
    /// The widest the cards get; past it the margins grow, so lines stay readable on a tablet.
    static let maxContentWidth: CGFloat = 1040
    /// Space between the two columns: the same as between cards in a column.
    static let columnGap: CGFloat = 12
    /// The arc gauge stops growing here, however wide its card.
    static let gaugeMaxWidth: CGFloat = 440
    /// With two columns on an unfolded foldable each is a little narrower than a phone.
    static let wideGutter: CGFloat = 16
    /// Narrower than this (a small phone, a dashboard beside a detail) cards use the tight sides too.
    static let narrowWidth: CGFloat = 400
    static let wideCardPadding = EdgeInsets(top: 32, leading: 20, bottom: 26, trailing: 20)
}

// MARK: - Card padding

private struct GlowCardPaddingKey: EnvironmentKey {
    static let defaultValue = GlowLayout.cardPadding
}

extension EnvironmentValues {
    /// `LocalCardPadding`: what a card or dot panel pads its content by. Two-column dashboards narrow it.
    var glowCardPadding: EdgeInsets {
        get { self[GlowCardPaddingKey.self] }
        set { self[GlowCardPaddingKey.self] = newValue }
    }
}

// MARK: - Blocks

/// Where a dashboard block goes when there is room for two columns. On a phone every block stacks in
/// the order given.
enum Pane: Hashable {
    case full
    case left
    case right
}

/// One block of a dashboard, in phone order, with the column it takes on a wide screen. A block given
/// a `key` can be scrolled to (see `GlowScreenList`'s `scrollTo`).
struct GlowBlock: Identifiable {
    let pane: Pane
    let key: String?
    let id: String
    let content: AnyView

    static func full<C: View>(key: String? = nil, @ViewBuilder _ content: () -> C) -> GlowBlock {
        GlowBlock(pane: .full, key: key, id: key ?? UUID().uuidString, content: AnyView(content()))
    }

    static func left<C: View>(key: String? = nil, @ViewBuilder _ content: () -> C) -> GlowBlock {
        GlowBlock(pane: .left, key: key, id: key ?? UUID().uuidString, content: AnyView(content()))
    }

    static func right<C: View>(key: String? = nil, @ViewBuilder _ content: () -> C) -> GlowBlock {
        GlowBlock(pane: .right, key: key, id: key ?? UUID().uuidString, content: AnyView(content()))
    }
}

/// A glow dashboard's list, ported from `GlowScreenList` (`GlowLayout.kt:96`). Narrower than
/// `GlowLayout.twoColumnWidth` it is the phone list: every block in order, one under the other.
/// Wider, each run of left and right blocks sits side by side in two columns, and full blocks span
/// both. `scrollTo` scrolls to the block with that key once it is there, then calls `onScrolled`.
struct GlowScreenList: View {
    var blocks: [GlowBlock]
    var contentPadding = EdgeInsets()
    var scrollTo: String?
    var onScrolled: () -> Void = {}

    var body: some View {
        GeometryReader { proxy in
            let wide = proxy.size.width >= GlowLayout.twoColumnWidth
            let tight = wide || proxy.size.width < GlowLayout.narrowWidth
            let gutter = max(
                (proxy.size.width - GlowLayout.maxContentWidth) / 2,
                tight ? GlowLayout.wideGutter : GlowLayout.gutter
            )
            ScrollView {
                ScrollViewReader { reader in
                    VStack(spacing: GlowLayout.columnGap) {
                        ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                            sectionView(section, wide: wide)
                        }
                    }
                    .padding(.horizontal, gutter)
                    .padding(.top, 4 + contentPadding.top)
                    .padding(.bottom, contentPadding.bottom + 24)
                    .onAppear {
                        if let scrollTo, blocks.contains(where: { $0.key == scrollTo }) {
                            reader.scrollTo(scrollTo, anchor: .top)
                            onScrolled()
                        }
                    }
                    .onChange(of: scrollTo) { _, value in
                        guard let value, blocks.contains(where: { $0.key == value }) else { return }
                        withAnimation { reader.scrollTo(value, anchor: .top) }
                        onScrolled()
                    }
                }
            }
            // Two-column and narrow dashboards give the cards back a phone card's inner width.
            .environment(\.glowCardPadding, tight ? GlowLayout.wideCardPadding : GlowLayout.cardPadding)
        }
    }

    /// Splits the blocks into runs: each full block on its own, and each unbroken stretch of left and
    /// right blocks together (`GlowLayout.kt:148-155`).
    private var sections: [[GlowBlock]] {
        var out: [[GlowBlock]] = []
        for block in blocks {
            if block.pane != .full, let last = out.last, last.first?.pane != .full {
                out[out.count - 1].append(block)
            } else {
                out.append([block])
            }
        }
        return out
    }

    @ViewBuilder
    private func sectionView(_ section: [GlowBlock], wide: Bool) -> some View {
        let left = section.filter { $0.pane == .left }
        let right = section.filter { $0.pane == .right }
        // A phone, or a run with nothing on one side, stacks rather than leave a hole.
        if !wide || left.isEmpty || right.isEmpty {
            VStack(spacing: GlowLayout.columnGap) {
                ForEach(section) { block in
                    block.content.id(block.id)
                }
            }
        } else {
            HStack(alignment: .top, spacing: GlowLayout.columnGap) {
                VStack(spacing: GlowLayout.columnGap) {
                    ForEach(left) { block in block.content.id(block.id) }
                }
                .frame(maxWidth: .infinity)
                VStack(spacing: GlowLayout.columnGap) {
                    ForEach(right) { block in block.content.id(block.id) }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - Header

/// The title row of a card: the title, an optional note on the right (text, or `trailingContent` for
/// anything richer) and an optional chevron. Ported from `GlowHeader` (`GlowLayout.kt:162`).
struct GlowHeader<Trailing: View>: View {
    let title: String
    let trailing: String?
    let chevron: Bool
    private let trailingContent: () -> Trailing

    init(
        _ title: String,
        trailing: String? = nil,
        chevron: Bool = false,
        @ViewBuilder trailingContent: @escaping () -> Trailing
    ) {
        self.title = title
        self.trailing = trailing
        self.chevron = chevron
        self.trailingContent = trailingContent
    }

    var body: some View {
        HStack(spacing: 0) {
            Text(title)
                .appTextStyle(GlowType.title)
                .foregroundStyle(glowInk)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let trailing {
                Text(trailing)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(glowInk.opacity(0.9))
            }
            trailingContent()
            if chevron {
                Color.clear.frame(width: 4, height: 0)
                PhosphorIcon.caretRight.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 16, height: 16)
                    .foregroundStyle(glowInk.opacity(0.9))
            }
        }
        .frame(maxWidth: .infinity)
    }
}

extension GlowHeader where Trailing == EmptyView {
    init(_ title: String, trailing: String? = nil, chevron: Bool = false) {
        self.init(title, trailing: trailing, chevron: chevron) { EmptyView() }
    }
}

// MARK: - Hero number

/// The hero number of a card in dot-matrix, its unit beside it, and up to two short facts on the
/// right (text, or `side` for anything richer). Sits `GlowLayout.titleGap` under the title.
/// Ported from `GlowHeroNumber` (`GlowLayout.kt:184`).
struct GlowHeroNumber<Side: View>: View {
    let value: String
    let unit: String
    let facts: [String]
    private let side: () -> Side

    init(_ value: String, unit: String, facts: [String] = [], @ViewBuilder side: @escaping () -> Side) {
        self.value = value
        self.unit = unit
        self.facts = facts
        self.side = side
    }

    var body: some View {
        HStack(alignment: .bottom) {
            DotMatrixText(value, dot: GlowLayout.heroDot, gap: GlowLayout.heroGap)
            Text(unit)
                .appTextStyle(AppTypography.bodySmall)
                .foregroundStyle(glowInk.opacity(0.7))
                .padding(.leading, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
            if !facts.isEmpty {
                VStack(alignment: .trailing, spacing: 2) {
                    ForEach(facts, id: \.self) { fact in
                        Text(fact)
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(glowInk.opacity(0.85))
                    }
                }
            }
            side()
        }
        .frame(maxWidth: .infinity)
        .padding(.top, GlowLayout.titleGap)
    }
}

extension GlowHeroNumber where Side == EmptyView {
    init(_ value: String, unit: String, facts: [String] = []) {
        self.init(value, unit: unit, facts: facts) { EmptyView() }
    }
}

// MARK: - Chart gap

/// The gap above a card's chart (`GlowChartGap`, GlowLayout.kt:197).
struct GlowChartGap: View {
    var body: some View {
        Color.clear.frame(height: GlowLayout.chartGap)
    }
}
