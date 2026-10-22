# Upstream sources copied into `shared/`

Format: `source path @ 19f2865 -> destination path`

C2 did not copy anything from `app/`. `shared/src/commonMain/kotlin/org/openstrap/shared/ble/BleBridge.kt`
and its test are new code written for the iOS plan (the Swift/Kotlin BLE boundary) and were taken
verbatim from the plan pack, not from the Android app.

| Source | Destination |
|---|---|
| `/opt/data/.hermes/plans/2026-10-19_061836-openstrap-ios/BleBridge.kt @ 19f2865` | `shared/src/commonMain/kotlin/org/openstrap/shared/ble/BleBridge.kt` |
| `/opt/data/.hermes/plans/2026-10-19_061836-openstrap-ios/BleBridgeTest.kt @ 19f2865` | `shared/src/commonTest/kotlin/org/openstrap/shared/ble/BleBridgeTest.kt` |

As the required `source path @ 19f2865 -> destination path` lines:

```
/opt/data/.hermes/plans/2026-10-19_061836-openstrap-ios/BleBridge.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/shared/ble/BleBridge.kt
/opt/data/.hermes/plans/2026-10-19_061836-openstrap-ios/BleBridgeTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/shared/ble/BleBridgeTest.kt
```

Both copies are byte-identical to the plan pack. Provenance: neither file has an upstream
counterpart at `19f2865`; `19f2865` is the upstream baseline the plan pack was authored against,
and the plan pack is the copy source. Tests: `BleBridgeTest` has 5 tests, the same 5 the plan
pack carries; `:shared:jvmTest` runs 5, 0 failures.

## Authored for iOS, not copied (no upstream source)

| File | Why it is new |
|---|---|
| `shared/src/commonMain/kotlin/org/openstrap/shared/SharedInfo.kt` | Link-proof function for the Xcode app. |
| `shared/src/iosMain/kotlin/org/openstrap/shared/Bytes.kt` | `ByteArray.toNSData()` / `NSData.toKotlinByteArray()` over `memcpy`; Android uses its own types, nothing to copy. |
| `shared/src/iosTest/kotlin/org/openstrap/shared/BytesTest.kt` | Round-trip test for the above (0, 1, 13, 4096 bytes). |
| `shared/src/commonMain/kotlin/org/openstrap/shared/device/StrapEngine.kt` | `StrapEngine` and `EngineListener`: the iOS-facing strap object. The behaviour is ported from `StrapManager` (rows below), but the class, the state strings and the callback interface exist only on iOS. `Profile` is a settable `UserProfile` because C3 has no settings store yet (C4 adds one). |
| `shared/src/commonTest/kotlin/org/openstrap/shared/device/StrapEngineTest.kt` | The engine tests: ready + battery + firmware + profile, live HR, wrong key, no key in the log, `linkDown`. |
| `shared/src/commonTest/kotlin/org/openstrap/app/ble/BridgeStrapLinkTest.kt` | UUID string form and the 0016/0017 routing. |
| `shared/src/commonTest/kotlin/org/openstrap/app/device/PairingErrorsTest.kt` | The copied status-code mapping. |

## C3 (pair, connect, live heart rate)

```
app/src/main/kotlin/org/openstrap/app/ble/GattStrapLink.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ble/BridgeStrapLink.kt
app/src/main/kotlin/org/openstrap/app/device/PairingErrors.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/device/PairingErrors.kt
app/src/main/kotlin/org/openstrap/app/zepp/ZeppCloud.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/zepp/ZeppCloud.kt
app/src/main/kotlin/org/openstrap/app/device/StrapManager.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/shared/device/StrapEngine.kt
app/src/test/kotlin/org/openstrap/app/zepp/ZeppCloudTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/zepp/ZeppCloudTest.kt
protocol/src/commonTest/kotlin/org/openstrap/protocol/ChannelSessionTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/shared/device/SimulatedStrap.kt
```

What changed in each copy, and why:

- `GattStrapLink.kt` → `BridgeStrapLink.kt`. Same package (`org.openstrap.app.ble`), same logic.
  `java.util.UUID` became lowercase 128-bit strings because `GattPort` is string-keyed and
  CoreBluetooth reports short forms; `GattClient` became `GattPort`; the class is named after the
  shared interface it implements. The `GattDfuLink` half of the file was not copied: firmware
  update is not in any card yet. Test count: Android has no `GattStrapLinkTest`; the 3 tests in
  `BridgeStrapLinkTest` are new (UUID form, write routing, notification split).
- `PairingErrors.kt`. Same package, same status codes, same wording. The return type is the new
  `PairingFailure(message, keyRejected)` instead of Android's `ConnectionState.Failed`, which does
  not exist in `shared/`. Android has no `PairingErrorsTest`; the 4 tests are new and cover the four
  branches.
- `ZeppCloud.kt`. C3 copied only `normalizeKey` and the `object` declaration; the sign-in session,
  parsers and networking stayed on Android until S1. 1 of the 2 tests (`badKeysAreIgnored`) was
  copied from Android's `ZeppCloudTest`, and its three assertions are unchanged; the second test
  (prefixed, colon-separated, upper-case input) was new for C3. **S1 replaced both copies with the
  whole file and the whole test class** — see the S1 section below.
- `StrapManager.kt` → `StrapEngine.kt`. Lines 136-270 (`startConnect` + `afterAuth`) and 513-598
  (`startLive` + `pauseLive` + `stopLive`). Kept: notification enabling on 0016/0017, the channel
  session, the handshake, the payload-MTU query, firmware from DIS 2A28, battery (channel 0x0029,
  else 2A2B's 0006), the time version query, the time set and the next-DST hint, the profile send
  on 0x0017, the 2A37 + 0x0048 live-HR pair with the 10 s keep-alive, and the `05 02` keep-alive
  reply filter. Left out because Android-only: the foreground service, the notification manager,
  the `PowerManager` wake lock, the time-change `BroadcastReceiver`, the settings DataStore, the
  Room DAO, history sync, alarms, steps and body-signal alerts. `ZonedDateTime.now()` became
  `Clock.System.now()` + `TimeZone.currentSystemDefault()` (kotlinx-datetime, as C1 did for
  `protocol/`), and `System.currentTimeMillis()` is gone with the log timestamps (Swift stamps the
  Diagnostics lines instead). Test count: Android has no `StrapManagerTest`; the 6 `StrapEngineTest`
  tests are new.
- `ChannelSessionTest.kt` → `SimulatedStrap.kt` (test sources). The device model is the same class,
  same handshake, same ECDH/cipher mirroring, same ACK handling. Added: replies for the endpoints
  C3's engine uses (payload MTU `02 n u16`, time version/set/DST, user profile, live HR subscribe
  with a first `05 48 00` push, open-app `05 03`), and a services list with 0x0017/0x0047/0x0048/
  0x001D. Added: `FakeStrapDriver`, a `BleDriver` that puts the strap behind a `BleBridge` the way
  CoreBluetooth would. Its 6 engine tests replace nothing on Android and are new.

`:shared:jvmTest` after C3: 20 tests, 0 failures (BridgeStrapLinkTest 3, PairingErrorsTest 4,
ZeppCloudTest 2, BleBridgeTest 5, StrapEngineTest 6).

## S1 (Zepp sign-in as the pairing fallback)

```
app/src/main/kotlin/org/openstrap/app/zepp/ZeppCloud.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/zepp/ZeppCloud.kt
app/src/test/kotlin/org/openstrap/app/zepp/ZeppCloudTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/zepp/ZeppCloudTest.kt
```

What changed, and why:

- `ZeppCloud.kt` is now the file's whole pure half: `ZeppSession`, `BoundDevice`, `DeviceListing`,
  `sessionFromCookies`, `parseDeviceList`, `pickHelioStrap`, `normalizeKey`, `ZeppCloudException`
  and the constants (`SIGN_IN_URL`, `COOKIE_URLS`, `API_HOSTS`, `APP_ID`, `APP_VERSION_NAME`,
  `APP_VERSION_CODE`). Same package, same logic, same wording. `ZeppCloudException` extends
  `Exception` instead of `IOException`, which common code does not have, and `parseDeviceList`
  carries `@Throws(ZeppCloudException::class)` so Swift can catch a malformed reply instead of the
  process aborting on an unhandled Kotlin exception.
- `org.json` became kotlinx-serialization-json (1.12.0, with the `kotlin-serialization` plugin at
  the Kotlin version, 2.4.21). `JSONObject.optString`/`optInt` are reproduced by private
  `JsonObject.text`/`int`/`intOrNull` helpers, including the object-or-JSON-string
  `additionalInfo` and the `productId >= 0` rule. `java.net.URLDecoder` became a private
  `urlDecode` over the UTF-8 bytes, with the same `+`-is-space and `%XX` behaviour and the same
  three-round retry.
- `const val` became `val` for `SIGN_IN_URL`, `COOKIE_URLS`, `API_HOSTS`, `APP_ID` and the version
  strings: Kotlin/Native exposes object properties to Swift as `ZeppCloud.shared.<name>`, which is
  how the Swift request reads them. `APP_ID`, `APP_VERSION_NAME` and `APP_VERSION_CODE` were
  private on Android and are public here because the Swift request sends them as headers.
- `listDevices`/`get` — the HTTP request — were not copied. S1 writes it in Swift at
  `iosApp/OpenStrap/ZeppSignIn/ZeppDeviceListClient.swift`, with the same hosts in the same
  fallback order, the same path, headers and query parameters (only the `device` query value is
  `ios_<major>`, because iOS has no Android SDK level). All parsing and picking stays in the
  shared Kotlin.
- `BoundDevice` used `StrapIdentity` (`app/src/main/kotlin/org/openstrap/app/firmware/FirmwareCloud.kt`
  @ 19f2865, lines 18-30) for `DEVICE_SOURCE_GLOBAL`, `DEVICE_SOURCE_CHINA` and `PRODUCT_ID`.
  Firmware checks are out of S1's scope and that file also does firmware networking, so the three
  constants are inlined in `BoundDevice`'s private companion as the same values.
- Tests: all 10 of Android's `ZeppCloudTest` tests are copied, the same count (C3's 2 are replaced).
  `org.junit.Assert` became `kotlin.test`, `@Test(expected = …)` became `assertFailsWith`, and
  `java.net.URLEncoder` became a test-local percent-encoding helper with the same unreserved set.

`:shared:jvmTest` after S1: 28 tests, 0 failures (BridgeStrapLinkTest 3, PairingErrorsTest 4,
ZeppCloudTest 10, BleBridgeTest 5, StrapEngineTest 6).

## C4 (database and history sync)

Every file C4 copied, as the required `source path @ 19f2865 -> destination path`:

