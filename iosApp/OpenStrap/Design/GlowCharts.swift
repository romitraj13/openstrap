import SwiftUI
import UIKit
import OpenStrapShared

/*
 * Charts for glow cards, ported from `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCharts.kt`.
 *
 * The card's colour carries the mood, so the marks are white: lines get a soft halo, guides are rows
 * of dots, and bars and rings are built from LED-style dots like the numerals.
 *
 * Every position comes from `GlowGeometry` (shared Kotlin) rather than being recomputed here, so the
 * two builds cannot drift apart. Only the drawing is Swift.
 */

let glowInk = Color.white
let glowFaint = Color.white.opacity(0.16)
let glowMuted = Color.white.opacity(0.55)

/// `axisText` in GlowCharts.kt: Funnel Sans 10, the app's muted white.
private let axisTextStyle = AppTextStyle(
    name: "glow.axis", family: .funnelSans, size: 10, weight: 400, tracking: 0, lineHeight: nil)
/// `valueText` (11) and `readoutText` (12), both Funnel Sans in white.
private let valueTextStyle = AppTextStyle(
    name: "glow.value", family: .funnelSans, size: 11, weight: 400, tracking: 0, lineHeight: nil)
private let readoutTextStyle = AppTextStyle(
    name: "glow.readout", family: .funnelSans, size: 12, weight: 400, tracking: 0, lineHeight: nil)

// MARK: - Drawing helpers

extension AppTextStyle {
    /// The UIKit font, so a string can be measured the way Compose's `TextMeasurer` measures it.
    var uiFont: UIFont { family.uiFont(size: size, weight: weight) }

    /// The size Compose would measure for `text` in this style.
    func measure(_ text: String) -> CGSize {
        (text as NSString).size(withAttributes: [.font: uiFont, .kern: NSNumber(value: Double(tracking))])
    }
}

/// A row of round dots along a straight line, the glow language's grid line. Compose draws it as a
/// zero-length dash with a round cap (`GlowCharts.kt:59`), which is one dot per `gap`.
func glowDottedLine(
    _ context: GraphicsContext,
    from start: CGPoint,
    to end: CGPoint,
    color: Color,
    width: CGFloat = 1.6,
    gap: CGFloat
) {
    let dx = end.x - start.x
    let dy = end.y - start.y
    let length = (dx * dx + dy * dy).squareRoot()
    guard length > 0, gap > 0 else { return }
    var travelled: CGFloat = 0
    while travelled <= length {
        let t = travelled / length
        let point = CGPoint(x: start.x + dx * t, y: start.y + dy * t)
        context.fill(
            Path(ellipseIn: CGRect(x: point.x - width / 2, y: point.y - width / 2, width: width, height: width)),
            with: .color(color)
        )
        travelled += gap
    }
}

/// The same dots along a curve (an arc, a rounded rectangle), as a round-capped dash.
func glowDottedStroke(
    _ context: GraphicsContext,
    path: Path,
    color: Color,
    width: CGFloat,
    gap: CGFloat
) {
    context.stroke(
        path,
        with: .color(color),
        style: StrokeStyle(lineWidth: width, lineCap: .round, dash: [0.1, gap])
    )
}

/// Text placed as Compose's `DrawScope.text` places it: `align` 0 is the left edge of the string,
/// 1 its right edge, and the result is kept inside the canvas (`GlowCharts.kt:65-68`).
func glowDrawText(
    _ context: GraphicsContext,
    _ string: String,
    style: AppTextStyle,
    color: Color,
    x: CGFloat,
    y: CGFloat,
    align: CGFloat = 0,
    canvasWidth: CGFloat
) {
    let measured = style.measure(string)
    let left = min(max(x - measured.width * align, 0), max(canvasWidth - measured.width, 0))
    context.draw(
        Text(string).font(Font(style.uiFont)).foregroundColor(color),
        at: CGPoint(x: left, y: y),
        anchor: .topLeading
    )
}

/// Bottom axis labels spread evenly from the left edge to the right edge (`GlowCharts.kt:89-95`).
private func glowAxis(
    _ context: GraphicsContext,
    labels: [String],
    y: CGFloat,
    canvasWidth: CGFloat
) {
    guard !labels.isEmpty else { return }
    for (index, label) in labels.enumerated() {
        let fraction = CGFloat(GlowGeometry.shared.axisLabelFraction(index: Int32(index), count: Int32(labels.count)))
        glowDrawText(
            context, label, style: axisTextStyle, color: glowMuted,
            x: canvasWidth * fraction, y: y, align: fraction, canvasWidth: canvasWidth
        )
    }
}

