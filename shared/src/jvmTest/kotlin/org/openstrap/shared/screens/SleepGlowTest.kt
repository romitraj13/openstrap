package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.atZone
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.SleepNight
import org.openstrap.app.ui.glow.SleepAccent
import org.openstrap.app.ui.glow.SleepGlow

/**
 * What `GlowSleepScreen.kt`, `SleepPanels.kt` and `NapsPanel.kt` compute, copied into `SleepGlow`
 * and `SleepPanels` so the iPhone's Sleep tab reads exactly what the Android screen reads
 * (standing instruction 5). Each test names the Compose lines it holds.
 */
class SleepGlowTest {
    private val utc = TimeZone.UTC

    /** A Wednesday; the night the screen shows ended on it. */
    private val day = LocalDate(2026, 10, 21)

    private fun epoch(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).atZone(utc).epochSeconds

    private val nightStart: Long = epoch(day.minusDays(1), 23, 12)
    private val nightEnd: Long = epoch(day, 7, 1)

    private fun session(
        score: Int? = null,
        wakes: Int? = 3,
        averageHr: Int? = 54,
        start: Long = nightStart,
        end: Long = nightEnd,
    ) = SleepSession(start = start, end = end, score = score, averageHeartRate = averageHr, wakeCount = wakes)

    /** 45m awake, 1h REM, 4h light, 2h 4m deep: 7h 4m asleep inside the 7h 49m session. */
    private val breakdown = Summaries.SleepBreakdown(
        asleepSeconds = 25440,
        awakeSeconds = 2700,
        byStage = mapOf(
            SleepStage.AWAKE to 2700L,
            SleepStage.REM to 3600L,
            SleepStage.LIGHT to 14400L,
            SleepStage.DEEP to 7440L,
        ),
    )

    private fun dayState(
        sleep: SleepSession? = session(),
        sleepBreakdown: Summaries.SleepBreakdown? = breakdown,
        nights: List<SleepNight> = emptyList(),
        naps: List<SleepSession> = emptyList(),
        sleepHeartRate: List<ChartPoint> = emptyList(),
        breathRate: List<ChartPoint> = emptyList(),
    ) = DayUiState(
        date = day,
        sleep = sleep,
        sleepBreakdown = sleepBreakdown,
        nights = nights,
        naps = naps,
        sleepHeartRate = sleepHeartRate,
        breathRate = breathRate,
        dayStart = epoch(day, 0),
        dayEnd = epoch(day, 23, 59),
    )

    // ---- NightCard (GlowSleepScreen.kt:122-171) -------------------------------------------------

    /** The scored hero: the number in the arc, its label, the sentence and the night's facts (:137-163). */
    @Test
    fun theScoredHeroIsTheScoreInItsArc() {
        val hero = SleepGlow.hero(state = dayState(sleep = session(score = 82)), goalSeconds = 8 * 3600L, zone = utc)

        assertFalse(hero.missing)
        assertEquals("23:12 – 07:01", hero.trailing)
        assertEquals("82", hero.value)
        assertEquals("Sleep score", hero.label)
        assertEquals(Narrative.sleepHeadline(82), hero.headline)
        // `listOfNotNull(asleep, "X in bed", "woke N×").joinToString(" · ")` (:157-162).
        assertEquals("7h 4m asleep · 7h 49m in bed · woke 3×", hero.facts)
        assertEquals(0.82f, hero.progress)
        assertTrue(hero.hasScore)
    }

    /** Without a score the arc shows time asleep against your goal, and the clock takes the dots (:141-153). */
    @Test
    fun aNightWithNoScoreShowsTheTimeAsleepAgainstYourGoal() {
        val hero = SleepGlow.hero(state = dayState(sleep = session(score = null)), goalSeconds = 8 * 3600L, zone = utc)

        assertEquals("7:04", hero.value)
        assertEquals("asleep · goal 8h", hero.label)
        assertEquals("7h 4m asleep", hero.headline)
        assertEquals("7h 49m in bed · woke 3×", hero.facts)
        assertEquals(25440f / (8 * 3600).toFloat(), hero.progress)
        assertFalse(hero.hasScore)
    }

    /** No session at all: Compose's two sentences, and no hero (:128-135). */
    @Test
    fun aNightWithNoSleepSaysWhy() {
        val empty = SleepGlow.hero(state = dayState(sleep = null, sleepBreakdown = null), goalSeconds = 8 * 3600L, zone = utc)
        assertTrue(empty.missing)
        assertEquals("No sleep recorded for this night. Wear the strap to bed and sync in the morning.", empty.emptyText)
        assertNull(empty.value)
        assertNull(empty.label)

        val napsOnly = SleepGlow.hero(
            state = dayState(
                sleep = null,
                sleepBreakdown = null,
                naps = listOf(SleepSession(start = epoch(day, 14), end = epoch(day, 14, 40), nap = true)),
            ),
            goalSeconds = 8 * 3600L,
            zone = utc,
        )
        assertEquals("No night sleep recorded, only naps.", napsOnly.emptyText)
    }

