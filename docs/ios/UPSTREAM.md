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
in `GlowGeometryTest`), `OpenStrapUITests` stays at 3, but `testDesignLab` now scrolls to
`lab.calendar.title` and attaches `U2-designlab-1` … `U2-designlab-14`.

Two things were decided for iOS and are recorded here rather than hidden:

- `DayCalendar.kt:148`'s enabled test stays in Swift. It is one comparison (`day <= today &&
  (day in available || day == today)`) against the day's own set, and a Kotlin `Set<Int>` reaches
  Swift as a boxed collection; the grid arithmetic around it is in `GlowGeometry`.
- `GlowHorizon`'s sweep-in (Compose redraws the arc each frame over 700 ms) becomes a fade of the
  finished arc, because standing instruction 7 allows only offset, scale and opacity to animate. The
  same rule leaves `DotHeader`'s caret turn instant and `GlowSwitch`'s track colour un-animated.

