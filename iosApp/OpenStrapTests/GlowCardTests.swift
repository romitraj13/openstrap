import XCTest
import SwiftUI
import UIKit
import OpenStrapShared
@testable import OpenStrap

/// Card F6: the card's slab edge, and the two ways a card could draw the wrong colour.
///
/// The overview card's tone mapping (`MetricColors.overview`) and the palette that tone turns into
/// are pinned here, and so is the layer cache's idea of "the same look": a card whose colour changes
/// has to get new layers, or it keeps drawing the ones it rendered before its data arrived.
final class GlowCardTests: XCTestCase {
    // MARK: - The overview tone

    /// `overviewColor` (GlowTodayScreen.kt:356-363), case for case. The Kotlin enum's entry names
    /// are printed with the assertion that fails, so a log says what Swift actually received.
    func testEveryOverviewToneGetsTheAndroidColour() {
        let expected: [(tone: OverviewTone, hex: UInt32, android: String)] = [
            (.none, 0x6E7280, "Color(0xFF6E7280), no score"),
            (.strain, 0xF2A33A, "Color(0xFFF2A33A), signs of strain"),
            (.good, 0x2DD4BF, "MetricColors.hrv, score >= 70"),
            (.middling, 0xF2A33A, "Color(0xFFF2A33A), score >= 50"),
            (.low, 0xF0605A, "Color(0xFFF0605A), score < 50"),
        ]
        for (tone, hex, android) in expected {
            print("overview tone \(tone.name) (Kotlin entry) -> Android \(android)")
            assertSame(
                MetricColors.overview(tone), Color(hex: hex),
                "OverviewTone.\(tone.name) drew \(components(MetricColors.overview(tone))); "
                    + "Android's overviewColor gives \(android)"
            )
        }
    }

    /// A low leading score is coral and a missing score is neutral: the two palettes cannot be
    /// mistaken for each other, so a card drawn in the wrong one is visible in a screenshot.
    func testTheOverviewPaletteKeepsTheCoralHue() {
        let coral = GlowPalette(GlowStyle.bloom.tinted(MetricColors.overview(.low))).mid.rgb
        XCTAssertGreaterThan(coral.red, coral.green + 0.25, "the low tone's card is red, not grey")
        XCTAssertGreaterThan(coral.red, coral.blue + 0.25, "the low tone's card is red, not blue")

        let neutral = GlowPalette(GlowStyle.bloom.tinted(MetricColors.overview(.none))).mid.rgb
        XCTAssertLessThan(
            abs(neutral.red - neutral.blue), 0.06,
            "the no-score card is neutral grey: the luminance cap must not colour it"
        )
    }

    // MARK: - The layer cache

    /// Two tones at one size are two looks: different keys, and different rendered layers. Card F6's
    /// grey overview card was the pre-data look's layers, served to a card that had since turned
    /// coral, so this is the property that keeps it from happening again.
    func testTheLayerCacheKeepsASeparateLookForEachColour() {
        let grey = GlowStyle.bloom.tinted(MetricColors.overview(.none))
        let coral = GlowStyle.bloom.tinted(MetricColors.overview(.low))
        let greyKey = GlowLayerCache.Key(style: grey, width: 362, height: 337, scale: 3)
        let coralKey = GlowLayerCache.Key(style: coral, width: 362, height: 337, scale: 3)

        XCTAssertNotEqual(greyKey, coralKey, "one size, two colours: two cache entries")

        let greyLayers = GlowLayerCache.shared.layers(for: greyKey)
        let coralLayers = GlowLayerCache.shared.layers(for: coralKey)
        XCTAssertNotEqual(
            greyLayers.base.pngData(), coralLayers.base.pngData(),
            "the cache served one look's fill for the other colour"
        )
        XCTAssertNotEqual(
            greyLayers.core.pngData(), coralLayers.core.pngData(),
            "the cache served one look's core for the other colour"
        )
    }

    // MARK: - The slab

    /// `slab = lerp(palette.mid, Color.Black, 0.55f)` (GlowCard.kt:101): darker than the card's own
    /// body, with its hue kept, so the 6 pt band under the card reads as the slab's edge.
    func testTheSlabIsTheCardsColourDarker() {
        for tone in [OverviewTone.none, .strain, .good, .middling, .low] {
            var style = GlowStyle.bloom.tinted(MetricColors.overview(tone))
            style.depth = 6
            let palette = GlowPalette(style)
            XCTAssertLessThan(
                palette.slab.luminance, palette.mid.luminance,
                "\(tone.name): the slab is darker than the card's body"
            )
            XCTAssertGreaterThan(
                palette.slab.luminance, 0.001,
                "\(tone.name): the slab is a colour, not black"
            )
        }

        // The coral day's slab keeps the coral: darkened, not desaturated.
        let coral = GlowPalette(GlowStyle.bloom.tinted(MetricColors.overview(.low))).slab.rgb
        XCTAssertGreaterThan(coral.red, coral.green + 0.1, "the coral card's slab is still red")
        XCTAssertGreaterThan(coral.red, coral.blue + 0.1, "the coral card's slab is still red")
    }

    // MARK: - Helpers

    private func components(_ color: Color) -> String {
        let rgb = SRGBColor(color: color)
        return "(\(Int((rgb.red * 255).rounded())), \(Int((rgb.green * 255).rounded())), \(Int((rgb.blue * 255).rounded())))"
    }

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