```
app/src/main/kotlin/org/openstrap/app/data/Activity.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Activity.kt
app/src/main/kotlin/org/openstrap/app/data/Backtest.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Backtest.kt
app/src/main/kotlin/org/openstrap/app/data/BodySignals.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/BodySignals.kt
app/src/main/kotlin/org/openstrap/app/data/BodySignalsCopy.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/BodySignalsCopy.kt
app/src/main/kotlin/org/openstrap/app/data/CheckIn.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/CheckIn.kt
app/src/main/kotlin/org/openstrap/app/data/EnergyExpenditure.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/EnergyExpenditure.kt
app/src/main/kotlin/org/openstrap/app/data/HealthRepository.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/HealthRepository.kt
app/src/main/kotlin/org/openstrap/app/data/History.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/History.kt
app/src/main/kotlin/org/openstrap/app/data/LiveSteps.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/LiveSteps.kt
app/src/main/kotlin/org/openstrap/app/data/NightMetrics.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/NightMetrics.kt
app/src/main/kotlin/org/openstrap/app/data/Recovery.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Recovery.kt
app/src/main/kotlin/org/openstrap/app/data/RecoveryEngine.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/RecoveryEngine.kt
app/src/main/kotlin/org/openstrap/app/data/SleepHistory.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/SleepHistory.kt
app/src/main/kotlin/org/openstrap/app/data/SleepRegularity.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/SleepRegularity.kt
app/src/main/kotlin/org/openstrap/app/data/Summaries.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Summaries.kt
app/src/main/kotlin/org/openstrap/app/data/Vo2max.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Vo2max.kt
app/src/main/kotlin/org/openstrap/app/data/Workouts.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Workouts.kt
app/src/main/kotlin/org/openstrap/app/data/db/Entities.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/db/Entities.kt
app/src/main/kotlin/org/openstrap/app/data/db/Daos.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/db/Daos.kt
app/src/main/kotlin/org/openstrap/app/data/settings/Goals.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/settings/Goals.kt
app/src/main/kotlin/org/openstrap/app/data/settings/Units.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/settings/Units.kt
app/src/main/kotlin/org/openstrap/app/data/settings/SettingsStore.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/settings/SettingsRepository.kt
app/src/main/kotlin/org/openstrap/app/device/HistorySync.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/device/HistorySync.kt
app/src/main/kotlin/org/openstrap/app/device/StrapController.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/device/SyncState.kt
app/src/test/kotlin/org/openstrap/app/data/ActivityTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/ActivityTest.kt
app/src/test/kotlin/org/openstrap/app/data/BodySignalsTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/BodySignalsTest.kt
app/src/test/kotlin/org/openstrap/app/data/DayStatTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/DayStatTest.kt
app/src/test/kotlin/org/openstrap/app/data/EnergyExpenditureTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/EnergyExpenditureTest.kt
app/src/test/kotlin/org/openstrap/app/data/HistoryTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/HistoryTest.kt
app/src/test/kotlin/org/openstrap/app/data/LiveStepsTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/LiveStepsTest.kt
app/src/test/kotlin/org/openstrap/app/data/LoadTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/LoadTest.kt
app/src/test/kotlin/org/openstrap/app/data/RecoveryTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/RecoveryTest.kt
app/src/test/kotlin/org/openstrap/app/data/SleepHistoryTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/SleepHistoryTest.kt
app/src/test/kotlin/org/openstrap/app/data/SleepRegularityTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/SleepRegularityTest.kt
app/src/test/kotlin/org/openstrap/app/data/SummariesTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/SummariesTest.kt
app/src/test/kotlin/org/openstrap/app/data/Vo2maxTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/Vo2maxTest.kt
app/src/test/kotlin/org/openstrap/app/data/WorkoutsTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/WorkoutsTest.kt
app/src/test/kotlin/org/openstrap/app/data/settings/UnitsTest.kt @ 19f2865 -> shared/src/commonTest/kotlin/org/openstrap/app/data/settings/UnitsTest.kt
app/src/test/kotlin/org/openstrap/app/data/BacktestTest.kt @ 19f2865 -> shared/src/jvmTest/kotlin/org/openstrap/app/data/BacktestTest.kt
app/src/test/kotlin/org/openstrap/app/data/RecoveryEngineTest.kt @ 19f2865 -> shared/src/jvmTest/kotlin/org/openstrap/app/data/RecoveryEngineTest.kt
```

Test counts, Android → shared (every copied file, same count):

| Test class | Android | shared | Where |
|---|---|---|---|
| ActivityTest | 7 | 7 | commonTest |
| BacktestTest | 2 | 2 | jvmTest |
| BodySignalsTest | 13 | 13 | commonTest |
| DayStatTest | 2 | 2 | commonTest |
| EnergyExpenditureTest | 5 | 5 | commonTest |
| HistoryTest | 6 | 6 | commonTest |
| LiveStepsTest | 4 | 4 | commonTest |
| LoadTest | 7 | 7 | commonTest |
| RecoveryEngineTest | 2 | 2 | jvmTest |
| RecoveryTest | 49 | 49 | commonTest |
| SleepHistoryTest | 7 | 7 | commonTest |
| SleepRegularityTest | 6 | 6 | commonTest |
| SummariesTest (with BucketTest) | 4 + 1 | 4 + 1 | commonTest |
| Vo2maxTest | 8 | 8 | commonTest |
| WorkoutsTest | 4 | 4 | commonTest |
| UnitsTest | 2 | 2 | commonTest |
| **total** | **129** | **129** | |

`BacktestTest` and `RecoveryEngineTest` are in `jvmTest` because they use things only the JVM
has — `java.io.File` and `System.getProperty` for the on-demand replay, and
`java.lang.reflect.Proxy` for a DAO that answers two reads. Their test bodies are otherwise the
Android ones: JUnit 4 is still the runner there, so `@Test` and `org.junit.Assert` needed no
conversion at all.

What changed in each copy, and why:

- **Time.** `java.time` became kotlinx-datetime and `kotlin.time.Instant`. `shared/.../data/Dates.kt`
  holds small internal extensions with the `java.time` names the copies use
  (`plusDays`, `minusDays`, `atStartOfDay(zone)`, `atTime(h, m).atZone(zone).toEpochSecond()`,
  `Instant.fromEpochSeconds(x).atZone(zone).toLocalDate()`, `toEpochDay()`, `mondayOfWeek()`), so the
  copied lines read as they did on Android. `System.currentTimeMillis() / 1000` became
  `Clock.System.now().epochSeconds`, `LocalDate.now()` became
  `Clock.System.todayIn(TimeZone.currentSystemDefault())`, `ZoneId.systemDefault()` became
  `TimeZone.currentSystemDefault()`, `ZoneOffset.UTC` became `TimeZone.UTC`,
  `TemporalAdjusters.previousOrSame(MONDAY)` became `mondayOfWeek()`.
- **`String.format`.** Common Kotlin has none, and `Recovery`, `BodySignals`, `SleepRegularity`,
  `Goals` and `Backtest` all use `"%.1f".format(...)`, `"%02d"`, `"%,d"` and `"%s"`.
  `shared/.../data/Format.kt` is a small `fun String.format(vararg args)` that reproduces those
  specs with `Locale.ROOT` behaviour (ASCII digits, `.` as the separator, half away from zero).
  `Backtest`'s `"%.1f".format(java.util.Locale.ROOT, x)` simply drops the locale argument.
- **`Math.round`.** Java's `Math.round(Float)`/`Math.round(Double)` became `roundToInt()` and
  `round(...).toInt()`, which round ties the same way.
- **`sortedMapOf` / `toSortedMap` / `toSortedSet` / `MutableMap.merge`** are JVM-only.
  `Summaries.heartRateSeries` sorts its entries explicitly, `History.weekly` and
  `Vo2max.estimate` sort the entries of a grouped map, `RecoveryEngine` uses
  `.distinct().sorted()`, and `HistorySync` writes `maxHr[day] = maxOf(...)`.
- **`UserProfile.usesImperial`** fell back on `java.util.Locale.getDefault().country`. Common code
  has no locale, so `settings/PlatformLocale.kt` declares `internal expect fun platformUsesImperial()`
  with a JVM actual (`Locale.getDefault().country`) and an iOS actual
  (`NSLocale.currentLocale.countryCode`). `age(today = LocalDate.now())` is now
  `Clock.System.todayIn(...)`.
- **`HistorySync`.** `GattClient` became `GattPort`, `SettingsStore` became `SettingsRepository`,
  and the two history characteristics are now string constants because `GattPort` is string-keyed
  (`enableNotifications(HISTORY_DATA)`, `write(HISTORY_CONTROL, …)`). `dumpRaw` — the debug aid that
  wrote chunks to a `java.io.File` — was not copied; nothing else in the file changed. Two
  additions, both for the engine's `sync_log`: a `rowsWritten` counter incremented where rows are
  inserted, and `CoroutineStart.UNDISPATCHED` on the notification collectors. The latter is a real
  fix: `GattPort`'s flows have no replay, so a strap reply that arrived before the collector
  subscribed was dropped and the whole sync timed out after ten seconds. On Android the collectors
  start on another thread and lose that race only rarely; here it was reproducible.
- **`SyncState`** is copied verbatim from `StrapController.kt` lines 20-26: `HistorySync` writes it
  and the engine turns it into `EngineListener.onSync`.
- **`SettingsStore.kt` → `SettingsRepository.kt`.** The `UserProfile` and `PairedDevice` data
  classes are copied unchanged (except the locale fallback above). The repository keeps exactly the
  six settings functions `HistorySync` and `StrapManager` call — `lastSync`, `setLastSync`, `profile`,
  `liveHeartRate`, `setLiveHeartRate`, `pairedDevice` — as suspending functions over the
  `key_value` table, because nothing that crosses into Swift may be a `Flow`. The keys are Android's
  (`last_sync_<type>`, `profile_*`, `live_hr`, `device_address`, `device_name`). No secret is stored:
  `pairedDevice().authKeyHex` is empty, the key lives in the iOS Keychain (card C3), and the Zepp
  account, goals and Today-tile settings were not copied.
- **The database.** `OpenStrapIosDatabase` has the same 27 entities as `OpenStrapDatabase` at
  version 1 — Android is at version 7 with six auto-migrations, and a new database on a new platform
  has no history to migrate — plus `KeyValue` and `SyncLog`. It is opened with
  `BundledSQLiteDriver` (or in memory with `:memory:`), annotated `@ConstructedBy`, and its schema is
  exported to `shared/schemas/org.openstrap.app.data.db.OpenStrapIosDatabase/1.json`.
- **`StrapEngine`.** The constructor now takes `databasePath: String` and opens the database; after
  `ready` it starts `watchDataAvailable` and `autoSync` and gained `syncNow()`, all ported from
  `StrapManager.kt` lines 374-474 without the Android notification and foreground-service parts.
  They only start when the session advertises the history endpoint — otherwise there is nothing to
  fetch. `synchronized` blocks became plain code because all engine work runs on one
  limited-parallelism thread. `onSync` is posted on the main thread, and `done` is posted after the
  `sync_log` row is written.

Authored for iOS in this card, with no upstream source:

| File | Why it is new |
|---|---|
| `shared/src/commonMain/kotlin/org/openstrap/app/data/Dates.kt` | The `java.time` names the copies use, over kotlinx-datetime. |
| `shared/src/commonMain/kotlin/org/openstrap/app/data/Format.kt` | `String.format` for the specs the copies use. |
| `shared/src/commonMain/kotlin/org/openstrap/app/data/db/KeyValue.kt` | The `key_value` and `sync_log` entities the card adds. |
| `shared/src/commonMain/kotlin/org/openstrap/app/data/db/SettingsDao.kt` | Their DAOs. |
| `shared/src/commonMain/kotlin/org/openstrap/app/data/db/OpenStrapIosDatabase.kt` | The multiplatform database and its builder. |
| `shared/src/commonMain/kotlin/org/openstrap/app/data/settings/SettingsRepository.kt` | Room-backed settings (copied `UserProfile`/`PairedDevice` + the six functions). |
| `shared/src/commonMain/kotlin/org/openstrap/app/data/settings/PlatformLocale.kt` (+ `.jvm.kt`, `.ios.kt`) | The region fallback for `usesImperial`, which common code cannot read from a locale. |
| `shared/src/commonTest/kotlin/org/openstrap/shared/device/SimulatedGdspStrap.kt` | The GDSP device side of `protocol/.../GdspClientTest.kt`, serving per-minute activity instead of a recorded workout. |
| `shared/src/commonTest/kotlin/org/openstrap/shared/device/HistorySyncTest.kt` | The four end-to-end sync tests (rows written, no duplicates, cursor, `sync_log`). |

`:shared:jvmTest` after C4: 161 tests, 0 failures (the 28 from S1 + the 129 copied + 4 new
`HistorySyncTest`).

## Card C5 (background sync on iPhone)

