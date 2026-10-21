package org.openstrap.shared.screens

import kotlinx.datetime.LocalDate
import org.openstrap.app.data.Summaries
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.dashboard.ABOUT
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.RELATED
import org.openstrap.app.ui.dashboard.SleepNight

/*
 * The few places a Kotlin type crosses into Swift badly, wrapped so Swift gets a plain list or a
 * plain number instead:
 *
 * - a `Map<Long, …>` reaches Swift keyed by boxed `KotlinLong` (and `Map<Int, Long>` by boxed
 *   `KotlinInt`), which no `ForEach` wants to read;
 * - a `Map<Metric, …>` reaches Swift as a dictionary keyed by the enum instance.
 *
 * These are iOS-only additions: the Android screens read the maps directly. Top-level functions in
 * this file reach Swift as static methods on `ScreenAccessors`.
 */

/** One nap's stages, so `DayUiState.napStages` can be read as a list. */
data class NapSpans(val start: Long, val spans: List<StageSpan>)

/** One nap's totals, so `DayUiState.napBreakdowns` can be read as a list. */
data class NapBreakdown(val start: Long, val breakdown: Summaries.SleepBreakdown)

/** The day as `yyyy-MM-dd`, for a title, a picker row or a query for the same day again. */
fun DayUiState.dateIso(): String = date.toString()

/** Every nap's stages, in the order the naps are stored. */
fun DayUiState.napSpanList(): List<NapSpans> = napStages.map { (start, spans) -> NapSpans(start, spans) }.sortedBy { it.start }

/** Every nap's totals, in the same order. Naps without stages have no breakdown and are left out. */
fun DayUiState.napBreakdownList(): List<NapBreakdown> =
    napBreakdowns.map { (start, breakdown) -> NapBreakdown(start, breakdown) }.sortedBy { it.start }

/** True when the strap recorded a nap that day. */
fun DayUiState.hasNaps(): Boolean = naps.isNotEmpty()

/** The seven nights behind the day as a plain list, oldest first (it already is). */
fun DayUiState.nightList(): List<SleepNight> = nights

/** Minutes in one stage of a night, from the `Map<Int, Long>` the breakdown keeps. */
fun Summaries.SleepBreakdown.minutesOf(stage: Int): Int = ((byStage[stage] ?: 0L) / 60).toInt()

/** The metrics the detail screen offers under [metric], as a list instead of a dictionary value. */
fun relatedMetrics(metric: Metric): List<Metric> = RELATED[metric].orEmpty()

/** The About panel's two sentences for [metric]: what it is, then what moves it. */
fun aboutMetric(metric: Metric): List<String> = ABOUT[metric]?.let { listOf(it.first, it.second) }.orEmpty()

/** Today, as the screens' `yyyy-MM-dd`. */
fun isoDate(date: LocalDate): String = date.toString()
