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

    private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
