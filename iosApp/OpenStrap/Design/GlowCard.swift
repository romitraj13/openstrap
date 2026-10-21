import SwiftUI
import UIKit
import CoreImage
import Foundation

// MARK: - Motion

/// Where the core is at a moment: an offset (fractions of the card), a scale, and `fade` on top of
/// the style's core opacity.
struct GlowDrift: Equatable {
    var dx: CGFloat
    var dy: CGFloat
    var scale: CGFloat
    var fade: CGFloat = 1
}

/// The core's motion over time, ported from `CoreDrift` in
/// `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCard.kt` (lines 357-430).
///
/// Underneath every `GlowMotion` is a never-repeating wander: each axis is two sine waves with
/// unrelated periods (10–24 s), and every card gets its own periods and phases.
struct CoreDrift {
    private struct Wave {
        let period: CGFloat
        let phase: CGFloat

        func callAsFunction(_ t: CGFloat) -> CGFloat {
            GlowMath.sin(2 * .pi * t / period + phase)
        }
    }

    private let x: (Wave, Wave)
    private let y: (Wave, Wave)
    private let breath: Wave
    /// Where in its cycle each card starts, so two heart cards don't beat in lockstep.
    private let offset: CGFloat

    init(seed: UInt64) {
        var random = GlowRandom(seed: seed)
        func wave() -> Wave {
            Wave(period: 10 + random.nextFloat() * 14, phase: random.nextFloat() * 2 * .pi)
        }
        let x = (wave(), wave())
        let y = (wave(), wave())
        self.x = x
        self.y = y
        breath = wave()
        offset = random.nextFloat() * 10
    }

    func at(_ time: CGFloat, motion: GlowMotion) -> GlowDrift {
        let t = time + offset
        switch motion {
        case .drift:
            return wander(t, amount: 1)
        case .still:
            return GlowDrift(dx: 0, dy: 0, scale: 1)
        case .heartbeat(let bpm):
            // An ECG beat: on each spike the dark core pulls in and thins, so the colour flares
            // inward. The small dips either side of the spike push it out a touch, like the trace
            // dipping below the line.
            let base = wander(t, amount: 0.4)
            let e = CoreDrift.ecg(t * CGFloat(bpm) / 60)
            return GlowDrift(dx: base.dx, dy: base.dy, scale: 1 - 0.04 * e, fade: 1 - 0.2 * max(e, 0))
        case .breath(let perMinute):
            // In: swell and rise like a chest. Out: slower, settling back.
            let base = wander(t, amount: 0.5)
            let b = CoreDrift.inhale(t * perMinute / 60)
            return GlowDrift(dx: base.dx, dy: base.dy - 0.01 * b, scale: 1 + 0.045 * b)
        case .stride(let cadence, let energy):
            // The same wander twice as fast, plus a light footfall bounce at the cadence.
            let e = min(max(energy, 0), 1)
            let base = wander(t * 2, amount: 0.4 + 0.4 * e)
            let step = abs(GlowMath.sin(.pi * t * CGFloat(cadence) / 60))
            return GlowDrift(dx: base.dx, dy: base.dy - 0.01 * e * step, scale: base.scale)
        }
    }

    private func wander(_ t: CGFloat, amount: CGFloat) -> GlowDrift {
        GlowDrift(
            dx: 0.035 * amount * (0.65 * x.0(t) + 0.35 * x.1(t)),
            dy: 0.03 * amount * (0.65 * y.0(t) + 0.35 * y.1(t)),
            // Breathing in: the core swells a little; breathing out, it settles.
            scale: 1 + 0.025 * amount * breath(t)
        )
    }

    /// One heartbeat as an ECG trace (phase in beats), about -0.25..1: a small P wave, the QRS
    /// complex (a dip, the sharp R spike, a deeper dip) and a broad, gentle T wave, then flat.
    static func ecg(_ phase: CGFloat) -> CGFloat {
        let f = phase - GlowMath.floor(phase)
        func bump(_ at: CGFloat, _ width: CGFloat) -> CGFloat {
            let x = (f - at) / width
            return GlowMath.exp(-(x * x))
        }
        return 0.12 * bump(0.10, 0.035)
            - 0.10 * bump(0.215, 0.012) + bump(0.24, 0.018) - 0.25 * bump(0.265, 0.014)
            + 0.30 * bump(0.52, 0.07)
    }

