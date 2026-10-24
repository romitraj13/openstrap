import XCTest
import SwiftUI
import UIKit
@testable import OpenStrap

/// Card F8: the hero number keeps its last digit.
///
/// `GlowHeroNumber` puts `DotMatrixText` in an `HStack` beside a unit `Text` that is flexible
/// (`.frame(maxWidth: .infinity)`) and the facts column. Since F2 the dot matrix answers a
/// constrained parent with `min(proposal, ideal)` (`DotMatrixSize.sizeThatFits`), so the `HStack`
/// measured it as a child that can shrink and gave it a share of the row; the number then clipped
/// at that share (`.clipped()`, `DotMatrixText.swift:91`). The demo day's 11,602 steps read "116"
/// and one column of a "0" in `U8-activity-1.png`. Compose's `Row` measures its unweighted children
/// first, with the whole remaining width, and gives what is left to the `weight(1f)` unit text
/// (`GlowLayout.kt:184-191`); `.layoutPriority(1)` is that rule in SwiftUI.
///
/// The fault is in layout, so this test renders the real `GlowHeroNumber` and reads the pixels
/// back: the number's last dot column has an x of its own, nothing else can paint there, and a
/// number given less than its ideal width cannot reach it.
@MainActor
final class HeroNumberTests: XCTestCase {
    /// The value the Design lab shows, and the case the bug ate: five digits.
    private static let value = "12345"
    private static let facts = ["43% of 8,000", "Goal reached"]

    /// The dot columns `12345` needs: five glyphs of five dot columns and one empty column
    /// between each pair (`DotMatrixText.glyphs`).
    private static let columns = 5 * 5 + 4

    /// The width the number asks for: `pitch × columns − gap`, `DotMatrixText`'s own rule.
    private var idealWidth: CGFloat {
        let pitch = GlowLayout.heroDot + GlowLayout.heroGap
        return pitch * CGFloat(Self.columns) - GlowLayout.heroGap // 180.9 pt
    }

    /// The card content widths the app draws. 330 pt is what the CI simulator gives a card: a
    /// 402 pt screen, the 16 pt tight gutter and 20 pt of card padding each side, both read off
    /// `U8-activity-1.png` (the card's "Steps" header starts at x = 36 pt). 303 pt is a 375 pt
    /// phone's — 16 + 20 pt each side — and the narrowest the app draws.
    private static let cardWidths: [(name: String, width: CGFloat)] = [
        ("a 402 pt screen's card, 330 pt wide", 330),
        ("a 375 pt screen's card, 303 pt wide, the narrowest the app draws", 303),
    ]

    func testTheHeroNumberDrawsEveryDigitOfItsValue() throws {
        let ideal = idealWidth
        let pitch = GlowLayout.heroDot + GlowLayout.heroGap
        print("F8: \"\(Self.value)\" needs \(Self.columns) dot columns = \(ideal) pt "
              + "(dot \(GlowLayout.heroDot), gap \(GlowLayout.heroGap), pitch \(pitch))")

        for card in Self.cardWidths {
            let render = try renderHero(cardWidth: card.width)
            print("F8: \(card.name): rendered \(render.width)x\(render.height) px (1 pt per pixel)")

            // The number's own half of the row: the unit text starts `8 pt` after a number that
            // got its ideal width, so nothing at or left of this can be anything but the number
            // (or, when the number was squeezed, the unit text following it early).
            let numberHalf = ideal + 8
            let end = render.inkEnd(below: numberHalf)
            print("F8: \(card.name): ink ends at x=\(end) pt, the value needs \(ideal) pt")

            // The last dot column of the last glyph: "5" draws a full row of five dots at its top,
            // so that column is lit whenever the whole number is on screen.
            XCTAssertTrue(
                render.hasInk(
                    x: Int((ideal - 2).rounded())...Int((ideal + 1).rounded()),
                    below: numberHalf
                ),
                "\(card.name): the hero number ends at x=\(end) pt, so its last digit is cut off — "
                    + "\"\(Self.value)\" needs \(ideal) pt (\(Self.columns) dot columns at a "
                    + "\(pitch) pt pitch) and the row gave the number \(end) pt of it. Card F8: "
                    + "the HStack measured the dot matrix as a child that can shrink; Compose's "
                    + "Row gives its unweighted number the whole row first."
            )
        }
    }

    // MARK: - Rendering

    /// The real `GlowHeroNumber` at one card content width, white dots on black at 1 pt per pixel.
    private func renderHero(cardWidth: CGFloat) throws -> Render {
        let hero = GlowHeroNumber(Self.value, unit: "steps", facts: Self.facts)
            .frame(width: cardWidth, alignment: .topLeading)
            .background(Color.black)
        let renderer = ImageRenderer(content: hero)
        renderer.scale = 1
        guard let image = renderer.uiImage, let cgImage = image.cgImage else {
            throw Failure("card F8: ImageRenderer drew no image for a hero number")
        }
        let bytesPerPixel = cgImage.bitsPerPixel / 8
        guard cgImage.bitsPerComponent == 8, bytesPerPixel == 3 || bytesPerPixel == 4 else {
            throw Failure(
                "card F8: the rendered image is \(cgImage.bitsPerComponent) bits per component, "
                    + "\(cgImage.bitsPerPixel) bits per pixel: this test reads 8-bit RGB(A)"
            )
        }
        guard let provider = cgImage.dataProvider, let pixels = provider.data else {
            throw Failure("card F8: the rendered image carries no pixel data")
        }
        let data = pixels as Data
        // A 4-byte pixel carries its alpha (or the unused byte) first or last; the three colour
        // bytes are the other three, and a white dot is white in any of their orders. Getting this
        // wrong would read an opaque alpha byte as light in every column, so it is taken from the
        // image rather than assumed.
        let alphaFirst = [
            CGImageAlphaInfo.premultipliedFirst, .first, .noneSkipFirst,
        ].contains(cgImage.alphaInfo)
        return Render(
            data: data,
            width: cgImage.width,
            height: cgImage.height,
            bytesPerRow: cgImage.bytesPerRow,
            bytesPerPixel: bytesPerPixel,
            colourStart: (bytesPerPixel == 4 && alphaFirst) ? 1 : 0
        )
    }

    struct Failure: Error, CustomStringConvertible {
        let description: String
        init(_ description: String) { self.description = description }
    }

    /// The rendered pixels, and the one question this test asks of them.
    struct Render {
        let data: Data
        let width: Int
        let height: Int
        let bytesPerRow: Int
        let bytesPerPixel: Int
        let colourStart: Int

        /// A dot is white on a black card: any colour channel well above the background.
        func lit(x: Int, y: Int) -> Bool {
            let offset = y * bytesPerRow + x * bytesPerPixel + colourStart
            return data[offset] > 100 || data[offset + 1] > 100 || data[offset + 2] > 100
        }

        /// The first x, in points, past the last inked pixel left of `limit`.
        func inkEnd(below limit: CGFloat) -> Int {
            let last = min(width, Int(limit.rounded(.up)))
            for x in stride(from: last - 1, through: 0, by: -1) where (0..<height).contains(where: { lit(x: x, y: $0) }) {
                return x + 1
            }
            return 0
        }

        func hasInk(x range: ClosedRange<Int>, below limit: CGFloat) -> Bool {
            let last = min(width, Int(limit.rounded(.up)), range.upperBound + 1)
            for x in range.lowerBound..<last where (0..<height).contains(where: { lit(x: x, y: $0) }) {
                return true
            }
            return false
        }
    }
}
