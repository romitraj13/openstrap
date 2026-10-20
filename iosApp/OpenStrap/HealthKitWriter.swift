import Foundation
import HealthKit
import OpenStrapShared

/// The five Apple Health types OpenStrap writes, keyed by the names the shared Kotlin queue uses in
/// `HealthExportTypes`. HRV is deliberately absent: the strap reports RMSSD and Apple Health only
/// has an SDNN type, so writing it here would put one measure under the other's label. There is no
/// honest Apple Health type for skin temperature or stress either (plan section 6).
enum HealthExportType: String, CaseIterable {
    case heartRate = "hr"
    case restingHeartRate = "rhr"
    case oxygenSaturation = "spo2"
    case steps = "steps"
    case sleep = "sleep"

    /// The types in the order the coordinator exports them. The shared Kotlin list is the single
    /// source of truth; the XCTest checks these raw values against it.
    static var all: [HealthExportType] {
        HealthExportTypes.shared.all.compactMap(HealthExportType.init(rawValue:))
    }

    /// The HealthKit type a row of this kind becomes.
    var sampleType: HKSampleType {
        switch self {
        case .heartRate: return HKObjectType.quantityType(forIdentifier: .heartRate)!
        case .restingHeartRate: return HKObjectType.quantityType(forIdentifier: .restingHeartRate)!
        case .oxygenSaturation: return HKObjectType.quantityType(forIdentifier: .oxygenSaturation)!
        case .steps: return HKObjectType.quantityType(forIdentifier: .stepCount)!
        case .sleep: return HKObjectType.categoryType(forIdentifier: .sleepAnalysis)!
        }
    }
}

/// Builds the samples the queue's rows become, and never saves one: the XCTests call these and check
/// type, unit, value, dates and metadata without touching the HealthKit store.
///
/// Every sample carries `HKMetadataKeySyncIdentifier` = `openstrap.<type>.<epochSeconds>` and
/// `HKMetadataKeySyncVersion` = 1, which is what makes a re-sent row an update in Apple Health
/// rather than a second copy, and the strap as its device.
enum HealthSampleMapper {
    /// What Apple Health lists as the data source: the strap, not the phone.
    static let device = HKDevice(
        name: "Helio Strap",
        manufacturer: "Amazfit",
        model: nil,
        hardwareVersion: nil,
        firmwareVersion: nil,
        softwareVersion: nil,
        localIdentifier: nil,
        udiDeviceIdentifier: nil
    )

    static let syncVersion = 1

    /// HealthKit's percent unit is a fraction: 0.97 is 97 %.
    static let percentUnit = HKUnit.percent()
    static let countUnit = HKUnit.count()
    static let beatsPerMinuteUnit = HKUnit.count().unitDivided(by: HKUnit.minute())

    static func syncIdentifier(type: HealthExportType, epochSeconds: Int64) -> String {
        "openstrap.\(type.rawValue).\(epochSeconds)"
    }

    static func metadata(type: HealthExportType, epochSeconds: Int64) -> [String: Any] {
        [
            HKMetadataKeySyncIdentifier: syncIdentifier(type: type, epochSeconds: epochSeconds),
            HKMetadataKeySyncVersion: syncVersion,
        ]
    }

    /// The four stages HealthKit has a category for, in `SleepStage`'s numbers
    /// (`app/src/main/kotlin/org/openstrap/app/data/db/Entities.kt` @ 19f2865: AWAKE 0, LIGHT 1,
    /// DEEP 2, REM 3). Anything else is not a stage this app can write.
    static func sleepCategory(_ stage: Int32) -> HKCategoryValueSleepAnalysis? {
        switch stage {
        case 0: return .awake
        case 1: return .asleepCore
        case 2: return .asleepDeep
        case 3: return .asleepREM
        default: return nil
        }
    }

