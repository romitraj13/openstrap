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
