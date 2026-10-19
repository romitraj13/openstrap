import XCTest
import OpenStrapShared

final class OpenStrapTests: XCTestCase {
    func testSharedGreetingComesFromKotlin() {
        XCTAssertEqual(SharedInfo.shared.greeting(), "OpenStrap shared module is linked")
    }
}
