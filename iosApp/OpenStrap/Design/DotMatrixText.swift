import SwiftUI

/// A number drawn as round dots, like an LED matrix (the "3.3" on the reference cards).
///
/// Ported from `DotMatrixText` in `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowParts.kt`
/// (line 64): same 5×7 glyphs, same one-empty-column gap between glyphs, same dot/gap sizing.
/// `dot` is the dot diameter; the grid pitch is dot + `gap`. Digits, `.`, `,`, `:`, `-`, `k` and
/// `%` are supported; anything not listed draws as a space.
struct DotMatrixText: View {
    var text: String
    var dot: CGFloat = 5
    var gap: CGFloat = 2
    var color: Color = .white

    /// 5×7 glyphs, one string per row.
    static let glyphs: [Character: [String]] = [
        "0": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
        "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
        "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
        "3": ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
        "4": ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
        "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
        "6": ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
        "7": ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
        "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
        "9": [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
        ".": [".", ".", ".", ".", ".", ".", "#"],
        ",": [".", ".", ".", ".", ".", "#", "#"],
        ":": [".", ".", "#", ".", ".", "#", "."],
        "-": ["...", "...", "...", "###", "...", "...", "..."],
        "k": ["#....", "#....", "#..#.", "#.#..", "##...", "#.#..", "#..#."],
        "%": ["##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"],
        " ": ["..", "..", "..", "..", "..", "..", ".."],
    ]

    private static let rows = 7

    init(_ text: String, dot: CGFloat = 5, gap: CGFloat = 2, color: Color = .white) {
        self.text = text
        self.dot = dot
        self.gap = gap
        self.color = color
    }

    private var glyphs: [[String]] {
        text.map { DotMatrixText.glyphs[$0] ?? DotMatrixText.glyphs[" "]! }
    }

    /// One empty column between glyphs.
    private var columns: Int {
        glyphs.reduce(0) { $0 + $1[0].count } + max(glyphs.count - 1, 0)
    }

    private var pitch: CGFloat { dot + gap }

    private var size: CGSize {
        CGSize(
            width: max(0, pitch * CGFloat(columns) - gap),
            height: max(0, pitch * CGFloat(DotMatrixText.rows) - gap)
        )
    }

    var body: some View {
        Canvas { context, _ in
            var x0: CGFloat = 0
            for rows in glyphs {
                for (row, line) in rows.enumerated() {
                    for (column, character) in line.enumerated() where character == "#" {
                        let rect = CGRect(
                            x: x0 + CGFloat(column) * pitch,
                            y: CGFloat(row) * pitch,
                            width: dot,
                            height: dot
                        )
                        context.fill(Path(ellipseIn: rect), with: .color(color))
                    }
                }
                x0 += CGFloat(rows[0].count + 1) * pitch
            }
        }
        .frame(width: size.width, height: size.height)
        // Compose sets the same contentDescription.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}
