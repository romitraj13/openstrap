import XCTest
import HealthKit
import OpenStrapShared
@testable import OpenStrap

/// Card C7: every sample type is built through the mapping functions and checked here — type, unit,
/// value, dates and metadata — without saving anything to HealthKit, so this runs on the simulator
/// with no permission sheet and no user data.
final class HealthExportTests: XCTestCase {
    /// 2026-09-01T00:00:00Z, the epoch the fixtures use.
    private let t0: Int64 = 1_788_220_800

    private func row(_ type: String, start: Int64? = nil, end: Int64? = nil, value: Int32) -> ExportRow {
        ExportRow(type: type, start: start ?? t0, end: end ?? (start ?? t0), value: value)
    }

    private func sample(_ row: ExportRow, file: StaticString = #filePath, line: UInt = #line) throws -> HKSample {
        try XCTUnwrap(HealthSampleMapper.sample(for: row), "no sample for \(row.type)", file: file, line: line)
    }

    // MARK: - The types

    /// The Swift enum and the shared Kotlin list have to name the same five types: the queue's rows
    /// carry the Kotlin name, and the sync identifier is built from it.
    func testTheFiveTypesMatchTheSharedKotlinNames() {
        XCTAssertEqual(HealthExportType.all.map(\.rawValue), HealthExportTypes.shared.all)
        XCTAssertEqual(HealthExportType.all.count, 5)
    }

    /// Card C7 step 3: HRV (the strap reports RMSSD, Apple Health has SDNN), skin temperature and
    /// stress are not exported, so they are not among the types the app asks to write.
    func testOnlyTheFiveExportTypesAreRequestedFromAppleHealth() {
        let identifiers = Set(HealthKitWriter.shareTypes.map(\.identifier))
        XCTAssertEqual(
            identifiers,
            Set([
                HKQuantityTypeIdentifier.heartRate.rawValue,
                HKQuantityTypeIdentifier.restingHeartRate.rawValue,
                HKQuantityTypeIdentifier.oxygenSaturation.rawValue,
                HKQuantityTypeIdentifier.stepCount.rawValue,
                HKCategoryTypeIdentifier.sleepAnalysis.rawValue,
            ])
        )
        XCTAssertFalse(identifiers.contains(HKQuantityTypeIdentifier.heartRateVariabilitySDNN.rawValue))
        XCTAssertFalse(identifiers.contains(HKQuantityTypeIdentifier.basalBodyTemperature.rawValue))
    }

    // MARK: - Heart rate

    func testHeartRateIsACountPerMinuteSampleAtItsOwnInstant() throws {
        let sample = try sample(row("hr", value: 62))
        let quantity = try XCTUnwrap(sample as? HKQuantitySample)

        XCTAssertEqual(quantity.quantityType, HKObjectType.quantityType(forIdentifier: .heartRate))
        XCTAssertEqual(quantity.quantity.doubleValue(for: HKUnit.count().unitDivided(by: .minute())), 62, accuracy: 0.001)
        // A reading covers no time: HealthKit keeps it as a point sample.
        XCTAssertEqual(quantity.startDate, Date(timeIntervalSince1970: TimeInterval(t0)))
        XCTAssertEqual(quantity.endDate, quantity.startDate)
    }

    func testRestingHeartRateIsACountPerMinuteSampleOnItsDay() throws {
        let day: Int64 = 1_787_875_200 // local midnight in the fixture
        let sample = try sample(row("rhr", start: day, end: day, value: 52))
        let quantity = try XCTUnwrap(sample as? HKQuantitySample)

        XCTAssertEqual(quantity.quantityType, HKObjectType.quantityType(forIdentifier: .restingHeartRate))
        XCTAssertEqual(quantity.quantity.doubleValue(for: HealthSampleMapper.beatsPerMinuteUnit), 52, accuracy: 0.001)
        XCTAssertEqual(quantity.startDate, Date(timeIntervalSince1970: TimeInterval(day)))
        XCTAssertEqual(quantity.endDate, quantity.startDate)
    }

    // MARK: - Blood oxygen

    /// Apple Health stores SpO2 as a fraction: the queue's 97 % has to become 0.97 of the percent unit.
    func testOxygenSaturationIsTheFractionOfThePercent() throws {
        let sample = try sample(row("spo2", value: 97))
        let quantity = try XCTUnwrap(sample as? HKQuantitySample)

        XCTAssertEqual(quantity.quantityType, HKObjectType.quantityType(forIdentifier: .oxygenSaturation))
        XCTAssertEqual(quantity.quantity.doubleValue(for: HKUnit.percent()), 0.97, accuracy: 0.0001)
        // The Health app prints it as 97 %, not 0 %.
        XCTAssertEqual(quantity.quantity.doubleValue(for: HealthSampleMapper.percentUnit) * 100, 97, accuracy: 0.001)
        XCTAssertEqual(quantity.startDate, Date(timeIntervalSince1970: TimeInterval(t0)))
    }