/// The touch readout: a dark glass pill with white text, above `anchorY` when there is room, else
/// below (`GlowCharts.kt:71-80`).
private func glowReadout(
    _ context: GraphicsContext,
    _ string: String,
    x: CGFloat,
    anchorY: CGFloat,
    canvasWidth: CGFloat
) {
    let measured = readoutTextStyle.measure(string)
    let box = GlowGeometry.shared.readoutBox(
        textWidth: Float(measured.width),
        textHeight: Float(measured.height),
        x: Float(x),
        anchorY: Float(anchorY),
        width: Float(canvasWidth),
        padX: 18,
        padY: 10,
        gap: 12
    )
    let rect = CGRect(x: CGFloat(box.x), y: CGFloat(box.y), width: CGFloat(box.width), height: CGFloat(box.height))
    let pill = Path(roundedRect: rect, cornerRadius: rect.height / 2)
    context.fill(pill, with: .color(.black.opacity(0.72)))
    context.stroke(pill, with: .color(glowInk.opacity(0.18)), lineWidth: 1)
    context.draw(
        Text(string).font(Font(readoutTextStyle.uiFont)).foregroundColor(glowInk),
        at: CGPoint(x: rect.minX + 9, y: rect.minY + 5),
        anchor: .topLeading
    )
}

/// A vertical guide made of dots, under the finger (`GlowCharts.kt:83-86`).
private func glowGuide(_ context: GraphicsContext, x: CGFloat, bottom: CGFloat) {
    glowDottedLine(
        context, from: CGPoint(x: x, y: 0), to: CGPoint(x: x, y: bottom),
        color: glowInk.opacity(0.6), gap: 4
    )
}

/// `Modifier.scrub` from Charts.kt: a sideways drag reads values out, while a vertical drag still
/// scrolls the list (Compose recognises the two gestures side by side; `simultaneousGesture` is the
/// SwiftUI equivalent).
struct GlowScrub: ViewModifier {
    let active: Bool
    @Binding var touchX: CGFloat?

    @ViewBuilder
    func body(content: Content) -> some View {
        if active {
            content.simultaneousGesture(
                DragGesture(minimumDistance: 6)
                    .onChanged { value in touchX = value.location.x }
                    .onEnded { _ in touchX = nil }
            )
        } else {
            content
        }
    }
}

// MARK: - GlowLineChart

/// A day of readings as one smooth white line with a faint halo, a soft fill, dotted guides, a dotted
/// average and a glowing dot on the latest value. `values` are evenly spaced; nulls break the line.
///
/// Ported from `GlowLineChart` (`GlowCharts.kt:103`).
struct GlowLineChart: View {
    var values: [Float?]
    var height: CGFloat = 120
    var labels: [String] = []
    var showAverage: Bool = true
    /// The line's colour; white suits glow cards, a metric colour can suit a black panel.
    var color: Color = glowInk
    /// A fixed value axis (e.g. 0...100 for scores); nil fits the data.
    var yRange: ClosedRange<Float>?
    /// A usual range drawn as a faint band between two dotted lines (e.g. your normal).
    var band: ClosedRange<Float>?
    /// How the average label prints a value.
    var format: (Float) -> String
    /// Text for the touch readout at a reading; nil turns scrubbing off.
    var describe: ((Int, Float) -> String)?

    @State private var touchX: CGFloat?

    init(
        values: [Float?],
        height: CGFloat = 120,
        labels: [String] = [],
        showAverage: Bool = true,
        color: Color = glowInk,
        yRange: ClosedRange<Float>? = nil,
        band: ClosedRange<Float>? = nil,
        format: @escaping (Float) -> String = { String(Int($0.rounded())) },
        describe: ((Int, Float) -> String)? = nil
    ) {
        self.values = values
        self.height = height
        self.labels = labels
        self.showAverage = showAverage
        self.color = color
        self.yRange = yRange
        self.band = band
        self.format = format
        self.describe = describe
    }