    /// One breath over a cycle (phase in breaths): eased inhale over 40%, longer eased exhale, 0..1.
    static func inhale(_ phase: CGFloat) -> CGFloat {
        let f = phase - GlowMath.floor(phase)
        func ease(_ x: CGFloat) -> CGFloat { (1 - GlowMath.cos(.pi * x)) / 2 }
        return f < 0.4 ? ease(f / 0.4) : 1 - ease((f - 0.4) / 0.6)
    }
}

/// CGFloat maths through Double, so the port does not depend on implicit CGFloat/Double bridging.
enum GlowMath {
    static func sin(_ x: CGFloat) -> CGFloat { CGFloat(Foundation.sin(Double(x))) }
    static func cos(_ x: CGFloat) -> CGFloat { CGFloat(Foundation.cos(Double(x))) }
    static func exp(_ x: CGFloat) -> CGFloat { CGFloat(Foundation.exp(Double(x))) }
    static func floor(_ x: CGFloat) -> CGFloat { CGFloat(Foundation.floor(Double(x))) }
}

/// A small deterministic generator (SplitMix64). Kotlin's `Random` is XorWowRandom; only the
/// distribution matters here, not the exact sequence.
struct GlowRandom {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed &+ 0x9E37_79B9_7F4A_7C15
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// A value in 0..<1, like Kotlin's `nextFloat`.
    mutating func nextFloat() -> CGFloat {
        CGFloat(next() >> 40) / CGFloat(1 << 24)
    }
}

// MARK: - Pause while scrolling

private struct GlowPausedKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// Whether glow card motion is paused because its scroll view is moving. Screens provide it
    /// through `glowScrollPhasePause()`; a card adds the system's Reduce Motion setting.
    var glowPaused: Bool {
        get { self[GlowPausedKey.self] }
        set { self[GlowPausedKey.self] = newValue }
    }
}

private struct GlowScrollPhasePause: ViewModifier {
    @State private var scrolling = false

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content
                .onScrollPhaseChange { _, phase in
                    scrolling = phase.isScrolling
                }
                .environment(\.glowPaused, scrolling)
        } else {
            content
        }
    }
}

extension View {
    /// Hold every glow core still while this scroll view moves (iOS 18 and later: `onScrollPhaseChange`
    /// does not exist before it). The cores get every frame back for the scroll itself.
    func glowScrollPhasePause() -> some View {
        modifier(GlowScrollPhasePause())
    }
}

// MARK: - The card

