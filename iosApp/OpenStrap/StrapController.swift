import Combine
import CoreBluetooth
import Foundation
import OpenStrapShared
import UIKit

/// One `sync_log` row as the Diagnostics screen shows it: when it finished, what asked for it,
/// whether the app was on screen, how many rows it wrote, and the error if it failed.
struct SyncLogRow: Identifiable {
    /// Position in the list the engine returned (newest first), because Kotlin's `id` is not needed
    /// anywhere else and this keeps the row a plain value.
    let id: Int
    let start: Int64
    let end: Int64
    let trigger: String
    let foreground: Bool
    let rows: Int
    let error: String?

    init(index: Int, row: SyncLog) {
        id = index
        start = row.start
        end = row.end
        trigger = row.trigger
        foreground = row.foreground
        rows = Int(row.rows)
        error = row.error
    }

    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM HH:mm"
        return formatter
    }()

    /// `20 Oct 06:12  background  auto  12 rows  <error>` — the fields H2 reads.
    var text: String {
        let when = Self.formatter.string(from: Date(timeIntervalSince1970: TimeInterval(end)))
        var line = "\(when)  \(foreground ? "foreground" : "background")  \(trigger)  \(rows) rows"
        if let error, !error.isEmpty { line += "  \(error)" }
        return line
    }
}

/// Owns the strap for the whole app: the Keychain key, the CoreBluetooth driver, the shared
/// `StrapEngine`, and everything the screens show.
final class StrapController: ObservableObject {
    static let peripheralIdentifierKey = "openstrap.strapPeripheralIdentifier"
    static let invalidKeyMessage = "That isn't a valid auth key: it should be 32 hex characters"
    private static let logLimit = 2000
    /// The Diagnostics screen's sync log: the newest rows, enough to cover a night and a day.
    static let syncLogLimit = 50

    @Published private(set) var state = "disconnected"
    @Published private(set) var stateDetail: String?
    @Published private(set) var firmware: String?
    @Published private(set) var battery = -1
    @Published private(set) var charging = false
    @Published private(set) var heartRate: Int?
    @Published private(set) var liveHeartRateEnabled = false
    @Published private(set) var logLines: [String] = []
    @Published private(set) var hasStoredKey: Bool
    /// When the last history sync finished, in epoch seconds; 0 before the first one.
    @Published private(set) var lastSyncEpochSeconds: Int64 = 0
    /// The newest sync log rows, newest first: the Strap screen's last sync and the Diagnostics list.
    @Published private(set) var syncLogRows: [SyncLogRow] = []
    /// True while the app is not on screen. Syncs then run from a Bluetooth wake, on borrowed time.
    @Published private(set) var inBackground = false