    var body: some View {
        Canvas { context, size in
            draw(context, size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .modifier(GlowScrub(active: describe != nil, touchX: $touchX))
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let known = values.compactMap { $0 }
        guard !known.isEmpty else { return }
        let axisH: CGFloat = labels.isEmpty ? 0 : 18
        let top: CGFloat = 10
        let h = size.height - axisH
        let domain = GlowGeometry.shared.lineChartDomain(
            knownMin: known.min() ?? 0,
            knownMax: known.max() ?? 0,
            hasBand: band != nil,
            bandLo: band?.lowerBound ?? 0,
            bandHi: band?.upperBound ?? 0,
            hasYRange: yRange != nil,
            yRangeLo: yRange?.lowerBound ?? 0,
            yRangeHi: yRange?.upperBound ?? 0
        )
        let lo = domain.lo
        let hi = domain.hi
        // Inset by the end dot's halo, so a reading at the very edge isn't cut off.
        let inset: CGFloat = 7
        let count = Int32(values.count)

        func xOf(_ index: Int) -> CGFloat {
            CGFloat(GlowGeometry.shared.lineChartX(
                index: Int32(index), count: count, width: Float(size.width), inset: Float(inset)))
        }
        func yOf(_ value: Float) -> CGFloat {
            CGFloat(GlowGeometry.shared.lineChartY(
                value: value, lo: lo, hi: hi, top: Float(top), h: Float(h)))
        }

        for fraction in [0, 0.5, 1] as [CGFloat] {
            let y = top + (h - top) * fraction
            glowDottedLine(context, from: CGPoint(x: 0, y: y), to: CGPoint(x: size.width, y: y),
                           color: glowFaint, gap: 6)
        }
        if let band {
            let y0 = yOf(band.upperBound)
            let y1 = yOf(band.lowerBound)
            context.fill(
                Path(CGRect(x: 0, y: y0, width: size.width, height: y1 - y0)),
                with: .color(color.opacity(0.10))
            )
            glowDottedLine(context, from: CGPoint(x: 0, y: y0), to: CGPoint(x: size.width, y: y0),
                           color: color.opacity(0.45), gap: 4)
            glowDottedLine(context, from: CGPoint(x: 0, y: y1), to: CGPoint(x: size.width, y: y1),
                           color: color.opacity(0.45), gap: 4)
        }

        // Runs of consecutive readings; a gap starts a new run.
        var runs: [[CGPoint]] = []
        var current: [CGPoint] = []
        for (index, value) in values.enumerated() {
            guard let value else {
                if !current.isEmpty { runs.append(current) }
                current = []
                continue
            }
            current.append(CGPoint(x: xOf(index), y: yOf(value)))
        }
        if !current.isEmpty { runs.append(current) }

        // A lone reading between gaps is a dot, so a sparse week still shows its days.
        for run in runs where run.count == 1 {
            if let point = run.first {
                context.fill(
                    Path(ellipseIn: CGRect(x: point.x - 2.6, y: point.y - 2.6, width: 5.2, height: 5.2)),
                    with: .color(color)
                )
            }
        }
        for run in runs where run.count >= 2 {
            guard let first = run.first, let last = run.last else { continue }
            let line = glowSmoothPath(run)
            var fill = Path()
            fill.move(to: first)
            glowAddCubics(&fill, run)
            fill.addLine(to: CGPoint(x: last.x, y: h))
            fill.addLine(to: CGPoint(x: first.x, y: h))
            fill.closeSubpath()
            context.fill(
                fill,
                with: .linearGradient(
                    Gradient(colors: [color.opacity(0.14), color.opacity(0)]),
                    startPoint: CGPoint(x: 0, y: top),
                    endPoint: CGPoint(x: 0, y: h)
                )
            )
            // A faint halo just wider than the line.
            context.stroke(
                line, with: .color(color.opacity(0.08)),
                style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
            )
            context.stroke(
                line, with: .color(color),
                style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round)
            )
        }

        if showAverage {
            let average = known.reduce(0, +) / Float(known.count)
            let y = yOf(average)
            glowDottedLine(context, from: CGPoint(x: 0, y: y), to: CGPoint(x: size.width, y: y),
                           color: glowInk.opacity(0.5), gap: 4)
            glowDrawText(
                context, "avg \(format(average))", style: axisTextStyle,
                color: glowInk.opacity(0.8), x: size.width, y: y - 16, align: 1, canvasWidth: size.width
            )
        }

        if let lastIndex = values.lastIndex(where: { $0 != nil }), let value = values[lastIndex] {
            let last = CGPoint(x: xOf(lastIndex), y: yOf(value))
            context.fill(
                Path(ellipseIn: CGRect(x: last.x - 6.5, y: last.y - 6.5, width: 13, height: 13)),
                with: .color(color.opacity(0.2))
            )
            context.fill(
                Path(ellipseIn: CGRect(x: last.x - 3.5, y: last.y - 3.5, width: 7, height: 7)),
                with: .color(color)
            )
        }

        glowAxis(context, labels: labels, y: h + 4, canvasWidth: size.width)

        // Scrubbing: the reading nearest the finger (gaps are skipped, not bridged).
        if let describe, let tx = touchX, let index = nearestIndex(tx, size: size, inset: inset) {
            let value = values[index] ?? 0
            let point = CGPoint(x: xOf(index), y: yOf(value))
            glowGuide(context, x: point.x, bottom: h)
            context.fill(
                Path(ellipseIn: CGRect(x: point.x - 6, y: point.y - 6, width: 12, height: 12)),
                with: .color(.black.opacity(0.6))
            )
            context.fill(
                Path(ellipseIn: CGRect(x: point.x - 4, y: point.y - 4, width: 8, height: 8)),
                with: .color(color)
            )
            glowReadout(context, describe(index, value), x: point.x, anchorY: point.y, canvasWidth: size.width)
        }
    }

    /// The reading nearest `tx`, skipping the gaps (`GlowCharts.kt:178-181`).
    private func nearestIndex(_ tx: CGFloat, size: CGSize, inset: CGFloat) -> Int? {
        guard !values.isEmpty else { return nil }
        let span = size.width - inset * 2
        guard span > 0 else { return values.indices.first { values[$0] != nil } }
        let near = Int(((tx - inset) / span * CGFloat(values.count - 1)).rounded())
        let clamped = min(max(near, 0), values.count - 1)
        let limit = values.count / 48 + 1
        for distance in 0...limit {
            for candidate in [clamped - distance, clamped + distance]
            where candidate >= 0 && candidate < values.count && values[candidate] != nil {
                if abs(candidate - clamped) <= limit { return candidate }
            }
        }
        return nil
    }
}

// MARK: - GlowDotBars

/// Bars made of dots, like an LED meter (e.g. steps per hour). Each column has `rows` dots; the lit
/// ones show the value. The `highlight` column (default: the last one with data) is drawn in `accent`.
///
/// Ported from `GlowDotBars` (`GlowCharts.kt:198`).
struct GlowDotBars: View {
    var values: [Float]
    var height: CGFloat = 96
    var rows: Int = 10
    var labels: [String] = []
    var highlight: Int
    var accent: Color = glowInk
    /// Text for the touch readout on a column; nil turns scrubbing off.
    var describe: ((Int, Float) -> String)?

