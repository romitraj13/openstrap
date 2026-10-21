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

    /// Card C6: `-demoData` seeds the sample month, so each of the four screens has something to
    /// draw. A screenshot of every tab is attached to the `.xcresult` with lifetime `keepAlways`.
    func testEveryTabShowsItsScreen() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["todayScore"].waitForExistence(timeout: 120),
            "Today never drew a Recovery score from the seeded sample data"
        )
        attach(app, "today")

        app.tabBars.buttons["Sleep"].tap()
        XCTAssertTrue(app.navigationBars["Sleep"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["sleepScore"].waitForExistence(timeout: 30), "Sleep never drew the night's score")
        attach(app, "sleep")

        app.tabBars.buttons["Heart"].tap()
        XCTAssertTrue(app.navigationBars["Heart"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["heartLatest"].waitForExistence(timeout: 30), "Heart never drew the day's rate")
        attach(app, "heart")

        app.tabBars.buttons["Strap"].tap()
        XCTAssertTrue(app.navigationBars["Strap"].waitForExistence(timeout: 30))
        attach(app, "strap")
    }

    /// Card U2: the same lab, now with the glow charts, the gauges, the quiet panels, the tab bar and
    /// the calendar sheet at the end. Scrolls to the last section and attaches a screenshot per
    /// screenful, named `U2-designlab-1` … `U2-designlab-N`.
    func testDesignLab() {
        let app = XCUIApplication()
        app.launchArguments = ["-designLab"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["lab.title"].waitForExistence(timeout: 120),
            "The design lab did not open for -designLab"
        )
        var shots = 0
        func capture() {
            shots += 1
            attach(app, "U2-designlab-\(shots)")
        }
        capture()

        let end = app.staticTexts["lab.calendar.title"]
        var swipes = 0
        while !(end.exists && end.isHittable) && swipes < 40 {
            app.swipeUp()
            swipes += 1
            // The glow layers are rendered off the main thread on first appearance.
            Thread.sleep(forTimeInterval: 0.6)
            capture()
        }

        XCTAssertTrue(end.exists, "The design lab never reached the calendar section")
        XCTAssertGreaterThan(shots, 1, "The lab fits on one screen, so nothing scrolled")
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let screenshot = app.screenshot()
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
