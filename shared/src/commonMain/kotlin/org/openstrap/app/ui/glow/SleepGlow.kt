package org.openstrap.app.ui.glow

import kotlinx.datetime.TimeZone
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.format
import org.openstrap.app.data.formatDuration
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.SleepVital
import org.openstrap.app.ui.dashboard.sleepVitals

/*
 * What Compose's Sleep tab computes, in one place Swift can call (card U7).
 *
 * `GlowSleepScreen.kt` works out more than formatting: which number the night leads with, the
 * sentence under it, the night's facts, each stage's share, the contributors' ratings, the vitals'
 * words against your normal, the nap lines and the week's regularity sentence. Standing instruction
 * 5 says such logic is copied into `shared/` with a JVM test rather than written again in Swift, so
 * the two builds cannot drift; the drawing stays Swift's. Every function below names the Compose
 * lines it came from, and is unchanged apart from `java.time` becoming `kotlinx.datetime`, `Color`
 * becoming an accent name for `Theme.swift`, and `Instant`/`DateTimeFormatter` becoming `clockOf`.
 */

/** A colour a Sleep panel passes to Compose as a `Color`; `Theme.swift` maps the name back. */
enum class SleepAccent { Heart, Hrv, Temperature, Spo2, SleepLight, SleepDeep, SleepRem, SleepAwake, Good, Warn, Stress }

/** Everything the night's hero draws (`NightCard`, GlowSleepScreen.kt:122-171). */
data class SleepHeroModel(
    /** "23:12 – 07:01", the header's note (:127). */
    val trailing: String?,
    /** True when the night has no session at all, so the card is the two empty sentences (:128-135). */
    val missing: Boolean,
    /** "No sleep recorded for this night. …", or "No night sleep recorded, only naps." */
    val emptyText: String?,
    /** The dot matrix's text: the score, else the time asleep as "7:04" (:142-145). */
    val value: String?,
    /** "Sleep score", or "asleep · goal 8h" (:147-150). */
    val label: String?,
    /** The sentence under it: `Narrative.sleepHeadline`, or "7h 4m asleep" (:152-155). */
    val headline: String?,
    /** "7h 4m asleep · 7h 49m in bed · woke 3×" (:156-163). */
    val facts: String?,
    /** The arc: score/100, else time asleep against the goal (:141). */
    val progress: Float?,
    val hasScore: Boolean,
)

/** One row of the Stages panel (StageRows, GlowSleepScreen.kt:200-219). */
data class SleepStageRow(
    val label: String,
    /** `formatDuration` of the stage's seconds. */
    val value: String,
    /** Its share of the night, as Compose prints it: "47%". */
    val percent: String,
    /** The bar's length, 0..1. */
    val fraction: Float,
    val accent: SleepAccent,
)

object SleepGlow {

    // ---- NightCard (GlowSleepScreen.kt:122-171) --------------------------------------------------

    /** The hero for [state]'s night, in [zone]. */
    fun hero(state: DayUiState, goalSeconds: Long, zone: TimeZone): SleepHeroModel {
        val session = state.sleep
        val trailing = session?.let { "${clock(it.start, zone)} – ${clock(it.end, zone)}" }
        if (session == null) {
            return SleepHeroModel(
                trailing = trailing,
                missing = true,
                emptyText = if (state.naps.isEmpty()) {
                    "No sleep recorded for this night. Wear the strap to bed and sync in the morning."
                } else {
                    "No night sleep recorded, only naps."
                },
                value = null,
                label = null,
                headline = null,
                facts = null,
                progress = null,
                hasScore = false,
            )
        }
        val asleep = state.asleepSeconds ?: (session.end - session.start)
        val score = session.score
        return SleepHeroModel(
            trailing = trailing,
            missing = false,
            emptyText = null,
            value = score?.toString() ?: "%d:%02d".format(asleep / 3600, asleep % 3600 / 60),
            label = if (score != null) "Sleep score" else "asleep · goal ${formatDuration(goalSeconds)}",
            headline = score?.let { Narrative.sleepHeadline(it) } ?: "${formatDuration(asleep)} asleep",
            facts = listOfNotNull(
                if (score != null) "${formatDuration(asleep)} asleep" else null,
                "${formatDuration(session.end - session.start)} in bed",
                session.wakeCount?.let { "woke $it×" },
            ).joinToString(" · "),
            progress = score?.let { it / 100f } ?: (asleep.toFloat() / goalSeconds),
            hasScore = score != null,
        )
    }

    // ---- StageRows (GlowSleepScreen.kt:200-219) --------------------------------------------------

    /** Minutes and share of each stage, in `SLEEP_NAMES`' order: Awake, REM, Light, Deep. */
    fun stageRows(b: Summaries.SleepBreakdown): List<SleepStageRow> {
        val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
        return SLEEP_NAMES.map { (stage, label) ->
            val secs = b.byStage[stage] ?: 0L
            SleepStageRow(
                label = label,
                value = formatDuration(secs),
                percent = "${secs * 100 / total}%",
                fraction = secs.toFloat() / total,
                accent = SLEEP_ACCENTS.getValue(stage),
            )
        }
    }

    // ---- VitalsPanel (GlowSleepScreen.kt:240-257) ------------------------------------------------

    /** The night's vitals as tiles, in `sleepVitals`' order; each opens its metric. */
    fun vitals(state: DayUiState): List<SleepVital> = sleepVitals(state)

    /** [vitals] in [zone], for the tests. */
    fun vitals(state: DayUiState, zone: TimeZone): List<SleepVital> = sleepVitals(state, zone)

    // ---- Helpers ---------------------------------------------------------------------------------

    /** `SLEEP_NAMES` (ScreenParts.kt:60-65), in the order the map declares them. */
    private val SLEEP_NAMES = listOf(
        SleepStage.AWAKE to "Awake",
        SleepStage.REM to "REM",
        SleepStage.LIGHT to "Light",
        SleepStage.DEEP to "Deep",
    )

    /** `SLEEP_COLORS` (ScreenParts.kt:53-58), as accent names. */
    private val SLEEP_ACCENTS = mapOf(
        SleepStage.AWAKE to SleepAccent.SleepAwake,
        SleepStage.REM to SleepAccent.SleepRem,
        SleepStage.LIGHT to SleepAccent.SleepLight,
        SleepStage.DEEP to SleepAccent.SleepDeep,
    )

    /** "HH:mm" of [epochSeconds] in [zone] (`clock`, GlowSleepScreen.kt:114). */
    fun clock(epochSeconds: Long, zone: TimeZone): String =
        org.openstrap.app.ui.dashboard.clockOf(epochSeconds, zone)
}
