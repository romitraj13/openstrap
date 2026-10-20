import CoreBluetooth
import Foundation
import OpenStrapShared

/// CoreBluetooth reports a 16-bit UUID as `2A37` and a 32-bit one as 8 hex digits, but the shared
/// Kotlin code compares lowercase 128-bit strings (`StrapUuids`). This is the one place that expands.
enum BleUuid {
    static func fullLength(_ uuid: CBUUID) -> String {
        let short = uuid.uuidString.lowercased()
        switch short.count {
        case 4:
            return "0000\(short)-0000-1000-8000-00805f9b34fb"
        case 8:
            return "\(short)-0000-1000-8000-00805f9b34fb"
        default:
            return short
        }
    }
}

extension Data {
    var kotlinByteArray: KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return array
    }
}

extension KotlinByteArray {
    var data: Data {
        var out = Data(capacity: Int(size))
        for index in 0..<Int(size) {
            out.append(UInt8(bitPattern: get(index: Int32(index))))
        }
        return out
    }
}

/// CoreBluetooth behind the shared `BleDriver` interface.
///
/// One serial queue owns CoreBluetooth and every driver call, so the delegate callbacks and the
/// Kotlin-side requests never interleave. Each request id is answered exactly once: writes with a
/// response in `didWriteValueFor`, writes without one once `canSendWriteWithoutResponse` allows the
/// next, notification changes in `didUpdateNotificationStateFor`, reads in `didUpdateValueFor`.
final class BleDriverImpl: NSObject, BleDriver, CBCentralManagerDelegate, CBPeripheralDelegate {
    static let restoreIdentifier = "org.openstrap.central"
    static let namePrefixes = ["Amazfit Helio Strap", "Stuttgart"]
    private static let mainService = CBUUID(string: "0000fee0-0000-1000-8000-00805f9b34fb")

    /// Kotlin's side of the boundary. Set before `start`.
    var bridge: BleBridge?
    /// Discovery finished: the peripheral, and every characteristic UUID in lowercase 128-bit form.
    var onDiscovered: ((UUID, [String]) -> Void)?
    /// The peripheral went away. The driver reconnects by itself.
    var onDisconnect: ((String) -> Void)?
    /// One Diagnostics line. Never carries the auth key.
    var onLog: ((String) -> Void)?

    private let queue = DispatchQueue(label: "org.openstrap.ble")
    private var central: CBCentralManager?
    private var peripheral: CBPeripheral?
    private var savedIdentifier: UUID?
    private var shouldReconnect = true

    private var characteristics: [String: CBCharacteristic] = [:]
    private var servicesRemaining = 0
    private var discovered: [String] = []

    private var pendingReads: [String: Int64] = [:]
    private var pendingNotificationChanges: [String: [Int64]] = [:]
    private var pendingWritesWithResponse: [String: [Int64]] = [:]
    private var pendingWritesWithoutResponse: [Int64] = []

    /// Starts CoreBluetooth. Called when there is a key to use, so the permission prompt appears then.
    func start(savedIdentifier: UUID?) {
        queue.async {
            self.savedIdentifier = savedIdentifier
            self.shouldReconnect = true
            if self.central != nil {
                if self.peripheral == nil { self.findStrap() }
                return
            }
            self.log("Bluetooth starting")
            self.central = CBCentralManager(
                delegate: self,
                queue: self.queue,
                options: [CBCentralManagerOptionRestoreIdentifierKey: BleDriverImpl.restoreIdentifier]
            )
        }
    }

    /// Forgets the strap: disconnects, stops scanning and stops reconnecting.
    func stop() {
        queue.async {
            self.shouldReconnect = false
            self.peripheral?.delegate = nil
            if let peripheral = self.peripheral {
                self.central?.cancelPeripheralConnection(peripheral)
            }
            self.peripheral = nil
            self.characteristics = [:]
            self.discovered = []
            self.central?.stopScan()
        }
    }

    // MARK: - Finding the strap

    private static func matches(_ name: String?) -> Bool {
        guard let name else { return false }
        return namePrefixes.contains { name.hasPrefix($0) }
    }