    @State private var touchX: CGFloat?

    init(
        values: [Float],
        height: CGFloat = 96,
        rows: Int = 10,
        labels: [String] = [],
        highlight: Int? = nil,
        accent: Color = glowInk,
        describe: ((Int, Float) -> String)? = nil
    ) {
        self.values = values
        self.height = height
        self.rows = rows
        self.labels = labels
        self.highlight = highlight ?? (values.lastIndex(where: { $0 > 0 }) ?? -1)
        self.accent = accent
        self.describe = describe
    }

    var body: some View {
        Canvas { context, size in
            draw(context, size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .modifier(GlowScrub(active: describe != nil, touchX: $touchX))
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        guard !values.isEmpty else { return }
        let axisH: CGFloat = labels.isEmpty ? 0 : 18
        let h = size.height - axisH
        let pitchX = CGFloat(GlowGeometry.shared.dotBarPitchX(width: Float(size.width), count: Int32(values.count)))
        let pitchY = CGFloat(GlowGeometry.shared.dotBarPitchY(h: Float(h), rows: Int32(rows)))
        let r = CGFloat(GlowGeometry.shared.dotBarRadius(pitchX: Float(pitchX), pitchY: Float(pitchY)))
        let maxValue = GlowGeometry.shared.dotBarMax(maxValue: values.max() ?? 0)
        let picked = touchX.flatMap { describe == nil ? nil : Int($0 / pitchX) }
            .map { min(max($0, 0), values.count - 1) }

        for (index, value) in values.enumerated() {
            let lit = Int(GlowGeometry.shared.dotBarLit(value: value, max: maxValue, rows: Int32(rows)))
            // While scrubbing, the other columns dim so the picked one stands out.
            let dim: CGFloat = (picked == nil || picked == index) ? 1 : 0.35
            let cx = CGFloat(GlowGeometry.shared.dotBarCenterX(index: Int32(index), pitchX: Float(pitchX)))
            for row in 0..<rows {
                let on = row < lit
                let dot: Color
                if !on {
                    dot = glowInk.opacity(0.10)
                } else if index == highlight || index == picked {
                    dot = accent
                } else {
                    dot = glowInk.opacity(Double(GlowGeometry.shared.dotBarAlpha(row: Int32(row), rows: Int32(rows))))
                }
                let cy = CGFloat(GlowGeometry.shared.dotBarDotY(row: Int32(row), pitchY: Float(pitchY), h: Float(h)))
                context.fill(
                    Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)),
                    with: .color(dot.opacity(dim))
                )
            }
        }

        glowAxis(context, labels: labels, y: h + 4, canvasWidth: size.width)

        if let picked, let describe {
            glowReadout(
                context, describe(picked, values[picked]),
                x: pitchX * CGFloat(picked) + pitchX / 2, anchorY: 0, canvasWidth: size.width
            )
        }
    }
}

// MARK: - GlowTrend

/// A day on a `GlowTrend`.
struct GlowDay: Hashable {
    let label: String
    let value: Float?
}

/// A week as dotted stems rising to each day's value, the value printed above. Today (the last day)
/// gets a glowing ring. `average` draws a dotted line across.
///
/// Ported from `GlowTrend` (`GlowCharts.kt:250`).
struct GlowTrend: View {
    var days: [GlowDay]
    var height: CGFloat = 130
    var average: Float?

