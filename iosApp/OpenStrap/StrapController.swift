import Combine
import CoreBluetooth
import Foundation
import OpenStrapShared

/// Owns the strap for the whole app: the Keychain key, the CoreBluetooth driver, the shared
/// `StrapEngine`, and everything the screens show.
final class StrapController: ObservableObject {
    static let peripheralIdentifierKey = "openstrap.strapPeripheralIdentifier"
    static let invalidKeyMessage = "That isn't a valid auth key: it should be 32 hex characters"
    private static let logLimit = 2000

    @Published private(set) var state = "disconnected"
    @Published private(set) var stateDetail: String?
    @Published private(set) var firmware: String?
    @Published private(set) var battery = -1
    @Published private(set) var charging = false
    @Published private(set) var heartRate: Int?
    @Published private(set) var liveHeartRateEnabled = false
    @Published private(set) var logLines: [String] = []
    @Published private(set) var hasStoredKey: Bool

    private let secrets: KeychainSecretStore
    private let defaults: UserDefaults
    private let driver: BleDriverImpl
    private let engine: StrapEngine
    private let sink: EngineSink
    private var peripheralIdentifier: UUID?
    private var discoveredCharacteristics: [String] = []

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
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
        self.engine = StrapEngine(driver: driver, listener: sink)
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
        if hasStoredKey {
            driver.start(savedIdentifier: Self.storedIdentifier(defaults))
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

    var logText: String {
        logLines.joined(separator: "\n")
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

    // MARK: - Plumbing

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
}
