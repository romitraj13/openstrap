import Foundation

/// The health database on this phone, as plan section 8 describes it: `Application Support/openstrap.db`,
/// with file protection "complete until first user authentication" — a sync that wakes the app
/// while the phone is locked has to be able to write — and excluded from iCloud backup, the way
/// upstream's `allowBackup="false"` keeps the Android copy out of cloud backups.
///
/// The app and the XCTest that checks the attributes both go through `prepare(_:)`, so the test
/// exercises the file system calls the app makes rather than a copy of them.
enum StrapDatabase {
    static let fileName = "openstrap.db"

    /// What the helper asks the file system for. `.completeUntilFirstUserAuthentication` is readable
    /// after the first unlock since boot, which is exactly the window a nightly sync runs in.
    static let protection = FileProtectionType.completeUntilFirstUserAuthentication

    /// What happened to the file, so the Diagnostics log can say it and the test can check it.
    struct Applied {
        /// The class the helper asked for; the file-system call that applies it has already run.
        var requestedProtection: FileProtectionType
        /// What the file system reports afterwards. `nil` in the Simulator, which has no Data Protection.
        var recordedProtection: FileProtectionType?
        /// Set when the file system refused the protection class; the app logs it.
        var protectionError: String?
        var excludedFromBackup: Bool
        var directoryExcludedFromBackup: Bool
        /// `-wal` and `-shm` files that were already beside the database and got the same attributes.
        var sidecars: Int

        /// One Diagnostics line: what the app asked for and what the phone reports.
        var summary: String {
            var line = "protection \(recordedProtection?.rawValue ?? requestedProtection.rawValue)"
            if let protectionError { line += " (refused: \(protectionError))" }
            line += excludedFromBackup ? ", excluded from backup" : ", NOT excluded from backup"
            return line
        }
    }

    /// `<Application Support>/openstrap.db`, creating Application Support on a fresh install.
    static func url(fileManager: FileManager = .default) throws -> URL {
        let support = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return support.appendingPathComponent(fileName)
    }

    /// Creates the file — SQLite starts from an empty one — and applies both attributes: protection
    /// and backup exclusion on the database and on the sidecars SQLite left behind, plus the exclusion
    /// on the folder, so the `-wal` and `-shm` it writes later are covered too.
    @discardableResult
    static func prepare(_ url: URL, fileManager: FileManager = .default) throws -> Applied {
        let directory = url.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        if !fileManager.fileExists(atPath: url.path) {
            _ = fileManager.createFile(atPath: url.path, contents: nil)
        }

        var files = [url]
        for suffix in ["-wal", "-shm"] where fileManager.fileExists(atPath: url.path + suffix) {
            files.append(URL(fileURLWithPath: url.path + suffix))
        }

        var protectionError: String?
        for file in files {
            do {
                try fileManager.setAttributes([.protectionKey: protection], ofItemAtPath: file.path)
            } catch {
                // The Simulator has no Data Protection. The file still has to exist and be excluded
                // from backup, so the app carries on and says what happened in Diagnostics.
                protectionError = error.localizedDescription
            }
            try excludeFromBackup(file)
        }
        try excludeFromBackup(directory)

        return Applied(
            requestedProtection: protection,
            recordedProtection: try protectionClass(of: url, fileManager: fileManager),
            protectionError: protectionError,
            excludedFromBackup: try isExcludedFromBackup(url),
            directoryExcludedFromBackup: try isExcludedFromBackup(directory),
            sidecars: files.count - 1
        )
    }

    /// Marks a file or folder as not for iCloud. The flag is what iOS stores for
    /// `NSURLIsExcludedFromBackupKey`; a folder's flag covers what is written inside it.
    static func excludeFromBackup(_ url: URL) throws {
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var target = url
        try target.setResourceValues(values)
    }

    static func isExcludedFromBackup(_ url: URL) throws -> Bool {
        try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup ?? false
    }

    /// The protection class the file system records, or `nil` where it records none (Simulator).
    static func protectionClass(of url: URL, fileManager: FileManager = .default) throws -> FileProtectionType? {
        let attributes = try fileManager.attributesOfItem(atPath: url.path)
        guard let raw = attributes[.protectionKey] as? String else { return nil }
        return FileProtectionType(rawValue: raw)
    }
}