    /** No wake count means no "woke N×" on the facts line (:160). */
    @Test
    fun aNightWithoutAWakeCountLeavesItOffTheFacts() {
        val hero = SleepGlow.hero(state = dayState(sleep = session(score = 70, wakes = null)), goalSeconds = 8 * 3600L, zone = utc)
        assertEquals("7h 4m asleep · 7h 49m in bed", hero.facts)
    }

    // ---- StageRows (GlowSleepScreen.kt:200-219) -------------------------------------------------

    /** Awake, REM, Light, Deep: minutes asleep in the stage, its share of the night, and its bar. */
    @Test
    fun stagesAreMinutesAndShareInComposeOrder() {
        val rows = SleepGlow.stageRows(b = breakdown)

        assertEquals(listOf("Awake", "REM", "Light", "Deep"), rows.map { it.label })
        assertEquals(listOf("45m", "1h", "4h", "2h 4m"), rows.map { it.value })
        // `${secs * 100 / total}` with total = awake + asleep = 28140, integer division.
        assertEquals(listOf("9%", "12%", "51%", "26%"), rows.map { it.percent })
        assertEquals(2700f / 28140f, rows[0].fraction)
        assertEquals(
            listOf(SleepAccent.SleepAwake, SleepAccent.SleepRem, SleepAccent.SleepLight, SleepAccent.SleepDeep),
            rows.map { it.accent },
        )
    }

    // ---- VitalTile + sleepVitals (GlowSleepScreen.kt:240-257, SleepPanels.kt:44-87) -------------

    /**
     * Fourteen nights behind the day: HRV and blood oxygen at the same value every night, so
     * tonight's is "Usual"; breathing only tonight, so it is still learning; skin temperature 1.5 °C
     * cooler on every earlier night, so tonight's reads as higher than usual.
     */
    private fun nightsOf(hrv: Float?, spo2: Float?, breathing: Float?, skin: Float?): List<SleepNight> =
        (13 downTo 0).map { ago ->
            val date = day.minusDays(ago.toLong())
            SleepNight(
                date = date,
                session = SleepSession(start = epoch(date.minusDays(1), 23, 15), end = epoch(date, 7), score = null),
                hrv = hrv,
                spo2 = spo2,
                breathing = if (ago == 0) breathing else null,
                skinTemperature = if (ago == 0) skin else skin?.minus(1.5f),
            )
        }

    @Test
    fun vitalsCompareTheNightWithYourNormal() {
        val vitals = SleepGlow.vitals(
            state = dayState(
                sleep = session(averageHr = 54),
                nights = nightsOf(hrv = 60f, spo2 = 96f, breathing = 14f, skin = 34.5f),
                sleepHeartRate = listOf(
                    ChartPoint(epoch(day, 0, 30), 52f),
                    ChartPoint(epoch(day, 2, 15), 48f),
                    ChartPoint(epoch(day, 6, 0), 51f),
                    // Outside the night, so not the night's lowest.
                    ChartPoint(epoch(day, 18, 0), 40f),
                ),
            ),
            zone = utc,
        )

        assertEquals(
            listOf("Lowest heart rate", "Average heart rate", "HRV", "Blood oxygen", "Breathing", "Skin temperature"),
            vitals.map { it.label },
        )
        assertEquals(listOf("48 bpm", "54 bpm", "60 ms", "96 %", "14.0 br/min", "+1.5 °C"), vitals.map { it.value })
        assertEquals("at 02:15", vitals[0].note)
        assertEquals("while asleep", vitals[1].note)
        assertEquals("Usual", vitals[2].note)
        assertFalse(vitals[2].off)
        assertEquals("Usual", vitals[3].note)
        // Only tonight has a breathing value, so no normal yet.
        assertEquals("Learning 0/7 nights", vitals[4].note)
        assertEquals("Higher than usual", vitals[5].note)
        assertTrue(vitals[5].off)
        assertEquals("Higher", vitals[5].pill)
        assertNull(vitals[3].pill)
        assertEquals(
            listOf(Metric.HeartRate, Metric.HeartRate, Metric.Hrv, Metric.Spo2, Metric.Breathing, Metric.SkinTemperature),
            vitals.map { it.metric },
        )
        assertEquals(SleepAccent.Heart, vitals[0].accent)
        assertEquals(SleepAccent.Temperature, vitals[5].accent)
    }

    /** Nothing to compare with and nothing measured leaves the tile out, as `listOfNotNull` does. */
    @Test
    fun vitalsLeaveOutWhatTheNightDoesNotHave() {
        val vitals = SleepGlow.vitals(
            state = dayState(
                sleep = session(averageHr = 54),
                nights = nightsOf(hrv = null, spo2 = null, breathing = null, skin = null),
                sleepHeartRate = emptyList(),
            ),
        )

        assertEquals(listOf("Average heart rate"), vitals.map { it.label })
        assertEquals("54 bpm", vitals.single().value)
    }

    // ---- Everything below arrives with the next slices -------------------------------------------
}