    /// One sample for one row, or nil for a row this app cannot write.
    static func sample(for row: ExportRow) -> HKSample? {
        guard let type = HealthExportType(rawValue: row.type) else { return nil }
        let start = Date(timeIntervalSince1970: TimeInterval(row.start))
        let end = Date(timeIntervalSince1970: TimeInterval(row.end))
        let sampleMetadata = metadata(type: type, epochSeconds: row.start)

        switch type {
        case .heartRate:
            return quantitySample(.heartRate, unit: beatsPerMinuteUnit, value: Double(row.value), start: start, end: end, metadata: sampleMetadata)
        case .restingHeartRate:
            return quantitySample(.restingHeartRate, unit: beatsPerMinuteUnit, value: Double(row.value), start: start, end: end, metadata: sampleMetadata)
        case .oxygenSaturation:
            // The queue carries the percentage the strap measured; Apple Health wants the fraction.
            return quantitySample(.oxygenSaturation, unit: percentUnit, value: Double(row.value) / 100, start: start, end: end, metadata: sampleMetadata)
        case .steps:
            return quantitySample(.stepCount, unit: countUnit, value: Double(row.value), start: start, end: end, metadata: sampleMetadata)
        case .sleep:
            guard let category = sleepCategory(row.value),
                  let sleepType = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) else { return nil }
            return HKCategorySample(
                type: sleepType,
                value: category.rawValue,
                start: start,
                end: end,
                device: device,
                metadata: sampleMetadata
            )
        }
    }

    /// Every row that can be written, in the order it arrived.
    static func samples(for rows: [ExportRow]) -> [HKSample] {
        rows.compactMap { sample(for: $0) }
    }

    private static func quantitySample(
        _ identifier: HKQuantityTypeIdentifier,
        unit: HKUnit,
        value: Double,
        start: Date,
        end: Date,
        metadata: [String: Any]
    ) -> HKQuantitySample? {
        guard let type = HKObjectType.quantityType(forIdentifier: identifier) else { return nil }
        return HKQuantitySample(
            type: type,
            quantity: HKQuantity(unit: unit, doubleValue: value),
            start: start,
            end: end,
            device: device,
            metadata: metadata
        )
    }
}

/// The Strap screen's "Write to Apple Health" switch: `UserDefaults`, beside the strap identifier,
/// and **off** until it is turned on (the key is absent on a fresh install).
enum HealthExportSettings {
    static let enabledKey = "openstrap.writeToAppleHealth"

    static func isEnabled(_ defaults: UserDefaults = .standard) -> Bool {
        defaults.object(forKey: enabledKey) as? Bool ?? false
    }

    static func setEnabled(_ enabled: Bool, _ defaults: UserDefaults = .standard) {
        defaults.set(enabled, forKey: enabledKey)
    }
}

/// Asks for permission and saves: the only part of the export that talks to HealthKit.
final class HealthKitWriter {
    /// Write-only authorisation for the five types. The read set stays empty: this app never reads
    /// Apple Health (card C7, out of scope).
    static var shareTypes: Set<HKSampleType> {
        Set(HealthExportType.all.map { $0.sampleType })
    }

    private let store: HKHealthStore

    init(store: HKHealthStore = HKHealthStore()) {
        self.store = store
    }

    static var isAvailable: Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    /// The system sheet, for the five write types only. The answer arrives on the main thread.
    func requestAuthorization(onResult: @escaping (Bool, String?) -> Void) {
        guard Self.isAvailable else {
            DispatchQueue.main.async { onResult(false, "Apple Health is not available on this device") }
            return
        }
        store.requestAuthorization(toShare: Self.shareTypes, read: Set<HKObjectType>()) { answered, error in
            DispatchQueue.main.async { onResult(answered, error?.localizedDescription) }
        }
    }

    /// Saves one batch. HealthKit applies it as one transaction, so either every sample in the batch
    /// is in Apple Health or none is and the caller must not move its cursor. The answer arrives on
    /// the main thread.
    func save(_ samples: [HKSample], onResult: @escaping (Error?) -> Void) {
        guard !samples.isEmpty else {
            DispatchQueue.main.async { onResult(nil) }
            return
        }
        store.save(samples) { _, error in
            DispatchQueue.main.async { onResult(error) }
        }
    }
}