    private func findStrap() {
        guard let central, central.state == .poweredOn, peripheral == nil else { return }
        // Card C3: the strap that another app or a previous run left connected comes back first.
        let alreadyConnected = central.retrieveConnectedPeripherals(withServices: [BleDriverImpl.mainService])
        if let strap = alreadyConnected.first(where: { BleDriverImpl.matches($0.name) }) {
            log("found the strap among the already connected peripherals")
            adopt(strap)
            return
        }
        // Next time, reconnect to the one we paired, by identifier.
        if let savedIdentifier, let strap = central.retrievePeripherals(withIdentifiers: [savedIdentifier]).first {
            log("reconnecting to the strap paired before")
            adopt(strap)
            return
        }
        guard !central.isScanning else { return }
        log("scanning for the strap")
        central.scanForPeripherals(withServices: nil, options: nil)
    }

    private func adopt(_ strap: CBPeripheral) {
        takeOver(strap)
        log("connecting")
        central?.connect(strap, options: nil)
    }

    /// This object becomes the strap's delegate with no cached characteristic map, so services are
    /// discovered again on this link rather than trusted from an earlier one.
    private func takeOver(_ strap: CBPeripheral) {
        peripheral = strap
        characteristics = [:]
        discovered = []
        servicesRemaining = 0
        strap.delegate = self
    }

    // MARK: - BleDriver, called from Kotlin on any thread

    func write(requestId: Int64, characteristic: String, data bytes: KotlinByteArray, withResponse: Bool) {
        let payload = bytes.data
        queue.async {
            guard let device = self.peripheral, let found = self.characteristics[characteristic] else {
                self.bridge?.onFailed(requestId: requestId, message: "The strap does not offer this characteristic")
                return
            }
            if withResponse {
                self.pendingWritesWithResponse[BleUuid.fullLength(found.uuid), default: []].append(requestId)
                device.writeValue(payload, for: found, type: .withResponse)
            } else {
                device.writeValue(payload, for: found, type: .withoutResponse)
                self.completeWriteWithoutResponse(requestId)
            }
        }
    }

    func read(requestId: Int64, characteristic: String) {
        queue.async {
            guard let peripheral = self.peripheral, let characteristic = self.characteristics[characteristic] else {
                self.bridge?.onFailed(requestId: requestId, message: "The strap does not offer this characteristic")
                return
            }
            self.pendingReads[BleUuid.fullLength(characteristic.uuid)] = requestId
            peripheral.readValue(for: characteristic)
        }
    }

    func setNotify(requestId: Int64, characteristic: String, enabled: Bool) {
        queue.async {
            guard let peripheral = self.peripheral, let characteristic = self.characteristics[characteristic] else {
                self.bridge?.onFailed(requestId: requestId, message: "The strap does not offer this characteristic")
                return
            }
            self.pendingNotificationChanges[BleUuid.fullLength(characteristic.uuid), default: []].append(requestId)
            peripheral.setNotifyValue(enabled, for: characteristic)
        }
    }

    private func completeWriteWithoutResponse(_ requestId: Int64) {
        guard let peripheral else {
            bridge?.onFailed(requestId: requestId, message: "The strap disconnected")
            return
        }
        if peripheral.canSendWriteWithoutResponse {
            bridge?.onComplete(requestId: requestId)
        } else {
            pendingWritesWithoutResponse.append(requestId)
        }
    }

