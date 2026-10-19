import Foundation
import Security

/// The strap's auth key in the Keychain: this device only, never synced to iCloud.
struct KeychainSecretStore {
    static let service = "org.openstrap.ios"
    static let account = "strap-auth-key"

    enum Failure: Error {
        case status(OSStatus)
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: Self.account,
        ]
    }

    func save(_ secret: String) throws {
        // One item per account: replace rather than add a second one.
        SecItemDelete(baseQuery as CFDictionary)
        var query = baseQuery
        query[kSecValueData as String] = Data(secret.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        query[kSecAttrSynchronizable as String] = false
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw Failure.status(status) }
    }

    func read() -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecAttrSynchronizable as String] = false
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func delete() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
