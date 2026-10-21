package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.atZone
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.CheckIn
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.data.db.PaiSample
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.dashboard.Baselines
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.SleepNight
import org.openstrap.app.ui.glow.OverviewTone
import org.openstrap.app.ui.glow.TODAY_TILES
import org.openstrap.app.ui.glow.TodayAccent
import org.openstrap.app.ui.glow.TodayGlow

/**
 * The arithmetic, dates and sentences `GlowTodayScreen.kt`, `GlowTodayExtras.kt`,
 * `GlowCheckIn.kt` and `GlowSleepScreen.kt`'s shared `GlowSleepStages` compute, copied into
 * `TodayGlow` so the iPhone screen reads exactly what the Android screen reads. Each test names the
 * Compose lines it holds.
 */
class TodayGlowTest {
    private val utc = TimeZone.UTC

    /** A Wednesday. */
    private val day = LocalDate(2026, 10, 21)

    private fun epoch(hour: Int, minute: Int, date: LocalDate = LocalDate(2026, 10, 21)): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).atZone(utc).epochSeconds

    private fun epoch(date: LocalDate, hour: Int): Long = epoch(hour, 0, date)

    private fun readiness(
        score: Int?,
        nights: Int = 20,
        withheld: Boolean = false,
        strain: Boolean = false,
    ) = Recovery.Readiness(
        score = score,
        contributors = emptyList(),
        baselineNights = nights,
        strainAlert = strain,
        alertSignals = emptyList(),
        capped = false,
        phase = Recovery.phase(nights),
        withheld = withheld,
    )

    private fun overview(
        chargeLatest: Float? = null,
        chargeAtWake: Float? = null,
        readiness: Recovery.Readiness = readiness(null, nights = 20, withheld = true),
        readinessBefore: Int? = null,
        strapScore: Int? = null,
        sleepScore: Int? = null,
        previousSleepScore: Int? = null,
        chargeLeads: Boolean = false,
    ) = TodayGlow.overview(
        chargeLatest = chargeLatest,
        chargeAtWake = chargeAtWake,
        readiness = readiness,
        readinessBefore = readinessBefore,
        strapScore = strapScore,
        sleepScore = sleepScore,
        previousSleepScore = previousSleepScore,
        chargeLeads = chargeLeads,
    )

    // ---- GlowTodayScreen.kt:189 bucket -------------------------------------------------------------

    @Test
    fun `bucket averages the readings in each five-minute slot and leaves an unmeasured slot null`() {
        val range = 0L..600L
        val points = listOf(ChartPoint(0, 50f), ChartPoint(100, 70f), ChartPoint(590, 61f))

        val buckets = TodayGlow.bucket(points, range, 300)

        // (600 - 0 + 1) / 300 = 2 slots, as Compose's coerceAtLeast(2) leaves them.
        assertEquals(listOf(60f, 61f), buckets)
        // A slot with no reading is a gap the chart breaks its line at, not a zero.
        assertNull(TodayGlow.bucket(listOf(ChartPoint(0, 50f)), range, 300)[1])
    }

    @Test
    fun `bucket never returns fewer than two slots`() {
        assertEquals(2, TodayGlow.bucket(listOf(ChartPoint(0, 50f)), 0L..10L, 300).size)
    }

    // ---- GlowTodayScreen.kt:205-269 OverviewCard ---------------------------------------------------

    @Test
    fun `overview leads with Recovery before Charge takes over`() {
        val card = overview(
            chargeLatest = 70f,
            chargeAtWake = 50f,
            readiness = readiness(80),
            readinessBefore = 60,
            sleepScore = 90,
            previousSleepScore = 85,
        )

        assertFalse(card.chargeLeads)
        assertEquals("Recovery", card.lead.label)
        assertEquals(80, card.lead.value)
        assertEquals(20, card.lead.change)
        assertEquals("vs yesterday", card.lead.against)
        assertEquals(TodayAccent.Recovery, card.lead.accent)
        // The card's other bar is Charge, with how far it moved since waking.
        assertEquals("Charge", card.second.label)
        assertEquals(70, card.second.value)
        assertEquals(20, card.second.change)
        assertEquals("since waking", card.second.against)
        assertEquals(TodayAccent.Charge, card.second.accent)
        // The bar beside it is the night's own score, against the night before.
        assertEquals("Sleep", card.sleep.label)
        assertEquals(90, card.sleep.value)
        assertEquals(5, card.sleep.change)
        assertEquals("vs night before", card.sleep.against)
        assertEquals(TodayAccent.Sleep, card.sleep.accent)
        // The verdict is the leading score's headline, and the glow is the good teal.
        assertEquals("Ready", card.verdict)
        assertEquals(Narrative.readinessHeadline(80, alert = false), card.verdict)
        assertEquals(OverviewTone.Good, card.tone)
    }

    @Test
    fun `overview hands the lead to Charge with Charge's own verdict`() {
        val card = overview(
            chargeLatest = 70f,
            chargeAtWake = 50f,
            readiness = readiness(80),
            readinessBefore = 60,
            sleepScore = 90,
            previousSleepScore = 85,
            chargeLeads = true,
        )

        assertTrue(card.chargeLeads)
        assertEquals("Charge", card.lead.label)
        assertEquals("Recovery", card.second.label)
        assertEquals(Narrative.chargeHeadline(70), card.verdict)
        assertEquals("Good energy", card.verdict)
    }

    @Test
    fun `overview has no verdict without a score and draws its neutral glow`() {
        val card = overview()

        assertNull(card.verdict)
        assertNull(card.lead.value)
        assertNull(card.lead.change)
        assertEquals(OverviewTone.None, card.tone)
    }

    @Test
    fun `overview stands the straps estimate in while our score is withheld`() {
        // Below Recovery.SCORED_NIGHTS the strap's own score speaks, marked as its estimate.
        val card = overview(
            readiness = readiness(null, nights = 3, withheld = true),
            strapScore = 71,
        )

        assertEquals(71, card.lead.value)
        assertTrue(card.lead.estimate)
        assertEquals("Strap's estimate", card.lead.note)
        assertEquals("Strap's estimate, 3/7 nights", card.lead.noteLong)
        // An estimate is not OpenStrap's verdict, so the gauge carries no headline.
        assertNull(card.verdict)
        assertEquals(OverviewTone.None, card.tone)
    }

    @Test
    fun `overview counts the learning nights under the gauge once there is a score`() {
        val card = overview(
            readiness = readiness(64, nights = 9),
            readinessBefore = 60,
        )

        assertEquals("Learning 9/14 nights", card.lead.noteLong)
        assertEquals("Learning 9/14 nights", card.lead.note)
        assertEquals(4, card.lead.change)
    }

    @Test
    fun `overview prefers the body signals wording when the strap is straining`() {
        val card = overview(
            chargeLatest = 80f,
            chargeAtWake = 40f,
            readiness = readiness(40, strain = true),
            readinessBefore = 30,
            sleepScore = 70,
            previousSleepScore = 70,
        )

        assertEquals("Signals off baseline", card.verdict)
        assertEquals(OverviewTone.Strain, card.tone)
    }

    // ---- GlowTodayScreen.kt:287 readinessState ----------------------------------------------------

    @Test
    fun `readiness state words the four cases as the Recovery tab does`() {
        assertEquals("Learning 9/14 nights", TodayGlow.readinessState(readiness(64, nights = 9), estimate = false, short = false))
        assertNull(TodayGlow.readinessState(readiness(64, nights = 20), estimate = false, short = false))
        assertEquals("No sleep yet", TodayGlow.readinessState(readiness(null, nights = 20), estimate = false, short = false))
        assertEquals("Learning 3/7 nights", TodayGlow.readinessState(readiness(null, nights = 3, withheld = true), estimate = false, short = false))
        assertEquals("Needs HRV data", TodayGlow.readinessState(readiness(null, nights = 20, withheld = true), estimate = false, short = false))
    }

    @Test
    fun `readiness state shortens the straps estimate for the half-width bar`() {
        val withheld = readiness(null, nights = 3, withheld = true)
        assertEquals("Strap's estimate", TodayGlow.readinessState(withheld, estimate = true, short = true))
        assertEquals("Strap's estimate, 3/7 nights", TodayGlow.readinessState(withheld, estimate = true, short = false))
        // A night that needs HRV data: the long line names it, kept under thirty characters.
        assertEquals("Strap's estimate, no HRV yet", TodayGlow.readinessState(readiness(null, nights = 20, withheld = true), estimate = true, short = false))
        // Nothing to say about a settled score: the estimate just says what it is.
        assertEquals("Strap's estimate", TodayGlow.readinessState(readiness(72, nights = 20), estimate = true, short = false))
    }

    // ---- GlowTodayScreen.kt:357 overviewColor -----------------------------------------------------

    @Test
    fun `overview tone follows the leading score's three bands, strain first`() {
        assertEquals(OverviewTone.None, TodayGlow.overviewTone(null, strain = false))
        assertEquals(OverviewTone.Strain, TodayGlow.overviewTone(95, strain = true))
        assertEquals(OverviewTone.Good, TodayGlow.overviewTone(70, strain = false))
        assertEquals(OverviewTone.Middling, TodayGlow.overviewTone(69, strain = false))
        assertEquals(OverviewTone.Middling, TodayGlow.overviewTone(50, strain = false))
        assertEquals(OverviewTone.Low, TodayGlow.overviewTone(49, strain = false))
    }

    // ---- ScreenParts.kt:183 chargeLeads ------------------------------------------------------------

    @Test
    fun `charge leads only today, only with a value, and only three hours after waking`() {
        // Woken two hours ago: too recent for Charge to mean the day yet.
        assertFalse(TodayGlow.chargeLeads(day, day, chargeLatest = 60f, sleepEnd = epoch(9, 0), nowEpoch = epoch(11, 0), hour = 11))
        // Three hours awake: it leads.
        assertTrue(TodayGlow.chargeLeads(day, day, chargeLatest = 60f, sleepEnd = epoch(8, 0), nowEpoch = epoch(11, 0), hour = 11))
        // No sleep recorded: it waits for noon.
        assertFalse(TodayGlow.chargeLeads(day, day, chargeLatest = 60f, sleepEnd = null, nowEpoch = epoch(11, 0), hour = 11))
        assertTrue(TodayGlow.chargeLeads(day, day, chargeLatest = 60f, sleepEnd = null, nowEpoch = epoch(12, 0), hour = 12))
        // A past day always leads with its Readiness.
        assertFalse(TodayGlow.chargeLeads(day.minusDays(1), day, chargeLatest = 60f, sleepEnd = epoch(8, 0), nowEpoch = epoch(11, 0), hour = 11))
        // And without a Charge value there is nothing to lead with.
        assertFalse(TodayGlow.chargeLeads(day, day, chargeLatest = null, sleepEnd = epoch(8, 0), nowEpoch = epoch(11, 0), hour = 11))
    }

    // ---- GlowTodayScreen.kt:146 morning ------------------------------------------------------------

    @Test
    fun `only today before noon is morning`() {
        assertTrue(TodayGlow.isMorning(day, day, 11))
        assertFalse(TodayGlow.isMorning(day, day, 12))
        assertFalse(TodayGlow.isMorning(day.minusDays(1), day, 8))
    }

    // ---- GlowTodayExtras.kt:131 TODAY_TILES, MainActivity.kt:178 -----------------------------------

    @Test
    fun `the tile choice keeps the stored order and falls back to all six`() {
        assertEquals(
            listOf(Metric.RestingHr, Metric.Hrv, Metric.Spo2, Metric.SkinTemperature, Metric.Stress, Metric.Breathing),
            TODAY_TILES,
        )
        assertEquals(TODAY_TILES, TodayGlow.todayTiles(null))
        assertEquals(TODAY_TILES, TodayGlow.todayTiles(emptyList()))
        // Unknown names (a metric since renamed) drop out; the stored order is kept.
        assertEquals(
            listOf(Metric.Spo2, Metric.RestingHr, Metric.Stress),
            TodayGlow.todayTiles(listOf("Spo2", "GoneAway", "RestingHr", "Stress")),
        )
        // Nothing recognisable left means all of them, as Compose's takeIf(not empty) leaves it.
        assertEquals(TODAY_TILES, TodayGlow.todayTiles(listOf("GoneAway")))
    }

    // ---- GlowTodayScreen.kt:413-425 What changed's signals ----------------------------------------

    @Test
    fun `signals carry each statistic against its seven-day baseline`() {
        val state = DayUiState(
            date = day,
            restingHeartRate = 55,
            hrv = listOf(ChartPoint(0, 44f), ChartPoint(60, 46f)),
            spo2 = listOf(ChartPoint(0, 97f)),
            skinTemperature = listOf(ChartPoint(0, 33.9f)),
            stress = listOf(ChartPoint(0, 20f)),
            breathRate = listOf(ChartPoint(0, 14f)),
            baselines = Baselines(restingHr = 50f, hrv = 50f, spo2 = 96f, stress = 20f, skinTemperature = 33.2f, breathRate = 15f),
        )

        val signals = TodayGlow.signalTiles(state, TODAY_TILES)

        assertEquals(listOf("Resting HR", "HRV", "Blood oxygen", "Skin temp", "Stress", "Breathing"), signals.map { it.label })
        assertEquals("55", signals[0].value)
        assertEquals("bpm", signals[0].unit)
        assertEquals("50", signals[0].baseline)
        // Five beats above the 1.5 bpm tolerance, and lower is better for a resting heart rate.
        assertEquals(1, signals[0].direction)
        assertEquals(false, signals[0].higherIsBetter)
        assertEquals(Metric.RestingHr, signals[0].target)
        // HRV averages the day's readings; a baseline prints to the same decimals as its value.
        assertEquals("45", signals[1].value)
        assertEquals("50", signals[1].baseline)
        assertEquals(-1, signals[1].direction)
        assertEquals(true, signals[1].higherIsBetter)
        // Skin temperature is the one with a decimal.
        assertEquals("33.9", signals[3].value)
        assertEquals("33.2", signals[3].baseline)
        assertEquals(1, signals[3].direction)
        assertNull(signals[3].higherIsBetter)
        // Stress reads without a unit.
        assertEquals("", signals[4].unit)
        assertEquals(0, signals[4].direction)
        // Breathing is a breath a minute under its baseline, which is outside the one-a-minute tolerance.
        assertEquals(-1, signals[5].direction)
    }

    @Test
    fun `signals drop what has neither value nor baseline, and follow the tile order`() {
        assertTrue(TodayGlow.signalTiles(DayUiState(date = day), TODAY_TILES).isEmpty())

        val state = DayUiState(
            date = day,
            restingHeartRate = 55,
            spo2 = listOf(ChartPoint(0, 97f)),
            baselines = Baselines(restingHr = 50f),
        )
        // Only the three tiles chosen, in the chosen order, and only what has data.
        val signals = TodayGlow.signalTiles(state, listOf(Metric.Spo2, Metric.RestingHr, Metric.Stress))
        assertEquals(listOf("Blood oxygen", "Resting HR"), signals.map { it.label })
        // Blood oxygen has a value but no baseline yet: it shows, with no arrow.
        assertEquals("97", signals[0].value)
        assertNull(signals[0].baseline)
        assertNull(signals[0].direction)
    }

    // ---- GlowTodayScreen.kt:474 ActivityGlow -------------------------------------------------------

    @Test
    fun `activity names the goal progress, the active hours and PAI`() {
        val goals = Goals()
        assertEquals(
            listOf("50% of 8,000", "Active 5 of 10 h"),
            TodayGlow.activityFacts(DayUiState(date = day, steps = 4000, activeHours = 5), goals),
        )
        val reached = DayUiState(date = day, steps = 9000, activeHours = 11, pai = PaiSample(0, 40f, 72.6f, 50, 120))
        assertEquals(
            listOf("Goal reached", "Active 11 of 10 h", "PAI 73"),
            TodayGlow.activityFacts(reached, goals),
        )
    }

    @Test
    fun `the hourly step readout names the hour and its steps`() {
        assertEquals("05:00–06:00 · 1,234 steps", TodayGlow.hourStepReadout(5, 1234.4f))
        assertEquals("23:00–00:00 · 0 steps", TodayGlow.hourStepReadout(23, 0f))
    }

    // ---- GlowTodayScreen.kt:366 HeartGlow ----------------------------------------------------------

    @Test
    fun `the heart card's buckets and readout follow the day`() {
        val state = DayUiState(
            date = day,
            heartRate = listOf(ChartPoint(epoch(7, 0), 60f), ChartPoint(epoch(7, 2), 64f)),
            dayStart = epoch(0, 0),
            dayEnd = epoch(0, 0) + 24 * 3600,
        )

        val buckets = TodayGlow.heartBuckets(state)
        // 86,401 seconds of range in 300-second slots.
        assertEquals(288, buckets.size)
        // 07:00 and 07:02 land in the same slot.
        assertEquals(62f, buckets[7 * 12])

        // The readout names the slot's start, as Compose's clock(state.dayStart + i * 5 * 60) does.
        assertEquals("62 bpm · 07:00", TodayGlow.heartReadout(state.dayStart, 7 * 12, 62f, utc))
    }

    @Test
    fun `the heart card's only fact is the resting rate`() {
        assertEquals(listOf("Resting 52"), TodayGlow.heartFacts(DayUiState(date = day, restingHeartRate = 52)))
        assertTrue(TodayGlow.heartFacts(DayUiState(date = day)).isEmpty())
    }

    // ---- GlowTodayScreen.kt:488 SleepPanel ---------------------------------------------------------

    @Test
    fun `the sleep hero is hours and minutes asleep, with the score's headline`() {
        assertEquals("7:05", TodayGlow.asleepClock(7 * 3600 + 5 * 60))
        assertEquals(listOf("Score 88", "Restful night"), TodayGlow.sleepFacts(88))
        assertTrue(TodayGlow.sleepFacts(null).isEmpty())
    }

    @Test
    fun `the naps line counts the naps, their total and when each began`() {
        val nap1 = SleepSession(start = epoch(13, 0), end = epoch(13, 40), nap = true)
        val nap2 = SleepSession(start = epoch(16, 10), end = epoch(16, 30), nap = true)

        assertEquals("1 nap · 40m · 13:00", TodayGlow.napsLine(listOf(nap1), utc))
        // formatDuration drops the minutes when there are none.
        assertEquals("2 naps · 1h · 13:00, 16:10", TodayGlow.napsLine(listOf(nap1, nap2), utc))
        assertEquals("", TodayGlow.napsLine(emptyList(), utc))
    }

    // ---- GlowSleepScreen.kt:175 GlowSleepStages, shared with Today ---------------------------------

    @Test
    fun `the stage chart lays the night's stages on Awake to Deep rows`() {
        val start = epoch(LocalDate(2026, 10, 20), 23)
        val spans = listOf(
            StageSpan(start, start + 3600, SleepStage.LIGHT),          // Light for the first hour
            StageSpan(start + 3600, start + 7200, SleepStage.DEEP),    // Deep for the second
            StageSpan(start + 7200, epoch(7, 0), SleepStage.REM),      // REM for the rest
        )

        val chart = TodayGlow.sleepStageChart(spans, utc)

        // DayViewModel.SLEEP_STAGE_ROWS: AWAKE 0, REM 1, LIGHT 2, DEEP 3.
        assertEquals(listOf("Awake", "REM", "Light", "Deep"), chart.rowNames)
        assertEquals(listOf("23:00", "07:00"), chart.labels)
        assertEquals(listOf(2, 3, 1), chart.blocks.map { it.row })
        // The fractions are of the whole night, which is eight hours.
        assertEquals(0f, chart.blocks[0].from)
        assertEquals(0.125f, chart.blocks[0].to)
        assertEquals(0.125f, chart.blocks[1].from)
        assertEquals(1f, chart.blocks[2].to)
        // The touch readout names the stage, its span and its minutes.
        assertEquals("Light · 23:00–00:00 · 60 min", chart.blocks[0].readout)
        assertEquals("Deep · 00:00–01:00 · 60 min", chart.blocks[1].readout)
        assertEquals("REM · 01:00–07:00 · 360 min", chart.blocks[2].readout)
    }

    @Test
    fun `the stage chart keeps a zero-length night from dividing by zero`() {
        val start = epoch(LocalDate(2026, 10, 20), 23)
        val chart = TodayGlow.sleepStageChart(listOf(StageSpan(start, start, SleepStage.LIGHT)), utc)
        // end.coerceAtLeast(start + 1) keeps the fractions finite and inside the night.
        assertTrue(chart.blocks.single().from.isFinite())
        assertTrue(chart.blocks.single().to.isFinite())
        assertEquals(0f, chart.blocks.single().to)
        assertEquals(listOf("23:00", "23:00"), chart.labels)
    }

    // ---- GlowTodayExtras.kt:40-65 AlertsPanel ------------------------------------------------------

    @Test
    fun `alert lines use the notification's words, newest first, with the metric they open`() {
        val alerts = listOf(
            HealthAlertRow(timestamp = epoch(9, 5), type = 1, value = 132),
            HealthAlertRow(timestamp = epoch(11, 30), type = 4, value = 88),
            HealthAlertRow(timestamp = epoch(12, 0), type = 3, value = 82),
            HealthAlertRow(timestamp = epoch(8, 0), type = 5, value = 0),
            HealthAlertRow(timestamp = epoch(7, 0), type = 2, value = 41),
            HealthAlertRow(timestamp = epoch(6, 0), type = 9, value = 0),
        )

        val lines = TodayGlow.alertLines(alerts, utc)

        assertEquals(
            listOf(
                "High stress: 82",
                "Low blood oxygen: 88%",
                "High heart rate: 132 bpm",
                "Irregular heart rhythm",
                "Low heart rate: 41 bpm",
                "Alert from the strap",
            ),
            lines.map { it.text },
        )
        assertEquals(listOf("12:00", "11:30", "09:05", "08:00", "07:00", "06:00"), lines.map { it.clock })
        // Stress opens Stress, blood oxygen opens Blood oxygen, everything else the heart.
        assertEquals(
            listOf(Metric.Stress, Metric.Spo2, Metric.HeartRate, Metric.HeartRate, Metric.HeartRate, Metric.HeartRate),
            lines.map { it.metric },
        )
    }

    @Test
    fun `alert lines sort newest first whatever order they arrived in`() {
        val alerts = listOf(
            HealthAlertRow(timestamp = 100, type = 5, value = 0),
            HealthAlertRow(timestamp = 300, type = 5, value = 0),
            HealthAlertRow(timestamp = 200, type = 5, value = 0),
        )
        assertEquals(listOf(300L, 200L, 100L), TodayGlow.alertLines(alerts, utc).map { it.epoch })
    }

    // ---- GlowTodayExtras.kt:74-126 WeeklyRecapPanel ------------------------------------------------

    @Test
    fun `the week counts steps, recovery, sleep and workouts, and says when comparisons start`() {
        // Fourteen days ending yesterday: 7-13 Oct are the week before, 14-20 Oct the week itself.
        val steps = steps(
            LocalDate(2026, 10, 7),
            listOf(3000f, 4000f, 5000f, 6000f, 7000f, 8000f, 9000f, 6000f, 7000f, 8000f, 9000f, 10000f, 11000f, 12000f),
        )
        val state = DayUiState(
            date = day,
            nights = listOf(
                SleepNight(day.minusDays(2), nightEndingOn(day.minusDays(2), hours = 8)),
                SleepNight(day.minusDays(1), nightEndingOn(day.minusDays(1), hours = 8)),
            ),
        )
        val workouts = listOf(workoutAt(epoch(day.minusDays(2), 18)), workoutAt(epoch(day.minusDays(4), 18)))

        val recap = TodayGlow.weeklyRecap(state, recovery = null, workouts = workouts, steps = steps, zone = utc)

        // The seven days before today, first to last.
        assertEquals("14 Oct – 20 Oct", recap.range)
        assertEquals(listOf("Steps a day", "Recovery", "Asleep a night", "Workouts"), recap.facts.map { it.label })
        // 6,000, 7,000, 8,000, 9,000, 10,000, 11,000, 12,000 average 9,000.
        assertEquals("9,000", recap.facts[0].value)
        assertEquals("—", recap.facts[1].value)
        // Both nights ran eight hours.
        assertEquals("8h", recap.facts[2].value)
        assertEquals("2", recap.facts[3].value)
        // 9,000 a day against last week's 6,000 is half as much again; neither week has a night, so no sleep change.
        assertEquals("Steps up 50% than the week before.", recap.note)
    }

    @Test
    fun `the week says comparisons start once a second week of data exists`() {
        val state = DayUiState(date = day)
        val steps = steps(LocalDate(2026, 10, 14), listOf(6000f, 7000f, 8000f, 9000f, 10000f, 11000f, 12000f))

        val recap = TodayGlow.weeklyRecap(state, recovery = null, workouts = emptyList(), steps = steps, zone = utc)

        assertEquals("9,000", recap.facts[0].value)
        assertEquals("0", recap.facts[3].value)
        assertEquals("Comparisons with the week before start once there are two weeks of data.", recap.note)
    }

    @Test
    fun `the week says when steps held steady and sleep was about the same`() {
        val steps = steps(LocalDate(2026, 10, 7), List(14) { 8000f })
        val nights = (14 downTo 1).map { ago ->
            val date = day.minusDays(ago.toLong())
            SleepNight(date, nightEndingOn(date, hours = 8))
        }
        val state = DayUiState(date = day, nights = nights)

        val recap = TodayGlow.weeklyRecap(state, recovery = null, workouts = emptyList(), steps = steps, zone = utc)

        assertEquals("Steps held steady, sleep about the same than the week before.", recap.note)
    }

    @Test
    fun `the week's recovery average is the readiness trend's scored days`() {
        val state = DayUiState(date = day)
        val recovery = RecoveryResult(
            date = day,
            readiness = readiness(70),
            charge = Recovery.ChargeDay(
                points = emptyList(), latest = 60f, atWake = 50f, recharged = 10f, drainedExercise = 5f,
                drainedStress = 2f, drainedLiving = 3f, highest = 70f, lowest = 40f, anchored = true,
            ),
            readinessTrend = listOf(day.minusDays(2) to 60, day.minusDays(1) to 70, day to null),
            load = 0f,
            chronicLoad = null,
            restingHr = 50f,
            maxHr = 190f,
            maxHrFromAge = false,
            stressThreshold = Recovery.StressThreshold(value = 60, calmLevel = 20, days = 30),
        )

        val recap = TodayGlow.weeklyRecap(state, recovery, workouts = emptyList(), steps = emptyList(), zone = utc)

        assertEquals("65", recap.facts[1].value)
    }

    @Test
    fun `the recap day is Monday`() {
        // 2026-10-19 is a Monday; the 21st is a Wednesday.
        assertTrue(TodayGlow.isRecapDay(LocalDate(2026, 10, 19)))
        assertFalse(TodayGlow.isRecapDay(day))
    }

    // ---- GlowTodayScreen.kt:167,175 TodayWorkouts --------------------------------------------------

    @Test
    fun `today's workouts are the selected day's, in start order`() {
        val morning = workoutAt(epoch(9, 0))
        val evening = workoutAt(epoch(18, 0))
        val yesterday = workoutAt(epoch(day.minusDays(1), 18))
        val dayStart = epoch(0, 0)

        val today = TodayGlow.dayWorkouts(listOf(evening, yesterday, morning), dayStart, dayStart + 24 * 3600)

        assertEquals(listOf(morning, evening), today)
    }

    // ---- GlowCheckIn.kt:33 ------------------------------------------------------------------------

    @Test
    fun `the check-in note names both ends of the scale`() {
        assertEquals("1 is ${CheckIn.label(CheckIn.MIN).lowercase()}, 5 is ${CheckIn.label(CheckIn.MAX).lowercase()}.", TodayGlow.checkInNote())
        assertEquals("1 is poor, 5 is great.", TodayGlow.checkInNote())
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    /** [values] are daily totals for consecutive days from [first]. */
    private fun steps(first: LocalDate, values: List<Float>): List<DayStat> = values.mapIndexed { index, value ->
        val date = LocalDate.fromEpochDays(first.toEpochDays() + index)
        DayStat(date, value, value, value, 1)
    }

    /** A main sleep of [hours], ending at 07:00 on [date]. */
    private fun nightEndingOn(date: LocalDate, hours: Int): SleepSession {
        val end = epoch(date, 7)
        return SleepSession(start = end - hours * 3600L, end = end)
    }

    private fun workoutAt(start: Long) = WorkoutAnalysis(
        workout = Workout(
            start = start, end = start + 1800, sportType = 1, exerciseSeconds = 1800, calories = 210,
            averageHeartRate = 140, maxHeartRate = 178, minHeartRate = 96, aerobicTrainingEffect = 2.4f,
            anaerobicTrainingEffect = 0.6f, trainingLoad = 120, vo2max = 45, steps = 3000,
        ),
        end = start + 1800,
        heartRate = emptyList(),
        effect = null,
        recovery60 = null,
        recovery120 = null,
        endHeartRate = null,
        zoneSeconds = emptyList(),
        maxHeartRate = 190,
    )
}