    // MARK: - CBCentralManagerDelegate

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            findStrap()
        case .poweredOff:
            log("Bluetooth is off")
        case .unauthorized:
            log("Bluetooth permission is denied for OpenStrap")
        case .unsupported:
            log("This device does not support Bluetooth")
        default:
            break
        }
    }

    /// Called by iOS when it launches the app for a strap that is still connected (H1: without this
    /// method CoreBluetooth throws `NSInternalInconsistencyException` at the first Connect, because
    /// the central manager was created with a restore identifier).
    ///
    /// The restored peripherals are put back under this driver — delegate, empty characteristic map —
    /// and a link that is already up is rediscovered, so `onDiscovered` reaches the engine and it
    /// authenticates and syncs again without the user opening anything.
    ///
    /// Delivered on the queue the central manager was created with, so this runs on the driver's own
    /// queue; `start(savedIdentifier:)` has already set the reconnect intent by then.
    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        let restored = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] ?? []
        guard let strap = restored.first(where: { BleDriverImpl.matches($0.name) }) ?? restored.first else {
            log("Bluetooth state restored with no strap")
            return
        }
        log("Bluetooth state restored: \(strap.name ?? "the strap")")
        shouldReconnect = true
        takeOver(strap)
        if strap.state == .connected {
            log("the strap came back connected, discovering services")
            strap.discoverServices(nil)
        } else {
            log("the strap came back disconnected, connecting")
            central.connect(strap, options: nil)
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard BleDriverImpl.matches(peripheral.name) else { return }
        central.stopScan()
        log("found \(peripheral.name ?? "the strap")")
        adopt(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        log("connected, discovering services")
        peripheral.discoverServices(nil)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        log("could not connect: \(error?.localizedDescription ?? "unknown error")")
        self.peripheral = nil
        guard shouldReconnect else { return }
        // Do not hammer the radio while the strap is off: try again in a few seconds.
        queue.asyncAfter(deadline: .now() + 5) { [weak self] in self?.findStrap() }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        onDisconnect?(error?.localizedDescription ?? "the strap disconnected")
        characteristics = [:]
        discovered = []
        guard shouldReconnect else { return }
        // No timeout: keep trying, as the card asks.
        log("reconnecting")
        central.connect(peripheral, options: nil)
    }

    // MARK: - CBPeripheralDelegate

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            log("service discovery failed: \(error.localizedDescription)")
            return
        }
        let services = peripheral.services ?? []
        guard !services.isEmpty else {
            log("the strap offered no services")
            return
        }
        servicesRemaining = services.count
        discovered = []
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            log("characteristic discovery failed: \(error.localizedDescription)")
        }
        for characteristic in service.characteristics ?? [] {
            let uuid = BleUuid.fullLength(characteristic.uuid)
            characteristics[uuid] = characteristic
            if !discovered.contains(uuid) { discovered.append(uuid) }
        }
        servicesRemaining -= 1
        guard servicesRemaining <= 0 else { return }
        finishDiscovery(peripheral)
    }

    private func finishDiscovery(_ peripheral: CBPeripheral) {
        let uuids = discovered
        log("discovered \(uuids.count) characteristics")
        onDiscovered?(peripheral.identifier, uuids)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let uuid = BleUuid.fullLength(characteristic.uuid)
        if let requestId = pendingReads.removeValue(forKey: uuid) {
            if let error {
                bridge?.onFailed(requestId: requestId, message: error.localizedDescription)
            } else {
                bridge?.onRead(requestId: requestId, value: (characteristic.value ?? Data()).kotlinByteArray)
            }
            return
        }
        if let error {
            log("a reading failed on \(uuid): \(error.localizedDescription)")
            return
        }
        _ = bridge?.onNotification(characteristic: uuid, value: (characteristic.value ?? Data()).kotlinByteArray)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let uuid = BleUuid.fullLength(characteristic.uuid)
        guard var waiting = pendingWritesWithResponse[uuid], !waiting.isEmpty else { return }
        let requestId = waiting.removeFirst()
        pendingWritesWithResponse[uuid] = waiting
        if let error {
            bridge?.onFailed(requestId: requestId, message: error.localizedDescription)
        } else {
            bridge?.onComplete(requestId: requestId)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        let uuid = BleUuid.fullLength(characteristic.uuid)
        let waiting = pendingNotificationChanges.removeValue(forKey: uuid) ?? []
        for requestId in waiting {
            if let error {
                bridge?.onFailed(requestId: requestId, message: error.localizedDescription)
            } else {
                bridge?.onComplete(requestId: requestId)
            }
        }
    }

    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        let waiting = pendingWritesWithoutResponse
        pendingWritesWithoutResponse = []
        for requestId in waiting {
            bridge?.onComplete(requestId: requestId)
        }
    }

    // MARK: - Diagnostics

    private func log(_ line: String) {
        onLog?(line)
    }
}
