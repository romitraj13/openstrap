import OpenStrapShared
import SwiftUI

/// The colour tokens, ported from `app/src/main/kotlin/org/openstrap/app/ui/theme/Theme.kt`
/// (`MetricColors` and the `Dark` colour scheme). Every colour the app draws comes from here.

extension Color {
    /// `Color(0xFFRRGGBB)`, as Compose writes it.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// A colour as sRGB components, for the maths the palette needs: HSL, luminance and the
/// Oklab mix Compose's `lerp` performs.
struct SRGBColor: Hashable {
    var red: CGFloat
    var green: CGFloat
    var blue: CGFloat

    init(hex: UInt32) {
        red = CGFloat((hex >> 16) & 0xFF) / 255
        green = CGFloat((hex >> 8) & 0xFF) / 255
        blue = CGFloat(hex & 0xFF) / 255
    }

    init(red: CGFloat, green: CGFloat, blue: CGFloat) {
        self.red = min(max(red, 0), 1)
        self.green = min(max(green, 0), 1)
        self.blue = min(max(blue, 0), 1)
    }

    var color: Color { Color(.sRGB, red: Double(red), green: Double(green), blue: Double(blue), opacity: 1) }

    /// sRGB transfer function, as Compose's `linearized` uses it.
    private static func linear(_ channel: Double) -> Double {
        channel <= 0.04045 ? channel / 12.92 : Foundation.pow((channel + 0.055) / 1.055, 2.4)
    }

    private static func delinear(_ channel: Double) -> Double {
        channel <= 0.0031308 ? channel * 12.92 : 1.055 * Foundation.pow(channel, 1 / 2.4) - 0.055
    }

    /// Compose's `Color.luminance()`: sRGB relative luminance.
    var luminance: CGFloat {
        let l = SRGBColor.linear(Double(red)) * 0.2126
            + SRGBColor.linear(Double(green)) * 0.7152
            + SRGBColor.linear(Double(blue)) * 0.0722
        return CGFloat(l)
    }

    /// Compose's `lerp(start, stop, fraction)`, which mixes in Oklab rather than sRGB.
    func mixedOklab(to other: SRGBColor, by fraction: CGFloat) -> SRGBColor {
        let t = Double(min(max(fraction, 0), 1))
        let from = SRGBColor.oklab(fromLinear: SRGBColor.linearComponents(self))
        let to = SRGBColor.oklab(fromLinear: SRGBColor.linearComponents(other))
        let mixed = (
            from.0 + (to.0 - from.0) * t,
            from.1 + (to.1 - from.1) * t,
            from.2 + (to.2 - from.2) * t
        )
        return SRGBColor.fromLinear(SRGBColor.linear(fromOklab: mixed))
    }

    private static func linearComponents(_ color: SRGBColor) -> (Double, Double, Double) {
        (linear(Double(color.red)), linear(Double(color.green)), linear(Double(color.blue)))
    }

    private static func fromLinear(_ rgb: (Double, Double, Double)) -> SRGBColor {
        SRGBColor(red: CGFloat(delinear(rgb.0)), green: CGFloat(delinear(rgb.1)), blue: CGFloat(delinear(rgb.2)))
    }

    private static func oklab(fromLinear rgb: (Double, Double, Double)) -> (Double, Double, Double) {
        let l = 0.4122214708 * rgb.0 + 0.5363325363 * rgb.1 + 0.0514459929 * rgb.2
        let m = 0.2119034982 * rgb.0 + 0.6806995451 * rgb.1 + 0.1073969566 * rgb.2
        let s = 0.0883024619 * rgb.0 + 0.2817188376 * rgb.1 + 0.6299787005 * rgb.2
        let l3 = cbrt(l), m3 = cbrt(m), s3 = cbrt(s)
        return (
            0.2104542553 * l3 + 0.7936177850 * m3 - 0.0040720468 * s3,
            1.9779984951 * l3 - 2.4285922050 * m3 + 0.4505937099 * s3,
            0.0259040371 * l3 + 0.7827717662 * m3 - 0.8086757660 * s3
        )
    }

