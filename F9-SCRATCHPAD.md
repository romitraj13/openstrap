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

## Red (commit A)

(paste the run id and the F4 lines here)

## Green (commit B)

(paste)