    init(days: [GlowDay], height: CGFloat = 130, average: Float? = nil) {
        self.days = days
        self.height = height
        self.average = average
    }

    var body: some View {
        Canvas { context, size in
            draw(context, size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        guard !days.isEmpty else { return }
        let axisH: CGFloat = 18
        let top: CGFloat = 22
        let h = size.height - axisH
        let known = days.compactMap { $0.value } + (average.map { [$0] } ?? [])
        let domain = GlowGeometry.shared.trendDomain(
            knownMin: known.min() ?? 0,
            knownMax: known.max() ?? 1
        )
        let inset: CGFloat = 6

        func yOf(_ value: Float) -> CGFloat {
            CGFloat(GlowGeometry.shared.trendY(
                value: value, lo: domain.lo, hi: domain.hi, top: Float(top), h: Float(h), inset: Float(inset)))
        }

        if let average {
            let y = yOf(average)
            glowDottedLine(context, from: CGPoint(x: 0, y: y), to: CGPoint(x: size.width, y: y),
                           color: glowInk.opacity(0.45), gap: 4)
        }

        for (index, day) in days.enumerated() {
            let cx = CGFloat(GlowGeometry.shared.trendCenterX(
                index: Int32(index), count: Int32(days.count), width: Float(size.width)))
            let today = index == days.count - 1
            glowDrawText(
                context, day.label, style: axisTextStyle, color: today ? glowInk : glowMuted,
                x: cx, y: h + 4, align: 0.5, canvasWidth: size.width
            )
            guard let value = day.value else {
                context.fill(
                    Path(ellipseIn: CGRect(x: cx - 2.5, y: h - 3 - 2.5, width: 5, height: 5)),
                    with: .color(glowFaint)
                )
                continue
            }
            let y = yOf(value)
            glowDottedLine(
                context, from: CGPoint(x: cx, y: h - 3), to: CGPoint(x: cx, y: y),
                color: glowInk.opacity(today ? 0.9 : 0.45), width: 2, gap: 5
            )
            if today {
                context.fill(
                    Path(ellipseIn: CGRect(x: cx - 11, y: y - 11, width: 22, height: 22)),
                    with: .color(glowInk.opacity(0.18))
                )
                context.fill(
                    Path(ellipseIn: CGRect(x: cx - 6, y: y - 6, width: 12, height: 12)),
                    with: .color(glowInk)
                )
            } else {
                context.fill(
                    Path(ellipseIn: CGRect(x: cx - 4, y: y - 4, width: 8, height: 8)),
                    with: .color(glowInk)
                )
            }
            glowDrawText(
                context, String(Int(value.rounded())), style: valueTextStyle,
                color: today ? glowInk : glowInk.opacity(0.75),
                x: cx, y: y - 20, align: 0.5, canvasWidth: size.width
            )
        }
    }
}

// MARK: - GlowArcGauge

/// A half-circle gauge from 9 o'clock over the top to 3: a smooth white line with a faint halo up to
/// `progress`, a dotted track for what's left, and a glowing dot in `color` at the leading edge.
/// Fills the width it's given; `content` sits at the bottom middle, inside the arc.
///
/// Ported from `GlowArcGauge` (`GlowCharts.kt:303`).
struct GlowArcGauge<Content: View>: View {
    var progress: Float
    var color: Color
    private let content: () -> Content

    init(progress: Float, color: Color, @ViewBuilder content: @escaping () -> Content) {
        self.progress = progress
        self.color = color
        self.content = content
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Canvas { context, size in
                draw(context, size)
            }
            content()
        }
        .aspectRatio(1.9, contentMode: .fit)
        .frame(maxWidth: GlowLayout.gaugeMaxWidth)
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let r = CGFloat(GlowGeometry.shared.arcGaugeRadius(width: Float(size.width), inset: 10))
        let centre = GlowGeometry.shared.arcGaugeCentre(
            width: Float(size.width), height: Float(size.height), bottom: 3)
        let c = CGPoint(x: CGFloat(centre.x), y: CGFloat(centre.y))
        let sweep = CGFloat(GlowGeometry.shared.arcGaugeSweep(progress: progress))

        var track = Path()
        track.addArc(center: c, radius: r, startAngle: .degrees(Double(180 + sweep)),
                     endAngle: .degrees(360), clockwise: false)
        glowDottedStroke(context, path: track, color: glowFaint.opacity(0.22), width: 2, gap: 7)
        guard sweep > 0 else { return }

        var filled = Path()
        filled.addArc(center: c, radius: r, startAngle: .degrees(180),
                      endAngle: .degrees(Double(180 + sweep)), clockwise: false)
        context.stroke(filled, with: .color(glowInk.opacity(0.1)),
                       style: StrokeStyle(lineWidth: 8, lineCap: .round))
        // Brighter towards the end, like the ring. Sweep gradients start at 3 o'clock; the arc at 9 (0.5).
        let shading = GraphicsContext.Shading.conicGradient(
            Gradient(stops: [
                .init(color: glowInk.opacity(0.55), location: 0.5),
                .init(color: glowInk, location: min(Double((180 + sweep) / 360), 1)),
            ]),
            center: c,
            angle: .degrees(0)
        )
        context.stroke(filled, with: shading, style: StrokeStyle(lineWidth: 3, lineCap: .round))

        let end = GlowGeometry.shared.arcGaugePoint(
            progress: progress, r: Float(r), width: Float(size.width), height: Float(size.height), bottom: 3)
        let dot = CGPoint(x: CGFloat(end.x), y: CGFloat(end.y))
        context.fill(
            Path(ellipseIn: CGRect(x: dot.x - 9, y: dot.y - 9, width: 18, height: 18)),
            with: .color(color.opacity(0.3))
        )
        context.fill(
            Path(ellipseIn: CGRect(x: dot.x - 5, y: dot.y - 5, width: 10, height: 10)),
            with: .color(color)
        )
    }
}

extension GlowArcGauge where Content == EmptyView {
    /// A gauge with nothing inside the arc.
    init(progress: Float, color: Color) {
        self.init(progress: progress, color: color) { EmptyView() }
    }
}

// MARK: - GlowHypnogram
/// A stretch of sleep on a `GlowHypnogram`: start and end as fractions of the night, and its row.
struct GlowStage: Hashable {
    var from: Float
    var to: Float
    var row: Int
}

/// Sleep stages as white pills on dotted rows (row 0 at the top, e.g. Awake), joined by thin
/// connectors. Deeper rows are drawn brighter so deep sleep stands out on the dark core.
///
/// Ported from `GlowHypnogram` (`GlowCharts.kt:351`).
struct GlowHypnogram: View {
    var stages: [GlowStage]
    var rowNames: [String]
    var height: CGFloat = 130
    var labels: [String] = []
    /// Text for the touch readout on a stage; nil turns scrubbing off.
    var describe: ((GlowStage) -> String)?