    private static func linear(fromOklab lab: (Double, Double, Double)) -> (Double, Double, Double) {
        let l3 = lab.0 + 0.3963377774 * lab.1 + 0.2158037573 * lab.2
        let m3 = lab.0 - 0.1055613458 * lab.1 - 0.0638541728 * lab.2
        let s3 = lab.0 - 0.0894841775 * lab.1 - 1.2914855480 * lab.2
        let l = l3 * l3 * l3, m = m3 * m3 * m3, s = s3 * s3 * s3
        return (
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        )
    }
}

/// Metric accent colours, shared by cards and charts. Tuned to read well on near-black.
///
/// Mirrors `MetricColors` in Theme.kt, same names in lowerCamelCase.
enum MetricColors {
    static let heart = Color(hex: 0xFF5A5F)
    static let steps = Color(hex: 0xC3F53C)
    static let sleepDeep = Color(hex: 0x4C6FFF)
    static let sleepLight = Color(hex: 0x8FA8FF)
    static let sleepRem = Color(hex: 0xB37CFF)
    static let sleepAwake = Color(hex: 0xFFB547)
    static let spo2 = Color(hex: 0x38BDF8)
    static let stress = Color(hex: 0xF062C0)
    static let hrv = Color(hex: 0x2DD4BF)
    static let temperature = Color(hex: 0xFF8A3D)
    static let readiness = Color(hex: 0x7CF2C9)
    static let charge = Color(hex: 0xFFD166)
    /// Recovery and Charge as saturated as `sleepDeep`, for marks that sit beside it (the Today top card).
    static let readinessDeep = Color(hex: 0x00C99B)
    static let chargeDeep = Color(hex: 0xF5B400)
    static let good = Color(hex: 0x3DDC97)
    static let warn = Color(hex: 0xFFB547)
    static let bad = Color(hex: 0xFF5A5F)

    /// Charge from empty to full: red, orange, gold, then green.
    static let chargeScaleRGB: [(level: CGFloat, color: SRGBColor)] = [
        (0, SRGBColor(hex: 0xFF5A5F)),
        (0.3, SRGBColor(hex: 0xFF9F43)),
        (0.55, SRGBColor(hex: 0xFFD166)),
        (0.8, SRGBColor(hex: 0x3DDC97)),
    ]

    /// The same scale as colours, for callers that draw it directly.
    static let chargeScale: [(level: CGFloat, color: Color)] =
        chargeScaleRGB.map { ($0.level, $0.color.color) }

    /// The colour for a Charge level (0–100) on `chargeScale`; plain gold when unknown.
    static func chargeAt(_ level: Double?) -> Color {
        guard let level else { return charge }
        let f = CGFloat(level) / 100
        var index = 0
        for i in chargeScaleRGB.indices where chargeScaleRGB[i].level <= f {
            index = i
        }
        let (a, ca) = chargeScaleRGB[index]
        guard index + 1 < chargeScaleRGB.count else { return ca.color }
        let (b, cb) = chargeScaleRGB[index + 1]
        let t = min(max((f - a) / (b - a), 0), 1)
        return ca.mixedOklab(to: cb, by: t).color
    }

    /// Battery colour: red at 15% or below, amber up to 40%, green above.
    static func battery(_ level: Int?) -> Color {
        guard let level else { return good }
        if level <= 15 { return bad }
        if level <= 40 { return warn }
        return good
    }

    /// Today's top card tells how the day looks before you read a number: teal when the leading
    /// score is good, amber when it is middling or the strap is straining, coral when it is low,
    /// and the neutral grey when there is no score. The bands themselves are shared Kotlin
    /// (`TodayGlow.overviewTone`, GlowTodayScreen.kt:357); this is only the colour they name.
    ///
    /// The switch is on the Kotlin enum itself, not on `tone.name`: a name string that stops matching
    /// falls through to the last case without a word, which is how a coral day could draw grey.
    /// `default` is `None`, and any tone Android adds before this file learns it.
    static func overview(_ tone: OverviewTone) -> Color {
        switch tone {
        case OverviewTone.strain: return Color(hex: 0xF2A33A)
        case OverviewTone.good: return hrv
        case OverviewTone.middling: return Color(hex: 0xF2A33A)
        case OverviewTone.low: return Color(hex: 0xF0605A)
        default: return Color(hex: 0x6E7280)
        }
    }