No file was copied from Android in this card, so the table above is unchanged.

- **`StrapEngine.recentSyncs(limit: Int, onRows: (List<SyncLog>) -> Unit)`** is new and iOS-only. The
  `sync_log` table itself is an iOS addition (C4); Android never reads it back, so there is no
  upstream reader to copy. It hands the newest rows to Swift on the main thread as a plain callback,
  because nothing crossing into Swift may be a `suspend` function or a `Flow`. The Diagnostics
  screen shows the newest 50.
- **`SyncLogDao.recent(limit)`** is the query behind it (`ORDER BY start DESC, id DESC LIMIT :limit`).
- **`shared/src/commonTest/.../device/SyncLogReadTest.kt`** is new: an engine that has not synced has
  no rows, the newest rows come back first with their `foreground` flag, and the limit is honoured.
- The engine's own wiring is unchanged: `SyncLog.foreground` is still written from `appVisible` at
  insert time; C5 makes the Swift app keep `appVisible` in step with the app lifecycle.

`:shared:jvmTest` after C5: 164 tests, 0 failures (161 + the 3 `SyncLogReadTest`).
`iosSimulatorArm64Test` total after C5: 260 (was 257).


## Card C6 (Today, Sleep, Heart, Recovery screens)

```
app/src/main/kotlin/org/openstrap/app/ui/dashboard/Narrative.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Narrative.kt
app/src/main/kotlin/org/openstrap/app/ui/components/Cards.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/Narrative.kt
app/src/debug/kotlin/org/openstrap/app/data/SampleData.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/SampleData.kt
app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/RecoveryCopy.kt
```

What changed in each copy, and why:

- **`Narrative.kt`** carries the headlines, rating words, advice sentences and `stressThreshold`
  text unchanged. What changed: `Rating` held a Compose `Color` on Android, which `shared/` has no
  type for, so it now carries the label only and the Swift view picks the colour from the rating.
  `formatDuration` came with it, copied from `Cards.kt` line 185 (the sentences are built with it),
  and `Math.round(x)` became `kotlin.math.round(x).toInt()` — `java.lang.Math` does not exist in
  common Kotlin. `org.openstrap.app.data.X` prefixes inside the file became plain names because the
  copy lives in that package.
- **`SampleData.kt`**: same numbers and the same `Random(42)`, so the sample week is the week the
  Android debug build writes. What changed: `java.time` to kotlinx-datetime (`ZoneId.systemDefault`
  → `TimeZone.currentSystemDefault`, `LocalDate.now` → `Clock.System.todayIn`) and
  `System.currentTimeMillis()` to `Clock.System.now().epochSeconds`. `load` takes the number of days
  to seed: it defaults to the 7 Android writes (`SampleData.DAYS`), and the iOS `-demoData` argument
  asks for a month (`DEMO_DAYS = 30`) so Readiness is past its seven-night learning phase on screen.
  Android has no test for this file; the JVM tests exercise it through a real database instead.
- **`RecoveryCopy.kt`** is the Recovery screen's copy, character for character: the two empty
  states in `RecoveryCard`, `ChargeCard`'s two hints, the "capped" note, and `HowItWorksPanel`'s two
  explanation paragraphs. They sit in `shared/` rather than in Swift because the card's rule is the
  Android app's wording with no new copy.

`:shared:jvmTest` after C6: 173 tests, 0 failures (164 + the 9 `HealthQueriesTest`).
`iosSimulatorArm64Test` after C6: 260, unchanged (the new tests are JVM-only; nothing commonTest
was added). XCTest: `OpenStrapTests` 11, unchanged; `OpenStrapUITests` 1 -> 2, the new test
launching with `-demoData`, visiting the four tabs and attaching a screenshot of each.

Authored for iOS in this card, with no upstream source:

| File | Why it is new |
|---|---|
| `shared/src/commonMain/kotlin/org/openstrap/shared/health/HealthQueries.kt` | The screens' reads: `today`, `sleep`, `heart`, `recovery` as plain callbacks on the main thread, and `seedDemoData` for `-demoData`. The values come from `RecoveryEngine` and `Narrative`; nothing new is computed here. |
| `shared/src/jvmTest/kotlin/org/openstrap/shared/health/HealthQueriesTest.kt` | The 9 tests that seed a real database from the copied sample week and check each snapshot against the score functions. |
| `iosApp/OpenStrap/HealthModel.swift` | The Swift object over `HealthQueries`, the `-demoData` flag (debug builds only) and the screens' formatters. |
| `iosApp/OpenStrap/TodayView.swift`, `SleepView.swift`, `HeartView.swift` | The three screens and their Swift Charts. |

## Card C7 (Apple Health export)

C7 copied nothing from `app/`. `HealthExportQueue` is new Kotlin and `HealthKitWriter.swift` is new
Swift; the only upstream text this card leans on is the export's own grouping rules, which were
**mirrored, not copied**, because the destination is HealthKit's sample model rather than Health
Connect's record model:

```
app/src/main/kotlin/org/openstrap/app/health/HealthConnectExporter.kt @ 19f2865 -> (not copied; the rules below were mirrored in shared/src/commonMain/kotlin/org/openstrap/shared/health/HealthExportQueue.kt and iosApp/OpenStrap/HealthKitWriter.swift)
```

What was taken from that file, and what could not be:

- **Heart rate** takes the heart-rate table plus the activity minutes that carry a reading, keeps
  30..230 bpm and one sample per timestamp (`distinctBy { it.timestamp }`). Health Connect groups an
  hour of samples into one record; HealthKit has one sample per reading, so the hour grouping has no
  destination and each reading is its own row. The `openstrap.hr.<epochSeconds>` sync identifier
  keeps a re-export one sample.
- **Steps** are one minute per activity minute with `steps > 0`, from the minute to the minute + 60,
  exactly the Android bucket.
- **Oxygen saturation** keeps the 70..100 filter and carries the percentage; Swift divides by 100
  because `HKUnit.percent()` is a fraction. Android's `Percentage(...)` is the same unit.
- **Resting heart rate** is one row per local day, as Android writes `time = r.day`.
- **Sleep** keeps the two hardening rules in `HealthConnectExporter.export`: a stage is clipped into
  its session (`start.coerceAtLeast(session.start)`, `maxOf(end, start + 60).coerceAtMost(session.end)`)
  and dropped when it is zero length. Android maps a fifth, `else` case to `STAGE_TYPE_UNKNOWN`;
  Apple Health has no unknown sleep stage, so C7 skips it (card step 2, "others skipped").
- **Not carried over**: Android's `deleteRecords` before re-inserting sleep (HealthKit's sync
  identifier replaces instead), Health Connect's 500-record batch cap (C7 saves 500 samples per call
  by choice), and the whole import direction.
- **Not exported at all** (plan section 6): HRV. Android writes `HeartRateVariabilityRmssdRecord`;
  the strap's value is RMSSD and HealthKit only has SDNN, so C7 writes neither. Skin temperature and
  stress have no honest Apple Health type and stay in `openstrap.db`. A JVM test seeds all three
  tables and asserts no type hands out a row.
- Android has no test for `HealthConnectExporter` (`app/src/test/kotlin/org/openstrap/app/health/`
  does not exist), so there was no test to copy. The 15 `HealthExportQueueTest` cases are new.

`:shared:jvmTest` after C7: 188 tests, 0 failures (173 + the 15 `HealthExportQueueTest`).
`iosSimulatorArm64Test` after C7: 260, unchanged (the new Kotlin tests are JVM-only; nothing
commonTest was added). XCTest: `OpenStrapTests` 11 -> 22, the 11 new `HealthExportTests`; the iOS
job's log reads "Executed 22 tests, with 0 failures" and "Executed 2 tests" for `OpenStrapUITests`,
then "** TEST SUCCEEDED **". Android: `:app:testDebugUnitTest` 151 tests and `:app:assembleDebug`
green, `git diff --stat 19f2865 -- app docs/protocol` empty.

Authored for iOS in this card, with no upstream source:

| File | Why it is new |
|---|---|
| `shared/src/commonMain/kotlin/org/openstrap/shared/health/HealthExportQueue.kt` | `HealthExportTypes`, `ExportRow`/`ExportBatch` as plain data, and the per-type cursor in `key_value` (`health_export_<type>`, never Android's `last_sync_<type>`), with one worker and main-thread callbacks. |
| `shared/src/jvmTest/kotlin/org/openstrap/shared/health/HealthExportQueueTest.kt` | The 15 tests: the five type names, an empty database, the heart-rate merge and 30..230 filter, the cursor (newer-only, survives a new queue, never backwards, per type, batch limit), the step minute buckets and their boundary, the oxygen range, the resting-HR day, the clipped sleep stages, a night found by its session end, and that HRV, stress and skin temperature never appear. |
| `iosApp/OpenStrap/HealthKitWriter.swift` | `HealthExportType`, `HealthSampleMapper` (pure builders), `HealthKitWriter` (write-only authorisation and `save`), `HealthExportSettings` (the switch, off when absent), and `HealthExportCoordinator` (pending, save, mark, next type, 500 samples a call). |
| `iosApp/OpenStrapTests/HealthExportTests.swift` | The 11 XCTests that build each type through the mapper without saving and check type, unit, value, dates, the sync identifier and version, the device, and that the switch starts off. |

## Card U1 (the glow design foundation)

Copies into the iOS bundle, byte-identical to their sources (checked with `md5sum`):

```
app/src/main/res/font/funnel_display.ttf @ 19f2865 -> iosApp/OpenStrap/Design/Fonts/funnel_display.ttf
app/src/main/res/font/funnel_sans.ttf @ 19f2865 -> iosApp/OpenStrap/Design/Fonts/funnel_sans.ttf
app/src/main/res/font/museo_moderno.ttf @ 19f2865 -> iosApp/OpenStrap/Design/Fonts/museo_moderno.ttf
licenses/OFL-Funnel.txt @ 19f2865 -> iosApp/OpenStrap/Design/Licenses/OFL-Funnel.txt
licenses/OFL-MuseoModerno.txt @ 19f2865 -> iosApp/OpenStrap/Design/Licenses/OFL-MuseoModerno.txt
licenses/MIT-Phosphor.txt @ 19f2865 -> iosApp/OpenStrap/Design/Licenses/MIT-Phosphor.txt
```

The three fonts are variable fonts: the default instance is Light (Thin for Museo Moderno) with an
`wght` axis from 300 to 800 (100 to 900 for Museo Moderno). `UIAppFonts` in `iosApp/project.yml`
registers the files; the weights Theme.kt instantiates are reached through the `wght` variation axis
on the default face's descriptor, because `UIFont(name:)` only finds the default instance.
`DesignTests.testVariableWeightsReachTheRenderer` measures Light against Bold so a weight that stops
rendering fails the suite.

The icon geometry moved from `PhosphorIcons.kt` to generated asset-catalogue SVGs, not a byte copy:

```
app/src/main/kotlin/org/openstrap/app/ui/theme/PhosphorIcons.kt @ 19f2865 -> iosApp/OpenStrap/Assets.xcassets/Phosphor/<Name>.imageset/<Name>.svg (46 icons, written by iosApp/scripts/gen_icons.py)
```

`gen_icons.py` parses every `icon("Name", …)` call (both the plain and the `autoMirror =` form) and
writes `<Name>.svg` with the same `viewBox="0 0 256 256"` and single path, plus a `Contents.json`
that renders it as a template image with vector preservation. Commit both the script and the
generated sets; `gen_icons.py --check` fails if the catalogue and the Kotlin file disagree.

No Kotlin was copied or written in U1: nothing in these Compose files computes a number, date or
sentence (standing instruction 5), so `:shared:jvmTest` stays at **188 tests, 0 failures** and
`iosSimulatorArm64Test` at **260**.

XCTest after U1: `OpenStrapTests` 22 -> 28 (the 6 new `DesignTests`: the three font PostScript names
load, the variable weights really differ, all 46 icons load as images, white stays readable on
`mid`/`tone` for the three presets and all 17 accents, the battery colours, the charge scale).
`OpenStrapUITests` 2 -> 3: `testDesignLab` launches with `-designLab`, scrolls the lab to the end and
attaches `U1-designlab-1` … `U1-designlab-8`.

Authored for iOS, with no upstream source:

| File | Why it is new |
|---|---|
| `iosApp/OpenStrap/Design/Theme.swift`, `Typography.swift`, `GlowStyle.swift`, `GlowCard.swift`, `DotMatrixText.swift`, `PhosphorIcon.swift` | The ports of the Compose files above. |
| `iosApp/OpenStrap/Design/DesignLabView.swift` | The debug design lab (`-designLab`); Android has no counterpart. |
| `iosApp/scripts/gen_icons.py`, `iosApp/scripts/screens.py` | Icon generation and the screenshot download. |
| `docs/ios/UI-PARITY.md` | The parity table, one section per ported Compose file. |

One flake seen while landing U1, not caused by it and not fixed here (no Kotlin changed in this
card): `:shared:iosSimulatorArm64Test` failed once on
`org.openstrap.shared.device.SyncLogReadTest.anEngineThatHasNotSyncedHasNoRows` with an
`org.openstrap.shared.ble.GattException` rather than an assertion. The test never connects a strap,
so the exception comes from another engine's background work surfacing while it runs; the previous
commit ran the same iOS tests green with no Kotlin change in between. Worth a card of its own if it
recurs.

## Card U2 (the glow charts, parts, quiet panels, tab bar and calendar)

No file was copied into the bundle. One file of Kotlin was **written**, because the Compose charts
compute their geometry inline inside `DrawScope` and standing instruction 5 forbids re-deriving it in
Swift:

```
app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCharts.kt:126-460      (GlowLineChart … GlowHorizon)
app/src/main/kotlin/org/openstrap/app/ui/glow/GlowLayout.kt:33-64        (the layout constants)
app/src/main/kotlin/org/openstrap/app/ui/components/Charts.kt:288-319    (smoothPath)
app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayCalendar.kt:138-153 (the month grid)
        -> shared/src/commonMain/kotlin/org/openstrap/app/ui/glow/GlowGeometry.kt (object GlowGeometry)
        -> shared/src/commonTest/kotlin/org/openstrap/app/ui/glow/GlowGeometryTest.kt (30 tests)
```

Each function is one expression of the Compose source, with the canvas size and the dp values passed
in as pixels, so both builds compute the same number: the line chart's domain (`yRange`, then band,
then the data, with the 1e-3 flat guard) and its value axis, the dot-bar pitch and lit rows, the
trend's 85%-of-the-lowest domain, the arc gauge's radius/centre/sweep/leading point, the hypnogram's
lanes, pill rectangle and row brightness, the horizon's radius, sag and point along the arc, the
Fritsch–Carlson control points behind `smoothPath`, and the calendar's lead/weeks/day numbers.

