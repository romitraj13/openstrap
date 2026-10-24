import SwiftUI
import UIKit
import CoreText

/// The three font families, ported from the `FontFamily` values in
/// `app/src/main/kotlin/org/openstrap/app/ui/theme/Theme.kt`. All three files are *variable*
/// fonts: each ships one default instance (Light, or Thin for Museo Moderno) and an `wght` axis,
/// which is why Compose builds a `FontFamily` from five `FontVariation.Settings`.
///
/// `UIFont(name:)` only reaches the default instance, so a weight is selected by adding the
/// variation axis to the default face's descriptor — the fallback the U1 card names. `DesignTests`
/// proves the weights really differ: Light and Bold measure differently.
enum DesignFontFamily: String, CaseIterable {
    /// PostScript name of the default instance, which is what `UIFont(name:size:)` finds.
    case funnelDisplay = "FunnelDisplay-Light"
    case funnelSans = "FunnelSans-Light"
    case museoModerno = "MuseoModerno-Thin"

    /// The name the family calls itself (TTF name id 16).
    var familyName: String {
        switch self {
        case .funnelDisplay: return "Funnel Display"
        case .funnelSans: return "Funnel Sans"
        case .museoModerno: return "MuseoModerno"
        }
    }

    /// The weights Theme.kt instantiates for this family, in the order it lists them.
    var weights: [CGFloat] {
        switch self {
        case .funnelDisplay: return [300, 400, 500, 600, 700]
        case .funnelSans: return [400, 500, 600, 700]
        case .museoModerno: return [600]
        }
    }

    /// The `wght` axis, as CoreText spells it.
    private static let weightAxis = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)

    func uiFont(size: CGFloat, weight: CGFloat) -> UIFont {
        guard let base = UIFont(name: rawValue, size: size) else {
            // The font is missing from the bundle (UIAppFonts in project.yml). The tests catch it;
            // the app still draws rather than crashing mid-layout.
            return UIFont.systemFont(ofSize: size, weight: Self.systemWeight(weight))
        }
        let descriptor = base.fontDescriptor.addingAttributes([
            Self.weightAxis: [NSNumber(value: 0x7767_6874): NSNumber(value: Double(weight))]
        ])
        return UIFont(descriptor: descriptor, size: size)
    }

    func font(size: CGFloat, weight: CGFloat, tabularFigures: Bool = false) -> Font {
        let font = Font(uiFont(size: size, weight: weight))
        return tabularFigures ? font.monospacedDigit() : font
    }

    private static func systemWeight(_ weight: CGFloat) -> UIFont.Weight {
        switch weight {
        case ..<350: return .light
        case ..<450: return .regular
        case ..<550: return .medium
        case ..<650: return .semibold
        default: return .bold
        }
    }

    /// The name Compose uses for a weight ("Light", "SemiBold"), for the Design lab's rows.
    static func weightName(_ weight: CGFloat) -> String {
        switch weight {
        case ..<350: return "Light"
        case ..<450: return "Normal"
        case ..<550: return "Medium"
        case ..<650: return "SemiBold"
        case ..<750: return "Bold"
        default: return "ExtraBold"
        }
    }
}

/// One text style: family, weight, size (points, equal to Compose's sp at the default type size),
/// tracking and line height. SwiftUI has no `lineHeight`, so Compose's is expressed as the extra
/// leading `.lineSpacing` adds below each line.
struct AppTextStyle {
    let name: String
    let family: DesignFontFamily
    let size: CGFloat
    let weight: CGFloat
    let tracking: CGFloat
    var lineHeight: CGFloat?
    var tabularFigures: Bool = false

    var font: Font { family.font(size: size, weight: weight, tabularFigures: tabularFigures) }

    var lineSpacing: CGFloat {
        guard let lineHeight else { return 0 }
        return max(0, lineHeight - size)
    }
}

extension View {
    /// The SwiftUI equivalent of a Compose `TextStyle`: family, weight, size, tracking, leading.
    func appTextStyle(_ style: AppTextStyle) -> some View {
        self.font(style.font)
            .tracking(style.tracking)
            .lineSpacing(style.lineSpacing)
    }
}

