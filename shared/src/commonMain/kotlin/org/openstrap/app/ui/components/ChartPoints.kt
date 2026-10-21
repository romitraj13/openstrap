package org.openstrap.app.ui.components

/*
 * The three plain data classes `Charts.kt` carries, copied so the shared day state can hold the same
 * shapes as the Android screen: `ChartPoint` (Charts.kt:44), `TrendPoint` (Charts.kt:327) and
 * `StageSpan` (Charts.kt:330). Only these lines were copied — the Compose drawing in that file is
 * Swift's (cards U2, U5-U8), not `shared/`'s.
 */

/** One point of a chart: epoch seconds and its value. */
data class ChartPoint(val x: Long, val y: Float)

/** One day of a trend strip: the day's short label, and its value where there is one. */
data class TrendPoint(val label: String, val value: Float?)

/** One stage of a night as the hypnogram draws it: epoch seconds and `SleepStage`'s code. */
data class StageSpan(val start: Long, val end: Long, val stage: Int)
