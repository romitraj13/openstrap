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
            app.staticTexts["todayScore"].waitForExistence(timeout: 120),
            "Today never drew a Recovery score from the seeded sample data"
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
            app.descendants(matching: .any)["interim.recovery"].waitForExistence(timeout: 30),
            "The Recovery tab never drew its panel"
        )
        attach(app, "U4-recovery-1")

        app.buttons["Sleep"].tap()
        XCTAssertTrue(app.staticTexts["sleepScore"].waitForExistence(timeout: 30), "Sleep never drew the night's score")
        attach(app, "U4-sleep-1")

        app.buttons["Activity"].tap()
        XCTAssertTrue(
            app.descendants(matching: .any)["interim.activity"].waitForExistence(timeout: 30),
            "The Activity tab never drew its panel"
        )
        attach(app, "U4-activity-1")

        // The date navigator's calendar sheet, from the shared header.
        XCTAssertTrue(app.buttons["date.pick"].waitForExistence(timeout: 30), "The header has no day picker")
        app.buttons["date.pick"].tap()
        XCTAssertTrue(app.staticTexts["Has data"].waitForExistence(timeout: 30), "The calendar sheet never opened")
        attach(app, "U4-calendar-1")
        dismissSheet(app)

        // You, and everything it opens.
        app.buttons["You"].tap()
        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "The You tab never drew")
        attach(app, "U4-you-1")

        app.buttons["you.profile"].tap()
        XCTAssertTrue(app.staticTexts["Your profile"].waitForExistence(timeout: 30))
        attach(app, "U4-profile-1")
        app.buttons["profile.back"].tap()

        XCTAssertTrue(app.buttons["you.goals"].waitForExistence(timeout: 30))
        app.buttons["you.goals"].tap()
        XCTAssertTrue(app.staticTexts["Your goals"].waitForExistence(timeout: 30), "The goals sheet never opened")
        attach(app, "U4-goals-1")
        app.buttons["goals.cancel"].tap()

        XCTAssertTrue(app.buttons["you.strap"].waitForExistence(timeout: 30))
        app.buttons["you.strap"].tap()
        XCTAssertTrue(app.staticTexts["Your strap"].waitForExistence(timeout: 30))
        attach(app, "U4-strap-1")
        app.buttons["strap.back"].tap()

        XCTAssertTrue(app.buttons["you.diagnostics"].waitForExistence(timeout: 30))
        app.buttons["you.diagnostics"].tap()
        XCTAssertTrue(app.navigationBars["Diagnostics"].waitForExistence(timeout: 30), "Diagnostics never opened")
        attach(app, "U4-diagnostics-1")
        app.navigationBars["Diagnostics"].buttons.firstMatch.tap()

        XCTAssertTrue(app.buttons["you.licences"].waitForExistence(timeout: 30))
        app.buttons["you.licences"].tap()
        XCTAssertTrue(app.staticTexts["Licences"].waitForExistence(timeout: 30), "The licence texts never opened")
        attach(app, "U4-licences-1")
        app.buttons["licences.back"].tap()

        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "Back from Licences never returned to You")
    }

    /// A medium sheet has no close button: drag it down, as a finger would.
    private func dismissSheet(_ app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.58))
            .press(
                forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1.05)),
                withVelocity: .fast,
                thenHoldForDuration: 0.1
            )
    }

    /// Card U2: the same lab, now with the glow charts, the gauges, the quiet panels, the tab bar and
    /// the calendar sheet at the end. Scrolls the whole lab and attaches a screenshot per screenful,
    /// named `U2-designlab-1` … `U2-designlab-N`.
    ///
    /// Card F2: every section is also asserted to sit inside the window. One child with an ideal
    /// width larger than the column (DotMatrixText("0123456789")) widened the whole VStack, and the
    /// scroll view centred it, so both edges were cut. A section frame outside the window is that
    /// bug, whether or not a screenshot happens to show it.
    ///
    /// The frame check follows the scroll instead of reading the whole tree at launch: XCUITest
    /// reports a rounded, scaled frame for an element that is thousands of points down a scroll
    /// view (F2 saw 440 pt for a 362 pt section, off the third-of-a-point layout grid) and that
    /// frame does not move with the scroll, so only what the window shows is trustworthy. Every
    /// section still unchecked is asked about on every screenful, which is what makes a section
    /// impossible to skip.
    ///
    /// The walk is a drag of most of a screen per capture, until the last section has been seen,
    /// rather than a fixed count of `swipeUp`s: `swipeUp` moves this lab about 400 pt, and the lab
    /// is about 9,000 pt tall, so fourteen swipes stopped at the charts (run 39589370097) even
    /// though the calendar's title was already in the tree.
    func testDesignLab() {
        let app = XCUIApplication()
        // `-designLabStill` holds the glow cores still: a lab that animates for ever never goes
        // quiescent, and that is what makes an XCUITest query wait out its 30 s timeout.
        app.launchArguments = ["-designLab", "-designLabStill"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["lab.title"].waitForExistence(timeout: 120),
            "The design lab did not open for -designLab"
        )
        // The glow layers are rendered once on first appearance, one card at a time (the renderer
        // holds a lock), and a dozen Core Image blurs keep the accessibility server busy enough to
        // blow XCUITest's query timeout (run 39587579214). Let the lab settle before asking it
        // anything.
        Thread.sleep(forTimeInterval: 5)
        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 60), "The app never drew a window")
        let windowFrame = window.frame

        /// The sections already proven to sit inside the window.
        var checked = Set<String>()
        func checkSectionsOnScreen() {
            for identifier in Self.labSections where !checked.contains(identifier) {
                let element = app.descendants(matching: .any)[identifier]
                guard element.exists else { continue }
                let frame = element.frame
                guard frame.minY < windowFrame.maxY, frame.maxY > windowFrame.minY else { continue }
                checked.insert(identifier)
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
        }

        var shots = 0
        func capture() {
            shots += 1
            let screenshot = app.screenshot()
            attach(screenshot, "U2-designlab-\(shots)")
            // U1's eight screenfuls are the same lab, so the committed U1 set is refreshed from the
            // same captures instead of keeping the clipped layout F2 removed.
            if shots <= Self.u1Screenfuls {
                attach(screenshot, "U1-designlab-\(shots)")
            }
        }

        /// One screenful: a slow drag from near the bottom of the window to just under half way up,
        /// held at the end. Holding is what stops the scroll view carrying on with momentum, so each
        /// capture is one drag's distance further down and no section can slip between two captures.
        func scrollOneScreenful() {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.88))
                .press(
                    forDuration: 0.1,
                    thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.28)),
                    withVelocity: .slow,
                    thenHoldForDuration: 0.4
                )
        }

        capture()
        checkSectionsOnScreen()

        var screenfuls = 1
        while checked.count < Self.labSections.count && screenfuls < 26 {
            scrollOneScreenful()
            // The glow layers are rendered off the main thread on first appearance.
            Thread.sleep(forTimeInterval: 0.5)
            capture()
            checkSectionsOnScreen()
            screenfuls += 1
        }

        XCTAssertEqual(
            checked.count, Self.labSections.count,
            "the walk never showed \(Self.labSections.filter { !checked.contains($0) }), "
                + "so their frames are unchecked"
        )
        XCTAssertGreaterThan(shots, 1, "The lab fits on one screen, so nothing scrolled")
    }

    /// Every identifier the lab puts on a section (`DesignLabView.section`), in layout order, plus
    /// the title. `testDesignLab` asserts each one sits inside the window.
    private static let labSections = [
        "lab.title",
        "lab.type",
        "lab.colours",
        "lab.cards",
        "lab.motions",
        "lab.dotmatrix",
        "lab.icons",
        "lab.charts",
        "lab.gauges",
        "lab.hypnogram",
        "lab.parts",
        "lab.quiet",
        "lab.navbar",
        "lab.calendar",
    ]

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