    @State private var touchX: CGFloat?

    init(
        stages: [GlowStage],
        rowNames: [String],
        height: CGFloat = 130,
        labels: [String] = [],
        describe: ((GlowStage) -> String)? = nil
    ) {
        self.stages = stages
        self.rowNames = rowNames
        self.height = height
        self.labels = labels
        self.describe = describe
    }

    var body: some View {
        Canvas { context, size in
            draw(context, size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .modifier(GlowScrub(active: describe != nil, touchX: $touchX))
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        guard !stages.isEmpty, !rowNames.isEmpty else { return }
        let axisH: CGFloat = labels.isEmpty ? 0 : 18
        let nameW: CGFloat = 46
        let h = size.height - axisH
        let w = size.width - nameW
        let rowH = CGFloat(GlowGeometry.shared.hypnogramRowH(h: Float(h), rows: Int32(rowNames.count)))

        func yOf(_ row: Int) -> CGFloat {
            CGFloat(GlowGeometry.shared.hypnogramRowY(row: Int32(row), rowH: Float(rowH)))
        }
        func xOf(_ fraction: Float) -> CGFloat {
            CGFloat(GlowGeometry.shared.hypnogramX(fraction: fraction, nameWidth: Float(nameW), w: Float(w)))
        }

        for (row, name) in rowNames.enumerated() {
            let y = yOf(row)
            glowDottedLine(context, from: CGPoint(x: nameW, y: y), to: CGPoint(x: size.width, y: y),
                           color: glowFaint, gap: 6)
            glowDrawText(context, name, style: axisTextStyle, color: glowMuted,
                         x: 0, y: y - 7, canvasWidth: size.width)
        }

        let sorted = stages.sorted { $0.from < $1.from }
        if sorted.count > 1 {
            for index in 1..<sorted.count {
                let previous = sorted[index - 1]
                let stage = sorted[index]
                if previous.row != stage.row {
                    var connector = Path()
                    connector.move(to: CGPoint(x: xOf(stage.from), y: yOf(previous.row)))
                    connector.addLine(to: CGPoint(x: xOf(stage.from), y: yOf(stage.row)))
                    context.stroke(connector, with: .color(glowInk.opacity(0.3)), lineWidth: 1)
                }
            }
        }

        for stage in sorted {
            let alpha = GlowGeometry.shared.hypnogramPillAlpha(row: Int32(stage.row), rows: Int32(rowNames.count))
            let pill = GlowGeometry.shared.hypnogramPill(
                from: stage.from, to: stage.to, row: Int32(stage.row),
                nameWidth: Float(nameW), w: Float(w), rowH: Float(rowH)
            )
            let rect = CGRect(
                x: CGFloat(pill.x), y: CGFloat(pill.y),
                width: CGFloat(pill.width), height: CGFloat(pill.height)
            )
            context.fill(
                Path(roundedRect: rect, cornerRadius: rect.height / 2),
                with: .color(glowInk.opacity(Double(alpha)))
            )
        }

        if !labels.isEmpty {
            for (index, label) in labels.enumerated() {
                let fraction = CGFloat(GlowGeometry.shared.axisLabelFraction(index: Int32(index), count: Int32(labels.count)))
                glowDrawText(context, label, style: axisTextStyle, color: glowMuted,
                             x: xOf(Float(fraction)), y: h + 4, align: fraction, canvasWidth: size.width)
            }
        }

        if let describe, let tx = touchX {
            let x = min(max(tx, nameW), size.width)
            let fraction = Float(GlowGeometry.shared.hypnogramFraction(
                x: Float(x), nameWidth: Float(nameW), w: Float(w)))
            // The stage under the finger, or the nearest one when the finger is in a gap.
            let stage = sorted.first { fraction >= $0.from && fraction < $0.to } ?? sorted.min { a, b in
                min(abs(a.from - fraction), abs(a.to - fraction)) < min(abs(b.from - fraction), abs(b.to - fraction))
            }
            guard let stage else { return }
            glowGuide(context, x: x, bottom: h)
            let pillTop = CGFloat(GlowGeometry.shared.hypnogramPill(
                from: stage.from, to: stage.to, row: Int32(stage.row),
                nameWidth: Float(nameW), w: Float(w), rowH: Float(rowH)
            ).y)
            glowReadout(context, describe(stage), x: x, anchorY: pillTop, canvasWidth: size.width)
        }
    }
}

// MARK: - GlowHorizon

/// The score as a wide, shallow arc across the card: a dotted track, ticks above it, a white fill with
/// a soft halo up to the score, and a knob glowing in `color` where it lands.
///
/// Ported from `GlowHorizon` (`GlowCharts.kt:419`). Compose sweeps the fill in over 700 ms by redrawing
/// the arc each frame; standing instruction 7 only allows offset, scale and opacity to move, so the
/// finished arc fades in instead — and with Reduce Motion on, it is simply there.
struct GlowHorizon: View {
    var progress: Float?
    var color: Color
    var height: CGFloat = 72

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false

    init(progress: Float?, color: Color, height: CGFloat = 72) {
        self.progress = progress
        self.color = color
        self.height = height
    }

    var body: some View {
        Canvas { context, size in
            draw(context, size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .opacity(reduceMotion || appeared ? 1 : 0)
        .onAppear {
            guard !reduceMotion else { appeared = true; return }
            withAnimation(.easeOut(duration: 0.7)) { appeared = true }
        }
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let sweep: Float = 84
        let r = CGFloat(GlowGeometry.shared.horizonRadius(
            width: Float(size.width), inset: 28, sweep: sweep))
        let sag = CGFloat(GlowGeometry.shared.horizonSag(r: Float(r), sweep: sweep))
        let top: CGFloat = 14
        let c = CGPoint(x: size.width / 2, y: top + r)
        let start = CGFloat(270 - sweep / 2)

        var track = Path()
        track.addArc(center: c, radius: r, startAngle: .degrees(Double(start)),
                     endAngle: .degrees(Double(start) + Double(sweep)), clockwise: false)
        glowDottedStroke(context, path: track, color: glowFaint, width: 1.6, gap: 6)

        for index in 0...4 {
            let point = GlowGeometry.shared.horizonPoint(
                fraction: Float(index) / 4, sweep: sweep, width: Float(size.width),
                top: Float(top), r: Float(r + 9))
            context.fill(
                Path(ellipseIn: CGRect(x: CGFloat(point.x) - 1.5, y: CGFloat(point.y) - 1.5, width: 3, height: 3)),
                with: .color(glowMuted)
            )
        }

        if let progress {
            let p = min(max(progress, 0), 1)
            if p > 0.001 {
                var filled = Path()
                filled.addArc(center: c, radius: r, startAngle: .degrees(Double(start)),
                              endAngle: .degrees(Double(start) + Double(sweep) * Double(p)), clockwise: false)
                context.stroke(filled, with: .color(glowInk.opacity(0.1)),
                               style: StrokeStyle(lineWidth: 9, lineCap: .round))
                context.stroke(filled, with: .color(glowInk),
                               style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
            }
            let knob = GlowGeometry.shared.horizonPoint(
                fraction: p, sweep: sweep, width: Float(size.width), top: Float(top), r: Float(r))
            let k = CGPoint(x: CGFloat(knob.x), y: CGFloat(knob.y))
            context.fill(
                Path(ellipseIn: CGRect(x: k.x - 11, y: k.y - 11, width: 22, height: 22)),
                with: .color(color.opacity(0.30))
            )
            context.fill(
                Path(ellipseIn: CGRect(x: k.x - 6.5, y: k.y - 6.5, width: 13, height: 13)),
                with: .color(color)
            )
            context.fill(
                Path(ellipseIn: CGRect(x: k.x - 2.6, y: k.y - 2.6, width: 5.2, height: 5.2)),
                with: .color(glowInk)
            )
        }

        let labelY = top + sag + 8
        let left = GlowGeometry.shared.horizonPoint(
            fraction: 0, sweep: sweep, width: Float(size.width), top: Float(top), r: Float(r))
        let right = GlowGeometry.shared.horizonPoint(
            fraction: 1, sweep: sweep, width: Float(size.width), top: Float(top), r: Float(r))
        glowDrawText(context, "0", style: axisTextStyle, color: glowMuted,
                     x: CGFloat(left.x), y: labelY, align: 0.5, canvasWidth: size.width)
        glowDrawText(context, "100", style: axisTextStyle, color: glowMuted,
                     x: CGFloat(right.x), y: labelY, align: 0.5, canvasWidth: size.width)
    }
}

// MARK: - Smooth path

/// A smooth path through `points`, built from `GlowGeometry.smoothCubics` (Compose's `smoothPath`,
/// Charts.kt:292): monotone cubic interpolation, so the line never overshoots the data.
func glowSmoothPath(_ points: [CGPoint]) -> Path {
    var path = Path()
    guard let first = points.first else { return path }
    path.move(to: first)
    glowAddCubics(&path, points)
    return path
}

/// Append the cubic segments for `points` to an open path that is already at its first point.
func glowAddCubics(_ path: inout Path, _ points: [CGPoint]) {
    guard points.count >= 2 else { return }
    let cubics = GlowGeometry.shared.smoothCubics(points: points.map { Pt(x: Float($0.x), y: Float($0.y)) })
    for cubic in cubics {
        path.addCurve(
            to: CGPoint(x: CGFloat(cubic.x), y: CGFloat(cubic.y)),
            control1: CGPoint(x: CGFloat(cubic.c1x), y: CGFloat(cubic.c1y)),
            control2: CGPoint(x: CGFloat(cubic.c2x), y: CGFloat(cubic.c2y))
        )
    }
}