`:shared:jvmTest` 188 -> **218 tests, 0 failures, 1 skipped** (+30 `GlowGeometryTest`).
`iosSimulatorArm64Test` 260 -> **290**: the same 30 tests run on the iOS target too.
`./gradlew :shared:compileKotlinIosArm64` is clean, which is what the Swift side calls through.

XCTest after U2: `OpenStrapTests` stays at 28 (no new unit tests: the numbers they would check live
in `GlowGeometryTest`), `OpenStrapUITests` stays at 3, but `testDesignLab` now walks the lab with 14
swipes and attaches `U2-designlab-1` … `U2-designlab-15`. It scrolls a fixed number of times instead
of asking whether the last section is hittable on every screenful: the lab's tree is now large
enough (5 tab bars, 46 icons, two calendar sheets) that a mid-scroll hit-test snapshot can outlast
XCUITest's query timeout, which is what failed run 39569063780 while the same code passed in
39567532008.

Two things were decided for iOS and are recorded here rather than hidden:

- `DayCalendar.kt:148`'s enabled test stays in Swift. It is one comparison (`day <= today &&
  (day in available || day == today)`) against the day's own set, and a Kotlin `Set<Int>` reaches
  Swift as a boxed collection; the grid arithmetic around it is in `GlowGeometry`.
- `GlowHorizon`'s sweep-in (Compose redraws the arc each frame over 700 ms) becomes a fade of the
  finished arc, because standing instruction 7 allows only offset, scale and opacity to animate. The
  same rule leaves `DotHeader`'s caret turn instant and `GlowSwitch`'s track colour un-animated.

## Card U3 (shared screen state)

Every file this card copied, as the required `source path @ 19f2865 -> destination path`:

```
app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayViewModel.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ui/dashboard/DayState.kt
app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayViewModel.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ui/dashboard/DayStateLoader.kt
app/src/main/kotlin/org/openstrap/app/ui/workout/WorkoutsViewModel.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ui/workout/WorkoutsLoader.kt
app/src/main/kotlin/org/openstrap/app/ui/dashboard/Routes.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ui/dashboard/Metric.kt
app/src/main/kotlin/org/openstrap/app/ui/dashboard/MetricInfo.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ui/dashboard/Metric.kt
app/src/main/kotlin/org/openstrap/app/ui/components/Charts.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/ui/components/ChartPoints.kt
app/src/main/kotlin/org/openstrap/app/data/settings/SettingsStore.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/app/data/settings/SettingsRepository.kt
app/src/main/kotlin/org/openstrap/app/MainActivity.kt @ 19f2865 -> shared/src/commonMain/kotlin/org/openstrap/shared/screens/ScreenQueries.kt
```

What changed in each copy, and why:

- **`DayViewModel.kt` → `DayState.kt` + `DayStateLoader.kt`.** The file is two things: the state
  classes (lines 42-110 and 342-352) and the loading logic (112-319). Both are copied, into two
  files so Swift can hold the state type without pulling in the reader. `Baselines`, `DayUiState`,
  `SleepNight` and `SLEEP_NIGHTS` keep the same package and every field; `java.time.LocalDate`
  became `kotlinx.datetime.LocalDate` and `LocalDate.now()` became
  `Clock.System.todayIn(TimeZone.currentSystemDefault())`. The `@OptIn` flows became plain
  suspending calls: each `combine(...)` arm is read once with `Flow.first()` from the same
  `HealthRepository` function the ViewModel used, so the expressions that filter, average and map
  are the ViewModel's own lines, in its order. What the flows gave for free had to be written out:
  the `sleep` flatMapLatest body became sequential reads of the same windows, the `nights` combine
  became one read per table, and `strapSteps` became the `todaySteps` argument Swift passes from
  `StrapEngine`. `Log.w("OpenStrap", "recovery failed", e)` became the constructor's `log`
  callback. `previousDay`/`nextDay`/`select` are not copied: the selected date is Swift's
  (`RootView`, card U4). `SLEEP_STAGE_ROWS` (312-317) is not copied: it orders the hypnogram lanes
  and is consumed by `GlowSleep`, which is Swift's (card U7); the stage codes themselves are on
  `SleepStage` in `shared/` already.
- **`WorkoutsViewModel.kt` → `WorkoutsLoader.kt`.** Same list read (`ORDER BY start DESC`), the same
  `Workouts.analyse(w, listOfNotNull(lastByWorkout[w.start]), effects, maxHr)` for a row and
  `Workouts.analyse(w, hr, effects, maxHr)` for the open one, and the same
  `RecoveryEngine(dao).maxHeartRate(profile).roundToInt()` fallback. The `stateIn` sharing that made
  the 90-day max-HR read run once per profile change became a one-entry cache keyed by the profile.
  `openStart` is not copied: which workout is open is Swift's navigation state.
- **`Routes.kt` → `Metric.kt`.** The `Metric` enum and its `format` are copied verbatim except
  `Math.round(v)` → `round(v).toLong()` and `"%,d".format(…)` → `shared/`'s `String.format`, which
  prints `Locale.ROOT` the way the Android call did. `LocalGoals` (24) is not copied: a
  `CompositionLocal` has no equivalent, and `ScreenQueries.goals()` reads the same `SettingsStore`
  values. `Detail` and `Detail.StackSaver` (27-55) are not copied: the per-tab detail stack is
  SwiftUI navigation (card U4), and its string form has no reader outside Android's saved state.
- **`MetricInfo.kt` → `Metric.kt`.** `Range` (13), `colorOf` (18-25), `RELATED` (27-36) and `ABOUT`
  (38-71) are copied with the same text, character for character. Compose `Color` became the
  `MetricColor` enum (Swift maps it to `Theme.swift`), `colorOf` became the `Metric.color`
  property, and `Range`, `RELATED` and `ABOUT` are public rather than `internal` because Swift
  calls them. `DailyHistory` (16) is not copied: it is a function type over a `Flow`, which is
  exactly what `ScreenQueries.history` replaces.
- **`Charts.kt` → `ChartPoints.kt`.** Three data classes only — `ChartPoint` (44), `TrendPoint`
  (327), `StageSpan` (330) — because `DayUiState` holds them and they are plain data. The Compose
  drawing in that file stays Swift's, as card U2 recorded.
- **`SettingsStore.kt` → `SettingsRepository.kt`.** Two additions to the C4 copy: `goals()` /
  `saveGoals()` (SettingsStore 160-178) and `saveProfile()` (143-158), with the same keys
  (`goal_steps`, `goal_sleep_minutes`, `goal_active_hours`, `profile_*`) and the same rule that a
  null field is removed rather than written. `profile()` already existed. `todayTiles`,
  `forgetDevice` and the Zepp account are not copied: no card asks for them yet.
- **`MainActivity.kt` → `ScreenQueries.kt`.** Lines 140-260 are not UI: they are the list of reads
  and writes the screens are fed with (`dayVm.state`, `dayVm.recovery`, `dayVm.daysWithData`,
  `health.checkIn`, `health.daily` as `history`, `settings.goals`, `workoutsVm.workouts`, `.open`,
  `.effects`, `profile`, `editGoals`). `ScreenQueries` is the one object Swift calls, with one
  method per item on that list, ISO dates in and out and every answer on the main thread.
  `HealthRepository.changes()` keeps the 800 ms debounce that `DayViewModel.recovery` puts on it,
  because a sync writes hundreds of rows.

Android has no test for any of these files (`app/src/test/` holds `data/`, `export/` and `zepp/`
only), so the 33 tests are new: `MetricTest` (7, commonTest), `DayStateLoaderTest` (9),
`WorkoutsLoaderTest` (6), `ScreenSettingsTest` (2) and `ScreenQueriesTest` (9). They check the
values against the functions that compute them (`Summaries`, `RecoveryEngine`, `Workouts`,
`SampleData`) rather than against written-down numbers, so the two cannot drift.

`:shared:jvmTest` 218 -> **251 tests, 0 failures, 1 skipped**. `iosSimulatorArm64Test` 290 -> **302**:
the 7 `MetricTest` cases run there too (they are in `commonTest`) and `ScreenAccessorsTest` adds 5.
This box cannot run the iOS target, so 302 is what CI will report; `compileTestKotlinIosSimulatorArm64`
is clean here.

Authored for iOS in this card, with no upstream source:

| File | Why it is new |
|---|---|
| `shared/src/commonMain/kotlin/org/openstrap/shared/screens/ScreenQueries.kt` | The screens' reads and writes as plain callbacks on the main thread, plus `CheckInAnswer` and the `changes()` subscription. Nothing is computed here: it wires the two loaders and the settings repository. |
| `shared/src/iosMain/kotlin/org/openstrap/shared/screens/ScreenAccessors.kt` | The maps and shapes that cross into Swift badly (`Map<Long, …>`, `Map<Int, Long>`, `Map<Metric, …>`) read back as lists and plain numbers. |
| `shared/src/iosTest/kotlin/org/openstrap/shared/screens/ScreenAccessorsTest.kt` | The 5 tests for them (CI runs this target). |
| `shared/src/jvmTest/kotlin/org/openstrap/shared/screens/{DayStateLoader,WorkoutsLoader,ScreenSettings,ScreenQueries}Test.kt` | The 26 JVM tests over a real database seeded with the copied sample month. |

One difference the tests have to live with: on this Linux box SQLite's `date(…, 'unixepoch',
'localtime')` resolves in UTC while `kotlinx-datetime` reads `TZ` (`Asia/Calcutta`), so the copied
`HealthRepository.daily` groups a day's rows by a date one earlier than the Kotlin side calls it —
only for the row that sits exactly on a midnight. On iOS and on the UTC CI runner the two agree.
The tests take their expected dates from the same read instead of writing the count down.


## Card F2 (the Design lab's column was wider than the screen)

Found in product review V1: every section of the lab was shifted about 24 pt left, so the first letter
of each heading was cut ("lisplayLarge", "ype", "auges", "ab bar", "eart", "leepRem") and the outer
colour swatches ran off both edges. Nothing was wrong with the lab's padding: one child had a fixed
width larger than the column, so the VStack took that width, `.padding(20)` added 40 pt to it, and the
scroll view centred the resulting 451 pt column in its 402 pt viewport.

The culprit was `DotMatrixText("0123456789")` (`DesignLabView.swift:176`) at the component's default
`dot` 5 and `gap` 2: ten digits are 59 columns of a 7 pt pitch, so the component asked for
`7 * 59 - 2 = 411` pt — 49 pt more than the 362 pt the lab offers. Measured from the committed
screenshots rather than by eye: on `U2-designlab-3` the colour grid's swatches are 285 px wide with
31 px gaps (4 * 285 + 3 * 30 = 1230 px = 410 pt, which matches the 411 pt row), and both outer swatches
are cut by 12 px — with the 60 px of padding that is the 72 px (24 pt) the centred column overshoots on
each side.

The fix is in the component, because the Today, Sleep, Heart and Recovery screens draw their hero
numbers with it:

- `DotMatrixText` sizes itself through `DotMatrixSize` (`DotMatrixText.swift:102`) instead of
  `.frame(width:height:)`. The layout gives the ideal size to a parent that asks for it — nothing else
  about the component changes: same glyphs, same `dot`, same `gap`, same drawing — and the width on
  offer when that is narrower, which is Compose's `Modifier.size()` with `enforceIncoming`
  (`GlowParts.kt:77`). A number too wide for its column is cropped at the column's edge, exactly as
  Compose coerces the Canvas, and `.clipped()` keeps the dots inside that width.
- The Design lab itself is unchanged: the ten-digit row now ends at the lab's right margin instead of
  pushing the column off both sides. It is the only place in the app that asks for more width than it
  is given, which is what makes the UI test below catch a regression.

The lab then still failed that test with the column in the right place, and the screenshots said the
layout was right (`first_lit_col` 61 px = the 20 pt padding, both edge columns dark). The second
defect was in `GlowCard`: a SwiftUI `Image` is an accessibility element by default, so the blurred
core's own frame — the card plus its 8% margin, times the drift's scale — joined the card's
accessibility frame and every ancestor's. A 362 pt section reported 437–452 pt depending on where the
drift was when the query ran. Two changes in the component fix it, and both are what the card should
have done anyway:

- The three pre-rendered layers (base, core, overlay) are decoration and are
  `.accessibilityHidden(true)`; only a card's `content` is readable.
- `GlowRenderer.core` crops the blurred core to the card. The card clips its layers to its own shape,
  so only the middle of the padded image was ever visible: the crop is the same pixels, and the
  layer's layout frame is now the card's, so nothing above it can inherit an oversized frame. The
  margin, the blur radius, the drift's scale/offset/opacity are unchanged.

`testDesignLab` asserts, for every section identifier the lab sets (`lab.title`, `lab.type`,
`lab.colours`, `lab.cards`, `lab.motions`, `lab.dotmatrix`, `lab.icons`, `lab.charts`, `lab.gauges`,
`lab.hypnogram`, `lab.parts`, `lab.quiet`, `lab.navbar`, `lab.calendar`), that
`element.frame.minX >= 0` and `element.frame.maxX <= app.windows.firstMatch.frame.maxX`. On fd5e09d's
lab the sections are 411 pt wide starting at -4.5 pt, so the test fails there and passes after the
fix; the failure message prints the offending frame and the window's.

The assertion runs at the first screenful that shows a section, not at launch: XCUITest's frame for an
element thousands of points down a scroll view is rounded, scaled and does not move with the scroll
(it reported 440 pt for a 362 pt section), so only what the window shows can be trusted. Every section
still unchecked is asked about on every screenful, and the walk drags most of a screen per capture —
held at the end so momentum cannot carry it past a section — until the last section has been seen.
That also fixed the screenshot walk: U2's fourteen `swipeUp`s moved about 400 pt each, so the set
stopped at the charts, roughly 6,000 pt into a lab about 10,000 pt tall (its "reached the calendar"
check was `exists`, which is true for anything in the accessibility tree). The new set is
`U2-designlab-1` … `U2-designlab-19` and covers the whole lab, and the eight screenfuls card U1
shipped (`U1-designlab-1` … `8`) are written from the same captures, because U1's eight are the top of
this lab and the committed U1 set still showed the clipped column.

The test launches the lab with `-designLabStill`, which holds the glow cores still
(`glowScrollPhasePause(always:)`, default off, so the real screens do not change). A lab whose thirteen
cards animate for ever never goes quiescent, and XCUITest waits for quiescence before every query and
swipe: two runs died on that 30 s timeout at different points in code that had just passed. Holding
still also makes the screenshots the same picture every run.

One flake outside this card: the retry run of the same tree failed `:shared:iosSimulatorArm64Test`
on `StrapEngineTest.theAuthKeyNeverAppearsInTheLog` with a `GattException`, and the empty rerun of the
same tree passed all 302 tests. That test is in the BLE code this card does not touch.


## Card U4 (the app shell: five tabs, header, date navigator, You)

### Kotlin copied out of the Compose screens (standing instruction 5)

The profile and goals screens compute their own ranges, unit conversions and timestamps. Those
functions are now in shared Kotlin, in the package of the file they came from, with JVM tests, and
Swift calls them instead of re-implementing them.

    app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt:56-63   -> shared/src/commonMain/kotlin/org/openstrap/app/ui/device/DeviceRules.kt  ProfileRules.summary
    app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt:88-102  -> DeviceRules.kt  ProfileRules.valid / .fields
    app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt:100-149 -> DeviceRules.kt  ProfileRules.toProfile
    app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt:74,78-86 -> DeviceRules.kt  sexIndex, heightText, feetText, inchesText, weightText, sleepNeedText
    app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt:250      -> DeviceRules.kt  formatNumber
    app/src/main/kotlin/org/openstrap/app/ui/device/GoalsDialog.kt:23-31      -> DeviceRules.kt  GoalsRules.valid, stepsText, sleepHoursText, sleepMinutesText, activeHoursText
    app/src/main/kotlin/org/openstrap/app/ui/device/GoalsDialog.kt:52-58      -> DeviceRules.kt  GoalsRules.toGoals

What changed in the move, and why:

- `String.format` has no Locale in common Kotlin, so `"%.1f".format(Locale.ROOT, v).removeSuffix(".0")`
  is `(v * 10).roundToInt() / 10f` and a trailing `.0` check. Same output for every value the fields
  can hold; the JVM test pins `72`, `158.7` and the kg/lb conversion.
- `LocalDate.now()` and `System.currentTimeMillis() / 1000` are parameters (`todayIso`,
  `nowEpochSeconds`) so the save is testable and Swift passes the phone's own clock.
- The extension function `UserProfile.summary()` became `ProfileRules.summary(profile)`, and the
  inline `when`/`copy` expressions became object members, because Swift cannot call a Kotlin
  extension or read Kotlin's default arguments: `ProfileRules.blank()` and `GoalsRules.defaults()`
  exist only for that.
- `ProfileRules.fields()` returns the per-field errors the Compose screen draws red
  (`age.isNotBlank() && ageValue == null`), so the Swift field outline is Kotlin's answer, not a
  second copy of the ranges.

Tests: `shared/src/jvmTest/kotlin/org/openstrap/shared/screens/DeviceRulesTest.kt`, 17 tests
(summary, every range, the imperial conversions, the save's copy(), the goals ranges and the four
opening texts). `:shared:jvmTest` 251 -> 268 tests, 0 failures, 1 opt-in skip; the full-suite run on
this box also executes F1's untracked scratch probe, which is not part of this card.

### Copies, not ports

The three licence texts were already in the app from card U1, byte-identical to the repository's
own files:

    licenses/MIT-Phosphor.txt     == iosApp/OpenStrap/Design/Licenses/MIT-Phosphor.txt
    licenses/OFL-Funnel.txt       == iosApp/OpenStrap/Design/Licenses/OFL-Funnel.txt
    licenses/OFL-MuseoModerno.txt == iosApp/OpenStrap/Design/Licenses/OFL-MuseoModerno.txt

U4 adds no second copy: `LicencesView` reads those three out of the bundle by name. A first attempt
put fresh copies under `OpenStrap/Licences/`, and CI rejected it with "Multiple commands produce
…/MIT-Phosphor.txt" — XcodeGen copies every resource to the bundle root, so two folders with the
same file names are two build commands for one path (run 39609938000).

### Not copied

- `MainActivity.kt`'s pager, rail, folding-tabletop layout, `ListDetail` side-by-side panes,
  `recoveryFocus`, alarms and firmware pages: iPhone layouts and screens this card does not build.
  The rows are in `docs/ios/UI-PARITY.md` with the reason for each.
- `YouScreen.kt`'s Export CSV, Import CSV and Restore from Health Connect rows: iOS has no CSV path
  and the card names one Apple Health row.
- `DeviceScreen.kt`'s scan-and-pair flow, `Disconnect`, alarms and firmware rows: the card keeps the
  existing pairing logic and only restyles it, and iOS has no scan API in this card.

### What the CI runs found

Six iOS runs before the one that compiled: the first two failed before any Swift error was
reachable, the rest are recorded here because each is a trap the next card should not step in.

- `39609938000` — "Multiple commands produce …/MIT-Phosphor.txt". XcodeGen copies every resource to
  the bundle root; U1 already put the three licence texts in `Design/Licenses/`, so a second folder
  with the same names was a second build command for one path. Fixed by reading U1's files.
- `39611818685` — three Swift errors: `Boolean` where Swift means `Bool`; `RecoverySnapshot` used
  without `import OpenStrapShared`; and `ProfileFields` missing because Kotlin/Native does not
  export a class whose only constructor is internal (its constructor is public now).
- `39615535475` — `extension LinkRow where Trailing == EmptyView` returned a `RowCaret`, which
  cannot type-check; the constraint is `Trailing == RowCaret`.
- `39614149641` — `ProfileScreen`, `YouView` and `GoalsSheet` named Kotlin types without
  `import OpenStrapShared`; the compiler reported only the first unresolved name.
- `39621907688` — the goals sheet opened with every element inside it identified as `sheet.goals`.
  A `.accessibilityIdentifier` on a container is applied to all of its descendants, replacing the
  identifiers they set themselves, so the Cancel button's `goals.cancel` did not exist. No screen
  sets an identifier on a container any more; they sit on the elements a test addresses.
- `39624290757` and the retry — `testDesignLab` (card F2's test) failed with "Failed to get
  matching snapshots: Timed out while evaluating UI query" on `lab.title`, the same flake F2
  recorded; `testEveryTabAndYouScreen` passed in 66.9 s and `testPairScreenAppears` in 10.9 s in
  both runs.

## Card U5 (Today)

### Kotlin copied out of the Compose screens (standing instruction 5)

Today works out more than formatting: which of the three scores leads and by how much, the seven-day
baselines behind What changed, the weekly recap's averages and its sentence, the hypnogram's rows and
readouts, the hourly and five-minute buckets. All of it is now in one shared file, in the package of
the Compose file it came from, with JVM tests, and Swift draws only what it returns.

    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:189-198   -> shared/src/commonMain/kotlin/org/openstrap/app/ui/glow/TodayGlow.kt  bucket
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:205-269  -> TodayGlow.kt  overview, TodayScore, TodayOverview, TodayAccent
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:271      -> TodayGlow.kt  Int?.minus
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:277-301  -> TodayGlow.kt  readinessState
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:304-317  -> TodayGlow.kt  (Change's text; drawn in Swift)
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:357-363  -> TodayGlow.kt  overviewTone, OverviewTone
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:211      -> TodayGlow.kt  readinessBefore
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:400-446  -> TodayGlow.kt  signalTiles, SignalTile, SignalAccent, avg
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:474-482  -> TodayGlow.kt  activityFacts, hourStepReadout
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:387-393  -> TodayGlow.kt  heartFacts, heartBuckets, heartReadout
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:494-513  -> TodayGlow.kt  asleepClock, sleepFacts, napsLine, sleepRange
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:167-180  -> TodayGlow.kt  dayWorkouts
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt:146,156   -> TodayGlow.kt  isMorning
    app/src/main/kotlin/org/openstrap/app/ui/dashboard/ScreenParts.kt:176-189 -> TodayGlow.kt  chargeLeads, READINESS_HOURS_AFTER_WAKING
    app/src/main/kotlin/org/openstrap/app/ui/dashboard/ScreenParts.kt:191     -> TodayGlow.kt  avg
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowSleepScreen.kt:175-192  -> TodayGlow.kt  sleepStageChart, SleepStageChart, SleepStageBlock
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayExtras.kt:40-65    -> TodayGlow.kt  alertLines, AlertLine, alertsDisclaimer
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayExtras.kt:74-126   -> TodayGlow.kt  weeklyRecap, WeeklyRecap, RecapFact, isRecapDay
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayExtras.kt:131      -> TodayGlow.kt  TODAY_TILES, todayTiles, allTodayTiles, metric(name)
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCheckIn.kt:33           -> TodayGlow.kt  checkInNote
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowActivityScreen.kt:410-418 -> TodayGlow.kt  workoutDetail

Reads added to the screen layer, not copied from Compose:

    app/src/main/kotlin/org/openstrap/app/MainActivity.kt:176-182  -> shared/src/commonMain/kotlin/org/openstrap/shared/screens/ScreenQueries.kt  monthHistory (the 30 days the recap compares), and the strap's own score as `strapReadiness` (MainActivity.kt:186 reads `healthDao.strapReadiness`)

What changed in the move, and why:

- `java.time` is `kotlinx.datetime`, as in cards U3 and U4: `Instant.ofEpochSecond(…).atZone(zone)`
  is `Instant.fromEpochSeconds(…).toLocalDateTime(zone)`, and `LocalDate.now()` is a parameter.
- `clock(epoch)` and every function that formats one take a `zone` parameter, because Kotlin/Native
  does not export default arguments: each has a two-argument overload (`clock(epochSeconds, zone)`)
  for the tests and a one-argument form that fills in the phone's zone for Swift.
- `String.format` comes from `shared/`'s own `format` (`app/data/Format.kt`), so `"%,d"`,
  `"%02d:%02d"` and `"%.1f"` print exactly what `java.util.Formatter` printed.
- `dayLabel`'s `getDisplayName(TextStyle.SHORT, locale)` and `DateTimeFormatter.ofPattern("d MMM")`
  became the `WEEKDAYS`/`MONTHS` tables, the same English abbreviations the app's own tests use.
- `Readiness.phase` is read through `Recovery.phase`, and `showStrapEstimate` through the property
  `Recovery.Readiness.showStrapEstimate`, so `readinessState` asks the model rather than copying its
  rule.
- `TODAY_TILES` and `Metric.entries` stay Kotlin; Swift receives the six metrics as a list and the
  stored names through `todayTiles(names)`, so an unknown name is dropped where Compose drops it.
- The strap's Readiness row is a read on `ScreenQueries` (`strapReadiness`) because Today shows
  "Strap's estimate" while our own score is withheld, exactly as `MainActivity:186` reads it.

Tests: `shared/src/jvmTest/kotlin/org/openstrap/shared/screens/TodayGlowTest.kt`, 40 tests (the
bucket slots, the overview's three scores and its verdict, the four readiness-state sentences and
their estimate forms, the tone's three bands, chargeLeads' three conditions, the morning test, the
tile choice's fallback and stored order, the six signals with their baselines and arrows, activity's
facts and hourly readout, the heart buckets and readout, the sleep hero and naps line, the hypnogram's
rows/fractions/readouts, the alert lines and their metrics, the weekly recap's facts and both
comparison sentences, the workout line, the check-in note, yesterday's Readiness, and the metric
lookup) and `TodayQueriesTest.kt`, 2 tests (a real database: the strap's row comes back and is null
without one; a month of steps covers both weeks the recap compares). `SampleDataTest.kt`, 2 tests,
records that seeding the demo month twice replaces its rows rather than doubling them, which is what
makes the UI test's several launches safe. `:shared:jvmTest` 270 -> 313 tests, 0 failures, 1 opt-in
skip.

### Not copied

- The `left`/`right` two-column layout: the app declares portrait only, so the 600 pt branch is
  never reached; Today stacks its blocks in Compose's phone order.
- The Design lab and `TODAY_TILES`' picker storage: Android keeps the chosen names in DataStore, iOS
  in the app's own defaults (`HealthModel.saveTodayTiles`), because it is this phone's preference.

### The Kotlin-to-Swift boundary, and what it cost

Two traps in one screen, both worth knowing before U6-U9:

- **A Kotlin enum entry whose name is not one word arrives all-lowercase.** Kotlin's ObjC export
  lowercases the whole entry name, so `Metric.HeartRate` is `Metric.heartrate` and
  `SignalAccent.SleepLight` is `Metric.sleeplight`; `Metric.Steps` is `steps` either way, which is
  why the one-word entry read correctly and hid the rule. `Theme.swift` sidesteps it by switching on
  the entry's `name` (`"HeartRate"`), which is stable. Run 39640117088: `type 'Metric' has no member
  'heartRate'`.
- **A `List<Float?>` comes across as `[Any]`.** A nullable boxed `Float` has no ObjC generic element
  type, so `TodayGlow.heartBuckets` arrives as `[Any]` and each gap must be read back as the
  `NSNumber` Kotlin boxes a `Float` into (`($0 as? NSNumber)?.floatValue`). Run 39640117088:
  `cannot use optional chaining on non-optional value of type 'Any'`. `List<Float>` stays
  `[KotlinFloat]` and is fine.
- **A SwiftUI stack's off-screen children report frames clamped into the viewport.** Tapping one
  taps whatever really sits at those coordinates, so `staticTexts["Heart rate"].tap()` opened Sleep
  five runs running (runs 39644024771, 39647481836, 39649434424, 39655540385, 39658773525) — first
  from the overview card's Sleep score, later from the bar. Scrolling cannot fix it, because the
  frame does not follow the scroll. U5's UI test therefore proves `TodayRoute.metric` from a What
  changed tile, which is on screen and whose frame is real; card F2's note in `OpenStrapUITests.swift`
  is the same behaviour.
- **The floating bar eats a tap that lands on its rectangle**, including a line that is "in the
  window" and reports `isHittable == true`. Anything a test taps must be above `navBar.top`.
- **`GlowCard` must be told to size from its content.** SwiftUI's bare `GeometryReader` has no
  height of its own, so a glow card in a `VStack` that proposes none collapses to 10 pt with its
  text clipped — which is what the first screenshots showed: every Today card was a thin coloured
  capsule, and the page looked like the overview and the three glowing cards were missing. Compose
  measures a card from its content, so `GlowCard(sizesToContent: true)` now draws the glow behind
  the content and lets the content set the height. The Design lab keeps the old form, which is only
  ever right under its `frame(height: 160)`.

Red runs, in order: 39640117088 (the two interop errors), 39644024771 / 39647481836 / 39649434424 /
39655540385 / 39658773525 (`testToday`'s off-screen tap, one hypothesis each, the last five all the
same clamped-frame cause), 39652336343 (`testEveryTabAndYouScreen`: `licence.OFL-MuseoModerno`
matched two texts, because `LicencesView` puts an identifier on a DotPanel — fixed test-side in
`clearsNavBar`, card F4 told), and 39661545695 / 39664474482 (red only on card F2's recorded
`testDesignLab` query timeout, retried as U4 did).

## Card U6 (Recovery)

### Kotlin copied out of the Compose screens (standing instruction 5)

`GlowRecoveryScreen.kt` works out more than formatting: which sentence a day without a score leads
with, how far the score moved and against what, the seven-day trend's labels, Charge's quarter-hour
buckets and their clock readouts, the fold of the contributors, and the six body signals with their
normal, their word against it and their week. All of it is in one shared file, in the package of the
Compose file it came from, with JVM tests; Swift draws only what it returns.

    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:78        -> shared/src/commonMain/kotlin/org/openstrap/app/ui/glow/RecoveryGlow.kt  forDay (recovery?.takeIf { it.date == state.date })
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:101       -> RecoveryGlow.kt  CHARGE_HOURS
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:105-168   -> RecoveryGlow.kt  card, RecoveryCardModel, RecoveryTrendDay, trailing, noScoreBody, trend
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:175-187   -> RecoveryGlow.kt  bodySignals, BodySignalsModel
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:191-216   -> RecoveryGlow.kt  chargeCard, ChargeCardModel
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:218-219   -> RecoveryGlow.kt  vsYesterday
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:222-233   -> RecoveryGlow.kt  chargeBuckets, chargeReadout, clock
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:237-254   -> RecoveryGlow.kt  flow, ChargeFlowModel, ChargeFlowRow, ChargeFlowAccent, signed
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:296-339   -> RecoveryGlow.kt  contributors, ContributorsModel, ContributorGroup, ContributorRow, CONTRIBUTORS_FOLDED
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:344-356   -> RecoveryGlow.kt  (BodySignal; built inside signals)
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:360-430   -> RecoveryGlow.kt  signals, SignalsModel, BodySignalTile
    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:466-496   -> RecoveryGlow.kt  howItWorks, HowItWorksModel

Reads added to the screen layer, not copied from Compose:

    app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt:360-384   -> shared/src/commonMain/kotlin/org/openstrap/shared/screens/ScreenQueries.kt  signalHistories, and `SignalHistories` (six month-long reads in one pass)
    (the strap's whole row)                                                     -> ScreenQueries.strapReadiness, which HealthModel now keeps whole (`strapReadinessRow`)

Drawn in Swift, not computed there: `Sparkline` (`GlowRecoveryParts.swift`), the two glow cards'
`GlowStyle`s, the tile and flow-row colours, and the panels' layouts.

What changed in the move, and why:

- `java.time` is `kotlinx.datetime`, as in cards U4 and U5: `Instant.ofEpochSecond(…).atZone(zone)`
  is `Instant.fromEpochSeconds(…).toLocalDateTime(zone)`; `clock` keeps a `zone` overload for the
  tests and a one-argument form that fills in the phone's zone for Swift, because Kotlin/Native does
  not export default arguments.
- Compose passes `Color` values into the flow rows and the signal tiles (`MetricColors.good`,
  `.steps`, `.stress`, `.sleepLight`, `.heart`, `.hrv`, `.temperature`, `.spo2`). `shared/` has no
  colour type, so the models carry a name (`ChargeFlowAccent`, `RecoveryAccent`) and
  `RecoveryScreen.swift` maps it to `Theme.swift`'s token, exactly as `TodayGlow`'s `SignalAccent`
  does.
- `recoveryColor(score, strain)` is `TodayGlow.overviewTone` plus `MetricColors.overview`, which card
  F6 made switch on the enum: the same five bands the Android `overviewColor` returns
  (`GlowTodayScreen.kt:357-363`), so the Recovery card cannot drift from Today's.
- `Narrative.ratingFor` comes from the shared copy (`app/data/Narrative.kt`), whose `Rating` has no
  colour; the four colours are `MetricColors.good` / `.hrv` / `.warn` / `.stress` as the dashboard
  `Narrative.Rating` declares them (`ui/dashboard/Narrative.kt:14-19`), mapped in
  `RecoveryScreen.swift`.
- `signed(v, sign)` truncates with `toInt()`, as Compose does: `Resting HR 54.6` prints 54, not 55.
- The contributors' fold uses Compose's `sortedByDescending { it.factor.weight }.take(3).toSet()`
  over the contributors themselves, then re-filters in the original order, so the rows keep
  Compose's order inside each group rather than the weight order it selected them by.

Tests: `shared/src/jvmTest/kotlin/org/openstrap/shared/screens/RecoveryGlowTest.kt`, 41 tests (the
header note's five forms, the three no-score sentences and which of them is the learning state, the
sleep contributor's note, the strap's estimate and its parts, the strap's fallbacks, the hero's
headline and body, the trend's two-letter weekdays and its empty case, Charge's at-waking note and
its four hint/fact forms, `signed`'s zero, the 96 quarter-hour buckets and their clock readout, the
four flow rows' signs and fractions, the highest/lowest note, the contributors' fold and unfold,
Compose's group order with its rules, the five caveat notes, the six tiles' order/units/accents,
faster/slower vs higher/lower, the skin temperature deviation, a missing reading, the seven-day week
with gaps, the normal note, the alert's and the watch's wording, How it works' comparison and its two
paragraphs, `forDay`, the empty histories and the 30-day window), `TodayQueriesTest.kt` gained one (a
real database: the six signals' month, the four the seed writes every day and the two it never
writes, the window's last day, and a malformed date answering six empty lists) and
`RecoveryQueriesTest.kt` is new, one test over the demo month: the learning state is days 23 to 29
back, 26 previous-day taps land inside it, and yesterday is a scored day. `:shared:jvmTest`
314 -> 358 tests, 0 failures, 1 opt-in skip (measured with the machine's zone in daylight: see the
last section).

### Not copied

- The `left`/`right` two-column layout: the app declares portrait only, so the 600 pt branch is never
  reached; Recovery stacks its blocks in Compose's phone order, as Today does.
- `focus` / `onFocused` and `GlowScreenList`'s `scrollTo`: Android scrolls the Recovery tab to the
  Recovery or Charge section when Today's top card opens it. On iOS the tab's scroll view belongs to
  `DayTab`/`PullSyncScroll`, which the four dated tabs share, so the hook is not this card's to add;
  Today's two taps still open the tab, which is the user-visible step. Recorded in UI-PARITY.md.
- `ui/dashboard/ChargeCard.kt`: the Recovery tab draws the glow `ChargeCard` inside
  `GlowRecoveryScreen.kt:191`, not this dashboard `Panel`; its state strip and legend belong to the
  non-glow dashboard iOS does not have. The parts that do show on the tab (the headline, the two
  empty hints, the at-waking note) are ported from the glow card, which words the note "at waking"
  where the dashboard says "Woke at". Every row of the dashboard file is in UI-PARITY.md with that
  reason.
- The "Training load" section the card's body names: none of the three named Compose files has one.
  `Training load` as a panel title exists only in `GlowActivityScreen.kt:272` (card U8); on Recovery
  the Load factor appears inside "What drove it" (`Recovery.Factor.PreviousDayLoad`) and in "How it
  works". Nothing was invented to fill it; the parity table says so on the row.

### What the CI runs found

Six iOS runs, in order. The Kotlin was green locally throughout; every failure below was Swift, the
UI test's own queries, or another card's recorded flake.

- 39695085373: `RecoveryScreen.swift:228: cannot find type 'RecoveryReadiness' in scope`. The Swift
  name of `Recovery.Readiness`, nested in a Kotlin `object`, is not what the nested-class rule
  suggests, and no iOS file had needed to spell it before (Today passes it straight into `TodayGlow`).
  The contributors panel now takes a `(Bool) -> ContributorsModel` builder, so the type is only ever
  passed through, as on Today.
- 39696188876: `testRecovery` drew the whole tab — the walk, How it works opened, the contributors
  unfolded — then failed at `recovery.body`.firstMatch.exists` with "Failed to get matching snapshot:
  No matches found". XCTest *raises* on a query that matches nothing in this Xcode; it does not answer
  false, and the scored first day has no such text.
