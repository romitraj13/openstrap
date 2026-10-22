# F9 scratchpad — V2 findings: pushed screens under the tab bar, "1 lines", demo header says Yesterday (task t_17fe536a)

## Acceptance criteria (from the card)

- [ ] A1 `ci.py wait <final full SHA>` prints ALL GREEN.
- [ ] A2 The extended end-of-scroll test fails on b204f3d and passes after — for the workout detail.
- [ ] A3 `.glowClearsNavBar()` on `WorkoutScreen`, `WorkoutsScreen`, `MetricDetailScreen` (and every
      other pushed `ScrollView` under `Screens/` that lacks it).
- [ ] A4 Regenerated screenshots show TODAY in the header (U4–U9, R1).
- [ ] A5 `U4-diagnostics-1.png` and `R1-you.png` read "1 line" if the demo log has one line.
- [ ] A6 Docs: the plural exception recorded in `docs/ios/UI-PARITY.md` next to the other two.
- [ ] A7 Release builds keep `Date()` and the phone's zone (unchanged code path).

## Evidence gathered before changing anything (all at b204f3d)

- `grep -rn glowClearsNavBar iosApp/OpenStrap` → only RootView:182 (tab roots), You, ProfileScreen,
  StrapView, PairView, LicencesView ×2, DiagnosticsView. Nothing under `Screens/`.
- `grep -rn "ScrollView {" iosApp/OpenStrap/Screens` → exactly three: WorkoutScreen:29,
  WorkoutsScreen:21, MetricDetailScreen:81. The four tab screens have no ScrollView of their own;
  DayTab wraps them in `PullSyncScroll`, which carries the clearance (RootView:181).
- `Calendar.current` / `isDateInToday` / `isDateInYesterday` / `Date()` on the dated path:
  DateHeader.swift:144 (`private let calendar`), :149 (`isDateInToday` via that calendar), :275-276
  (`label(for:)` asks the *system* clock whether a day is today/yesterday), HealthModel.swift:344
  (`Calendar.current.startOfDay(for: Date())`), RootView.swift:154 (`Calendar.current`).
- The shared clock is already pinned: AppClock.kt `DemoDay` = 2026-10-21T09:30Z, zone UTC, and
  `ScreenClock.pinDemo()` runs from `HealthModel.init` (HealthModel.swift:109). The Swift side was
  the only reader left on the wall clock, so on the CI runner's later real date the header's
  `label(for:)` called the pinned day "Yesterday" while every shared date said Today, and
  `date.next` stayed enabled (`isToday` false).