/// A card in the glow language (see `GlowStyle`). Layers, back to front: rim-coloured fill, rim
/// light, second-tone band, dark core, `content`, film grain, hairline edge.
///
/// Ported from `GlowCard` in `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCard.kt`. The
/// blurred layers are rendered once per style and size into images that are cached across cards;
/// the core then moves on its own layer by offset, scale and opacity only, so a moving card never
/// re-draws its content and never re-blurs anything. It holds still when Reduce Motion is on or the
/// containing scroll view is moving.
struct GlowCard<Content: View>: View {
    let style: GlowStyle
    let contentPadding: CGFloat
    private let content: () -> Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.glowPaused) private var scrollPaused
    @Environment(\.displayScale) private var displayScale

    @State private var layers: GlowLayers?
    @State private var drift = CoreDrift(seed: UInt64.random(in: 0..<UInt64.max))
    @State private var started = Date()
    @State private var pausedTotal: TimeInterval = 0
    @State private var pauseStarted: Date?

    init(_ style: GlowStyle, contentPadding: CGFloat = 16, @ViewBuilder content: @escaping () -> Content) {
        self.style = style
        self.contentPadding = contentPadding
        self.content = content
    }

    private var paused: Bool { reduceMotion || scrollPaused }

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let key = GlowLayerCache.Key(style: style, width: size.width, height: size.height, scale: displayScale)
            ZStack(alignment: .topLeading) {
                if let layers {
                    Image(uiImage: layers.base)
                        .resizable()
                        .frame(width: size.width, height: size.height)
                    core(layers, size: size)
                    content()
                        .padding(contentPadding)
                        .frame(width: size.width, height: size.height, alignment: .topLeading)
                    Image(uiImage: layers.overlay)
                        .resizable()
                        .frame(width: size.width, height: size.height)
                        .allowsHitTesting(false)
                } else {
                    // Until the layers are ready, the plain mid colour.
                    GlowPalette(style).mid.color
                }
            }
            .frame(width: size.width, height: size.height)
            .clipShape(RoundedRectangle(cornerRadius: style.radius, style: .continuous))
            .task(id: key) { await load(key) }
        }
        .onChange(of: paused) { _, isPaused in
            if isPaused {
                pauseStarted = Date()
            } else if let startedAt = pauseStarted {
                pausedTotal += Date().timeIntervalSince(startedAt)
                pauseStarted = nil
            }
        }
    }

    /// The pre-blurred core, moved by the motion through layer properties only.
    private func core(_ layers: GlowLayers, size: CGSize) -> some View {
        let margin = layers.coreMargin
        return TimelineView(.animation(minimumInterval: nil, paused: paused)) { timeline in
            let second = elapsed(at: timeline.date)
            let motion = drift.at(second, motion: style.motion)
            ZStack {
                Image(uiImage: layers.core)
                    .resizable()
                    .frame(width: size.width + margin * 2, height: size.height + margin * 2)
            }
            .frame(width: size.width, height: size.height)
            .scaleEffect(motion.scale, anchor: UnitPoint(x: style.coreX, y: style.coreY))
            .offset(x: motion.dx * size.width, y: motion.dy * size.height)
            .opacity(min(max(style.coreOpacity * motion.fade, 0), 1))
            .allowsHitTesting(false)
        }
    }

    /// Seconds since the card appeared, not counting time spent paused, so the core picks up where
    /// it stopped.
    private func elapsed(at date: Date) -> CGFloat {
        var seconds = date.timeIntervalSince(started) - pausedTotal
        if let pauseStarted {
            seconds -= date.timeIntervalSince(pauseStarted)
        }
        return CGFloat(max(0, seconds))
    }

    @MainActor
    private func load(_ key: GlowLayerCache.Key) async {
        if let hit = GlowLayerCache.shared.peek(key) {
            layers = hit
            return
        }
        let built = await Task.detached(priority: .userInitiated) {
            GlowLayerCache.shared.layers(for: key)
        }.value
        layers = built
    }
}

// MARK: - Layers and their cache

/// A card's pre-rendered layers: the fill and rim light, the blurred core, and the grain and
/// hairline overlay. `coreMargin` is how far the core image reaches past the card on every side.
struct GlowLayers: @unchecked Sendable {
    let base: UIImage
    let core: UIImage
    let overlay: UIImage
    let coreMargin: CGFloat
}

/// Rendered glow layers, shared by every card with the same look and size. Rendering blurs is the
/// expensive part of a glow card; with the cache, a card scrolling back into view costs nothing.
final class GlowLayerCache {
    struct Key: Hashable {
        let style: GlowStyle
        let width: CGFloat
        let height: CGFloat
        let scale: CGFloat
    }

    static let shared = GlowLayerCache()

    /// Enough for every card on a screen plus the ones just scrolled past.
    private let maxEntries = 10
    private let lock = NSLock()
    private var entries: [Key: GlowLayers] = [:]
    private var order: [Key] = []

    func peek(_ key: Key) -> GlowLayers? {
        lock.lock()
        defer { lock.unlock() }
        return entries[key]
    }

    func layers(for key: Key) -> GlowLayers {
        lock.lock()
        if let hit = entries[key] {
            order.removeAll { $0 == key }
            order.append(key)
            lock.unlock()
            return hit
        }
        lock.unlock()

        let built = GlowRenderer.render(
            style: key.style,
            size: CGSize(width: key.width, height: key.height),
            scale: max(key.scale, 1)
        )

        lock.lock()
        entries[key] = built
        order.removeAll { $0 == key }
        order.append(key)
        while order.count > maxEntries {
            entries.removeValue(forKey: order.removeFirst())
        }
        lock.unlock()
        return built
    }
}

