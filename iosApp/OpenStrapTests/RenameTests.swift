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

    /// The icon is compiled into the bundle, and this is the check that says so — with the one
    /// caveat a CI run paid for.
    ///
    /// `UIImage(named: "AppIcon")` is nil on iOS even when the icon is right: UIKit keeps the
    /// app-icon assets out of its named-image lookup. Run 39761384671 shows both halves of that
    /// at once — `CFBundleIconName` was `AppIcon` and springboard drew the dot-matrix icon while
    /// the lookup returned nil, so asserting on it tests UIKit's lookup table, not the icon.
    /// What is asserted instead is what the plist and the bundle actually carry: actool named the
    /// primary icon `AppIcon`, and the compiled catalogue (and, on a set with explicit sizes, the
    /// icon files beside it) are in the app. The pixel proof that springboard draws it is the
    /// `R1-homescreen` screenshot.
    func testThePrimaryIconIsTheAppIconSet() {
        let icons = info["CFBundleIcons"] as? [String: Any]
        let primary = icons?["CFBundlePrimaryIcon"] as? [String: Any]
        XCTAssertEqual(
            primary?["CFBundleIconName"] as? String,
            "AppIcon",
            "CFBundleIcons does not name an AppIcon app icon, so actool compiled no icon set"
        )

        let root = Bundle.main.bundleURL
        let contents = (try? FileManager.default.contentsOfDirectory(atPath: root.path)) ?? []
        let iconFiles = contents.filter { $0.hasPrefix("AppIcon") && $0.hasSuffix(".png") }
        if !iconFiles.isEmpty {
            for file in iconFiles {
                let path = root.appendingPathComponent(file).path
                XCTAssertNotNil(
                    UIImage(contentsOfFile: path),
                    "\(file) is in the bundle but does not decode as an image"
                )
            }
        } else {
            // A single-size icon set can compile into the catalogue alone. The catalogue is the
            // bundle's one place an icon can then live, so it has to be there.
            XCTAssertTrue(
                contents.contains("Assets.car"),
                "the bundle has neither AppIcon png files nor Assets.car: \(contents.sorted())"
            )
        }
        let iconName = (primary?["CFBundleIconName"] as? String) ?? "nil"
        let catalogue = contents.contains("Assets.car")
        print("R1 icon: CFBundleIconName=\(iconName) files=\(iconFiles.sorted()) car=\(catalogue)")
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