    private let secrets: KeychainSecretStore
    private let defaults: UserDefaults
    private let driver: BleDriverImpl
    private let engine: StrapEngine
    private let sink: EngineSink
    private var peripheralIdentifier: UUID?
    private var discoveredCharacteristics: [String] = []
    private var appStateObservers: [NSObjectProtocol] = []
    private var syncBackgroundTask: UIBackgroundTaskIdentifier = .invalid

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM HH:mm"
        return formatter
    }()

    init(secrets: KeychainSecretStore = KeychainSecretStore(), defaults: UserDefaults = .standard) {
        self.secrets = secrets
        self.defaults = defaults
        self.hasStoredKey = secrets.read() != nil
        let sink = EngineSink()
        self.sink = sink
        let driver = BleDriverImpl()
        self.driver = driver
        // The health database lives in Application Support with the attributes plan section 8 asks
        // for; `openDatabase` never throws, it reports what went wrong in the log instead.
        let database = Self.openDatabase()
        self.engine = StrapEngine(driver: driver, listener: sink, databasePath: database.path)
        // The sink calls back into this object; it holds us weakly, so no cycle through Kotlin.
        sink.owner = self
        driver.bridge = engine.bridge
        driver.onLog = { [weak self] line in self?.append(line) }
        driver.onDiscovered = { [weak self] identifier, characteristics in
            DispatchQueue.main.async {
                self?.startEngine(identifier: identifier, characteristics: characteristics)
            }
        }
        driver.onDisconnect = { [weak self] reason in
            DispatchQueue.main.async {
                self?.engine.linkDown(reason: reason)
            }
        }
        observeAppState()
        append("database \(database.path): \(database.summary)")
        // The rows outlive the app: a sync that ran overnight is already in the table at launch.
        refreshSyncLog()
        if hasStoredKey {
            driver.start(savedIdentifier: Self.storedIdentifier(defaults))
        }
    }

    deinit {
        for observer in appStateObservers {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    /// The key came from a paste: normalise it with the shared Kotlin code, store it, connect.
    /// Returns the message for the Pair screen, or nil when the key was accepted.
    func connect(pastedKey: String) -> String? {
        guard let key = ZeppCloud.shared.normalizeKey(raw: pastedKey) else {
            return Self.invalidKeyMessage
        }
        do {
            try secrets.save(key)
        } catch {
            return "The key could not be saved to this phone's Keychain."
        }
        hasStoredKey = true
        append("auth key saved")
        connectWithStoredKey()
        return nil
    }

    /// Uses the key already in the Keychain: starts Bluetooth, or reconnects when it is already up.
    func connectWithStoredKey() {
        guard secrets.read() != nil else {
            hasStoredKey = false
            state = "disconnected"
            stateDetail = "Pair the strap first."
            return
        }
        if let identifier = peripheralIdentifier, !discoveredCharacteristics.isEmpty {
            startEngine(identifier: identifier, characteristics: discoveredCharacteristics)
        } else {
            state = "connecting"
            stateDetail = nil
            driver.start(savedIdentifier: Self.storedIdentifier(defaults))
        }
    }

    /// Deletes the key and the saved peripheral, and drops the link.
    func forgetStrap() {
        secrets.delete()
        defaults.removeObject(forKey: Self.peripheralIdentifierKey)
        driver.stop()
        engine.linkDown(reason: "forgotten")
        endSyncBackgroundTask()
        hasStoredKey = false
        peripheralIdentifier = nil
        discoveredCharacteristics = []
        heartRate = nil
        liveHeartRateEnabled = false
        state = "disconnected"
        stateDetail = nil
        append("forgot the strap")
    }

    func setLiveHeartRate(_ enabled: Bool) {
        liveHeartRateEnabled = enabled
        engine.setLiveHeartRate(enabled: enabled)
        append(enabled ? "live heart rate on" : "live heart rate off")
    }

    /// The Strap screen's button. The engine queues it behind a sync that is already running, and the
    /// background task that gives it time to finish starts with the `running` state.
    func syncNow() {
        guard hasStoredKey else {
            append("sync now: no strap paired yet")
            return
        }
        guard state == "ready" else {
            append("sync now: the strap is \(state)")
            return
        }
        append("sync now")
        engine.syncNow()
    }

    /// Asks the engine for the newest sync log rows; the answer arrives on the main thread.
    func refreshSyncLog() {
        engine.recentSyncs(limit: Int32(Self.syncLogLimit)) { [weak self] rows in
            guard let self else { return }
            self.syncLogRows = rows.enumerated().map { SyncLogRow(index: $0.offset, row: $0.element) }
            if let newest = self.syncLogRows.first, newest.end > self.lastSyncEpochSeconds {
                self.lastSyncEpochSeconds = newest.end
            }
        }
    }

    /// "Never" before the first sync, otherwise the time the last one finished. A sync from a
    /// previous day keeps its date, so an overnight run is not mistaken for one this morning.
    var lastSyncText: String {
        guard lastSyncEpochSeconds > 0 else { return "Never" }
        let finished = Date(timeIntervalSince1970: TimeInterval(lastSyncEpochSeconds))
        return Calendar.current.isDateInToday(finished)
            ? Self.timeFormatter.string(from: finished)
            : Self.dateTimeFormatter.string(from: finished)
    }

    var logText: String {
        logLines.joined(separator: "\n")
    }

    /// What the Copy button puts on the clipboard: the sync log rows first, then the engine's lines.
    /// No key, token or cookie ever reaches either; the auth key lives in the Keychain only.
    var diagnosticsText: String {
        var sections: [String] = []
        if !syncLogRows.isEmpty {
            sections.append("sync log (newest first)\n" + syncLogRows.map { $0.text }.joined(separator: "\n"))
        }
        sections.append(logText.isEmpty ? "nothing logged yet" : logText)
        return sections.joined(separator: "\n\n")
    }

    /// A Diagnostics line from another screen (the Zepp sign-in logs one per sign-in). Never
    /// called with the auth key, a token or a cookie string.
    func note(_ line: String) {
        append(line)
    }

    // MARK: - Called by the sink on the main thread

    fileprivate func engineState(state: String, detail: String?) {
        self.state = state
        self.stateDetail = detail
        var line = "state: \(state)"
        if let detail { line += " (\(detail))" }
        append(line)
        if state == "ready", let identifier = peripheralIdentifier {
            // Card C3: next launch reconnects to this strap by identifier.
            defaults.set(identifier.uuidString, forKey: Self.peripheralIdentifierKey)
        }
        if state == "failed" || state == "disconnected" || state == "connecting" {
            heartRate = nil
        }
    }

    fileprivate func engineDevice(firmware: String?, batteryPercent: Int32, charging: Bool) {
        self.firmware = firmware
        self.battery = Int(batteryPercent)
        self.charging = charging
    }

    fileprivate func engineHeartRate(bpm: Int32) {
        heartRate = Int(bpm)
    }

    fileprivate func engineLog(line: String) {
        append(line)
    }

    /// Sync progress from the shared engine: the background task that gives the sync its time, the
    /// last-sync time on the Strap screen, and the log row the two screens read back.
    fileprivate func engineSync(state: String, detail: String?, lastSyncEpochSeconds: Int64) {
        if state == "running" {
            beginSyncBackgroundTask()
        } else if state == "done" || state == "failed" {
            endSyncBackgroundTask()
        }
        if lastSyncEpochSeconds > 0 {
            self.lastSyncEpochSeconds = lastSyncEpochSeconds
        }
        let extra = detail.map { " (\($0))" } ?? ""
        let last = lastSyncEpochSeconds > 0
            ? " last sync \(Self.timeFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(lastSyncEpochSeconds))))"
            : ""
        append("sync \(state)\(extra)\(last)")
        if state == "done" || state == "failed" {
            refreshSyncLog()
        }
    }

    // MARK: - Background

    private func observeAppState() {
        let center = NotificationCenter.default
        appStateObservers.append(
            center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
                self?.setAppVisible(true)
            }
        )
        appStateObservers.append(
            center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
                self?.setAppVisible(false)
            }
        )
        // A Bluetooth relaunch starts without an active scene; the notification above says when it
        // becomes one.
        let active = UIApplication.shared.applicationState == .active
        inBackground = !active
        engine.appVisible = active
    }

    private func setAppVisible(_ visible: Bool) {
        let wasVisible = !inBackground
        inBackground = !visible
        // Each sync_log row records this: a sync from a Bluetooth wake overnight is "background".
        engine.appVisible = visible
        if wasVisible != visible {
            append(visible ? "app in the foreground" : "app in the background")
        }
    }

    /// A history sync gets a background task so iOS does not suspend the app in the middle of one.
    /// It starts when `onSync` reports `running` and ends on `done` or `failed`.
    private func beginSyncBackgroundTask() {
        guard syncBackgroundTask == .invalid else { return }
        syncBackgroundTask = UIApplication.shared.beginBackgroundTask(withName: "strap history sync") { [weak self] in
            // iOS is about to suspend the app: the task has to end here or the app is terminated.
            self?.append("sync background time ran out")
            self?.endSyncBackgroundTask()
        }
        append("sync background task started")
    }

    private func endSyncBackgroundTask() {
        guard syncBackgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(syncBackgroundTask)
        syncBackgroundTask = .invalid
        append("sync background task ended")
    }

    // MARK: - Plumbing

    /// Opens the health database in Application Support and applies plan section 8's two attributes.
    /// Never throws: the app still runs when the file system refuses, and says so in Diagnostics.
    private static func openDatabase() -> (path: String, summary: String) {
        do {
            let url = try StrapDatabase.url()
            let applied = try StrapDatabase.prepare(url)
            return (url.path, applied.summary)
        } catch {
            let fallback = (try? StrapDatabase.url())
                ?? FileManager.default.temporaryDirectory.appendingPathComponent(StrapDatabase.fileName)
            return (fallback.path, "the database attributes could not be applied: \(error.localizedDescription)")
        }
    }

    private func startEngine(identifier: UUID, characteristics: [String]) {
        guard let key = secrets.read() else { return }
        peripheralIdentifier = identifier
        discoveredCharacteristics = characteristics
        engine.linkUp(discoveredCharacteristics: characteristics, authKeyHex: key)
    }

    private static func storedIdentifier(_ defaults: UserDefaults) -> UUID? {
        guard let text = defaults.string(forKey: peripheralIdentifierKey) else { return nil }
        return UUID(uuidString: text)
    }

    private func append(_ line: String) {
        let stamped = "\(Self.timeFormatter.string(from: Date()))  \(line)"
        if Thread.isMainThread {
            add(stamped)
        } else {
            DispatchQueue.main.async { [weak self] in self?.add(stamped) }
        }
    }

    private func add(_ line: String) {
        logLines.append(line)
        if logLines.count > Self.logLimit {
            logLines.removeFirst(logLines.count - Self.logLimit)
        }
    }
}

/// Kotlin calls these on the main thread. The sink is a separate object so Kotlin's strong reference
/// to it does not keep the controller alive.
final class EngineSink: NSObject, EngineListener {
    weak var owner: StrapController?

    func onState(state: String, detail: String?) {
        owner?.engineState(state: state, detail: detail)
    }

    func onDevice(firmware: String?, batteryPercent: Int32, charging: Bool) {
        owner?.engineDevice(firmware: firmware, batteryPercent: batteryPercent, charging: charging)
    }

    func onHeartRate(bpm: Int32) {
        owner?.engineHeartRate(bpm: bpm)
    }

    func onLog(line: String) {
        owner?.engineLog(line: line)
    }

    func onSync(state: String, detail: String?, lastSyncEpochSeconds: Int64) {
        owner?.engineSync(state: state, detail: detail, lastSyncEpochSeconds: lastSyncEpochSeconds)
    }
}
