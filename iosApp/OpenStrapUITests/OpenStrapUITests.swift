import UIKit
import XCTest

final class OpenStrapUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    /// The Pair screen is where a fresh install starts: no key in the Keychain.
    func testPairScreenAppears() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["openStrapTitle"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.secureTextFields["authKeyField"].exists)
    }

    /// Card R1: the name a person reads is OpenHealth, from the wordmark down to the You footer and
    /// the licence notice, and the icon behind the home screen's label is the new dot-matrix "OH.".
    ///
    /// The identifiers this card must *not* have changed are pinned by `RenameTests` in the unit
    /// test bundle; this walks the screens and attaches `R1-today-header`, `R1-you`, `R1-licences`
    /// and, when springboard answers, `R1-homescreen`.
    func testRename() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        // The wordmark, with the red full stop still after it. Waiting for Today's first panel as
        // well, so the shot is the header over a drawn screen rather than over an empty one: the
        // wordmark draws at once, the seeded day a moment later.
        let wordmark = app.staticTexts["app.wordmark"]
        XCTAssertTrue(wordmark.waitForExistence(timeout: 120), "Today never drew its header")
        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded sample data"
        )
        XCTAssertEqual(wordmark.label, "OpenHealth.", "the wordmark is not the new name plus its red full stop")
        // Card F9: the pinned demo day must read TODAY, in the header and in every R1 screenshot.
        XCTAssertTrue(
            app.staticTexts["TODAY"].waitForExistence(timeout: 30),
            "the date navigator does not read TODAY on the pinned demo day"
        )
        attach(app, "R1-today-header")

        // The You footer: "OpenHealth <version> · local-first, no cloud". Matched by prefix because
        // the version comes from the bundle.
        app.buttons["You"].tap()
        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "The You tab never drew")
        // Card F9: R1-you carries the Diagnostics row, whose count must read "1 line" for one line.
        let diagnosticsRow = app.buttons["you.diagnostics"]
        XCTAssertTrue(diagnosticsRow.waitForExistence(timeout: 30), "The You list has no Diagnostics row")
        if let problem = lineCountProblem(diagnosticsRow.label, where: "the You Diagnostics row") {
            XCTFail(problem)
        }
        // Scroll to the bottom, which is where the footer and the Licences row sit.
        if let problem = clearsNavBar(app, lastRow: "you.licences", screen: "You") { XCTFail(problem) }
        let footer = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "OpenHealth ")
        ).firstMatch
        XCTAssertTrue(footer.exists, "the You footer does not start with OpenHealth")
        attach(app, "R1-you")

        // The licence notice: upstream's row is first, above the fonts and icons, and it opens the
        // MIT text with the Apache text below it.
        tap(app, "you.licences")
        XCTAssertTrue(app.buttons["licences.back"].waitForExistence(timeout: 30), "The licence texts never opened")
        let upstream = app.buttons["licence.OpenStrap"]
        XCTAssertTrue(upstream.waitForExistence(timeout: 30), "the licence list has no OpenStrap row")
        attach(app, "R1-licences")

        upstream.tap()
        XCTAssertTrue(
            app.buttons["upstream.back"].waitForExistence(timeout: 30),
            "the OpenStrap row opened no licence text"
        )
        let mit = app.staticTexts["licence.OpenStrap-MIT"]
        XCTAssertTrue(mit.waitForExistence(timeout: 30), "there is no MIT text under the OpenStrap row")
        XCTAssertTrue(mit.label.contains("MIT License"), "the OpenStrap MIT panel does not carry the MIT licence")
        XCTAssertTrue(
            app.staticTexts["licence.OpenStrap-Apache-2.0"].exists,
            "the Apache text is not below the MIT text"
        )
        app.buttons["upstream.back"].tap()
        XCTAssertTrue(app.buttons["licences.back"].waitForExistence(timeout: 30), "Back from the licence texts never returned")

        // The home screen's label is springboard's to draw, so it is read from springboard. That is
        // a different process and may refuse to be driven; a refusal skips the shot and says so
        // rather than failing the rename the app itself got right.
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        if springboard.wait(for: .runningForeground, timeout: 20) {
            attach(springboard, "R1-homescreen")
        } else {
            print("R1-homescreen skipped: springboard never came to the foreground")
        }
    }

    /// Card U4: five tabs, the shared header, the calendar and every You screen. `-demoData`
    /// seeds the sample month, so each tab has something to draw without a strap.
    ///
    /// One screenshot per state the card touches, named `U4-<screen>-<n>` so `screens.py` picks
    /// them up.
    func testEveryTabAndYouScreen() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded sample data"
        )
        // Card F9: `-demoData` pins the shared clock to 2026-10-21 (AppClock.kt `DemoDay`), and the
        // header must call that day Today whatever date the runner's calendar is at. Every U5–U9
        // screenshot showed YESTERDAY here before the Swift side read the pinned clock.
        XCTAssertTrue(
            app.staticTexts["TODAY"].waitForExistence(timeout: 30),
            "the date navigator does not read TODAY on the pinned demo day"
        )
        XCTAssertFalse(
            app.staticTexts["YESTERDAY"].exists,
            "the date navigator calls the pinned demo day yesterday"
        )
        // The header the four date tabs share.
        XCTAssertTrue(app.staticTexts["app.wordmark"].exists, "The header never drew its wordmark")
        attach(app, "U4-today-1")

        // The five destinations, in the order MainActivity's Tab enum declares them.
        for label in ["Today", "Recovery", "Sleep", "Activity", "You"] {
            XCTAssertTrue(app.buttons[label].exists, "There is no \(label) tab")
        }

        app.buttons["Recovery"].tap()
        XCTAssertTrue(
            app.staticTexts["Recovery"].waitForExistence(timeout: 30),
            "The Recovery tab never drew its panel"
        )
        attach(app, "U4-recovery-1")

        app.buttons["Sleep"].tap()
        XCTAssertTrue(app.staticTexts["sleep.title"].waitForExistence(timeout: 60), "Sleep never drew the night's card")
        attach(app, "U4-sleep-1")

        app.buttons["Activity"].tap()
        XCTAssertTrue(
            app.staticTexts["Activity"].waitForExistence(timeout: 30),
            "The Activity tab never drew its panel"
        )
        attach(app, "U4-activity-1")

        // The date navigator's calendar sheet, from the shared header.
        XCTAssertTrue(app.buttons["date.pick"].waitForExistence(timeout: 30), "The header has no day picker")
        app.buttons["date.pick"].tap()
        if !app.staticTexts["Has data"].waitForExistence(timeout: 30) {
            attach(app, "U4-calendar-missing")
            XCTFail("The calendar sheet never opened")
        }
        attach(app, "U4-calendar-1")
        dismissSheet(app)
        XCTAssertTrue(
            app.staticTexts["Has data"].waitForNonExistence(timeout: 30),
            "The calendar sheet never closed, so the tab bar is covered"
        )

        // You, and everything it opens. Each step waits on an element only that screen has: the
        // hub's own rows carry the same words.
        app.buttons["You"].tap()
        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "The You tab never drew")
        // Card F9: the row carries the log's length, and one line reads "1 line" (the third allowed
        // exception to standing instruction 4; Android prints "1 lines").
        let diagnosticsRow = app.buttons["you.diagnostics"]
        XCTAssertTrue(diagnosticsRow.waitForExistence(timeout: 30), "The You list has no Diagnostics row")
        if let problem = lineCountProblem(diagnosticsRow.label, where: "the You Diagnostics row") {
            XCTFail(problem)
        }
        // Card F4: every screen that scrolls under the floating bar must let its last row come clear
        // of it. Each screen's numbers are measured as the walk goes, and asserted together at the end
        // so one run names every screen whose last row is still under the bar.
        var navBarFailures: [String] = []
        // Card F4: the hub's last row is the Licences row; scrolled to the end it must sit above
        // the floating bar, and the screenshot is taken there.
        if let problem = clearsNavBar(app, lastRow: "you.licences", screen: "You") { navBarFailures.append(problem) }
        attach(app, "U4-you-1")
        scrollToTop(app, until: app.buttons["you.profile"])

        tap(app, "you.profile")
        XCTAssertTrue(app.buttons["profile.back"].waitForExistence(timeout: 30), "Your profile never opened")
        // The panel's last field is the one to reach at the end; Save is in the header above it.
        if let problem = clearsNavBar(app, lastRow: "profile.sleepNeed", screen: "Your profile") { navBarFailures.append(problem) }
        attach(app, "U4-profile-1")
        scrollToTop(app, until: app.buttons["profile.back"])
        app.buttons["profile.back"].tap()
        XCTAssertTrue(app.buttons["you.profile"].waitForExistence(timeout: 30), "Back from Your profile never returned to You")

        tap(app, "you.goals")
        if !app.buttons["goals.cancel"].waitForExistence(timeout: 30) {
            // A screenshot and the hierarchy, so a failure here says what it saw.
            attach(app, "U4-goalssheet-missing")
            print("U4 after you.goals:\n\(app.debugDescription)")
            XCTFail("The goals sheet never opened")
        }
        attach(app, "U4-goals-1")
        app.buttons["goals.cancel"].tap()
        XCTAssertTrue(app.buttons["you.goals"].waitForExistence(timeout: 30), "Cancel never closed the goals sheet")

        tap(app, "you.strap")
        XCTAssertTrue(app.buttons["strap.back"].waitForExistence(timeout: 30), "Your strap never opened")
        // The Diagnostics row is the last thing on the screen, and the row U4's screenshot showed
        // under the bar.
        if let problem = clearsNavBar(app, lastRow: "diagnosticsLink", screen: "Your strap") { navBarFailures.append(problem) }
        attach(app, "U4-strap-1")
        scrollToTop(app, until: app.buttons["strap.back"])
        app.buttons["strap.back"].tap()
        XCTAssertTrue(app.buttons["you.strap"].waitForExistence(timeout: 30), "Back from Your strap never returned to You")

        tap(app, "you.diagnostics")
        XCTAssertTrue(app.navigationBars["Diagnostics"].waitForExistence(timeout: 30), "Diagnostics never opened")
        // Card F9: the log's own heading carries the count, and one line reads "1 line".
        let logCount = app.staticTexts["log.lineCount"]
        XCTAssertTrue(logCount.waitForExistence(timeout: 30), "The Diagnostics screen never drew its log's line count")
        if let problem = lineCountProblem(logCount.label, where: "the Diagnostics log heading") {
            XCTFail(problem)
        }
        // The log is the last thing here, and it can be thousands of points long.
        if let problem = clearsNavBar(app, lastRow: "logText", screen: "Diagnostics") { navBarFailures.append(problem) }
        attach(app, "U4-diagnostics-1")
        app.navigationBars["Diagnostics"].buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["you.diagnostics"].waitForExistence(timeout: 30), "Back from Diagnostics never returned to You")

        tap(app, "you.licences")
        XCTAssertTrue(app.buttons["licences.back"].waitForExistence(timeout: 30), "The licence texts never opened")
        // The last licence text is the last row of the screen.
        if let problem = clearsNavBar(app, lastRow: "licence.OFL-MuseoModerno", screen: "Licences") { navBarFailures.append(problem) }
        attach(app, "U4-licences-1")
        scrollToTop(app, until: app.buttons["licences.back"])
        app.buttons["licences.back"].tap()

        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "Back from Licences never returned to You")

        XCTAssertTrue(
            navBarFailures.isEmpty,
            "the floating tab bar still covers the last row of:\n" + navBarFailures.joined(separator: "\n")
        )
    }

    /// Card F10: F4's assertion, for one of the four dated tabs. Today, Recovery, Sleep and
    /// Activity scroll their content under the floating bar (`RootView`'s `DayTab`), so once the
    /// list ends its last text must come fully clear of the bar, the same bound F4 measures for the
    /// You screens and F9's workout screens.
    ///
    /// Unlike F4's screens, a tab has no single row to name as its last: the panel at the bottom is
    /// whichever one this day's data draws last. So the walk measures the lowest text on the screen
    /// instead, which at the end of the scroll is the bottom of what the tab is drawing. The drag
    /// count and the travel are printed with it, so the line says how far the walk had to go.
    ///
    /// Everything here is a hard assertion: a tab that cannot be walked to its end is a broken test,
    /// not a defect, and must fail. The clearance verdict is the caller's, so that one run names every
    /// tab that is wrong instead of stopping at the first.
    private func measureTabEnd(_ app: XCUIApplication, screen: String) -> TabEnd? {
        let bar = app.descendants(matching: .any)["navBar.top"]
        guard bar.waitForExistence(timeout: 30) else {
            print("F4 \(screen): there is no navBar.top. Tree:\n\(app.debugDescription)")
            XCTFail("\(screen): the floating bar never reported its top edge")
            return nil
        }
        let barTop = bar.frame.minY
        let wordmark = app.staticTexts["app.wordmark"]
        guard wordmark.waitForExistence(timeout: 30) else {
            XCTFail("\(screen): the dated tab has no wordmark to walk by")
            return nil
        }
        let walk = scrollToEndOfTab(app, wordmark: wordmark)
        // The end is only the end once the content is inside the window and holds still: a list whose
        // panels are still arriving ends the scroll early (run 39873446048 walked Recovery to the end
        // of a half-drawn screen, 454 pt of travel, and measured its last text 1705 pt down a 874 pt
        // window), and a card that lands afterwards lengthens the list under the measurement. Each
        // round walks again and measures again.
        let windowBottom = app.windows.firstMatch.frame.maxY
        var drawn = lowestTexts(app, above: barTop)
        for _ in 0..<3 {
            let bottom = drawn.first?.frame.maxY ?? 0
            Thread.sleep(forTimeInterval: 2.5)
            let after = lowestTexts(app, above: barTop)
            let settled = abs((after.first?.frame.maxY ?? 0) - bottom) < 1
            if settled && bottom <= windowBottom { break }
            print(
                "F10 \(screen): not the end yet (last text \(bottom), window bottom \(windowBottom), "
                    + "settled \(settled)); walking again"
            )
            _ = scrollToEndOfTab(app, wordmark: wordmark)
            drawn = lowestTexts(app, above: barTop)
        }
        for one in drawn.prefix(6) {
            print("F10 \(screen) lowest: \(one.label.prefix(44)) minY=\(one.frame.minY) maxY=\(one.frame.maxY)")
        }
        attach(app, "F10-\(screen)-end")
        let bottom = drawn.first ?? (label: "(no text)", frame: .zero)
        let window = app.windows.firstMatch.frame
        print(
            "F4 \(screen): \(bottom.label) maxY=\(bottom.frame.maxY) barTop=\(barTop) "
                + "window=\(window) drags=\(walk.drags) scrolled=\(walk.moved)"
        )
        // A walk that never moved the list measured the top of the screen, or a tab the tap did not
        // switch to: that is the test's own fault and must never be read as a clearance result.
        XCTAssertGreaterThan(
            walk.moved, 200,
            "\(screen): the walk never scrolled the tab — the wordmark moved \(walk.moved) pt in "
                + "\(walk.drags) drags, so the lowest text above is not the end of this screen"
        )
        return TabEnd(
            screen: screen,
            barTop: barTop,
            label: bottom.label,
            maxY: bottom.frame.maxY,
            drags: walk.drags,
            scrolled: walk.moved
        )
    }

    /// What one dated tab's walk measured.
    private struct TabEnd {
        var screen: String
        var barTop: CGFloat
        var label: String
        var maxY: CGFloat
        var drags: Int
        var scrolled: CGFloat
    }

    /// Card F10: F4's test, in a sentence — or nil when the tab's last text clears the bar.
    private func clearanceProblem(_ end: TabEnd) -> String? {
        guard end.maxY <= end.barTop else {
            return "\(end.screen): the screen's last text, \"\(end.label)\", ends at \(end.maxY), under "
                + "the floating bar whose top is \(end.barTop), so it cannot be scrolled clear of it"
        }
        return nil
    }

    /// The floating bar's own labels, which sit in the glass below `navBar.top`. They are the only
    /// text at the bottom of the window that is not the tab's content.
    private static let tabBarLabels: Set<String> = ["Today", "Recovery", "Sleep", "Activity", "You"]

    /// Card F10: every text a dated tab is drawing, lowest first.
    ///
    /// This is only meaningful once the walk has reached the end: a SwiftUI stack's off-screen
    /// children report frames clamped into the viewport (card F2's note), so before then the lowest
    /// frame in the tree can be a row the window never showed. At the end, the tree holds nothing
    /// below the fold but the bar. The bar's own labels are dropped by name — dropping everything
    /// below `barTop` instead would also drop a row hidden entirely under the glass, which is the
    /// defect this looks for.
    private func lowestTexts(_ app: XCUIApplication, above barTop: CGFloat) -> [(label: String, frame: CGRect)] {
        let drawn = app.staticTexts.allElementsBoundByIndex
            .map { (label: $0.label, frame: $0.frame) }
            .filter { $0.frame.height > 0 }
            .filter { !(Self.tabBarLabels.contains($0.label) && $0.frame.minY >= barTop) }
        return drawn.sorted { $0.frame.maxY > $1.frame.maxY }
    }

    /// Card F10: drag a dated tab to its end, watching the header's wordmark.
    ///
    /// The wordmark is the top of the scrolled content: it starts on screen and its y goes negative
    /// as the list moves, so it is the one element that can be followed the whole way down. A row
    /// near the bottom cannot be followed (card F2's note: a SwiftUI stack's off-screen children
    /// report frames clamped into the viewport, and a clamped frame never moves with the scroll —
    /// the first draft of this walk stopped after four drags on Today for that reason), and a
    /// capture cannot either while a card still animates (the draft before this one ran its whole
    /// drag budget on captures that never settled).
    ///
    /// Rounds of `walkDown`, with a pause between them, until two rounds end at the same place: a
    /// card whose demo rows are analysed after the first paint takes its place below the fold, which
    /// lengthens the list without moving the wordmark until the next round takes up the room it
    /// added. The round that finds no new room costs two drags. Without this Today was read 140 pt
    /// short of its end on some runs and at its end on others (runs 39877626154 and 39882557206).
    private func scrollToEndOfTab(_ app: XCUIApplication, wordmark: XCUIElement) -> (drags: Int, moved: CGFloat) {
        let start = wordmark.frame.minY
        var drags = 0
        var lastEnd = CGFloat.greatestFiniteMagnitude
        // Three rounds at most. A round that ends where the previous round ended is the end: the list
        // has not grown since that round. A round that ends higher has taken up room a card added
        // after the last one (Today's workouts card is the one that lands late), and needs another.
        for _ in 0..<3 {
            drags += walkDown(app, wordmark: wordmark)
            let end = wordmark.frame.minY
            if abs(end - lastEnd) < 1 {
                return (drags, start - end)
            }
            lastEnd = end
            Thread.sleep(forTimeInterval: 3)
        }
        return (drags, start - wordmark.frame.minY)
    }

    /// One pass to the bottom: drag until two drags in a row leave the wordmark where it is.
    private func walkDown(_ app: XCUIApplication, wordmark: XCUIElement) -> Int {
        var previous = wordmark.frame.minY
        var still = 0
        var drags = 0
        // A held drag moves about half a window; the tallest dated tab is six screenfuls (card U7),
        // so sixteen is past the end of any of them.
        for _ in 0..<16 {
            drag(app, from: 0.78, to: 0.22)
            drags += 1
            Thread.sleep(forTimeInterval: 0.4)
            let top = wordmark.frame.minY
            still = top < previous - 1 ? 0 : still + 1
            previous = top
            if still == 2 { return drags }
        }
        return drags
    }

    /// Card F4: a screen that scrolls under the floating bar must let its last row come fully clear
    /// of it. `navBar.top` is the bar's own top edge (`FloatingNavBar`), so the bound is the bar's
    /// real frame and not a number copied into the test. Each screen's numbers are printed as they
    /// are measured and returned as a sentence when the row is still under the bar, so one run
    /// names every screen that is wrong instead of stopping at the first (the walk asserts them
    /// together at the end).
    private func clearsNavBar(_ app: XCUIApplication, lastRow identifier: String, screen: String) -> String? {
        let bar = app.descendants(matching: .any)["navBar.top"]
        guard bar.waitForExistence(timeout: 30) else {
            print("F4 \(screen): there is no navBar.top. Tree:\n\(app.debugDescription)")
            return "\(screen): the floating bar never reported its top edge"
        }
        let row = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        guard row.waitForExistence(timeout: 30) else {
            return "\(screen): \(identifier) never appeared"
        }
        scrollToEnd(app, until: row)
        let barTop = bar.frame.minY
        // An identifier set on a container is repeated by SwiftUI on every text inside it
        // (`LicencesView`'s DotPanel does this, so `licence.OFL-MuseoModerno` matches the label and
        // the body). The row's bottom edge is what must clear the bar, so measure the lowest match.
        let lowest = app.descendants(matching: .any).matching(identifier: identifier)
            .allElementsBoundByIndex.max { $0.frame.maxY < $1.frame.maxY }
        let rowBottom = (lowest ?? row).frame.maxY
        print("F4 \(screen): \(identifier) maxY=\(rowBottom) barTop=\(barTop) window=\(app.windows.firstMatch.frame)")
        guard rowBottom <= barTop else {
            return "\(screen): \(identifier) ends at \(rowBottom), under the floating bar whose top is "
                + "\(barTop), so the last row cannot be scrolled clear of it"
        }
        return nil
    }

    /// Card F9's rule for a log's length: one line reads "1 line", any other count "N lines" — the
    /// only wording iOS does not copy from Android, whose `"${lines.size} lines"` prints "1 lines"
    /// (`DeviceScreen.kt:453`, :456). Returns what is wrong with `text`, or nil.
    private func lineCountProblem(_ text: String, where place: String) -> String? {
        guard let range = text.range(of: #"[0-9]+ lines?"#, options: .regularExpression) else {
            return "\(place): \"\(text)\" carries no line count"
        }
        let words = String(text[range])
        let first = words.split(separator: " ").first.map { String($0) } ?? ""
        let count = Int(first) ?? -1
        let expected = count == 1 ? "1 line" : "\(count) lines"
        guard words == expected else {
            return "\(place): says \"\(words)\" for \(count) line(s), not \"\(expected)\""
        }
        return nil
    }

    /// Drags to the end of a scrolling screen: a held drag moves about half a screen, so the walk
    /// repeats until the row being watched stops moving up (twice, so a single stale frame cannot
    /// end the walk early). A row thousands of points down the list reports a frame that does not
    /// move with the scroll (card F2's note), so only a row the window shows is trusted to say the
    /// list has reached its end.
    private func scrollToEnd(_ app: XCUIApplication, until row: XCUIElement) {
        let window = app.windows.firstMatch.frame
        var previous = row.frame.maxY
        var still = 0
        for _ in 0..<80 {
            drag(app, from: 0.78, to: 0.22)
            Thread.sleep(forTimeInterval: 0.25)
            let frame = row.frame
            let onScreen = frame.minY < window.maxY && frame.maxY > window.minY
            still = onScreen && frame.maxY >= previous - 1 ? still + 1 : 0
            previous = frame.maxY
            if still == 2 { return }
        }
    }

    /// Back to the top of a screen whose header scrolls with it (Your profile, Licences): the back
    /// button is only reachable once the list is back at its start.
    private func scrollToTop(_ app: XCUIApplication, until element: XCUIElement) {
        var drags = 0
        while !element.isHittable && drags < 60 {
            drag(app, from: 0.28, to: 0.78)
            Thread.sleep(forTimeInterval: 0.15)
            drags += 1
        }
        XCTAssertTrue(element.isHittable, "the screen never scrolled back to its top")
    }

    /// One held drag, from `from` to `to` of the window's height. It starts above the floating bar
    /// (its top edge is about 0.85 of the window) so the bar never takes the gesture, and holds at
    /// the end so momentum does not carry the scroll further than the drag.
    private func drag(_ app: XCUIApplication, from: CGFloat, to: CGFloat) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: from))
            .press(
                forDuration: 0.05,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: to)),
                withVelocity: .fast,
                thenHoldForDuration: 0.05
            )
    }

    /// Taps a row once it can actually take the tap. A popped `NavigationStack` keeps the old
    /// screen's frame over the list for a moment, and a tap sent then lands on the screen leaving
    /// (run 39619975079: no goals sheet, because the tap went to Your profile's fading view). Rows
    /// below the fold are scrolled up first.
    private func tap(_ app: XCUIApplication, _ identifier: String) {
        let row = app.buttons[identifier]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "\(identifier) never appeared")
        let deadline = Date().addingTimeInterval(20)
        var swipes = 0
        while !row.isHittable && Date() < deadline {
            // A row past the bottom of the list needs the list moved, not more waiting.
            if swipes < 4 && row.frame.minY > app.windows.firstMatch.frame.maxY - 120 {
                app.swipeUp()
                swipes += 1
            }
            Thread.sleep(forTimeInterval: 0.3)
        }
        XCTAssertTrue(row.isHittable, "\(identifier) never became tappable")
        row.tap()
    }

    /// Open a metric's detail from a What changed tile.
    ///
    /// The tile is on screen at the top of Today, so its frame is real — which is the whole point.
    /// Card U5's five red runs were one lesson: a SwiftUI stack's off-screen children report frames
    /// clamped into the viewport (card F2's note in this file), so "tap Heart rate" tapped the
    /// overview card's Sleep row every time, whatever the test did about scrolling. The tile is the
    /// same `TodayRoute.metric` push the heart card makes.
    ///
    /// Card U9 replaced the placeholder this used to land on, so the proof that the detail opened is
    /// its range switch — every metric detail has one — and its title.
    private func openTileDetail(_ app: XCUIApplication, _ label: String, opens title: String) {
        let tile = app.staticTexts[label]
        XCTAssertTrue(tile.waitForExistence(timeout: 30), "the \(label) tile never appeared")
        tile.tap()
        let daySegment = app.buttons["detail.range.Day"]
        if !daySegment.waitForExistence(timeout: 30) {
            attach(app, "U5-detail-missing")
            print("U5 after tapping \(label):\n\(app.debugDescription)")
            XCTFail("Tapping the \(label) tile pushed no detail")
            return
        }
        XCTAssertTrue(
            app.staticTexts[title].exists,
            "the detail with the range switch is not the \(label) tile's metric (\(title))"
        )
    }

    /// Card F10: every dated tab — Today, Recovery, Sleep and Activity — lets the last thing it
    /// draws come clear of the floating bar once its scroll has reached the end, as card F4's You
    /// screens and card F9's workout screens already do.
    ///
    /// The committed `U6-recovery-7` and `U7-sleep-6` screenshots show content under the bar. That
    /// is either a screenful short of the end or a real defect, and only a walk to the end can say
    /// which: this one drags until the header's wordmark holds still, then measures the lowest text
    /// on the screen against `navBar.top`, the bar's own top edge. At its end every tab clears by
    /// 52–64 pt on this device, so those screenshots were a screenful short.
    ///
    /// Card F11 made Today the fourth tab that passes: the workouts card, drawn by a second read,
    /// landed after Today's list had laid out, and the scroll view clamped its gestures to the height
    /// it had laid the content out at, so the last card sat under the bar on nine runs in ten.
    /// `TodayScreen` now draws its list once every read that feeds it has answered, so the list's first
    /// layout is its final shape; the walk launches with `-lateWorkouts` so it still measures the card
    /// arriving late instead of waiting for luck.
    ///
    /// `-glowStill` holds the cards' cores still — the walk asks for the screen's whole text tree
    /// twice per tab, and a card that animates for ever makes such a query wait (card F5's lesson).
    func testEveryDatedTabScrollsClearOfTheBar() {
        // F4's walk asserts each screen as it goes and names every screen that is wrong at the end,
        // so this test keeps going after a failure too — without it the first wrong tab would stop
        // the test before the other three were measured (run 39901865515 passed after one tab).
        let previousContinue = continueAfterFailure
        continueAfterFailure = true
        defer { continueAfterFailure = previousContinue }

        let app = XCUIApplication()
        // Card F11: `-lateWorkouts 20` holds the workouts card back for twenty seconds, so it cannot be
        // in Today's list when the list lays out for the first time — that is the condition the card is
        // about, and it used to decide whether Today's last card could be scrolled clear of the bar (nine
        // runs in ten it could not). Twenty seconds is well past the day's own read and the first paint,
        // so this is the late card on every run, not a race that sometimes goes the other way.
        app.launchArguments = ["-demoData", "-glowStill", "-lateWorkouts", "20"]
        app.launch()

        var failures: [String] = []
        // The four dated tabs. Each row names the tab's own screen mark — without it a tap that did
        // not switch tabs leaves the previous screen's tree to be measured (run 39894321302 did
        // exactly that with Activity's "Workouts" standing in for Today's) — and the panel at the
        // bottom of its list, which is what the walk is about to measure. Waiting for that panel is
        // what makes the end of the scroll a fixed place: the demo's workout analysis lands well
        // after the first paint (it is still missing 54 s into run 39886609260's `testActivity`).
        //
        // Today is first, at launch. Switching *to* Today rebuilds the app's longest list and its
        // first query has timed out twice (runs 39897512933 and 39894321302 measured Activity's tree
        // instead), while the launch screen's own first query is the one every other test here
        // already waits on.
        let tabs: [(name: String, mark: String, bottom: String)] = [
            ("Today", "What changed", "Workouts"),
            ("Recovery", "recovery.title", "How it works"),
            ("Sleep", "sleep.title", "This week"),
            ("Activity", "Steps", "Workouts"),
        ]
        for (index, tab) in tabs.enumerated() {
            // Today is the launch screen: tapping its own cell would rebuild the list, and re-run the
            // queries behind its cards, for nothing.
            if index > 0 {
                app.buttons[tab.name].tap()
                // Card F5: the first query after a tab switch waits for the cards that render behind
                // their lock, and a 30 s snapshot timeout is a failed run. The steps card never
                // settles (run 39737184826).
                if tab.name == "Activity" {
                    Thread.sleep(forTimeInterval: 5)
                }
            }
            XCTAssertTrue(
                app.staticTexts[tab.mark].firstMatch.waitForExistence(timeout: 120),
                "The tab bar's \(tab.name) did not open its screen (no \(tab.mark))"
            )
            XCTAssertTrue(
                app.staticTexts[tab.bottom].firstMatch.waitForExistence(timeout: 120),
                "The \(tab.name) tab never drew the bottom of its list (\(tab.bottom))"
            )
            // Give the tab's own data a moment to land before the walk starts asking for frames:
            // `RecoveryScreen` and the others read their panels on appear, and a walk that starts
            // too early ends on a half-drawn screen (run 39873446048). Card F5's rule for the first
            // query after a tab switch, with room for the queries behind the panels.
            Thread.sleep(forTimeInterval: 6)
            // The walk and its hard checks: a tab that cannot be walked to its end is a broken test,
            // and one whose last text stays under the bar is a defect. Both fail the run; the walk
            // asserts as it goes and every tab is measured before the run is judged.
            let end = measureTabEnd(app, screen: tab.name)
            if let end, let problem = clearanceProblem(end) {
                failures.append(problem)
            }
        }

        XCTAssertTrue(
            failures.isEmpty,
            "the floating tab bar still covers the end of:\n" + failures.joined(separator: "\n")
        )
    }

    /// Card U6: the Recovery tab, every section in Compose's order, the folded panels opened, and the
    /// learning state from the first days of the seeded month (too few nights behind them to score).
    /// Screenshots: `U6-recovery-1..n` from the top down, then `U6-recoverylearning-1..n`.
    func testRecovery() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Recovery"].tap()
        XCTAssertTrue(
            app.staticTexts["recovery.title"].firstMatch.waitForExistence(timeout: 60),
            "The Recovery tab never drew its score card"
        )

        // Every section `GlowRecoveryScreen.kt:76-95` draws, in its order. A section that never
        // rendered at all fails here rather than quietly missing from the screenshots.
        let sections = ["Recovery", "What drove it", "Charge", "Where your charge went", "Body signals", "How it works"]
        let missing = sections.filter { !app.staticTexts[$0].exists }
        XCTAssertTrue(missing.isEmpty, "Recovery never drew: \(missing)")
        walk(app, prefix: "U6-recovery", milestones: sections, screenfuls: 5)

        // "How it works" is folded away at the bottom of the screen: scroll until it can take a tap,
        // then open it for Compose's two paragraphs.
        let howItWorks = app.staticTexts["How it works"]
        for _ in 0..<6 where !howItWorks.isHittable {
            app.swipeUp()
            Thread.sleep(forTimeInterval: 0.4)
        }
        XCTAssertTrue(howItWorks.isHittable, "How it works never came into reach")
        howItWorks.tap()
        let chargeParagraph = "Charge is a running battery: sleep and calm rest fill it, movement, stress "
            + "and simply being awake drain it."
        XCTAssertTrue(
            app.staticTexts[chargeParagraph].firstMatch.waitForExistence(timeout: 30),
            "Tapping How it works opened nothing"
        )
        attach(app, "U6-recovery-6")

        // Back at the top, the contributors unfold from "Top 3 of N" to the whole list.
        scrollToTop(app)
        app.staticTexts["What drove it"].tap()
        Thread.sleep(forTimeInterval: 0.5)
        attach(app, "U6-recovery-7")

        // The learning state: the first days of the demo seed have fewer than the seven nights a
        // score needs, so the card says what it is waiting for (GlowRecoveryScreen.kt:135-137).
        //
        // Three runs (39696188876, 39699291782, 39702319788) died here asking XCUITest to read a
        // list of elements step by step: a query for an element that is not there raises "Failed to
        // get matching snapshot" rather than answering empty, and enumerating `app.staticTexts`
        // while a day's card re-renders can go stale mid-list. So the walk is a fixed count and one
        // wait: the demo seed is three weeks, and `RecoveryQueriesTest` pins that its learning
        // state covers the week at its start, days 14-20 back; 17 taps lands inside that window
        // even if two taps are swallowed by a re-render.
        scrollToTop(app)
        let previous = app.buttons["date.previous"]
        XCTAssertTrue(previous.waitForExistence(timeout: 30), "The header has no previous-day button")
        for _ in 0..<17 {
            previous.tap()
            Thread.sleep(forTimeInterval: 0.5)
        }
        XCTAssertTrue(
            app.staticTexts["recovery.learning"].firstMatch.waitForExistence(timeout: 60),
            "Seventeen days back is not drawing the learning state's sentence"
        )
        walk(app, prefix: "U6-recoverylearning", milestones: ["Recovery", "Charge", "Body signals"], screenfuls: 3)
    }

    /// Card U7: the Sleep tab, every section in Compose's order, and a night the strap never recorded.
    /// Screenshots: `U7-sleep-1..n` from the top down, then `U7-sleepempty-1..n`.
    ///
    /// The demo data writes a scored night (`DemoData.kt` seeds `score = 72 + …`), so the hero here
    /// is the scored state and "What shaped the score" is reachable. It still writes no naps, so
    /// "Naps" is the one section `-demoData` cannot photograph.
    func testSleep() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Sleep"].tap()
        XCTAssertTrue(
            app.staticTexts["sleep.title"].firstMatch.waitForExistence(timeout: 60),
            "The Sleep tab never drew the night's card"
        )
        // A scored night: the arc carries the score (GlowSleepScreen.kt:141-150).
        XCTAssertTrue(
            app.staticTexts["sleep.score"].exists,
            "The seeded night is not drawing the scored label"
        )

        // Every section `GlowSleepScreen.kt:81-101` draws for this data, in its order. A section that
        // never rendered at all fails here rather than quietly missing from the screenshots.
        let sections = ["Sleep", "Stages", "What shaped the score", "Sleep vitals", "Heart rate", "This week", "Last 7 nights"]
        let missing = sections.filter { !app.staticTexts[$0].exists }
        XCTAssertTrue(missing.isEmpty, "Sleep never drew: \(missing)")
        walk(app, prefix: "U7-sleep", milestones: sections, screenfuls: 6)

        // A night the strap never recorded: far enough back that the seeded month has run out
        // (GlowSleepScreen.kt:128-135).
        scrollToTop(app)
        let previous = app.buttons["date.previous"]
        XCTAssertTrue(previous.waitForExistence(timeout: 30), "The header has no previous-day button")
        for _ in 0..<40 {
            previous.tap()
        }
        XCTAssertTrue(
            app.staticTexts["sleep.empty"].waitForExistence(timeout: 60),
            "A day before the seeded month is not drawing the no-sleep sentence"
        )
        walk(app, prefix: "U7-sleepempty", milestones: [], screenfuls: 2)
    }

    /// Card U8: the Activity tab, every panel in `GlowActivityScreen.kt:87-97`'s order.
    /// Screenshots: `U8-activity-1..n`.
    ///
    /// The demo data writes PAI, workouts and their sport effects (`DemoData.kt`, Android's emulator
    /// seed), so the PAI panel shows its total and the workouts panel lists today's run. It still
    /// writes no profile, so the energy row that asks for the profile and the fitness estimate's
    /// withheld note are the missing-data states shown here. `testWorkout` opens the run and the
    /// all-workouts list.
    func testActivity() {
        let app = XCUIApplication()
        // `-glowStill` holds the cards' cores still: the steps card's stride never settles, and
        // XCUITest waits for a quiescent app before every query (card F5's `-designLabStill`, and
        // run 39737184826's 30 s query timeout on this test).
        app.launchArguments = ["-demoData", "-glowStill"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Activity"].tap()

        // The steps card glows on the day's stride and never settles, and XCUITest waits for a
        // quiescent app before every query: card F5 lost runs 39587579214 and 39594198894 to a 30 s
        // query timeout on a screen that animated for ever, and this test timed out the same way in
        // run 39737184826 after passing in 39732609901 on an identical tree. A glow card renders its
        // blurred layers on first appearance, one at a time and behind a lock, so the first query
        // after the tab switch is the one that waits. Pause for that render, then ask one query per
        // group of strings instead of one per string.
        Thread.sleep(forTimeInterval: 5)

        XCTAssertTrue(
            app.staticTexts["Steps"].waitForExistence(timeout: 60),
            "The Activity tab never drew its steps card"
        )

        // Every section `GlowActivityScreen.kt:87-97` draws, in its order, in a single query.
        let sections = ["Steps", "Active minutes", "Energy", "PAI", "Training load", "Fitness", "Workouts"]
        let drawn = Set(
            app.staticTexts
                .matching(NSPredicate(format: "label IN %@", sections))
                .allElementsBoundByIndex
                .map(\.label)
        )
        let missing = sections.filter { !drawn.contains($0) }
        XCTAssertTrue(missing.isEmpty, "Activity never drew: \(missing)")

        // The state each panel is in for this data: PAI's note, the profile row the energy panel
        // asks for, the workouts panel's empty note and the load panel's trend line.
        let notes = [
            "Add age, weight and height to estimate the energy you use.",
            "Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue.",
        ]
        let written = Set(
            app.staticTexts
                .matching(NSPredicate(format: "label IN %@", notes))
                .allElementsBoundByIndex
                .map(\.label)
        )
        for note in notes {
            XCTAssertTrue(written.contains(note), "a panel is not drawing its own note: \(note)")
        }

        // The demo data has PAI and a workout, so those panels show data, not their empty notes.
        XCTAssertTrue(
            app.staticTexts["Outdoor run"].exists,
            "The Workouts panel is not listing the seeded run"
        )

        // The two sentences that carry a figure, matched by their opening words.
        let prefixOne = "Estimated fitness: "
        let prefixTwo = "Each minute counts by how hard your heart works between resting "
        let sentences = app.staticTexts
            .matching(NSPredicate(format: "label BEGINSWITH %@ OR label BEGINSWITH %@", prefixOne, prefixTwo))
            .allElementsBoundByIndex
            .map(\.label)
        for prefix in [prefixOne, prefixTwo] {
            XCTAssertTrue(
                sentences.contains { $0.hasPrefix(prefix) },
                "no sentence starts with \(prefix), so that panel's own line is missing"
            )
        }

        walk(app, prefix: "U8-activity", milestones: [], screenfuls: 6)
    }

    /// Card U8: the workout detail and the all-workouts list, reached from the Activity tab's
    /// Workouts panel. Screenshots: `U8-workout-1..n` (today's run, pushed) and `U8-workouts-1..n`
    /// (the list). The demo data seeds one run today and ten on other evenings (`DemoData.kt`), so
    /// both screens have a workout to draw.
    ///
    /// The recovery-heart-rate panel needs the three-minute tail the strap records after a workout,
    /// which the seed's `WorkoutHeartRate` stream stops short of, so it is not asserted here — the
    /// heart chart, zones and training effect are.
    func testWorkout() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-glowStill"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Activity"].tap()
        XCTAssertTrue(
            app.staticTexts["Steps"].waitForExistence(timeout: 60),
            "The Activity tab never drew its steps card"
        )
        Thread.sleep(forTimeInterval: 5)

        // Scroll the Workouts panel into view and open today's run, the first "Outdoor run" row.
        let run = app.staticTexts["Outdoor run"].firstMatch
        var swipes = 0
        while !run.isHittable && swipes < 12 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(run.isHittable, "never scrolled the run into view")
        run.tap()

        // The pushed detail: the heart card, the summary, the zones and the strap's training effect.
        XCTAssertTrue(
            app.staticTexts["Training effect"].waitForExistence(timeout: 60),
            "The workout detail never drew its training-effect panel"
        )
        let detail = ["Heart rate", "Active", "Paused", "Lowest HR", "Heart-rate zones", "Training effect"]
        let drawn = Set(
            app.staticTexts
                .matching(NSPredicate(format: "label IN %@", detail))
                .allElementsBoundByIndex
                .map(\.label)
        )
        let missing = detail.filter { !drawn.contains($0) }
        XCTAssertTrue(missing.isEmpty, "the workout detail never drew: \(missing)")
        walk(app, prefix: "U8-workout", milestones: detail, screenfuls: 3)
        // Card F9: this screen was built after F4 and never took the bar's clearance, so the end of
        // its scroll sat under the floating bar — U8-workout-3.png shows the training effect's
        // footnote with its second line behind the glass. The measurement is F4's: the last row's
        // bottom against the bar's own top edge.
        var navBarFailures: [String] = []
        if let problem = clearsNavBar(app, lastRow: "workout.footnote", screen: "Workout detail") {
            navBarFailures.append(problem)
        }

        // Back to the Activity tab, then the whole list. The header scrolls with the list, so the
        // walk's end-of-scroll check left it above the fold.
        scrollToTop(app, until: app.buttons["Back"])
        app.buttons["Back"].tap()
        XCTAssertTrue(
            app.staticTexts["Steps"].waitForExistence(timeout: 60),
            "Back never returned to the Activity tab"
        )
        let all = app.staticTexts["All workouts"].firstMatch
        var listSwipes = 0
        while !all.isHittable && listSwipes < 12 {
            app.swipeUp()
            listSwipes += 1
        }
        XCTAssertTrue(all.isHittable, "never scrolled the All workouts row into view")
        all.tap()
        XCTAssertTrue(
            app.staticTexts["Workouts"].waitForExistence(timeout: 60),
            "The all-workouts list never drew"
        )
        walk(app, prefix: "U8-workouts", milestones: ["Workouts", "Outdoor run"], screenfuls: 4)
        // Card F9: the all-workouts list is the second screen U8 built after F4; its last row must
        // clear the floating bar too.
        if let problem = clearsNavBar(app, lastRow: "workouts.last", screen: "All workouts") {
            navBarFailures.append(problem)
        }
        XCTAssertTrue(
            navBarFailures.isEmpty,
            "the floating tab bar still covers the last row of:\n"
                + navBarFailures.joined(separator: "\n")
        )
    }

    /// Card U9: the metric detail, every state the card touches.
    ///
    /// One screen serves all eight metrics, so the walk is: heart rate in all four ranges (the day's
    /// own card, then a week, a month and a year of daily values), then the day for the other seven.
    /// Each launch opens its metric straight away with `-metricDetail` (a debug flag, like
    /// `-todayEvening`), so a screenshot never depends on which tiles Today happens to show.
    ///
    /// Screenshots: `U9-detail-heartrate-day`, `U9-detail-heartrate-week`, `U9-detail-heartrate-month`,
    /// `U9-detail-heartrate-year`, then `U9-detail-<metric>-day` for restinghr, hrv, spo2, stress,
    /// skintemperature, breathing and steps.
    func testMetricDetail() {
        // Heart rate: the day, then one value a day over the week, the month and the year.
        let heart = openDetail("HeartRate")
        XCTAssertTrue(
            heart.staticTexts["Heart rate"].waitForExistence(timeout: 120),
            "the heart-rate detail never drew its header"
        )
        XCTAssertTrue(
            heart.staticTexts["Latest"].waitForExistence(timeout: 60),
            "the day's card never drew its latest reading"
        )
        assertPanels(heart, about: "About heart rate")
        attach(heart, "U9-detail-heartrate-day")
        // Card F9: the third screen U9 built after F4. Heart rate's last row is the Related panel's
        // last metric, and the whole detail must scroll clear of the floating bar like F4's screens.
        if let problem = clearsNavBar(heart, lastRow: "metric.detail.related.Stress", screen: "Metric detail") {
            XCTFail(problem)
        }
        scrollToTop(heart, until: heart.buttons["detail.range.Day"])

        selectRange(heart, "Week", showing: "Last 7 days")
        XCTAssertTrue(heart.staticTexts["Lowest"].exists, "the week has no lowest and highest")
        attach(heart, "U9-detail-heartrate-week")

        selectRange(heart, "Month", showing: "Last 30 days")
        attach(heart, "U9-detail-heartrate-month")

        selectRange(heart, "Year", showing: "Last 12 months")
        // Over a year the panel names the day it belongs to (:338).
        XCTAssertTrue(heart.staticTexts["Lowest day"].exists, "the year has no lowest day")
        attach(heart, "U9-detail-heartrate-year")
        heart.terminate()

        // The other seven metrics, each on its day, where the card leads with its own word.
        let others: [(name: String, header: String, about: String)] = [
            ("RestingHr", "Resting", "About resting heart rate"),
            ("Hrv", "Day average", "About heart-rate variability"),
            ("Spo2", "Day average", "About blood oxygen"),
            ("Stress", "Day average", "About stress"),
            ("SkinTemperature", "Day average", "About skin temperature"),
            ("Breathing", "Average during sleep", "About breathing rate"),
            ("Steps", "Total", "About steps"),
        ]
        for metric in others {
            let app = openDetail(metric.name)
            XCTAssertTrue(
                app.staticTexts[metric.header].waitForExistence(timeout: 120),
                "the \(metric.name) detail never drew its \(metric.header) card"
            )
            assertPanels(app, about: metric.about)
            // The Kotlin name, lowercased: `heartrate`, `skintemperature`. `screens.py` reads it
            // with `(U|R)\d+-<word>(-<word>)*`.
            attach(app, "U9-detail-\(metric.name.lowercased())-day")
            app.terminate()
        }
    }

    /// A launch that opens one metric's detail: `-metricDetail <Kotlin name>`, as a tap would.
    private func openDetail(_ metric: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-glowStill", "-metricDetail", metric]
        app.launch()
        return app
    }

    /// Taps one range and waits for the card's trailing note, which only that range prints.
    private func selectRange(_ app: XCUIApplication, _ range: String, showing trailing: String) {
        let segment = app.buttons["detail.range.\(range)"]
        XCTAssertTrue(segment.waitForExistence(timeout: 60), "the \(range) range never appeared")
        segment.tap()
        XCTAssertTrue(
            app.staticTexts[trailing].waitForExistence(timeout: 60),
            "the \(range) range never drew \"\(trailing)\""
        )
    }

    /// The three quiet panels every metric detail carries: Your normal, About, Related (:142-145).
    private func assertPanels(_ app: XCUIApplication, about: String) {
        XCTAssertTrue(app.staticTexts["Your normal"].exists, "no Your normal panel")
        XCTAssertTrue(app.staticTexts[about].exists, "no \(about) panel")
        XCTAssertTrue(app.staticTexts["Related"].exists, "no Related panel")
    }

    /// A medium sheet has no close button: drag it down, as a finger would.
    private func dismissSheet(_ app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(
                forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1.05)),
                withVelocity: .fast,
                thenHoldForDuration: 0.1
            )
    }

    /// Card U5: Today, in the two orders a day has, the tile chooser, the pushed detail, Monday's
    /// recap and a day with no data at all. Screenshots: `U5-today-1..n` (later in the day),
    /// `U5-tilesheet-1`, `U5-detail-1`, `U5-todaymorning-1..n`, `U5-todayempty-1..n`,
    /// `U5-todayrecap-1..n`.
    ///
    /// The app is launched several times, which is safe: the demo seed writes the same rows for the
    /// same day (`SampleDataTest` in shared pins that), so the screenshots all show one month of wear.
    func testToday() {
        // Later in the day: Charge leads the top card, and last night sits at the bottom.
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-todayEvening"]
        app.launch()
        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        walk(app, prefix: "U5-today", milestones: ["What changed", "Heart rate", "steps", "asleep"], screenfuls: 5)

        // The tile chooser, from the top of the screen.
        scrollToTop(app)
        app.staticTexts["Edit"].tap()
        if !app.buttons["tiles.cancel"].waitForExistence(timeout: 30) {
            attach(app, "U5-tilesheet-missing")
            print("U5 after Edit:\n\(app.debugDescription)")
            XCTFail("The tile chooser never opened")
        }
        XCTAssertTrue(app.staticTexts["What changed shows"].exists, "The tile chooser has no title")
        attach(app, "U5-tilesheet-1")
        app.buttons["tiles.cancel"].tap()
        XCTAssertTrue(app.staticTexts["What changed"].waitForExistence(timeout: 30), "Cancel never closed the tile chooser")

        // A metric tap pushes the detail route card U9 fills in, from a What changed tile: the tile
        // is on screen, so its frame is real (see `openTileDetail`).
        openTileDetail(app, "Resting HR", opens: "Resting heart rate")
        attach(app, "U5-detail-1")
        // Back to Today. The pushed card wears the stack's own bar; if the tab's hidden toolbar takes
        // it with it, restart instead of guessing at a button that is not there.
        if app.navigationBars.buttons.count > 0 {
            app.navigationBars.buttons.firstMatch.tap()
        } else {
            app.terminate()
            app.launch()
        }
        XCTAssertTrue(app.staticTexts["What changed"].waitForExistence(timeout: 60), "Back never returned to Today")

        // A day with no data at all: far enough back that the seeded month has run out.
        scrollToTop(app)
        let previous = app.buttons["date.previous"]
        XCTAssertTrue(previous.waitForExistence(timeout: 30), "The header has no previous-day button")
        for _ in 0..<40 {
            previous.tap()
        }
        XCTAssertTrue(
            app.staticTexts["No heart-rate data yet. Sync your strap."].waitForExistence(timeout: 60),
            "A day before the seeded month is not drawing the empty state"
        )
        XCTAssertFalse(app.staticTexts["What changed"].exists, "An empty day still drew What changed")
        walk(app, prefix: "U5-todayempty", milestones: ["Heart rate", "steps"], screenfuls: 2)

        app.terminate()

        // The morning order: the check-in under the score, and last night right after it.
        let morning = XCUIApplication()
        morning.launchArguments = ["-demoData", "-todayMorning"]
        morning.launch()
        XCTAssertTrue(
            morning.staticTexts["Morning check-in"].waitForExistence(timeout: 120),
            "Today never drew the morning check-in"
        )
        walk(morning, prefix: "U5-todaymorning", milestones: ["Morning check-in", "What changed", "Heart rate", "steps"], screenfuls: 5)
        morning.terminate()

        // Monday: the week's recap, under the top card.
        let monday = XCUIApplication()
        monday.launchArguments = ["-demoData", "-todayMonday", "-todayEvening"]
        monday.launch()
        XCTAssertTrue(
            monday.staticTexts["Your week"].waitForExistence(timeout: 120),
            "Monday's recap never drew"
        )
        attach(monday, "U5-todayrecap-1")
        monday.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
            .press(
                forDuration: 0.1,
                thenDragTo: monday.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)),
                withVelocity: .slow,
                thenHoldForDuration: 0.4
            )
        Thread.sleep(forTimeInterval: 0.4)
        attach(monday, "U5-todayrecap-2")
    }

    /// Every screenful of a long screen from the top down, attaching `prefix-1`, `prefix-2`, ….
    ///
    /// The stop condition is a count, not a frame: a SwiftUI stack's off-screen children report
    /// frames clamped into the viewport (card F2's note), so "the milestone is inside the window"
    /// is true even at the very top of the list — card U5's first walk stopped after one screenshot
    /// for that reason. `screenfuls` is how many screenfuls the screen is tall, so the screenshots
    /// really do run top to bottom. The milestones are then asserted to have rendered at all.
    @discardableResult
    private func walk(_ app: XCUIApplication, prefix: String, milestones: [String], screenfuls: Int = 4) -> Int {
        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 60), "the app never drew a window")
        let missing = milestones.filter { !app.staticTexts[$0].exists }
        XCTAssertTrue(
            missing.isEmpty,
            "the walk over \(prefix) found no \(missing) in the tree at all, so the screen is missing a section"
        )

        func capture(_ number: Int) {
            attach(app, "\(prefix)-\(number)")
        }

        func scrollOneScreenful() {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
                .press(
                    forDuration: 0.1,
                    thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)),
                    withVelocity: .slow,
                    thenHoldForDuration: 0.4
                )
        }

        capture(1)
        for number in 2...max(screenfuls, 1) {
            scrollOneScreenful()
            Thread.sleep(forTimeInterval: 0.5)
            capture(number)
        }
        return max(screenfuls, 1)
    }

    /// Back to the top of a dated tab, where the header and the day's first card are.
    private func scrollToTop(_ app: XCUIApplication) {
        for _ in 0..<8 {
            app.swipeDown()
        }
        Thread.sleep(forTimeInterval: 0.3)
    }

    /// Cards U2, F2 and F5: the design lab, one section per launch.
    ///
    /// Card F5 split the walk by section: `-designLabSection <name>` makes the lab draw a single
    /// section, so no launch holds the whole tree. One screen holding thirteen glow cards, 46
    /// icons, five tab bars and two calendar sheets made XCUITest's queries time out on a lab that
    /// was otherwise correct — `testDesignLab` failed with "Timed out while evaluating UI query"
    /// and passed on the same tree's rerun three times after F2 (runs 39647481836, 39664474482 and
    /// the retries of 883bb5b and e2c6ed6), each one a 25 to 40 minute CI round. A launch now
    /// walks about a screenful of tree.
    ///
    /// Card F2's assertion is kept for every section, and is stronger here: the section sits at the
    /// top of its own scroll view when the launch settles, so the frame XCUITest reports is the one
    /// the window shows. (F2 measured 440 pt for a 362 pt section that was thousands of points down
    /// the old shared scroll view, and that frame did not move with the scroll.)
    ///
    /// The screenshots keep the names the whole-lab walk used — `U2-designlab-1`, … — and stay in
    /// layout order, because the counter runs across the sections instead of restarting at each
    /// launch. How many there are changes with the sections' heights.
    func testDesignLab() {
        // `-designLabStill` holds the glow cores still: a lab that animates for ever never goes
        // quiescent, and that is what makes an XCUITest query wait out its 30 s timeout.
        var shots = 0

        for section in Self.labSections {
            let app = XCUIApplication()
            app.launchArguments = ["-designLab", "-designLabStill", "-designLabSection", section]
            app.launch()

            // Card F5: the first query of a launch is the one that times out. A query waits for the
            // app to answer, the glow cards render on first appearance (one at a time, behind a
            // lock), and a query issued into that render waited 9.6 s in one green run and past the
            // 30 s snapshot timeout in run 39706541852, at the cards launch. The sections only
            // render the cards the window shows now (`LazyVStack`), and this pause lets those few
            // finish before the first query: `wait(for:)` reads the app's state, not its tree.
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 60), "the app never reached the foreground")
            Thread.sleep(forTimeInterval: 4)
            XCTAssertTrue(
                app.staticTexts["lab.title"].waitForExistence(timeout: 120),
                "the design lab did not open for -designLabSection \(section)"
            )
            Thread.sleep(forTimeInterval: 1)
            let window = app.windows.firstMatch
            XCTAssertTrue(window.waitForExistence(timeout: 60), "the app never drew a window")
            let windowFrame = window.frame

            // The title travels with the column, so a section wider than the window pushes it off
            // the left the way F2 saw (`lab.title` at x=-4.33); the section itself is the other
            // half of the same check.
            for identifier in ["lab.title", "lab.\(section)"] {
                let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
                XCTAssertTrue(
                    element.waitForExistence(timeout: 60),
                    "the lab drew no \(identifier) for -designLabSection \(section)"
                )
                let frame = element.frame
                print("F2 \(identifier) frame=\(frame) window=\(windowFrame)")
                XCTAssertGreaterThanOrEqual(
                    frame.minX, 0,
                    "\(identifier) starts at x=\(frame.minX), off the left of the window \(windowFrame) "
                        + "(section frame \(frame))"
                )
                XCTAssertLessThanOrEqual(
                    frame.maxX, windowFrame.maxX,
                    "\(identifier) ends at x=\(frame.maxX), off the right of the window \(windowFrame) "
                        + "(section frame \(frame))"
                )
            }

            // One screenshot per screenful of this section, top to bottom. The walk stops when a
            // screenful is identical to the one before it, which is the scroll view saying it has
            // reached the bottom of the section — the lab holds its glow still, so two captures of
            // one scroll position are the same bytes.
            var previous: Data?
            var screenfuls = 0
            func capture() -> Bool {
                let screenshot = app.screenshot()
                guard screenshot.pngRepresentation != previous else { return false }
                previous = screenshot.pngRepresentation
                screenfuls += 1
                shots += 1
                attach(screenshot, "U2-designlab-\(shots)")
                // U1's eight screenfuls are the same lab, so the committed U1 set is refreshed from
                // the same captures instead of keeping the clipped layout F2 removed.
                if shots <= Self.u1Screenfuls {
                    attach(screenshot, "U1-designlab-\(shots)")
                }
                return true
            }

            _ = capture()
            while screenfuls < Self.screenfulsPerSection {
                scrollLabOneScreenful(app)
                Thread.sleep(forTimeInterval: 0.5)
                if !capture() { break }
            }
            if screenfuls >= Self.screenfulsPerSection {
                print("F5 lab.\(section) took all \(screenfuls) screenfuls; it may be taller than the walk")
            }
            app.terminate()
        }

        XCTAssertGreaterThanOrEqual(
            shots, Self.labSections.count,
            "\(shots) screenshots for \(Self.labSections.count) sections: a section drew nothing"
        )
    }

    /// One screenful down the lab: a slow drag from near the bottom of the window to just under
    /// half way up, held at the end. Holding is what stops the scroll view carrying on with
    /// momentum, so each capture is one drag's distance further down and no row can slip between
    /// two captures.
    private func scrollLabOneScreenful(_ app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.88))
            .press(
                forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.28)),
                withVelocity: .slow,
                thenHoldForDuration: 0.4
            )
    }

    /// Every section of the lab, in layout order: the names `-designLabSection` takes, which are
    /// `DesignLabView.LabSection`'s raw values and the section identifiers' suffixes (`lab.type`).
    /// A name the lab does not know draws `lab.unknown`, so a typo here fails rather than passes.
    private static let labSections = [
        "type",
        "colours",
        "cards",
        "motions",
        "dotmatrix",
        "icons",
        "charts",
        "gauges",
        "hypnogram",
        "parts",
        "quiet",
        "navbar",
        "calendar",
    ]

    /// How many screenfuls one section may take before the walk stops. The whole lab used to be
    /// nineteen; the tallest section (the type ramp) is a few.
    private static let screenfulsPerSection = 10

    /// How many screenfuls card U1 shipped (`U1-designlab-1` … `U1-designlab-8`).
    private static let u1Screenfuls = 8

    private func attach(_ app: XCUIApplication, _ name: String) {
        attach(app.screenshot(), name)
    }

    private func attach(_ screenshot: XCUIScreenshot, _ name: String) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        // The workflow cannot always export attachments from the .xcresult (that subcommand is
        // recent), so the same PNG is written straight to SCREENSHOT_DIR when it is set.
        write(screenshot, as: name)
    }

    private func write(_ screenshot: XCUIScreenshot, as name: String) {
        guard let directory = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !directory.isEmpty else {
            return
        }
        let folder = URL(fileURLWithPath: directory, isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        try? screenshot.pngRepresentation.write(to: folder.appendingPathComponent("\(name).png"))
    }
}
