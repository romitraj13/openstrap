import XCTest
import SwiftUI
import UIKit
@testable import OpenStrap

/// Card U1: the design foundation. The fonts and the icon sets have to be in the bundle (not just in
/// the project), the variable weights have to reach the renderer, and the glow palette has to keep
/// white text readable — the invariant GlowStyle.kt states.
final class DesignTests: XCTestCase {
    // MARK: - Fonts

    /// Every PostScript name UIFont is asked for. UIAppFonts in project.yml registers the files; a
    /// missing entry makes UIFont(name:) return nil, which is what this catches.
    func testEveryFontPostScriptNameLoads() {
        for family in DesignFontFamily.allCases {
            XCTAssertNotNil(
                UIFont(name: family.rawValue, size: 20),
                "\(family.rawValue) did not load: is the file in the bundle and in UIAppFonts?"
            )
        }
    }

    /// The three files are variable fonts whose default instance is Light (Thin for Museo Moderno),
    /// so heavier weights only exist through the `wght` variation axis. If the axis does not reach
    /// the renderer, every weight measures the same.
    func testVariableWeightsReachTheRenderer() {
        let sample = "Recovery 73 Ready" as NSString
        for family in [DesignFontFamily.funnelDisplay, .funnelSans] {
            let light = family.uiFont(size: 24, weight: 300)
            let bold = family.uiFont(size: 24, weight: 700)
            let lightWidth = sample.size(withAttributes: [.font: light]).width
            let boldWidth = sample.size(withAttributes: [.font: bold]).width
            XCTAssertGreaterThan(
                boldWidth, lightWidth + 1,
                "\(family.familyName): Bold did not render wider than Light, so the wght axis is not applied"
            )
        }
    }

    // MARK: - Icons

    /// Every case has an image set, and the count matches PhosphorIcons.kt's 46 `icon("…")` calls.
    func testEveryPhosphorIconLoads() {
        XCTAssertEqual(PhosphorIcon.allCases.count, 46)
        for icon in PhosphorIcon.allCases {
            XCTAssertNotNil(UIImage(named: icon.rawValue), "\(icon.rawValue).imageset is missing or has no image")
        }
    }

    // MARK: - Glow palette

    /// The invariants GlowStyle.kt states: white on `mid` is at least 7.5:1 and on `tone` at least
    /// 8:1, for the three presets and for every MetricColors accent through `chart(color:)`.
    func testWhiteStaysReadableOnEveryGlowCard() {
        var styles: [(name: String, style: GlowStyle)] = GlowStyle.presets.map { ($0.name, $0.style) }
        styles += MetricColors.allAccents.map { ($0.name, GlowStyle.chart($0.color)) }
        XCTAssertEqual(styles.count, 20)

        for (name, style) in styles {
            let palette = GlowPalette(style)
            XCTAssertLessThanOrEqual(
                palette.mid.luminance, GlowPalette.midMaxLuminance + 1e-6,
                "\(name): mid is brighter than the cap"
            )
            XCTAssertLessThanOrEqual(
                palette.tone.luminance, GlowPalette.toneMaxLuminance + 1e-6,
                "\(name): tone is brighter than the cap"
            )
            // 1e-4 of slack: the cap is exactly 7.5:1, and the maths is Float-like either side.
            XCTAssertGreaterThanOrEqual(
                palette.mid.contrastOnWhite, 7.5 - 1e-4,
                "\(name): white on mid is below 7.5:1"
            )
            XCTAssertGreaterThanOrEqual(
                palette.tone.contrastOnWhite, 8.0 - 1e-4,
                "\(name): white on tone is below 8.0:1"
            )
        }
    }

    // MARK: - Tokens

    func testBatteryColourFollowsTheLevel() {
        assertSame(MetricColors.battery(nil), MetricColors.good, "unknown battery is green")
        assertSame(MetricColors.battery(15), MetricColors.bad, "15% or below is red")
        assertSame(MetricColors.battery(40), MetricColors.warn, "up to 40% is amber")
        assertSame(MetricColors.battery(41), MetricColors.good, "above 40% is green")
    }

    func testChargeColourFollowsTheScale() {
        assertSame(MetricColors.chargeAt(nil), MetricColors.charge, "an unknown level is plain gold")
        assertSame(MetricColors.chargeAt(0), MetricColors.bad, "empty is the first stop")
        assertSame(MetricColors.chargeAt(30), MetricColors.chargeScale[1].color, "30% is the second stop")
        assertSame(MetricColors.chargeAt(80), MetricColors.chargeScale[3].color, "80% is the last stop")
        assertSame(MetricColors.chargeAt(100), MetricColors.chargeScale[3].color, "100% stays on the last stop")

        // Halfway between the first two stops is a mix of them, not one of them.
        let middle = SRGBColor(color: MetricColors.chargeAt(15))
        let first = SRGBColor(hex: 0xFF5A5F)
        let second = SRGBColor(hex: 0xFF9F43)
        XCTAssertGreaterThan(middle.green, first.green + 0.05)
        XCTAssertLessThan(middle.green, second.green - 0.05)
    }

    // MARK: - Helpers

    /// Colour equality through the sRGB components, which is the representation the palette uses.
    private func assertSame(_ actual: Color, _ expected: Color, _ message: String,
                            file: StaticString = #filePath, line: UInt = #line) {
        let a = SRGBColor(color: actual)
        let b = SRGBColor(color: expected)
        let accuracy: CGFloat = 0.004
        XCTAssertEqual(a.red, b.red, accuracy: accuracy, message, file: file, line: line)
        XCTAssertEqual(a.green, b.green, accuracy: accuracy, message, file: file, line: line)
        XCTAssertEqual(a.blue, b.blue, accuracy: accuracy, message, file: file, line: line)
    }
}