    // MARK: - Steps

    /// The queue's step row is one activity minute; the sample has to keep both ends of it.
    func testStepsSampleCoversItsMinute() throws {
        let sample = try sample(row("steps", start: t0, end: t0 + 60, value: 12))
        let quantity = try XCTUnwrap(sample as? HKQuantitySample)

        XCTAssertEqual(quantity.quantityType, HKObjectType.quantityType(forIdentifier: .stepCount))
        XCTAssertEqual(quantity.quantity.doubleValue(for: HKUnit.count()), 12, accuracy: 0.001)
        XCTAssertEqual(quantity.startDate, Date(timeIntervalSince1970: TimeInterval(t0)))
        XCTAssertEqual(quantity.endDate, Date(timeIntervalSince1970: TimeInterval(t0 + 60)))
    }

    // MARK: - Sleep

    /// LIGHT to asleepCore, DEEP to asleepDeep, REM to asleepREM, AWAKE to awake
    /// (`SleepStage`'s numbers: AWAKE 0, LIGHT 1, DEEP 2, REM 3).
    func testSleepStagesMapToTheirFourCategories() throws {
        let expected: [(Int32, HKCategoryValueSleepAnalysis)] = [
            (0, .awake),
            (1, .asleepCore),
            (2, .asleepDeep),
            (3, .asleepREM),
        ]
        for (stage, category) in expected {
            let sample = try sample(row("sleep", start: t0, end: t0 + 1800, value: stage))
            let categorySample = try XCTUnwrap(sample as? HKCategorySample, "stage \(stage)")
            XCTAssertEqual(categorySample.categoryType, HKObjectType.categoryType(forIdentifier: .sleepAnalysis))
            XCTAssertEqual(categorySample.value, category.rawValue, "stage \(stage)")
            XCTAssertEqual(categorySample.startDate, Date(timeIntervalSince1970: TimeInterval(t0)))
            XCTAssertEqual(categorySample.endDate, Date(timeIntervalSince1970: TimeInterval(t0 + 1800)))
        }
    }

    /// Card C7 step 2: "others skipped". A stage HealthKit has no category for is not written.
    func testAnUnknownSleepStageHasNoSample() {
        XCTAssertNil(HealthSampleMapper.sample(for: row("sleep", start: t0, end: t0 + 1800, value: 9)))
        XCTAssertNil(HealthSampleMapper.samples(for: [row("sleep", start: t0, end: t0 + 1800, value: 9)]).first)
    }

    // MARK: - The metadata and the device

    /// `openstrap.<type>.<epochSeconds>`, version 1: what keeps a re-sent row one sample instead of
    /// two in Apple Health.
    func testEveryTypeCarriesItsSyncIdentifierAndVersion() throws {
        for type in HealthExportType.all {
            let sample = try sample(row(type.rawValue, start: t0, end: t0 + 60, value: 1))
            let metadata = try XCTUnwrap(sample.metadata)
            XCTAssertEqual(
                metadata[HKMetadataKeySyncIdentifier] as? String,
                "openstrap.\(type.rawValue).\(t0)",
                "\(type.rawValue) sync identifier"
            )
            XCTAssertEqual((metadata[HKMetadataKeySyncVersion] as? Int) ?? -1, 1, "\(type.rawValue) sync version")
        }
    }

    /// Apple Health lists the data source as the strap, so the samples say which device they came from.
    func testEverySampleNamesTheStrapAsItsDevice() throws {
        for type in HealthExportType.all {
            let sample = try sample(row(type.rawValue, start: t0, end: t0 + 60, value: 1))
            let device = try XCTUnwrap(sample.device, "\(type.rawValue) device")
            XCTAssertEqual(device.name, "Helio Strap", "\(type.rawValue) name")
            XCTAssertEqual(device.manufacturer, "Amazfit", "\(type.rawValue) manufacturer")
        }
    }

    // MARK: - The switch

    /// Card C7 step 4: "Write to Apple Health" is off until the user turns it on.
    func testTheAppleHealthSwitchIsOffUntilItIsTurnedOn() throws {
        let name = "openstrap-health-export-tests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }

        XCTAssertFalse(HealthExportSettings.isEnabled(defaults))
        HealthExportSettings.setEnabled(true, defaults)
        XCTAssertTrue(HealthExportSettings.isEnabled(defaults))
        HealthExportSettings.setEnabled(false, defaults)
        XCTAssertFalse(HealthExportSettings.isEnabled(defaults))
    }
}