/// Card C7: after each successful history sync, while the switch is on, hand Apple Health the rows
/// the shared queue says are new and move the cursor once HealthKit has them.
///
/// One type at a time, one batch of `batchLimit` at a time, oldest first, so a first export after a
/// long history does not ask HealthKit for one enormous save. A type that fails is logged and
/// skipped; its cursor stays put, so the next sync tries it again.
final class HealthExportCoordinator {
    /// Rows per HealthKit save (a busy month is tens of thousands of step minutes).
    static let batchLimit = 500

    /// A Diagnostics line from a run; the controller sets it in `init`.
    var onLog: ((String) -> Void)?

    private let queue: HealthExportQueue
    private let writer: HealthKitWriter
    private let defaults: UserDefaults
    private var running = false

    init(databasePath: String, defaults: UserDefaults = .standard, writer: HealthKitWriter = HealthKitWriter()) {
        self.queue = HealthExportQueue(databasePath: databasePath)
        self.defaults = defaults
        self.writer = writer
    }

    var isEnabled: Bool {
        HealthExportSettings.isEnabled(defaults)
    }

    /// Stores the switch, and on turning it on asks for permission. [onResult] is the line the Strap
    /// screen shows under the toggle.
    func setEnabled(_ enabled: Bool, onResult: ((String) -> Void)? = nil) {
        HealthExportSettings.setEnabled(enabled, defaults)
        guard enabled else {
            onResult?("Off. Nothing more is written to Apple Health.")
            return
        }
        guard HealthKitWriter.isAvailable else {
            let line = "Apple Health is not available on this device."
            onLog?(line)
            onResult?(line)
            return
        }
        writer.requestAuthorization { [weak self] answered, error in
            var line = answered
                ? "Asked Apple Health for the five write permissions; data from the next sync is written."
                : "Apple Health did not confirm the permissions."
            if let error { line += " (\(error))" }
            self?.onLog?(line)
            onResult?(line)
        }
    }

    /// One pass over the five types. [onDone] gets a Diagnostics line when the pass finishes, and is
    /// not called at all when a pass was already running (that pass calls its own).
    func run(onDone: ((String) -> Void)? = nil) {
        guard isEnabled else { return }
        guard !running else { return }
        running = true
        export(typeIndex: 0, types: HealthExportTypes.shared.all, written: 0, failures: [], onDone: onDone)
    }

    private func export(
        typeIndex: Int,
        types: [String],
        written: Int,
        failures: [String],
        onDone: ((String) -> Void)?
    ) {
        guard typeIndex < types.count else {
            running = false
            var line = "Apple Health: \(written) samples written"
            if !failures.isEmpty {
                line += ", not written: \(failures.joined(separator: ", "))"
            }
            onLog?(line)
            onDone?(line)
            return
        }

        let name = types[typeIndex]
        queue.pending(type: name, limit: Int32(Self.batchLimit)) { [weak self] batch in
            guard let self else { return }
            guard !batch.rows.isEmpty else {
                self.export(typeIndex: typeIndex + 1, types: types, written: written, failures: failures, onDone: onDone)
                return
            }

            let samples = HealthSampleMapper.samples(for: batch.rows)
            self.writer.save(samples) { [weak self] error in
                guard let self else { return }
                if let error {
                    // The cursor does not move: the next sync hands the same rows over again, and
                    // the sync identifier turns that into one sample, not two.
                    self.onLog?("Apple Health: \(name) failed (\(error.localizedDescription)); those rows stay queued")
                    self.export(
                        typeIndex: typeIndex + 1,
                        types: types,
                        written: written,
                        failures: failures + [name],
                        onDone: onDone
                    )
                    return
                }
                self.queue.markExported(type: name, upToEpoch: batch.upTo) { [weak self] in
                    guard let self else { return }
                    let total = written + samples.count
                    if batch.rows.count >= Self.batchLimit {
                        self.export(typeIndex: typeIndex, types: types, written: total, failures: failures, onDone: onDone)
                    } else {
                        self.export(typeIndex: typeIndex + 1, types: types, written: total, failures: failures, onDone: onDone)
                    }
                }
            }
        }
    }
}