    /// A What changed tile's dot: the metric's own accent (`Signal` accents, GlowTodayScreen.kt:416).
    static func signal(_ accent: SignalAccent) -> Color {
        switch accent.name {
        case "Heart": return heart
        case "Hrv": return hrv
        case "Spo2": return spo2
        case "Temperature": return temperature
        case "Stress": return stress
        case "SleepLight": return sleepLight
        default: return hrv
        }
    }

    /// A score on Today's top card wears the deep accent its tab uses (`MetricColors.chargeDeep`,
    /// `readinessDeep`, and the Sleep card's blue).
    static func today(_ accent: TodayAccent) -> Color {
        switch accent.name {
        case "Charge": return chargeDeep
        case "Recovery": return readinessDeep
        default: return sleepDeep
        }
    }

    /// Every accent, in the order Theme.kt declares them, for the Design lab and the tests.
    static let allAccents: [NamedColor] = [
        NamedColor(name: "heart", color: heart),
        NamedColor(name: "steps", color: steps),
        NamedColor(name: "sleepDeep", color: sleepDeep),
        NamedColor(name: "sleepLight", color: sleepLight),
        NamedColor(name: "sleepRem", color: sleepRem),
        NamedColor(name: "sleepAwake", color: sleepAwake),
        NamedColor(name: "spo2", color: spo2),
        NamedColor(name: "stress", color: stress),
        NamedColor(name: "hrv", color: hrv),
        NamedColor(name: "temperature", color: temperature),
        NamedColor(name: "readiness", color: readiness),
        NamedColor(name: "charge", color: charge),
        NamedColor(name: "readinessDeep", color: readinessDeep),
        NamedColor(name: "chargeDeep", color: chargeDeep),
        NamedColor(name: "good", color: good),
        NamedColor(name: "warn", color: warn),
        NamedColor(name: "bad", color: bad),
    ]
}

/// A colour with the token name it was declared under, for the Design lab and the parity table.
struct NamedColor: Identifiable, Hashable {
    let name: String
    let color: Color
    var id: String { name }
}

/// The `Dark` colour scheme from Theme.kt. Always dark: the glow design is built for OLED black
/// and does not follow the system light mode.
///
/// No accent: controls are white with black on top, like the marks on the glow cards, so colour in
/// the app always means data. Elevation would tint raised surfaces with the primary, hence a
/// transparent surface tint and neutral greys for the containers.
enum ThemeColors {
    static let primary = Color(hex: 0xFFFFFF)
    static let onPrimary = Color(hex: 0x000000)
    static let primaryContainer = Color(hex: 0x2C2C2E)
    static let onPrimaryContainer = Color(hex: 0xFFFFFF)
    static let secondary = Color(hex: 0xFFFFFF)
    static let onSecondary = Color(hex: 0x000000)
    static let secondaryContainer = Color(hex: 0x2C2C2E)
    static let onSecondaryContainer = Color(hex: 0xFFFFFF)
    static let tertiary = Color(hex: 0xFFFFFF)
    static let onTertiary = Color(hex: 0x000000)
    static let tertiaryContainer = Color(hex: 0x2C2C2E)
    static let onTertiaryContainer = Color(hex: 0xFFFFFF)
    static let surfaceTint = Color.clear
    static let background = Color(hex: 0x000000)
    static let onBackground = Color(hex: 0xFFFFFF)
    static let surface = Color(hex: 0x000000)
    static let onSurface = Color(hex: 0xFFFFFF)
    static let surfaceContainerLowest = Color(hex: 0x000000)
    static let surfaceContainerLow = Color(hex: 0x0A0A0A)
    static let surfaceContainer = Color(hex: 0x141414)
    static let surfaceContainerHigh = Color(hex: 0x1C1C1E)
    static let surfaceContainerHighest = Color(hex: 0x2C2C2E)
    static let onSurfaceVariant = Color(hex: 0x8E8E93)
    static let outline = Color(hex: 0x2C2C2E)
    static let outlineVariant = Color(hex: 0x232323)
    static let error = Color(hex: 0xFF6B6B)

    /// The two sex toggles' accents (`FemaleColor`/`MaleColor`, ProfileScreen.kt:252-253).
    static let female = Color(hex: 0xFFF472B6)
    static let male = Color(hex: 0xFF60A5FA)
}
