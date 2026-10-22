import XCTest
import UIKit
@testable import OpenStrap

/// Card R1 renamed what a person reads to OpenHealth and left everything that identifies the app
/// or its data to iOS alone. These are the values that decide that identity: change one and iOS
/// treats the build as a different app — Romit loses his history and pairing, and background
/// Bluetooth restore stops. The test is the pin, not the comment above the constant.
final class RenameTests: XCTestCase {
    private var info: [String: Any] {
        Bundle.main.infoDictionary ?? [:]
    }

    // MARK: - The name a person reads

    func testTheHomeScreenNameIsOpenHealth() {
        XCTAssertEqual(info["CFBundleDisplayName"] as? String, "OpenHealth")
    }

    // MARK: - The icon

    /// actool emits both keys for an app whose ASSETCATALOG_COMPILER_APPICON_NAME is AppIcon.
    func testThePrimaryIconIsTheAppIconSet() {
        let icons = info["CFBundleIcons"] as? [String: Any]
        let primary = icons?["CFBundlePrimaryIcon"] as? [String: Any]
        XCTAssertEqual(primary?["CFBundleIconName"] as? String, "AppIcon")
        XCTAssertNotNil(
            UIImage(named: "AppIcon"),
            "the AppIcon set did not compile into the bundle, so the home screen has no icon"
        )
    }

    // MARK: - Every identifier iOS keys this app and its data on

    /// The Keychain item the auth key lives in.
    func testTheKeychainIdentifiersHaveNotMoved() {
        XCTAssertEqual(KeychainSecretStore.service, "org.openstrap.ios")
        XCTAssertEqual(KeychainSecretStore.account, "strap-auth-key")
    }

    /// The identifier CoreBluetooth state restoration is keyed on, so a background sync survives a
    /// relaunch.
    func testTheBluetoothRestoreIdentifierHasNotMoved() {
        XCTAssertEqual(BleDriverImpl.restoreIdentifier, "org.openstrap.central")
    }

    /// The database file, and the defaults keys beside it.
    func testTheStoredDataKeysHaveNotMoved() {
        XCTAssertEqual(StrapDatabase.fileName, "openstrap.db")
        XCTAssertEqual(StrapController.peripheralIdentifierKey, "openstrap.strapPeripheralIdentifier")
        XCTAssertEqual(HealthExportSettings.enabledKey, "openstrap.writeToAppleHealth")
    }
}
