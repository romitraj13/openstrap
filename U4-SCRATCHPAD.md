# U4 scratchpad — app shell: five tabs, header, date navigator, You

## Contract checklist (from the card body)

1. RootView: five tabs Today/Recovery/Sleep/Activity/You, icons House/HouseFill, Plant, Moon/MoonFill,
   Footprints, User, in the U2 FloatingNavBar. Each tab its own NavigationStack; a detail opens in
   the current tab.
2. One selected date shared by the four date tabs (as DayViewModel is shared). Header: "OpenStrap."
   wordmark in MuseoModerno with the coloured period, watch icon showing strap state (opens You >
   Strap), date navigator (prev, TODAY or the date with a caret opening DayCalendarSheet, next
   disabled on today).
3. Pull to sync with `.refreshable` calling the existing sync; the header shows sync progress as
   Android's PullSyncReveal does.
4. You tab port of YouScreen.kt: Your profile (ProfileScreen.kt, saving via ScreenQueries), Your
   goals (GoalsDialog.kt), DEVICE section with existing strap + pairing screens restyled in quiet
   panels (logic unchanged), YOUR DATA with Android's privacy sentence + one "Apple Health" row
   (subtitle "Copy your data to Apple Health") driving the existing toggle, Diagnostics, "Licences"
   row listing the three bundled licence texts.
5. Heart and Strap tabs gone. Today/Sleep keep current views; Recovery/Activity show existing
   recovery data in a DotPanel.
6. Screenshots U4-*: each tab, calendar sheet, You and each You sub-screen.
A. ci.py wait ALL GREEN; screenshots committed; UI-PARITY sections for every source file.
B. Networking guard passes; standing-instruction-2 files unchanged (or only screen moves).
C. An XCTest saves profile + goals through ScreenQueries and reads them back.

## Files to create

- `shared/src/commonMain/kotlin/org/openstrap/app/ui/device/DeviceRules.kt` — `ProfileRules`
  (summary, valid, weightText, heightText, toProfile) and `GoalsRules` (valid, toGoals), copied
  from ProfileScreen.kt:56,88-149 and GoalsDialog.kt:28-31,55.
- `shared/src/jvmTest/kotlin/org/openstrap/shared/screens/DeviceRulesTest.kt` — JVM tests.
- `iosApp/OpenStrap/DateHeader.swift` — DateHeader, PullSyncReveal, PullSyncScroll (port of
  ScreenParts.kt:101 DateHeader + PullToSync.kt).
- `iosApp/OpenStrap/YouView.swift` — the You hub (YouScreen.kt).
- `iosApp/OpenStrap/ProfileScreen.swift` — ProfileScreen.kt.
- `iosApp/OpenStrap/GoalsSheet.swift` — GoalsDialog.kt.
- `iosApp/OpenStrap/LicencesView.swift` — the three bundled licence texts.
- `iosApp/OpenStrap/Design/QuietRows.swift` — SectionLabel, YouIcon, SettingRow, LinkRow (port of
  Cards.kt:59 SectionLabel and YouScreen.kt:181-201).
- `iosApp/OpenStrap/Licences/*.txt` — copies of licenses/MIT-Phosphor.txt, OFL-Funnel.txt,
  OFL-MuseoModerno.txt, bundled as resources.
- `iosApp/OpenStrapTests/ScreenQueriesTests.swift` — the XCTest for criterion C.

## Files to modify

- `iosApp/OpenStrap/RootView.swift` — the shell (Tab enum, per-tab NavigationStack, shared date,
  FloatingNavBar, fresh-install Pair).
- `iosApp/OpenStrap/TodayView.swift`, `SleepView.swift` — content only (no NavigationStack/title).
- `iosApp/OpenStrap/HealthModel.swift` — selected date, reload on change, daysWithData, profile,
  goals, save via `ScreenQueries`.
- `iosApp/OpenStrap/StrapController.swift` — `@Published syncRunning` mirroring the engine's
  existing sync events (no behaviour change; the header needs the signal).
- `iosApp/OpenStrap/StrapView.swift`, `PairView.swift` — restyled in quiet panels, logic unchanged.
- `iosApp/OpenStrapUITests/OpenStrapUITests.swift` — five tabs, U4 screenshot walk.
- `docs/ios/UI-PARITY.md`, `docs/ios/UPSTREAM.md`.

## Not ported (recorded in UI-PARITY)

- MainActivity's pager swipe, rail, fold/tabletop, GlassHost/ListDetail side-by-side, BackHandler
  (iOS uses NavigationStack back), recoveryFocus, firmware/alarms/CSV rows: not on iOS.
- YouScreen's Export CSV / Import CSV / Restore from Health Connect rows: iOS has no CSV path and
  the card names only the Apple Health row.
- DeviceScreen's scan/manual-address pairing and Disconnect: no iOS equivalent (logic unchanged).

## Verification plan

- `./gradlew :shared:jvmTest` and `:shared:compileKotlinIosArm64` locally (RED first for the new
  Kotlin rules).
- `python3 iosApp/scripts/ci.py wait` for the pushed commit (Swift only runs in CI).
- `python3 iosApp/scripts/screens.py` after green, then edge-column/dimension checks on the PNGs
  (no vision tool is available in this run: recorded in the summary).

## Notes

- CI round is ~15 min; batch Swift changes, then one push.
- Honcho memory is off for this session (no user peer in honcho.json).