/// The app's type scale, ported from `AppTypography` in Theme.kt. The sizes, weights, tracking and
/// line heights are Compose's: the `display*` styles are Funnel Display SemiBold with tabular
/// figures, the `text*` ones Funnel Sans over Material 3's default sizes.
enum AppTypography {
    static let displayLarge = AppTextStyle(
        name: "displayLarge", family: .funnelDisplay, size: 64, weight: 600, tracking: -1.5, lineHeight: 64, tabularFigures: true)
    static let displayMedium = AppTextStyle(
        name: "displayMedium", family: .funnelDisplay, size: 48, weight: 600, tracking: -1, lineHeight: 52, tabularFigures: true)
    static let displaySmall = AppTextStyle(
        name: "displaySmall", family: .funnelDisplay, size: 34, weight: 600, tracking: -0.5, lineHeight: 44, tabularFigures: true)
    static let headlineLarge = AppTextStyle(
        name: "headlineLarge", family: .funnelDisplay, size: 30, weight: 600, tracking: -0.4, lineHeight: 40, tabularFigures: true)
    static let headlineMedium = AppTextStyle(
        name: "headlineMedium", family: .funnelDisplay, size: 26, weight: 600, tracking: -0.3, lineHeight: 36, tabularFigures: true)
    static let headlineSmall = AppTextStyle(
        name: "headlineSmall", family: .funnelDisplay, size: 22, weight: 600, tracking: -0.2, lineHeight: 32, tabularFigures: true)
    static let titleLarge = AppTextStyle(
        name: "titleLarge", family: .funnelDisplay, size: 20, weight: 600, tracking: 0, lineHeight: 28, tabularFigures: true)
    static let titleMedium = AppTextStyle(
        name: "titleMedium", family: .funnelSans, size: 16, weight: 600, tracking: 0.15, lineHeight: 24, tabularFigures: true)
    static let titleSmall = AppTextStyle(
        name: "titleSmall", family: .funnelSans, size: 14, weight: 600, tracking: 0.1, lineHeight: 20)
    static let bodyLarge = AppTextStyle(
        name: "bodyLarge", family: .funnelSans, size: 16, weight: 400, tracking: 0.5, lineHeight: 24)
    static let bodyMedium = AppTextStyle(
        name: "bodyMedium", family: .funnelSans, size: 14, weight: 400, tracking: 0.25, lineHeight: 20)
    static let bodySmall = AppTextStyle(
        name: "bodySmall", family: .funnelSans, size: 12, weight: 400, tracking: 0.4, lineHeight: 16)
    static let labelLarge = AppTextStyle(
        name: "labelLarge", family: .funnelSans, size: 14, weight: 600, tracking: 0.1, lineHeight: 20)
    /// Uppercase tracked labels ("TIME ASLEEP") are styled with labelMedium.
    static let labelMedium = AppTextStyle(
        name: "labelMedium", family: .funnelSans, size: 11, weight: 600, tracking: 1.4, lineHeight: 16)
    static let labelSmall = AppTextStyle(
        name: "labelSmall", family: .funnelSans, size: 11, weight: 500, tracking: 0.5, lineHeight: 16)

    /// Every style, in Compose's declaration order.
    static let all: [AppTextStyle] = [
        displayLarge, displayMedium, displaySmall,
        headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall,
        bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall,
    ]
}

/// Display type for the glow language, ported from
/// `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowType.kt`: Funnel Display Light with tight
/// tracking. Body text stays in the app's Funnel Sans.
enum GlowType {
    /// Verdicts and hero headlines ("Good energy", "Ready").
    static let headline = AppTextStyle(
        name: "GlowType.headline", family: .funnelDisplay, size: 26, weight: 300, tracking: -0.52,
        lineHeight: 30, tabularFigures: true)

    /// Card titles ("Heart rate", "Activity").
    static let title = AppTextStyle(
        name: "GlowType.title", family: .funnelDisplay, size: 20, weight: 300, tracking: -0.4,
        tabularFigures: true)

    /// Key numbers next to rings.
    static let number = AppTextStyle(
        name: "GlowType.number", family: .funnelDisplay, size: 20, weight: 300, tracking: -0.4,
        tabularFigures: true)

    /// Every glow style, in Compose's declaration order.
    static let all: [AppTextStyle] = [headline, title, number]
}
