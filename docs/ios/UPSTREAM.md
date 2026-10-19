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
