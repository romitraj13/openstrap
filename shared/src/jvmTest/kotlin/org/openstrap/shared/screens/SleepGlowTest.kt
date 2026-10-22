package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.BodySignalsCopy
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.SleepRegularity
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.atZone
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
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
        sleepStages: List<StageSpan> = emptyList(),
    ) = DayUiState(
        date = day,
        sleep = sleep,
        sleepBreakdown = sleepBreakdown,
        nights = nights,
        naps = naps,
        sleepHeartRate = sleepHeartRate,
        breathRate = breathRate,
        sleepStages = sleepStages,
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

    // ---- ContributorsPanel (GlowSleepScreen.kt:86-92, 221-237) ----------------------------------

    private fun recovery(sleepNeed: Int = 480) = RecoveryResult(
        date = day,
        readiness = Recovery.Readiness(
            score = 72,
            contributors = emptyList(),
            baselineNights = 20,
            strainAlert = false,
            alertSignals = emptyList(),
            capped = false,
        ),
        charge = Recovery.ChargeDay(
            points = emptyList(), latest = null, atWake = null, recharged = 0f,
            drainedExercise = 0f, drainedStress = 0f, drainedLiving = 0f,
            highest = null, lowest = null, anchored = false,
        ),
        readinessTrend = emptyList(),
        load = 0f,
        chronicLoad = null,
        restingHr = 55f,
        maxHr = 190f,
        maxHrFromAge = true,
        stressThreshold = Recovery.StressThreshold(60, null, 2),
        sleepNeed = sleepNeed,
    )

    /** The night's stages: 30m deep, 5m awake, then 2h light. */
    private val stagesWithAWake = listOf(
        StageSpan(nightStart, nightStart + 1800, SleepStage.DEEP),
        StageSpan(nightStart + 1800, nightStart + 2100, SleepStage.AWAKE),
        StageSpan(nightStart + 2100, nightStart + 9300, SleepStage.LIGHT),
    )

    /** Total sleep and continuity, rated, with Compose's guidance line under them (:222-236). */
    @Test
    fun contributorsRateTheNightAndSayTheyAreOnlyAGuide() {
        val model = SleepGlow.contributors(
            state = dayState(sleep = session(score = 82), sleepStages = stagesWithAWake),
            recovery = null,
        )

        assertEquals(listOf("Total sleep", "Continuity"), model!!.rows.map { it.label })
        assertEquals("Good", model.rows[0].rating)
        assertEquals("7h 4m of 8h", model.rows[0].detail)
        assertEquals(424f / 480f, model.rows[0].fraction)
        // 5 minutes of wake after falling asleep is inside the free thirty, so continuity is perfect.
        assertEquals("Optimal", model.rows[1].rating)
        assertEquals("5m awake after falling asleep", model.rows[1].detail)
        assertEquals(1f, model.rows[1].fraction)
        assertEquals(
            "Ratings use general sleep guidance (7+ hours, about a fifth each of deep and REM sleep). " +
                "They're a guide, not a medical assessment.",
            model.note,
        )
    }

    /** The card only draws contributors on a scored night (:92). */
    @Test
    fun aNightWithNoScoreHasNoContributors() {
        assertNull(
            SleepGlow.contributors(state = dayState(sleep = session(score = null), sleepStages = stagesWithAWake), recovery = null),
        )
    }

    /** The day's own sleep need sets the target both screens use (`rec = recovery?.takeIf { … }`, :79). */
    @Test
    fun theSleepNeedIsTheDaysOwn() {
        val model = SleepGlow.contributors(
            state = dayState(sleep = session(score = 82), sleepStages = stagesWithAWake),
            recovery = recovery(sleepNeed = 420),
        )
        // 7h 4m = 424 minutes against a 420-minute need: full marks, and the detail says so.
        assertEquals("7h 4m of 7h", model!!.rows[0].detail)
        // The bar itself is not clamped, as Compose's `fraction` is not: GlowBar clamps when it draws.
        assertEquals(424f / 420f, model.rows[0].fraction)
    }

    // ---- NightChartsPanel (GlowSleepScreen.kt:259-284) -------------------------------------------

    @Test
    fun theNightChartsAverageFiveMinutesAtATime() {
        val model = SleepGlow.nightCharts(
            state = dayState(
                sleep = session(),
                sleepHeartRate = listOf(
                    ChartPoint(nightStart + 60, 50f),
                    ChartPoint(nightStart + 120, 52f),
                    ChartPoint(nightStart + 400, 48f),
                ),
                breathRate = listOf(
                    ChartPoint(nightStart + 60, 12f),
                    ChartPoint(nightStart + 200, 13f),
                ),
            ),
            zone = utc,
        )

        assertEquals(listOf("Heart rate", "Breathing"), model!!.rows.map { it.title })
        assertEquals("Lowest 48 · avg 50 bpm", model.rows[0].trailing)
        assertEquals("avg 12.5 br/min", model.rows[1].trailing)
        // Both charts share the night's clock.
        assertEquals(listOf("23:12", "07:01"), model.labels)
        // `evenly(hr, window, 5 * 60)`: a bucket per five minutes across the night, gaps null.
        assertEquals(((nightEnd - nightStart + 1) / 300).toInt(), model.rows[0].values.size)
        assertEquals(51f, model.rows[0].values[0])
        assertNull(model.rows[0].values[2])
        assertEquals(12.5f, model.rows[1].values[0])
        assertEquals(110f, model.rows[0].height)
        assertEquals(90f, model.rows[1].height)
        assertEquals("51 bpm · 23:12", SleepGlow.readout(from = nightStart, index = 0, value = 51f, unit = "bpm", zone = utc))
    }

    /** Neither stream on the night means no panel at all (:265). */
    @Test
    fun aNightWithNoChartsDrawsNoPanel() {
        assertNull(SleepGlow.nightCharts(state = dayState(sleep = session()), zone = utc))
    }

    // ---- NapsPanel (GlowSleepScreen.kt:298-335) --------------------------------------------------

    private val napOne = SleepSession(start = epoch(day, 14), end = epoch(day, 14, 40), nap = true)
    private val napTwo = SleepSession(start = epoch(day, 17), end = epoch(day, 17, 20), nap = true)

    @Test
    fun napsAreOneLineEachUnderTheirTotal() {
        val model = SleepGlow.naps(state = dayState(naps = listOf(napOne, napTwo)), openStart = -1, zone = utc)

        assertEquals("1h", model!!.total)
        assertEquals(listOf("14:00 – 14:40 · 40m", "17:00 – 17:20 · 20m"), model.rows.map { it.line })
        assertEquals(listOf(napOne.start, napTwo.start), model.rows.map { it.start })
        // Folded: no stages and no heart rate worked out for either.
        assertTrue(model.rows.all { it.stages.isEmpty() && it.hrValues.isEmpty() })
        assertNull(model.rows[0].hrNote)
    }

    /** The open nap shows its hypnogram, its stage rows and the day's heart rate across it (:318-331). */
    @Test
    fun theOpenNapShowsItsStagesAndHeartRate() {
        val model = SleepGlow.naps(
            state = dayState(naps = listOf(napOne, napTwo)).copy(
                napStages = mapOf(
                    napOne.start to listOf(StageSpan(napOne.start, napOne.start + 1200, SleepStage.LIGHT)),
                ),
                napBreakdowns = mapOf(
                    napOne.start to Summaries.SleepBreakdown(
                        asleepSeconds = 1200,
                        awakeSeconds = 0,
                        byStage = mapOf(SleepStage.LIGHT to 1200L),
                    ),
                ),
                heartRate = listOf(
                    ChartPoint(napOne.start + 60, 58f),
                    ChartPoint(napOne.start + 300, 61f),
                    ChartPoint(epoch(day, 20), 70f),
                ),
            ),
            openStart = napOne.start,
            zone = utc,
        )

        val open = model!!.rows[0]
        assertEquals(1, open.stages.size)
        assertEquals(listOf("Awake", "REM", "Light", "Deep"), open.stageRows.map { it.label })
        assertEquals("20m", open.stageRows[2].value)
        assertNull(open.stagesNote)
        assertEquals("Heart rate · lowest 58 bpm", open.hrNote)
        assertEquals(58f, open.hrValues[0])
        assertEquals(61f, open.hrValues[1])
        assertEquals(listOf("14:00", "14:40"), open.hrLabels)
        // The other nap stays folded.
        assertTrue(model.rows[1].stages.isEmpty())
    }

    /** A nap the strap staged nothing for says so (:320). */
    @Test
    fun aNapWithoutStagesSaysTheStrapRecordedNone() {
        val model = SleepGlow.naps(state = dayState(naps = listOf(napTwo)), openStart = napTwo.start, zone = utc)
        assertEquals("The strap didn't record stages for this nap.", model!!.rows.single().stagesNote)
        assertTrue(model.rows.single().stageRows.isEmpty())
    }

    /** No naps means no panel (:99). */
    @Test
    fun aDayWithoutNapsDrawsNoPanel() {
        assertNull(SleepGlow.naps(state = dayState(), openStart = -1, zone = utc))
    }

    // ---- WeekPanel + SleepWindows (GlowSleepScreen.kt:337-373, SleepPanels.kt:89-210) -----------

    /** Seven nights, all 23:15 to 07:00 with 6h 30m staged, scored 80. */
    private fun sevenNights(): List<SleepNight> =
        (6 downTo 0).map { ago ->
            val date = day.minusDays(ago.toLong())
            SleepNight(
                date = date,
                session = SleepSession(
                    start = epoch(date.minusDays(1), 23, 15),
                    end = epoch(date, 7),
                    score = 80,
                    deepMinutes = 90,
                    lightMinutes = 240,
                    remMinutes = 60,
                ),
            )
        }

    @Test
    fun theWeekAveragesTheSevenNightsAndCountsYourGoal() {
        val model = SleepGlow.week(state = dayState(nights = sevenNights()), goalSeconds = 8 * 3600L, zone = utc)

        assertTrue(model.hasWeek)
        assertEquals("Last 7 nights", model.trailing)
        assertNull(model.emptyNote)
        assertEquals(listOf("Avg. asleep", "Avg. score", "Reached 8h"), model.facts.map { it.label })
        assertEquals(listOf("6h 30m", "80", "0 of 7"), model.facts.map { it.value })

        // The same bedtime and wake time every night: perfectly regular (SleepPanels.kt:119).
        val regularity = model.regularity!!
        assertEquals("100%", regularity.score)
        assertEquals("Very regular", regularity.band)
        assertEquals(SleepAccent.SleepRem, regularity.accent)
        assertEquals("Fall asleep 23:15", regularity.fallAsleep)
        assertEquals("Wake up 07:00", regularity.wakeUp)
        assertEquals(1f, regularity.fraction)
        assertEquals(
            "Regularity is how much your fall-asleep and wake-up times moved over the last 7 nights: " +
                "100% means the same times every night, 50% about an hour either way.",
            regularity.note,
        )
        assertNull(model.learningNote)
    }

    /** A week of unvarying timing is drawn from 22:00 to 08:00, one bar a night (SleepPanels.kt:147-166). */
    @Test
    fun theWeekDrawsItsBarsOnTheNightsClock() {
        val windows = SleepGlow.week(state = dayState(nights = sevenNights()), goalSeconds = 8 * 3600L, zone = utc).windows!!

        // Minutes since noon, so a night never wraps: 23:15 is 11h 15m, 07:00 is 19h.
        assertEquals(listOf("22:00", "01:00", "04:00", "07:00"), windows.hours.map { it.label })
        assertEquals(listOf(600, 780, 960, 1140), windows.hours.map { it.minute })
        assertEquals(675, windows.averageBedtimeMinute)
        assertEquals(1140, windows.averageWakeMinute)
        assertEquals(7, windows.nights.size)
        assertTrue(windows.hasAverage)
        assertEquals(listOf("Th", "Fr", "Sa", "Su", "Mo", "Tu", "We"), windows.nights.map { it.label })
        assertEquals(listOf(false, false, false, false, false, false, true), windows.nights.map { it.isToday })
        assertTrue(windows.nights.all { it.hasSleep })
        assertEquals(675, windows.nights.last().startMinute)
        assertEquals(1140, windows.nights.last().endMinute)
        assertEquals("6:30", windows.nights.last().asleepLabel)
        assertFalse(windows.nights.last().metGoal)
    }

    /** A night the strap never recorded is a dot on the chart, not a bar (:191-193). */
    @Test
    fun aNightWithoutSleepIsADot() {
        val nights = sevenNights().mapIndexed { index, night -> if (index == 3) night.copy(session = null) else night }

        val windows = SleepGlow.week(state = dayState(nights = nights), goalSeconds = 8 * 3600L, zone = utc).windows!!
        assertFalse(windows.nights[3].hasSleep)
        assertEquals("", windows.nights[3].asleepLabel)
        assertEquals(0, windows.nights[3].startMinute)
        assertEquals(0, windows.nights[3].endMinute)
    }

    /** No sleep in the window at all: the panel's one sentence (:345-348). */
    @Test
    fun aWeekWithNoSleepSaysSo() {
        val model = SleepGlow.week(
            state = dayState(nights = sevenNights().map { it.copy(session = null) }),
            goalSeconds = 8 * 3600L,
            zone = utc,
        )

        assertFalse(model.hasWeek)
        assertEquals("No sleep recorded in the last 7 nights.", model.emptyNote)
        assertNull(model.windows)
        assertTrue(model.facts.isEmpty())
        assertNull(model.regularity)
    }

    /** Fewer than five staged nights leaves the timing unlearned, with Compose's note (:356). */
    @Test
    fun aThinWeekLeavesRegularityLearning() {
        val nights = sevenNights().mapIndexed { index, night -> if (index < 3) night.copy(session = null) else night }

        val model = SleepGlow.week(state = dayState(nights = nights), goalSeconds = 8 * 3600L, zone = utc)
        assertNull(model.regularity)
        assertEquals("Sleep regularity shows once 5 of the last 7 nights have sleep (4 so far).", model.learningNote)
    }

    // ---- BreathingCard (GlowSleepScreen.kt:379-393) ---------------------------------------------

    @Test
    fun breathingShowsTheCountAndWaitsForYourUsual() {
        val learning = SleepGlow.breathing(b = Recovery.BreathingDisturbances(perHour = 3.4f, usual = null, nights = 2))
        assertEquals("3.4 /h", learning.disturbances)
        assertEquals("—", learning.usual)
        assertEquals("Your usual builds after 7 nights with a breathing summary.", learning.note)

        val learnt = SleepGlow.breathing(b = Recovery.BreathingDisturbances(perHour = 3.4f, usual = 2f, nights = 12))
        assertEquals("2.0 /h", learnt.usual)
        assertEquals(
            "Dips in blood oxygen during sleep, counted by the strap. They are fairly steady for each person, " +
                "so they're shown here rather than in Recovery. " + BodySignalsCopy.DISCLAIMER,
            learnt.note,
        )
    }

    // ---- SleepHealthPanel (GlowSleepScreen.kt:399-426) -------------------------------------------

    private val health = SleepRegularity.Health(
        count = 2,
        duration = true,
        continuity = null,
        regularity = true,
        midpointMinutes = 195,
        midpointOutside = true,
    )

    @Test
    fun sleepHealthCountsThreeDimensionsAndNamesTheMidpoint() {
        val model = SleepGlow.sleepHealth(
            reg = SleepRegularity.Result(sri = 80f, validPairs = 7 * 60 * 23, midpointSdMinutes = null),
            health = health,
        )

        assertEquals("2 of 3 good", model.trailing)
        assertEquals("80", model.regularityValue)
        assertEquals("Regular", model.bandValue)
        // 9660 valid pairs over 60 minutes an hour: 161 hours compared.
        assertEquals("How alike your sleep and wake times were, minute by minute, across the last 7 days (161 h compared).", model.note)
        assertEquals(listOf("Duration", "Continuity", "Regularity"), model.rows.map { it.label })
        assertEquals(listOf("Good", "—", "Good"), model.rows.map { it.value })
        assertEquals(
            "Midpoint of your sleep 03:15, outside 01:30–04:30. Shown for interest; it isn't counted.",
            model.midpointNote,
        )
    }

    /** Without an SRI the midpoint fallback stands in, and without either the panel says what it needs (:404-413). */
    @Test
    fun sleepHealthFallsBackToTheMidpointThenSaysWhatItNeeds() {
        val fallback = SleepGlow.sleepHealth(
            reg = SleepRegularity.Result(sri = null, validPairs = 0, midpointSdMinutes = 42f),
            health = null,
        )
        assertEquals("±42 min", fallback.regularityValue)
        // Under an hour of movement is "Steady", as `Result.good` reads it (SleepRegularity.kt:49).
        assertEquals("Steady", fallback.bandValue)
        assertNull(fallback.trailing)
        assertTrue(fallback.rows.isEmpty())
        assertNull(fallback.midpointNote)
        assertEquals(
            "Not enough wear for the regularity index yet, so this is how much your sleep's midpoint moved this week.",
            fallback.note,
        )

        val variable = SleepGlow.sleepHealth(
            reg = SleepRegularity.Result(sri = null, validPairs = 0, midpointSdMinutes = 90f),
            health = null,
        )
        assertEquals("Variable", variable.bandValue)

        val nothing = SleepGlow.sleepHealth(
            reg = SleepRegularity.Result(sri = null, validPairs = 0, midpointSdMinutes = null),
            health = null,
        )
        assertEquals("—", nothing.regularityValue)
        assertEquals("—", nothing.bandValue)
        assertEquals("Regularity needs about 5 days of wear around the clock, night and day.", nothing.note)
    }
}
