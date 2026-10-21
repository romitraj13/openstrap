import SwiftUI
import UIKit

/// Shape of the dark core: a rounded rectangle that follows the card, or a soft oval.
enum CoreShape: Hashable {
    case rounded
    case radial
}

/// Which edge the rim light is strongest on.
enum LightFrom: Hashable {
    case top
    case bottom
    case all
}

/// How a card's dark core moves: its personality. Pick the one that matches what the card
/// measures, so motion carries meaning (a heart card beats, a breathing card breathes).
enum GlowMotion: Hashable {
    /// A slow, calm wander. The default for scores and anything without a rhythm of its own.
    case drift
    /// A lub-dub pulse at `bpm`: the core kicks and tightens on each beat, then relaxes.
    case heartbeat(bpm: Int)
    /// A slow inhale (swell, soften, rise) and longer exhale, `perMinute` breaths a minute.
    case breath(perMinute: CGFloat)
    /// Lively: a quick wander with a bounce at walking `cadence` (steps a minute). `energy` 0..1
    /// scales it, e.g. progress toward the step goal, so a lazy day moves less.
    case stride(cadence: Int, energy: CGFloat)
    /// No motion.
    case still

    /// Kotlin's default arguments, kept as named values because Swift enum cases carry no defaults.
    static let heartbeatDefault = GlowMotion.heartbeat(bpm: 62)
    static let breathDefault = GlowMotion.breath(perMinute: 6)
    static let strideDefault = GlowMotion.stride(cadence: 110, energy: 1)
}

/// The "glow" design language: a card lit from its rim, with a dark core floating inside it and an
/// optional second-tone band between the two. Everything is derived from one `color`.
///
/// Ported from `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowStyle.kt`. Core position and size
/// are fractions of the card (0..1, may go past the edges). A `toneWidth` of 0 turns the second
/// tone off; a `glowStrength` of 0 turns the rim light off. Dp values are points.
struct GlowStyle: Hashable {
    var color: Color
    var radius: CGFloat = 48
    var coreShape: CoreShape = .rounded
    var coreX: CGFloat = 0.5
    var coreY: CGFloat = 0.64
    var coreWidth: CGFloat = 0.74
    var coreHeight: CGFloat = 0.6
    var coreBlur: CGFloat = 22
    var coreOpacity: CGFloat = 1
    /// HSL lightness of the core, 0..1. 0.09 is the reference's deep plum; ~0.02 reads as black.
    var coreLightness: CGFloat = 0.09
    /// Hue rotation of the second tone, in degrees (-40 turns pink into violet).
    var toneShift: CGFloat = -40
    var toneWidth: CGFloat = 24
    var lightFrom: LightFrom = .top
    var glowSize: CGFloat = 30
    var glowStrength: CGFloat = 0.8
    /// A thick band of rim light all the way round, like a glossy bevel, 0..1 (0 = none).
    var bevel: CGFloat = 0
    /// How far the card's slab edge shows below it, for a 3D look (0 = flat).
    var depth: CGFloat = 0
    /// Film grain opacity, 0..1.
    var grain: CGFloat = 0.06
    var motion: GlowMotion = .drift

    /// Where this look samples the film-grain tile. Android gives every card instance its own
    /// random spot; here it comes from the style, so the rendered layers stay cacheable by style
    /// and size instead of re-rendering whenever a card scrolls back into view.
    var grainSeed: Int {
        (hashValue & 0x7FFF_FFFF) % (512 * 512)
    }

    /// The same look in another colour.
    func tinted(_ color: Color) -> GlowStyle {
        var copy = self
        copy.color = color
        return copy
    }

    /// Pink rim, violet band, plum core that follows the card ("Last test").
    static let bloom = GlowStyle(color: Color(hex: 0xD65AA0), bevel: 1)

    /// Near-black core in the upper left, navy light from below ("Training").
    static let midnight = GlowStyle(
        color: Color(hex: 0x2A44B0),
        coreShape: .radial,
        coreX: 0.30, coreY: 0.20, coreWidth: 0.80, coreHeight: 1.5,
        coreBlur: 32, coreLightness: 0.03,
        toneWidth: 0,
        lightFrom: .bottom, glowSize: 26, glowStrength: 0.7
    )

    /// Black top melting into orange from below ("Breath Volume").
    static let ember = GlowStyle(
        color: Color(hex: 0xF2582A),
        coreShape: .radial,
        coreX: 0.5, coreY: 0.05, coreWidth: 1.4, coreHeight: 1.2,
        coreBlur: 32, coreLightness: 0.02,
        toneShift: -15, toneWidth: 26,
        lightFrom: .bottom, glowSize: 34, glowStrength: 0.9
    )

    /// The three presets, as the Android `presets` list names them.
    static let presets: [GlowPreset] = [
        GlowPreset(name: "Bloom", style: .bloom),
        GlowPreset(name: "Midnight", style: .midnight),
        GlowPreset(name: "Ember", style: .ember),
    ]

    /// A glow style tuned for charts: the dark core sits low and wide, behind the plot.
    static func chart(_ color: Color) -> GlowStyle {
        var style = GlowStyle.bloom
        style.color = color
        style.coreY = 0.64
        style.coreWidth = 0.88
        style.coreHeight = 0.66
        style.coreBlur = 24
        style.toneWidth = 16
        // Sleep's quiet recipe: a near-same-hue band, a softer rim and a lighter bevel.
        style.toneShift = -10
        style.glowStrength = 0.6
        style.bevel = 0.6
        return style
    }
}