- 39699291782: the same step again, with the matches read as a list
  (`matching(identifier:).allElementsBoundByIndex`). Same raise, from the query itself rather than
  from `firstMatch`.
- 39702319788: the same step, enumerating `app.staticTexts` while a day's card re-rendered: the list
  went stale mid-read ("No matches found for Element at index 64"). Three different ways of polling
  the tree, three ways to lose; the state is now named where it is decided instead
  (`RecoveryGlow.card`'s `learning`, the identifier `recovery.learning`), the test steps back a fixed
  26 days and waits once, and `RecoveryQueriesTest` pins the window that depends on.
- 39705831612: red in the Kotlin step, before Swift: `StrapEngineTest.linkDownReportsDisconnected`
  threw a `GattException`. Card F1's recorded leak between the shared device tests (`t_f71167f6`), in
  `shared/.../device/` which this card does not touch. Rerun as fa6b839.
- 39706501713 / 39706497906 and 39708990618 / 39708996602: cancelled, not failed. The iOS workflow has
  `concurrency: cancel-in-progress`, and three cards were pushing reruns to `ios` within minutes of
  each other, so each new push cancelled the run before it.
- 39706541852: `testRecovery` **passed**, with `testEveryTabAndYouScreen`, `testToday` and
  `testPairScreenAppears`. The one red was `testDesignLab`'s recorded "Timed out while evaluating UI
  query" (card F5's flake; the operator's note says rerun once and do not fix).
- 39709841241 (commit e63ee39, which contains every U6 commit): **ALL GREEN**, Android run
  39709845294 green too. The ten U6 screenshots committed here come from that run's artifact.

Two local measurements worth recording, because they cost time to find:

- `:shared:jvmTest` on this machine was red for six *pre-existing* tests at 00:50 local time
  (`HealthQueriesTest.todayCarriesWhatTheScoreFunctionsCompute`, `recoveryIsTheAndroidRecoveryScreen`,
  `DayStateLoaderTest.loadFillsTheDayFromTheSampleMonth`, `recoveryIsScoredAfterTheLearningPeriod`,
  `ScreenQueriesTest.dayIsTheAndroidTodayState`, `recoveryIsScoredAfterTheLearningPeriod`) and green
  at 13:50 with the same tree, 358 tests either way. The sample seed writes only the minutes up to
  "now", so at 01:00 the day it calls today has had an hour, all of it asleep: no steps, no awake
  stress, no evening. The suite is 314 -> 358 tests, 0 failures when `TZ` puts the machine's clock in
  daylight. None of the six is this card's.
- `TZ=Asia/Calcutta` on this machine is not the zone the JVM reads: `/usr/share/zoneinfo/Asia/Calcutta`
  does not exist, so libc falls back to UTC while the JDK's own tzdb resolves +05:30. SQLite's
  `date(…, 'localtime')` and `Clock.System.todayIn` then disagree by a day after 18:30 UTC. The same
  six tests fail. `Asia/Kolkata` or `UTC` keeps them in step.


## Card F7 (the demo day followed the CI clock)

### Authored for iOS, not copied (no upstream source)

| File | Why it is new |
|---|---|
| `shared/src/commonMain/kotlin/org/openstrap/shared/clock/AppClock.kt` | `AppClock`, `PhoneClock`, `DemoDay` and `ScreenClock`. Android reads `LocalDate.now()`, `ZoneId.systemDefault()` and `System.currentTimeMillis()` inline because its only clock is the phone's; the iPhone screens are also photographed by a CI runner, so the reads that decide "what day is it" and "how far into it are we" needed one seam. `DemoDay` is the fixed instant: Wednesday 2026-10-21T09:30Z in UTC |
| `shared/src/jvmTest/kotlin/org/openstrap/shared/screens/DemoDayTest.kt` | The card's step-2 test: the demo month ends at the fixed mid-morning, and two loads with different wall clocks give an equal `DayUiState` |

### What changed in the copied Kotlin

Every read of `Clock.System`, `kotlinx.datetime.todayIn(TimeZone.currentSystemDefault())` and
`TimeZone.currentSystemDefault()` on the screen path now reads `ScreenClock`: `SampleData.load`
(`today` and `nowEpoch`, so the seed ends at the fixed instant), `HealthQueries` (all four screens and
`seedDemoData`), `ScreenQueries` (`todayIso`, `strapReadiness`), `DayStateLoader` (zone defaults, the
`live` steps' "is this today" test), `HealthRepository` (zone defaults, `saveCheckIn`, `logNight`), the
`RecoveryEngine.compute` / `maxHeartRate` defaults, `DayUiState`'s default date, and the zone-less
overloads in `TodayGlow` and `RecoveryGlow`. `RecoveryEngine.compute` keeps its `zone` and `now`
parameters — they were already there; what changed is who supplies them.

`ScreenClock.pinDemo()` is the only switch. `HealthModel.init` calls it when `-demoData` is on in a
debug build, so the pin is in place before the seed and before the first query. A release build
returns `false` from `DemoData.isEnabled` however it is launched, so it keeps the phone's clock. Real
data on a debug build without `-demoData` is unaffected for the same reason.

Swift reads the same day for its own chrome: `HealthModel.today` (the header, the calendar's ring, the
initial selected day) and `TodayFlags.now` (the `-todayMorning` / `-todayEvening` hours, now applied
to the pinned date) come from `DemoDay.shared` in demo mode and from `Date()` otherwise.

### The JVM suite no longer depends on the hour

Six tests in `HealthQueriesTest`, `DayStateLoaderTest`, `ScreenQueriesTest` and `RecoveryQueriesTest`
used to seed "today" from the machine's clock and then assert on it, so they failed between midnight
and waking (card U6 recorded this at 01:00 and 19:20 UTC). They now pin the demo day like the app
does, via `ScreenClock.pinDemo()` in their setup and `DemoDay.TODAY` / `DemoDay.ZONE`. Run at 03:10
UTC — the hour that used to be red — `:shared:jvmTest` is 363 tests, 0 failures.

`SleepGlowTest.kt` (card U7's, untracked and mid-flight) was moved aside for the local runs and
restored byte-for-byte; it references `hasAverage` / `hasSleep`, which no shared class has yet.

(Card U7 landed those classes in the commits after this one: `SleepWindowNight.hasSleep` and
`SleepWindowsModel.hasAverage` are on `ios` now, and `SleepGlowTest` is committed with them.)

## Card U7 (Sleep)

Three copies, all under standing instruction 5 ("if a Compose screen computes a number, date or
sentence beyond plain formatting, copy that function into shared Kotlin … with a JVM test").

### `ui/dashboard/SleepPanels.kt` -> `shared/.../app/ui/dashboard/SleepPanels.kt`

Same package and the same names as the Compose file, so the two read side by side: `SleepVital`,
`sleepVitals(state[, zone])`, `SleepWeek`, `sleepWeek`, `SleepWindowHour`, `SleepWindowNight`,
`SleepWindowsModel`, `sleepWindows`, `minutesClock`, `clockOf`. What changed:

- `java.time` became `kotlinx.datetime` (`Instant.ofEpochSecond(…).atZone(zone)` became
  `Instant.fromEpochSeconds(…).toLocalDateTime(zone)`, `Math.floorMod` is a local helper).
- `Color` became a `SleepAccent` name (`bandColor` -> `bandAccent`), because shared Kotlin has no
  colour type; `Theme.swift` maps the name back.
- `SleepWindows`' `@Composable` canvas stayed in Swift (`GlowSleepWindows.swift`) and its
  arithmetic — minutes since noon, the chart's top and bottom, the hour marks, each bar's span —
  moved into `sleepWindows()`, because where a bar starts is not drawing.
- The vitals' `isOff` extension and the pill's word (`note.removeSuffix(" than usual")`) became
  properties on `SleepVital`, so Swift does not repeat the string surgery.
- The windows' optional fields became `hasSleep` / `hasAverage` flags beside non-optional numbers:
  Kotlin's `Int?` reaches Swift as a boxed `KotlinInt`, and the canvas has no place for that.

Tests: `shared/src/jvmTest/.../screens/SleepGlowTest.kt`.

### `ui/glow/GlowSleepScreen.kt` -> `shared/.../app/ui/glow/SleepGlow.kt`

New file, same package as its source. It holds everything the screen computes: the hero's number,
label, sentence, facts line and arc progress (`hero`); the stage rows with their shares
(`stageRows`); the contributors and their ratings (`contributors`, which also applies
`recovery?.takeIf { it.date == state.date }`, `Recovery.wasoMinutes` and `Narrative.sleepContributors`);
the vitals (`vitals`, a pass-through to `sleepVitals`); the night's two charts and their five-minute
buckets (`nightCharts`, with the private `evenly` copied verbatim); the naps and what opening one
adds (`naps`, `defaultOpenNap`); the week with its regularity row and windows (`week`); the breathing
card (`breathing`); the sleep-health panel (`sleepHealth`); and the chart readout (`readout`).

Unchanged apart from `java.time` -> `kotlinx.datetime`, `Color` -> `SleepAccent`, and no-overload
wrappers that read `TimeZone.currentSystemDefault()` so Swift never has to name a kotlinx-datetime
type (the same shape `RecoveryGlow.chargeReadout` already had). Tests: `SleepGlowTest.kt`, 24 cases.

### `app/.../data/Format.kt` -> `shared/.../app/data/Format.kt` (one flag)

`sleepVitals` prints the skin temperature as `"%+.1f °C"`, and shared `String.format` parsed the `+`
flag without honouring it, so the iPhone would have printed "1.5 °C" where Android prints
"+1.5 °C". The flag is now applied to `%f` and `%d` (Java's rule: a non-negative number gets the
sign, a negative one keeps its own). `FormatTest.kt` (3 cases) pins it. Nothing else in `shared/`
uses `+`, so no other output moved.

### Swift

`iosApp/OpenStrap/Screens/SleepScreen.swift` draws the tab; `iosApp/OpenStrap/Design/GlowSleepWindows.swift`
draws the week's clock. `SleepView.swift`, `HeartView.swift`, `StageBar` and `Stat` are deleted: the
first was card U4's interim Sleep tab, the second was never routed, and the two helpers existed only
for them.

## Card U8 (Activity and workouts)

Three copies and two new reads, all under standing instruction 5.

### `ui/glow/GlowActivityScreen.kt` -> `shared/.../app/ui/glow/ActivityGlow.kt`

New file, same package as its source. It holds everything the tab works out beyond formatting: the
steps goal's sentence, its distance fallback and its two notes (`steps`); the active-minutes row and
its WHO note (`activeMinutes`); the energy panel's three figures and its five-part note (`energy`);
PAI's total, its seven days, its points and its three closing sentences (`pai`); the rolling bar's
segments (`paiSegments`); the load panel's strain/load/fitness/fatigue/form rows, its acute:chronic
row, the strap's weekly load against its productive range, and every note under them (`load`); the
fitness panel's VO2 max, its sparkline values, the recovery time left and the Jurca/FRIEND estimate's
sentence (`fitness`); the workouts panel's day/recent/all split (`workoutsPanel`); and `shortDate`.

Unchanged apart from `java.time` -> `kotlinx.datetime`, `Color` -> an `ActivityAccent` name, and
`LocalDate.now()` / `System.currentTimeMillis()` -> `ScreenClock` (card F7: a `-demoData` run must
draw the same day at any hour). Two pieces are deliberately Kotlin's rather than Swift's:

- `paiSegments(daily, width)` takes the bar's width, because where a segment starts is arithmetic
  (Compose's canvas does it inline at :249-262); the drawing stays `PaiStack`'s.
- the workouts panel receives `WorkoutAnalysis` values so `WorkoutLine` (card U5's port) can draw
  them, rather than Swift slicing the list itself.

### `ui/glow/GlowWorkoutScreen.kt` -> `shared/.../app/ui/glow/WorkoutGlow.kt`

New file, same package. The one workout: the heart card's ten-second buckets, its two labels, its
touch readout and the recovery tail's sentence (`heart`, `heartReadout`); the time split
(`summary`); the recovery rating, its two drops, its bar's fraction and its two sentences
(`recovery`); the five zones with their bpm ranges and shares (`zones`, which also answers
`zoneSeconds.sum() > 0` so Swift never adds them up); the training effect (`effect`); and the
all-workouts list's days (`all`, `dateLine`). `signedDrop` moves here from `ui/workout/WorkoutStyle.kt`
unchanged, and the five zone names and colours are private to it.

### `shared/.../shared/screens/ScreenQueries.kt` (two reads)

- `sportEffects()` - `WorkoutsLoader.effects()`, the strap's sport-effect rows (Android's
  `WorkoutsViewModel.effects`). The load and fitness panels need them and nothing else fetched them.
- `paiWeek(from, to)` - `HealthRepository.dailyPai`'s `Flow<Map<LocalDate, DailyPai>>` flattened to a
  list, oldest first, because neither a `Flow` nor a `Map` crosses into Swift (plan section 6). A
  malformed date is an empty week, like every other read here.

Tests: `ActivityGlowTest.kt` (26 cases), `WorkoutGlowTest.kt` (17), and two new cases in
`ScreenQueriesTest.kt` for the two reads.

### Swift

`iosApp/OpenStrap/Screens/ActivityScreen.swift` draws the tab, `WorkoutScreen.swift` one workout and
`WorkoutsScreen.swift` the whole list. `iosApp/OpenStrap/Design/GlowDetailHeader.swift` is
`GlowMetricDetail.kt`'s header row - shared by both detail screens here and by card U9 later. The
Activity tab's interim panel (`InterimRecoveryPanel` in `RootView.swift`) is deleted now that the
screen exists. `HealthModel` gains `sportEffects`, `paiWeek` and `openWorkout`, read in the same
pass as Today's own data.

### `app/src/debug/kotlin/org/openstrap/app/DemoData.kt` -> `shared/.../app/DemoData.kt`

The demo seed the workout screens needed. Android's emulator demo is not `SampleData.kt` (that copy
writes no workout): it is `DemoData.kt`, called from `MainActivity.kt:104`, which seeds three weeks
with a workout on alternate evenings (and this morning, so Today has one), its `WorkoutHeartRate`
stream, a `SportEffectRow`, `RecoveryHeartRateRow`, `DailyMaxHr` and `PaiSample`, plus sleep SpO2,
HRV SDNN, OSA, stress and skin temperature. `-demoData` now fills from it instead of `SampleData`,
which stays only for the JVM tests that seed it directly (`HealthQueriesTest`, `SampleDataTest`, the
screens' query tests).

Copy is line-for-line with the same `Random(7)` seed, so iOS shows the workout Android's screenshots
show. What changed, as in the `SampleData` copy: `java.time` -> `kotlinx-datetime` via the `Dates.kt`
shims (`atStartOfDay`, `toEpochSecond`, `minusDays`), `ZoneId.systemDefault()` -> `ScreenClock.zone()`,
`LocalDate.now()` -> `ScreenClock.today()`, `System.currentTimeMillis()` -> `ScreenClock.nowEpochSeconds()`
(card F7's fixed demo day still holds), and `Math.PI` -> `kotlin.math.PI` for commonMain. The shared
entities and `HealthDao` methods it calls are already present, so no DAO method needed porting.
`DemoDayTest.kt` now fills `DemoData` and adds a test that the demo day has a workout with its stream
and effect.



## R1 — the visible name differs from upstream's

**The iPhone app's visible name is OpenHealth, and upstream's is OpenStrap.** Card R1 renames what a
person reads on the iPhone — the home-screen label, the wordmark, the usage prompts, the pairing,
You and Recovery sentences, one Diagnostics log line — while the Android app in `app/` is upstream's
and keeps OpenStrap. Nothing that identifies the app or its data to iOS moved: the bundle
identifier, `PRODUCT_NAME`, the target and scheme names, the Swift modules, the `org.openstrap.*`
packages and the seven `org.openstrap.*` / `openstrap.*` / `strap-auth-key` string constants are all
as upstream has them. `RenameTests` (`iosApp/OpenStrapTests/RenameTests.swift`) pins them, and
`docs/ios/UI-PARITY.md` lists every changed sentence with its old text. The one place the two names
sit beside each other on screen is the first row of the licence list, which has to name upstream.

### The icon, copied from the plan pack

```
/opt/data/.hermes/plans/2026-10-22_openhealth-rename/icon/AppIcon.appiconset/ @ 2026-10-22 -> iosApp/OpenStrap/Assets.xcassets/AppIcon.appiconset/
/opt/data/.hermes/plans/2026-10-22_openhealth-rename/icon/{openhealth-icon-full,openhealth-icon-small,openhealth-icon-tinted}.svg @ 2026-10-22 -> iosApp/design/icon/
/opt/data/.hermes/plans/2026-10-22_openhealth-rename/icon/make_icon.js @ 2026-10-22 -> iosApp/design/icon/make_icon.js
```

The appiconset is byte-identical to the pack: 13 PNGs, all 8-bit colour type 2 (opaque RGB, which is
what iOS requires of an app icon) at the sizes their names claim, and the 18-entry `Contents.json`
covering iPhone, iPad and the App Store. `project.yml` sets
`ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`, so actool emits `CFBundleIcons` with
`CFBundleIconName` = `AppIcon`. The pack's comparison sheet and the 1024 px previews are not copied —
the repository carries no other PNGs.

`iosApp/design/icon/` is the artwork's source, not a copy for reference: the three SVGs and
`make_icon.js` regenerate the whole set.

```
cd iosApp/design/icon
npm install --no-save @resvg/resvg-js pngjs
node make_icon.js <outdir>        # writes <outdir>/AppIcon.appiconset + the three 1024 px SVGs
```

Run here with those two packages, `node make_icon.js out` produced an `AppIcon.appiconset`
byte-identical to the committed one (md5 `15c588470112cfb47d128daf7c9db149` over all 14 files).

### The licences upstream's notice needs

```
LICENSE-MIT @ 2026-10-22 -> iosApp/OpenStrap/Design/Licenses/OpenStrap-LICENSE-MIT.txt
LICENSE-APACHE @ 2026-10-22 -> iosApp/OpenStrap/Design/Licenses/OpenStrap-LICENSE-APACHE.txt
```

Both are byte-identical to the repository root (checked with `cmp`) and keep their names: the file
name, not the licence, says whose notice it is. XcodeGen copies `Design/Licenses/` to the bundle root
beside `MIT-Phosphor.txt`, `OFL-Funnel.txt` and `OFL-MuseoModerno.txt`, which is how `LicencesView`
finds them.
