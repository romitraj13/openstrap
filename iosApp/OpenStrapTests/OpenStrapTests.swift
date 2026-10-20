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

    // MARK: - Zepp sign-in helpers

    /// The cookie header only carries the Zepp domains, so no unrelated cookie reaches the parser
    /// (or the device-list request).
    func testCookieHeaderKeepsOnlyTheZeppDomains() throws {
        let cookies = [
            try XCTUnwrap(HTTPCookie(properties: [.domain: "watchface.zepp.com", .path: "/", .name: "hm-user-login-info", .value: "x"])),
            try XCTUnwrap(HTTPCookie(properties: [.domain: ".zepp.com", .path: "/", .name: "apptoken", .value: "t"])),
            try XCTUnwrap(HTTPCookie(properties: [.domain: "example.com", .path: "/", .name: "other", .value: "y"])),
        ]
        XCTAssertEqual(ZeppSignInModel.cookieHeader(from: cookies), "hm-user-login-info=x; apptoken=t")
        XCTAssertNil(ZeppSignInModel.cookieHeader(from: []))
    }

    func testMaskedKeyHidesAllButTheLastFourCharacters() {
        XCTAssertEqual(ZeppSignInModel.maskedKey("ffeeddccbbaa99887766554433221100"), "••••••••1100")
    }

    // MARK: - CoreBluetooth state restoration

    /// H1 crashed at the first Connect on a real iPhone: the central manager is created with a
    /// restore identifier (BleDriverImpl.swift), so CoreBluetooth calls this delegate method and
    /// throws NSInternalInconsistencyException when the delegate does not implement it. The crash
    /// costs a card, not a phone: a missing handler fails here instead.
    func testBleDriverImplementsTheRestoreStateDelegateMethod() {
        let driver = BleDriverImpl()
        XCTAssertTrue(driver is CBCentralManagerDelegate)
        XCTAssertTrue(
            driver.responds(to: NSSelectorFromString("centralManager:willRestoreState:")),
            "BleDriverImpl must implement centralManager(_:willRestoreState:)"
        )
    }

    // MARK: - The health database's file attributes (plan section 8)

    /// A temporary `openstrap.db`, in its own folder so the folder's backup flag can be read back.
    private func temporaryDatabase() throws -> URL {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("openstrap-db-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        addTeardownBlock { [folder] in
            try? FileManager.default.removeItem(at: folder)
        }
        return folder.appendingPathComponent(StrapDatabase.fileName)
    }

    /// Step 6 of the card: the helper the app uses applies protection and backup exclusion. The backup
    /// flag is a real file-system property on the simulator as well, so it is read back; the protection
    /// class is only reported by a device (the Simulator has no Data Protection), and the app puts the
    /// same read-back in the Diagnostics log for the phone run.
    func testPrepareAppliesProtectionAndBackupExclusionToATemporaryFile() throws {
        let url = try temporaryDatabase()
        let applied = try StrapDatabase.prepare(url)

        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertEqual(applied.requestedProtection, .completeUntilFirstUserAuthentication)

        let excluded = try StrapDatabase.isExcludedFromBackup(url)
        XCTAssertTrue(excluded)
        XCTAssertTrue(applied.excludedFromBackup)
        XCTAssertTrue(applied.directoryExcludedFromBackup)

        #if targetEnvironment(simulator)
        // Data Protection does not exist in the Simulator: if it reports a class after all, it has to
        // be the one asked for. H2 step 6 reads the device's own answer from the log.
        if let recorded = applied.recordedProtection {
            XCTAssertEqual(recorded, .completeUntilFirstUserAuthentication)
        }
        #else
        XCTAssertNil(applied.protectionError)
        XCTAssertEqual(applied.recordedProtection, .completeUntilFirstUserAuthentication)
        #endif
    }

    /// SQLite writes `-wal` and `-shm` beside the database; the helper has to cover the ones already
    /// there, and the folder's flag the ones written later.
    func testPrepareAlsoCoversTheSidecarsBesideTheDatabase() throws {
        let url = try temporaryDatabase()
        let wal = URL(fileURLWithPath: url.path + "-wal")
        let shm = URL(fileURLWithPath: url.path + "-shm")
        try Data("wal".utf8).write(to: wal)
        try Data("shm".utf8).write(to: shm)

        let applied = try StrapDatabase.prepare(url)

        XCTAssertEqual(applied.sidecars, 2)
        let walExcluded = try StrapDatabase.isExcludedFromBackup(wal)
        let shmExcluded = try StrapDatabase.isExcludedFromBackup(shm)
        XCTAssertTrue(walExcluded)
        XCTAssertTrue(shmExcluded)
    }

    /// Step 2 of the card: Application Support, not Documents.
    func testTheDatabaseLivesInApplicationSupport() throws {
        let url = try StrapDatabase.url()
        XCTAssertEqual(url.lastPathComponent, "openstrap.db")
        XCTAssertTrue(url.path.contains("/Library/Application Support/"), url.path)
        let support = url.deletingLastPathComponent()
        var isDirectory: ObjCBool = false
        XCTAssertTrue(FileManager.default.fileExists(atPath: support.path, isDirectory: &isDirectory))
        XCTAssertTrue(isDirectory.boolValue)
    }
}
