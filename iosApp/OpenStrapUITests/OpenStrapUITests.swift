import XCTest

final class OpenStrapUITests: XCTestCase {
    func testPlaceholderScreenShowsSharedText() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["sharedGreeting"].waitForExistence(timeout: 30))
    }
}
