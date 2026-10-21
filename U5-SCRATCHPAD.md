# U5 Scratchpad — Today

## Acceptance criteria (from the card)

A. `iosApp/OpenStrap/Screens/TodayScreen.swift` ports, in order: overview card (arc gauge + score
   bars), last night (hypnogram), what changed tiles, heart rate (live toggle), activity, workouts
   rows, naps, check-in, insight bar — with the same show conditions (morning vs later, empty states).
B. Taps go where Android sends them; metric taps push a placeholder detail (route kept for U9).
C. Old `TodayView.swift` deleted.
D. Screenshots `U5-today-1..n` top to bottom + an empty-state day.
E. `python3 iosApp/scripts/ci.py wait` ALL GREEN for the last pushed commit.
F. Screenshots committed; compared with `docs/screenshots/today.png`.
G. `docs/ios/UI-PARITY.md` rows complete, no unported row without a reason.
H. Kotlin verified: `./gradlew :shared:jvmTest :shared:compileKotlinIosArm64`.
I. Forbidden paths untouched (app/, docs/protocol, android.yml, BleDriverImpl, StrapDatabase,
   KeychainSecretStore, HealthKitWriter, ZeppSignIn/, shared/device/, HealthExportQueue.kt).
J. Commit each passing step, push origin ios, summary with test counts + screenshots + differences.

## Android sources (the spec)

- GlowTodayScreen.kt (516): TodayList block order, OverviewCard, Score/ScoreBar/Change,
  readinessState, overviewColor, HeartGlow, WhatChanged/Signal/DotStatTile use, EditPill,
  ActivityGlow, SleepPanel, TodayWorkouts, bucket().
- GlowTodayExtras.kt (159): AlertsPanel + alertText, WeeklyRecapPanel + isRecapDay, TODAY_TILES,
  TilesDialog.
- GlowCheckIn.kt (50): CheckInPanel + ChoicePill.
- NapsPanel.kt (28): the naps panel (Today shows naps inside SleepPanel; NapsPanel is Sleep's).
- ScreenParts.kt (191): chargeLeads (Today's hero rule), avg(), DateHeader (U4).
- components/Insights.kt: **this file is FloatingNavBar/NavItem** (already ported by U2, and its
  parity section exists). There is no "insight bar" in the Compose source: the only insight text on
  Today is the advice line inside What changed (`Narrative.readinessBody`). Recorded as such.

## Shared Kotlin (TDD first)

`shared/src/commonMain/kotlin/org/openstrap/app/ui/glow/TodayGlow.kt` — every number/date/sentence
Today's Compose computes, copied, with JVM tests in
`shared/src/jvmTest/kotlin/org/openstrap/shared/screens/TodayGlowTest.kt`:

- bucket(points, range, seconds)                      GlowTodayScreen.kt:189
- overview(...) -> TodayOverview (lead/second/sleep, verdict, tone, notes)  :205-269
- readinessState(r, estimate, short)                  :287
- overviewTone(score, strain)                         :357
- signalTiles(state, tiles) -> List<SignalTile>       :413-425
- activityGoalText(state, goals)                      :474
- napsLine(naps)                                      :507-510
- sleepStageChart(spans) -> SleepStageChart           GlowSleepScreen.kt:175
- alertLines(alerts) -> List<AlertLine>               GlowTodayExtras.kt:40-65
- weeklyRecap(...) -> WeeklyRecap (facts + note)      GlowTodayExtras.kt:74-123
- isRecapDay(date)                                    :126
- chargeLeads(date, today, chargeLatest, sleepEnd, now, hour)  ScreenParts.kt:183
- isMorning(date, today, hour)                        GlowTodayScreen.kt:146
- todayTiles(names)                                   MainActivity.kt:178-182
- dayWorkouts(workouts, dayStart, dayEnd)             GlowTodayScreen.kt:167

Also `SampleData.load` gets an idempotence guard so the UI test's several launches (`-demoData`
again, for the morning/evening states) do not double the seeded month; JVM test added.

## Swift files

- `Screens/TodayScreen.swift`   the screen + sections + TodayRoute placeholders
- `Screens/TodayModel.swift`    the day's reads (day state, recovery, workouts, check-in, strap
                                readiness) — a small ObservableObject over `HealthModel`
- `Design/GlowBar.swift`        GlowBar (GlowRecoveryScreen.kt:272), shared with Recovery (U6)
- `Design/GlowSleepStages.swift GlowSleepStages + SleepGlow (GlowSleepScreen.kt:108,175), shared
                                with Sleep (U7)
- `Design/GlowWorkoutRow.swift  WorkoutLine (GlowActivityScreen.kt:403, showDate), shared with U8
- `Design/QuietNote.swift`      Note() (GlowRecoveryScreen.kt:295) — the small note under a panel
- delete `TodayView.swift`
- RootView: four tab paths hoisted so Today can push its placeholders; `openRecovery(section)` /
  `openActivity` switch tabs.
- HealthModel: expose the screen reads Today needs through the one `ScreenQueries`.
- UI test: `testToday` (U5 screenshots, morning + evening + empty day), U4 test's `todayScore` wait
  retargeted.
- UI-PARITY + UPSTREAM rows.

## Verification order

1. `./gradlew :shared:jvmTest` RED on the new TodayGlow tests, then GREEN.
2. `./gradlew :shared:compileKotlinIosArm64`.
3. Commit, push, `ci.py wait` (Swift compile is the only Swift check).
4. `screens.py`, look at the PNGs, commit, push again, `ci.py wait` again if anything changed.
