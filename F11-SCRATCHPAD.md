# F11 scratchpad — Today's last card ends under the floating bar (task t_a2ea904f)

Started from 2ca1bb9 (ios branch, green: iOS 39909415622, Android 39909420159).

## Acceptance criteria

- [ ] A1 `python3 iosApp/scripts/ci.py wait <final full SHA>` -> ALL GREEN.
- [ ] A2 `testEveryDatedTabScrollsClearOfTheBar` prints `F4 Today:` with maxY <= barTop, and the
      `XCTExpectFailure` around the Today check is gone in the same commit (all four tabs asserted).
- [ ] A3 One green run's `U1-designlab-*` / `U2-designlab-*` set committed if the lab output changes
      (it should not).
- [ ] A4 The reason Today's inset misses the late card is stated in docs/ios/UI-PARITY.md or the F4
      comment, whichever the fix makes true.

## What the F10 numbers already fix in place (run 39909415622, bar top 754.0, window 402x874)

    F4 Today:    07:30 · 50m · avg 147 bpm · 561 kcal  maxY=843.65 drags=6 scrolled=1354.0
    F4 Recovery: How it works                           maxY=698.43 drags=7 scrolled=1461.33
    F4 Sleep:    regularity note                        maxY=702.00 drags=8 scrolled=2349.0
    F4 Activity: All workouts                           maxY=689.97 drags=7 scrolled=1824.33

The last text's content coordinate (maxY + scrolled) is 2197.65 on this run and 2197.66 on the one
run that measured correctly (39882557206: 691.99 + 1505.67). Same content, different reachable end:
the scroll stops 151.65 pt (and 127-153 pt across the nine F10 runs) short of the content it holds.

Two candidate mechanisms, and they need different fixes:

A. The scroll *content extent* is fixed by the content that existed at the scroll view's first
   layout. Today's list is drawn before its workouts read lands (HealthModel.reloadToday asks for the
   day and for the workouts separately), so the panel is appended after that first layout: it is
   drawn, but the range never grows to include it. That is a shortfall of the panel (~126-152 pt).
B. The *bottom safe area is dropped from the range* when the content grows after the first layout
   (34 pt home indicator + 112 pt `glowClearsNavBar`), which is 146 pt - also in the observed band.

Both predict "one run in nine works" (the run where the workouts read answered before the day read).
They disagree on what to fix, so F11 measures it instead of guessing: a DEBUG-only probe reports the
enclosing UIScrollView's contentSize / adjustedContentInset / bounds / offset, read by the UI test.

## Plan

1. Probe (temporary, DEBUG only, removed in the final commit): `ScrollGeometryProbe` in
   `PullSyncScroll`; the UI test prints `F11 <tab> geometry:` before and after each walk.
2. One CI run to read the numbers; pick the fix the numbers name.
3. Fix, remove the probe, remove `XCTExpectFailure`, update the F4 comment and docs/ios/UI-PARITY.md.
4. Green run at the final SHA; design lab set only if it changed.