- `"\(controller.logLines.count) lines"` (YouView.swift:134) is the one count on the You row;
  Android prints `"${lines.size} lines"` (DeviceScreen.kt:453 and the `asRow` branch :456), which
  reads "1 lines" for a one-line log. `StrapController.init` appends exactly one line
  (`append("database \(path): \(summary)")`, StrapController.swift:139), so every demo screenshot
  of the You row reads "1 lines". `DiagnosticsView` shows no count at all today, so its own
  screenshot cannot read "1 line" until the port carries it (Android's `PanelHeader` trailing).

## File plan

1. `iosApp/OpenStrap/ScreenNow.swift` (new) — the one Swift accessor: `date`, `timeZone`,
   `calendar`, `startOfDay`. Demo branch reads `DemoDay` (instant + zone id); release branch is
   `Date()` / `TimeZone.current` / `Calendar.current`, unchanged.
2. `shared/.../clock/AppClock.kt` — `DemoDay.zoneId: String` (`ZONE.id`), so Swift can build a
   Foundation `TimeZone` from the pinned zone without importing kotlinx types. Mirrors the existing
   `isoDate` property that Swift already reads.
3. `iosApp/OpenStrap/HealthModel.swift` — `today`, `iso`, `day(fromIso:)`, `reloadRecapSteps`,
   `isoDaysBefore` go through `ScreenNow`.
4. `iosApp/OpenStrap/DateHeader.swift` — `calendar`, `today`, and `label(for:today:calendar:)` that
   compares days instead of asking `isDateInToday`/`isDateInYesterday`.
5. `iosApp/OpenStrap/RootView.swift` — DayTab's `calendar` and `today`.
6. `iosApp/OpenStrap/Design/DayCalendarSheet.swift` — same calendar, so the sheet's month grid agrees
   with the day the header shows.
7. `iosApp/OpenStrap/StrapController.swift` — `logLineCountText` / `lineCountText(_:)`, the one
   wording.
8. `iosApp/OpenStrap/YouView.swift`, `iosApp/OpenStrap/DiagnosticsView.swift` — both use it; the
   Diagnostics screen's Log heading carries the count with `log.lineCount`.
9. `iosApp/OpenStrap/Screens/WorkoutScreen.swift`, `WorkoutsScreen.swift`,
   `MetricDetailScreen.swift` — `.glowClearsNavBar()`, plus `workout.footnote` and `workouts.last`
   identifiers so F4's walk can measure the last row.
10. `iosApp/OpenStrapUITests/OpenStrapUITests.swift` — the F4 check extended to the workout detail
    (testWorkout), the all-workouts list (testWorkout) and the heart-rate detail (testMetricDetail);
    testEveryTabAndYouScreen asserts TODAY in the header and the plural rule on the You row and the
    Diagnostics heading.
11. `iosApp/OpenStrapTests/ScreenNowTests.swift` (new) — `DateHeader.label` reads "Today" for the
    day passed as today and "Yesterday" for the day before, whatever the host clock says.
12. `docs/ios/UI-PARITY.md` — the third exception, next to the Apple Health and Licences rows.
13. `docs/ios/screenshots/*` — one green run, all U4–U9 and R1.

## Commits

- A: test + identifiers only (`F9: the end-of-scroll walk reaches the workout screens`) → red on CI,
  the numbers from the run are pasted below.
- B: the fixes (clearance, clock accessor, plural rule, docs) → green.
- C: every screenshot from B's green run, one commit.

## Red (commit A, 16f36e8, run 39822446565)

`python3 .scratch/joblog.py 39822446565 .scratch/red-full.txt` (the whole failed job log; `ci.py logs`
keeps only the last 150 lines, which is the artifact upload). The extended walk failed on all three
screens, for the reason the card gives:

    F4 Workout detail: workout.footnote maxY=789.9666666666666 barTop=754.0 window=(0.0, 0.0, 402.0, 874.0)
    F4 All workouts: workouts.last maxY=780.0000000000005 barTop=754.0 window=(0.0, 0.0, 402.0, 874.0)
    error: OpenStrapUITests.swift:674: testWorkout : XCTAssertTrue failed - the floating tab bar still covers the last row of:
    F4 Metric detail: metric.detail.related.Stress maxY=777.9666666666667 barTop=754.0 window=(0.0, 0.0, 402.0, 874.0)
    error: OpenStrapUITests.swift:707: testMetricDetail : failed - Metric detail: metric.detail.related.Stress ends at 777.9666666666667, under the floating bar whose top is 754.0, so the last row cannot be scrolled clear of it

The workout detail's footnote ended **36 pt** under the bar (789.97 against 754.0), the list's last
row 26 pt and the metric detail's last Related row 24 pt.

The two assertions for the other defects fired in the same run, before their walks got that far
(`continueAfterFailure = false` stops a test at the first failure, and both come first):

    error: OpenStrapUITests.swift:115: testEveryTabAndYouScreen : XCTAssertTrue failed - the date navigator does not read TODAY on the pinned demo day
    error: OpenStrapUITests.swift:39: testRename : XCTAssertTrue failed - the date navigator does not read TODAY on the pinned demo day

The plural rule could not fail in that run: the You row's label is only read after the header
assertion, so the run stopped first. The committed R1-you.png / U4-you-1.png read "1 lines" because
`YouView` printed `"\(controller.logLines.count) lines"` and `StrapController.init` appends exactly
one line (`append("database \(path): \(summary)")`).

## Green (commit B, b3d1ea5, run 39826877022)

`python3 iosApp/scripts/ci.py wait b3d1ea5` → `ALL GREEN`. The same three measurements, now clear of
the bar:

    F4 Workout detail: workout.footnote maxY=677.9666666666665 barTop=754.0 window=(0.0, 0.0, 402.0, 874.0)
    F4 All workouts: workouts.last maxY=668.0000000000005 barTop=754.0 window=(0.0, 0.0, 402.0, 874.0)
    F4 Metric detail: metric.detail.related.Stress maxY=665.9666666666667 barTop=754.0 window=(0.0, 0.0, 402.0, 874.0)
    F4 You: you.licences maxY=693.0 barTop=754.0 … (F4's own screens unchanged)
    Test Case '-[OpenStrapUITests.OpenStrapUITests testWorkout]' passed (84.351 seconds).
    Test Case '-[OpenStrapUITests.OpenStrapUITests testMetricDetail]' passed (268.645 seconds).
    Test Case '-[OpenStrapUITests.OpenStrapUITests testEveryTabAndYouScreen]' passed (232.176 seconds).
    Test Case '-[OpenStrapUITests.OpenStrapUITests testRename]' passed (45.067 seconds).
    ** TEST SUCCEEDED **

The workout detail gained 112 pt of clearance (789.97 → 677.97, i.e. `barClearance`), the list
112 pt, the metric detail 112 pt. The header assertion (`staticTexts["TODAY"]`) and the count
assertions (`1 line`) are inside the three tests that passed, so the demo header and the You row /
Diagnostics heading are machine-checked, not eyeballed.

The new unit tests ran on the same run:

    Test Case '-[OpenStrapTests.ScreenNowTests testTheLogCountIsSingularForOneLine]' passed
    Test Case '-[OpenStrapTests.ScreenNowTests testThePinnedDemoDayReadsToday]' passed
    Test Case '-[OpenStrapTests.ScreenNowTests testWithoutDemoDataTheClockIsThePhones]' passed
         Executed 43 tests, with 0 failures (0 unexpected)

And the Kotlin side, locally (`source .ios-env.sh && ./gradlew :shared:jvmTest --tests
org.openstrap.shared.screens.DemoDayTest`): `BUILD SUCCESSFUL`, 4 tests, 0 failures, including
`the demo day's zone id names the zone the seed writes in`.

## Screenshots (commit C)

`python3 iosApp/scripts/screens.py b3d1ea53d755cda0f6369da296e5d13108880df0` → 105 screenshots from
run 39826877022. Every U4–U9 and R1 file is in that list (U4 11, U5 16, U6 10, U7 8, U8 13, U9 11,
R1 4), so V2 reviews one set from one run. Two U2 lab files (U2-designlab-25/26) are not produced by
the current tests any more; they are U2's, not this card's, and are left alone.
