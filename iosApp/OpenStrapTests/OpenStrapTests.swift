import XCTest
import CoreBluetooth
import OpenStrapShared
@testable import OpenStrap

final class OpenStrapTests: XCTestCase {
    func testSharedGreetingComesFromKotlin() {
        XCTAssertEqual(SharedInfo.shared.greeting(), "OpenStrap shared module is linked")
    }

    // MARK: - Keychain

    func testKeychainWritesReadsAndDeletesTheAuthKey() throws {
        let store = KeychainSecretStore()
        let key = "3c4a6f0e9b1d22e7a8c5f01b6d9e4a77"

        try store.save(key)
        XCTAssertEqual(store.read(), key)

        // Saving again replaces the item instead of adding a second one.
        let second = "00112233445566778899aabbccddeeff"
        try store.save(second)
        XCTAssertEqual(store.read(), second)

        store.delete()
        XCTAssertNil(store.read())
    }

    // MARK: - Key normalisation through the shared Kotlin code

    func testKeyNormalisationGoesThroughSharedKotlin() {
        XCTAssertEqual(
            ZeppCloud.shared.normalizeKey(raw: "0x3C:4A:6F:0E:9B:1D:22:E7:A8:C5:F0:1B:6D:9E:4A:77"),
            "3c4a6f0e9b1d22e7a8c5f01b6d9e4a77"
        )
        XCTAssertNil(ZeppCloud.shared.normalizeKey(raw: "1234"))
        XCTAssertNil(ZeppCloud.shared.normalizeKey(raw: "zz112233445566778899aabbccddeeff"))
        XCTAssertNil(ZeppCloud.shared.normalizeKey(raw: nil))
    }

    // MARK: - UUID strings

    func testUuidStringsAreLowercase128Bit() {
        // CoreBluetooth hands back the short form; the shared code compares the 128-bit one.
        XCTAssertEqual(BleUuid.fullLength(CBUUID(string: "2A37")), "00002a37-0000-1000-8000-00805f9b34fb")
        XCTAssertEqual(BleUuid.fullLength(CBUUID(string: "FEE0")), StrapUuids.shared.MAIN_SERVICE)
        XCTAssertEqual(
            BleUuid.fullLength(CBUUID(string: "00000016-0000-3512-2118-0009af100700")),
            StrapUuids.shared.CHANNEL_CONTROL
        )
        XCTAssertEqual(
            BleUuid.fullLength(CBUUID(string: "2A28")),
            "00002a28-0000-1000-8000-00805f9b34fb"
        )
    }

    // MARK: - Bytes over the boundary

    func testKotlinByteArrayRoundTrip() {
        let bytes = Data([0x00, 0x01, 0x7f, 0x80, 0xff])
        XCTAssertEqual(bytes.kotlinByteArray.data, bytes)
        XCTAssertEqual(Data().kotlinByteArray.data, Data())
    }
}