// MARK: - Rendering

enum GlowRenderer {
    private static let ciContext = CIContext(options: [.useSoftwareRenderer: false])
    /// One card at a time: the Design lab shows every style at once, and a dozen Core Image blurs
    /// in parallel would only fight over the same cores.
    private static let renderLock = NSLock()

    static func render(style: GlowStyle, size: CGSize, scale: CGFloat) -> GlowLayers {
        renderLock.lock()
        defer { renderLock.unlock() }
        let palette = GlowPalette(style)
        return GlowLayers(
            base: base(style: style, palette: palette, size: size, scale: scale),
            core: core(style: style, palette: palette, size: size, scale: scale),
            overlay: overlay(style: style, size: size, scale: scale),
            coreMargin: max(size.width, size.height) * 0.08
        )
    }

    private static func cardPath(_ size: CGSize, radius: CGFloat, inset: CGFloat = 0) -> CGPath {
        let rect = CGRect(x: inset, y: inset, width: size.width - inset * 2, height: size.height - inset * 2)
        return CGPath(roundedRect: rect, cornerWidth: max(radius - inset, 0), cornerHeight: max(radius - inset, 0), transform: nil)
    }

    private static func format(scale: CGFloat) -> UIGraphicsImageRendererFormat {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        return format
    }

    /// The fill and the rim light.
    private static func base(style: GlowStyle, palette: GlowPalette, size: CGSize, scale: CGFloat) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: size, format: format(scale: scale))
        return renderer.image { context in
            let cg = context.cgContext
            let path = cardPath(size, radius: style.radius)
            cg.saveGState()
            cg.addPath(path)
            cg.clip()
            fill(style: style, palette: palette, size: size, into: cg)
            if style.glowStrength > 0 {
                let g = style.glowSize
                let k = style.glowStrength
                let light = UIColor(palette.light.color)
                // Rim light: CSS-style inset shadows (one leaning toward the lit edge, one all the
                // way round).
                if style.lightFrom != .all {
                    let dy = style.lightFrom == .top ? g * 0.6 : -g * 0.6
                    insetShadow(
                        size: size, radius: style.radius, dy: dy, blur: g * 1.2, spread: -g * 0.5,
                        color: light.withAlphaComponent(k), scale: scale, into: cg
                    )
                }
                insetShadow(
                    size: size, radius: style.radius, dy: 0, blur: g, spread: g * 0.1,
                    color: light.withAlphaComponent(0.6 * k), scale: scale, into: cg
                )
                // Bevel: a thick band of light all the way round, like the edge of a glossy slab.
                if style.bevel > 0 {
                    let b = min(size.width, size.height) * 0.075 * style.bevel
                    insetShadow(
                        size: size, radius: style.radius, dy: 0, blur: b * 1.6, spread: b * 0.45,
                        color: light.withAlphaComponent(0.85 * k), scale: scale, into: cg
                    )
                }
            }
            cg.restoreGState()
        }
    }

    /// The card's base fill: rim colour at the lit edge melting into the mid colour. The melt is
    /// eased (fast near the edge, then slowly settling, with no corner where it ends), so the light
    /// reads as a soft glow but has mostly faded by the title row.
    private static func fill(style: GlowStyle, palette: GlowPalette, size: CGSize, into cg: CGContext) {
        var colors: [CGColor]
        var locations: [CGFloat]
        if style.lightFrom == .all {
            colors = [palette.mid.color, palette.mid.color].map { UIColor($0).cgColor }
            locations = [0, 1]
        } else {
            let reach: CGFloat = 0.4
            let steps = 12
            // Ease-out: 1 - (1 - x)^3 runs from light to mid and arrives with zero slope, so
            // there's no seam.
            let fade = (0...steps).map { CGFloat($0) / CGFloat(steps) }
            let melted = fade.map { x -> CGColor in
                let inverse = 1 - x
                let eased = 1 - inverse * inverse * inverse
                return UIColor(palette.light.rgb.mixedOklab(to: palette.mid.rgb, by: 1 - eased).color).cgColor
            }
            colors = melted + [UIColor(palette.mid.color).cgColor]
            locations = fade.map { $0 * reach } + [1]
            if style.lightFrom == .bottom {
                colors.reverse()
                locations = Array(locations.map { 1 - $0 }.reversed())
            }
        }
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: locations
        ) else { return }
        cg.drawLinearGradient(
            gradient,
            start: .zero,
            end: CGPoint(x: 0, y: size.height),
            options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        )
    }

    /// The equivalent of CSS `box-shadow: inset 0 dy blur spread color`: everything outside the
    /// card's shape (shifted by `dy`, shrunk by `spread`), blurred and clipped to the card.
    private static func insetShadow(
        size: CGSize, radius: CGFloat, dy: CGFloat, blur: CGFloat, spread: CGFloat,
        color: UIColor, scale: CGFloat, into cg: CGContext
    ) {
        guard blur > 0 else { return }
        // Just enough room round the card that the blur fades outside it, not at its edge.
        let pad = blur * 2 + abs(dy) + abs(spread) + 4
        let padded = CGSize(width: size.width + pad * 2, height: size.height + pad * 2)
        let renderer = UIGraphicsImageRenderer(size: padded, format: format(scale: scale))
        let shadow = renderer.image { context in
            let c = context.cgContext
            c.translateBy(x: pad, y: pad)
            let path = CGMutablePath()
            path.addRect(CGRect(x: -pad, y: -pad, width: padded.width, height: padded.height))
            let holeRadius = max(radius - spread, 0)
            path.addRoundedRect(
                in: CGRect(x: spread, y: spread + dy, width: size.width - spread * 2, height: size.height - spread * 2),
                cornerWidth: holeRadius,
                cornerHeight: holeRadius
            )
            c.addPath(path)
            c.setFillColor(color.cgColor)
            c.fillPath(using: .evenOdd)
        }
        let blurred = gaussianBlur(shadow, sigma: blur / 2)
        cg.saveGState()
        cg.addPath(cardPath(size, radius: radius))
        cg.clip()
        cg.draw(blurred, in: CGRect(x: -pad, y: -pad, width: padded.width, height: padded.height))
        cg.restoreGState()
    }

    /// The dark core and the second-tone band around it, pre-blurred at half resolution.
    private static func core(style: GlowStyle, palette: GlowPalette, size: CGSize, scale: CGFloat) -> UIImage {
        let margin = max(size.width, size.height) * 0.08
        let padded = CGSize(width: size.width + margin * 2, height: size.height + margin * 2)
        let width = size.width * style.coreWidth
        let height = size.height * style.coreHeight
        let rect = CGRect(
            x: size.width * style.coreX - width / 2,
            y: size.height * style.coreY - height / 2,
            width: width,
            height: height
        )
        let radius = max(style.radius - 8, 0)
        let band = style.toneWidth
        let renderer = UIGraphicsImageRenderer(size: padded, format: format(scale: scale * 0.5))
        let image = renderer.image { context in
            let cg = context.cgContext
            cg.translateBy(x: margin, y: margin)
            if band > 0 {
                let toneRect = rect.insetBy(dx: -band, dy: -band)
                cg.addPath(shape(style.coreShape, rect: toneRect, radius: radius + band))
                cg.setFillColor(UIColor(palette.tone.color).cgColor)
                cg.fillPath()
            }
            cg.addPath(shape(style.coreShape, rect: rect, radius: radius))
            cg.setFillColor(UIColor(palette.core.color).cgColor)
            cg.fillPath()
        }
        return gaussianBlur(image, sigma: max(style.coreBlur - 0.5, 0))
    }

    private static func shape(_ shape: CoreShape, rect: CGRect, radius: CGFloat) -> CGPath {
        switch shape {
        case .rounded:
            return CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)
        case .radial:
            return CGPath(ellipseIn: rect, transform: nil)
        }
    }

    /// Grain and the hairline edge with its gloss.
    private static func overlay(style: GlowStyle, size: CGSize, scale: CGFloat) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: size, format: format(scale: scale))
        return renderer.image { context in
            let cg = context.cgContext
            if style.grain > 0 {
                cg.saveGState()
                cg.addPath(cardPath(size, radius: style.radius))
                cg.clip()
                drawGrain(style: style, size: size, scale: scale, into: cg)
                cg.restoreGState()
            }
            // A glossy edge: a lighter hairline along the top fading out down the sides.
            let path = cardPath(size, radius: style.radius, inset: 0.5)
            cg.saveGState()
            cg.addPath(path)
            cg.setStrokeColor(UIColor.white.withAlphaComponent(0.07).cgColor)
            cg.setLineWidth(1)
            cg.strokePath()
            cg.restoreGState()
            let gloss = [
                UIColor.white.withAlphaComponent(0.35).cgColor,
                UIColor.white.withAlphaComponent(0.08).cgColor,
                UIColor.white.withAlphaComponent(0.04).cgColor,
            ]
            let stops: [CGFloat] = [0, 0.25, 1]
            guard let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: gloss as CFArray, locations: stops
            ) else { return }
            cg.saveGState()
            cg.addPath(path)
            cg.setLineWidth(1)
            cg.replacePathWithStrokedPath()
            cg.clip()
            cg.drawLinearGradient(
                gradient,
                start: .zero,
                end: CGPoint(x: 0, y: size.height),
                options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
            )
            cg.restoreGState()
        }
    }

    /// Film grain: a large tile of sparse specks, one device pixel each, so it reads as texture
    /// rather than dots. The seed shifts where the card starts in the tile, which hides the repeat.
    private static func drawGrain(style: GlowStyle, size: CGSize, scale: CGFloat, into cg: CGContext) {
        guard let tile = grainImage else { return }
        let tilePoints = CGFloat(GlowRenderer.grainTileSize) / scale
        let offsetX = CGFloat(style.grainSeed % GlowRenderer.grainTileSize) / scale
        let offsetY = CGFloat(style.grainSeed / GlowRenderer.grainTileSize) / scale
        cg.saveGState()
        cg.setAlpha(min(max(style.grain, 0), 1))
        var y = -offsetY
        while y < size.height {
            var x = -offsetX
            while x < size.width {
                cg.draw(tile, in: CGRect(x: x, y: y, width: tilePoints, height: tilePoints))
                x += tilePoints
            }
            y += tilePoints
        }
        cg.restoreGState()
    }

    static let grainTileSize = 512

    private static let grainImage: CGImage? = {
        let n = grainTileSize
        var random = GlowRandom(seed: 7)
        var bytes = [UInt8](repeating: 0, count: n * n * 4)
        for i in 0..<(n * n) {
            let v = random.nextFloat()
            if v < 0.55 { continue }
            // Most pixels stay clear; the rest get a random strength, and some darken instead of
            // lighten.
            let alpha = UInt8(min(max(((v - 0.55) / 0.45) * 255, 0), 255))
            let light = random.nextFloat() >= 0.35
            let value: UInt8 = light ? alpha : 0
            bytes[i * 4 + 0] = value
            bytes[i * 4 + 1] = value
            bytes[i * 4 + 2] = value
            bytes[i * 4 + 3] = alpha
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }()

    /// A Gaussian blur with standard deviation `sigma` points (Compose passes a mask-filter radius
    /// that works out to sigma - 0.5, which the caller applies to `coreBlur`).
    private static func gaussianBlur(_ image: UIImage, sigma: CGFloat) -> UIImage {
        guard sigma > 0.5, let source = image.cgImage else { return image }
        let input = CIImage(cgImage: source)
        guard let filter = CIFilter(name: "CIGaussianBlur") else { return image }
        filter.setValue(input, forKey: kCIInputImageKey)
        filter.setValue(NSNumber(value: Double(sigma)), forKey: kCIInputRadiusKey)
        guard let output = filter.outputImage,
              let result = ciContext.createCGImage(output, from: input.extent) else { return image }
        return UIImage(cgImage: result, scale: image.scale, orientation: .up)
    }
}