/// A style with the name the Android `presets` list gives it.
struct GlowPreset: Identifiable, Hashable {
    let name: String
    let style: GlowStyle
    var id: String { name }
}

/// The handful of colours a `GlowStyle` is drawn with, all derived from its main colour.
///
/// Ported from `GlowPalette` in GlowStyle.kt: HSL from `ColorUtils.colorToHSL`, HSL back through
/// Compose's `Color.hsl`, and a luminance cap so white text stays readable whatever the hue.
struct GlowPalette {
    struct Swatch: Hashable {
        let rgb: SRGBColor
        var color: Color { rgb.color }
        var luminance: CGFloat { rgb.luminance }
        /// WCAG contrast of white on this colour: 1.05 / (L + 0.05).
        var contrastOnWhite: CGFloat { 1.05 / (rgb.luminance + 0.05) }
    }

    let light: Swatch
    let mid: Swatch
    let tone: Swatch
    let core: Swatch

    init(_ style: GlowStyle) {
        let hsl = GlowPalette.hsl(from: SRGBColor(color: style.color))
        let h = hsl.h
        let l = hsl.l
        // Near-grey colours stay neutral (a dark grey glow); anything coloured gets enough
        // saturation to glow.
        let s = hsl.s < 0.12 ? hsl.s : min(max(hsl.s, 0.35), 0.9)
        // A rim not far above the body in brightness, so the light melts in rather than sitting on
        // top.
        light = Swatch(rgb: GlowPalette.rgb(h: h, s: s * 0.9, l: min(max(l + 0.03, 0.45), 0.6)))
        // Same lightness looks far brighter in yellow-green than in red or blue, so the body and
        // band are darkened until their actual brightness keeps white text readable, whatever the
        // hue. Only a small hue step from the rim, so the card reads as one colour in different
        // light.
        mid = Swatch(rgb: GlowPalette.capped(h: h - 4, s: s * 0.85, l: 0.32, maxLuminance: GlowPalette.midMaxLuminance))
        tone = Swatch(rgb: GlowPalette.capped(
            h: h + style.toneShift, s: s * 0.8, l: 0.28, maxLuminance: GlowPalette.toneMaxLuminance))
        core = Swatch(rgb: GlowPalette.rgb(h: h, s: 0.7, l: style.coreLightness))
    }

    /// Relative luminance; white on it has a contrast of at least 1.05 / (0.09 + 0.05) = 7.5:1.
    static let midMaxLuminance: CGFloat = 0.09
    /// The band round the core stays a step darker than the body.
    static let toneMaxLuminance: CGFloat = 0.07

    /// The colour at lightness `l`, or darker until its actual brightness is at most `max`.
    static func capped(h: CGFloat, s: CGFloat, l: CGFloat, maxLuminance: CGFloat) -> SRGBColor {
        var lightness = l
        while lightness > 0.08 {
            let color = rgb(h: h, s: s, l: lightness)
            if color.luminance <= maxLuminance { return color }
            lightness -= 0.01
        }
        return rgb(h: h, s: s, l: 0.08)
    }

    /// `ColorUtils.colorToHSL`: hue in degrees, saturation and lightness 0..1.
    static func hsl(from color: SRGBColor) -> (h: CGFloat, s: CGFloat, l: CGFloat) {
        let r = color.red, g = color.green, b = color.blue
        let maxValue = Swift.max(r, Swift.max(g, b))
        let minValue = Swift.min(r, Swift.min(g, b))
        let delta = maxValue - minValue
        let l = (maxValue + minValue) / 2
        if maxValue == minValue {
            return (0, 0, min(max(l, 0), 1))
        }
        var h: CGFloat
        if maxValue == r {
            h = ((g - b) / delta).truncatingRemainder(dividingBy: 6)
        } else if maxValue == g {
            h = (b - r) / delta + 2
        } else {
            h = (r - g) / delta + 4
        }
        h = (h * 60).truncatingRemainder(dividingBy: 360)
        if h < 0 { h += 360 }
        let s = delta / (1 - abs(2 * l - 1))
        return (min(max(h, 0), 360), min(max(s, 0), 1), min(max(l, 0), 1))
    }

    /// Compose's `Color.hsl`: wrap the hue, clamp saturation and lightness, then the standard
    /// HSL-to-RGB conversion.
    static func rgb(h: CGFloat, s: CGFloat, l: CGFloat) -> SRGBColor {
        var hue = h.truncatingRemainder(dividingBy: 360)
        if hue < 0 { hue += 360 }
        let hh = hue / 360 * 6
        let saturation = min(max(s, 0), 1)
        let lightness = min(max(l, 0), 1)
        let c = (1 - abs(2 * lightness - 1)) * saturation
        let x = c * (1 - abs(hh.truncatingRemainder(dividingBy: 2) - 1))
        let m = lightness - c / 2
        let rgb: (CGFloat, CGFloat, CGFloat)
        switch hh {
        case ..<1: rgb = (c, x, 0)
        case ..<2: rgb = (x, c, 0)
        case ..<3: rgb = (0, c, x)
        case ..<4: rgb = (0, x, c)
        case ..<5: rgb = (x, 0, c)
        default: rgb = (c, 0, x)
        }
        return SRGBColor(red: rgb.0 + m, green: rgb.1 + m, blue: rgb.2 + m)
    }
}

extension SRGBColor {
    /// The sRGB components of a SwiftUI colour, as `Color.toArgb()` would give them to Compose.
    init(color: Color) {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        _ = UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        self.init(red: red, green: green, blue: blue)
    }
}
