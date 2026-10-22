package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.Narrative
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.atZone
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.glow.ChargeFlowAccent
import org.openstrap.app.ui.glow.RecoveryAccent
import org.openstrap.app.ui.glow.RecoveryGlow
import org.openstrap.app.ui.glow.SignalHistories

/**
 * What `GlowRecoveryScreen.kt` computes, copied into `RecoveryGlow` so the iPhone's Recovery tab
 * reads exactly what the Android screen reads (standing instruction 5). Each test names the Compose
 * lines it holds.
 */
class RecoveryGlowTest {
    private val utc = TimeZone.UTC

    /** A Wednesday. */
    private val day = LocalDate(2026, 10, 21)

    private fun epoch(hour: Int, minute: Int = 0, date: LocalDate = day): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).atZone(utc).epochSeconds

    private fun readiness(
        score: Int? = 72,
        nights: Int = 20,
        withheld: Boolean = false,
        strain: Boolean = false,
        contributors: List<Recovery.Contributor> = emptyList(),
        capped: Boolean = false,
        hrvRange: Recovery.HrvRange? = null,
        hrvWeekNights: Int = 0,
        fallbackHrv: Float? = null,
        fallbackRestingHr: Float? = null,
        qualityFlags: List<String> = emptyList(),
        bodySignals: org.openstrap.app.data.BodySignals.Day? = null,
    ) = Recovery.Readiness(
        score = score,
        contributors = contributors,
        baselineNights = nights,
        strainAlert = strain,
        alertSignals = emptyList(),
        capped = capped,
        phase = Recovery.phase(nights),
        withheld = withheld,
        hrvRange = hrvRange,
        hrvWeekNights = hrvWeekNights,
        fallbackHrv = fallbackHrv,
        fallbackRestingHr = fallbackRestingHr,
        qualityFlags = qualityFlags,
        bodySignals = bodySignals,
    )

    private fun charge(
        latest: Float? = 64f,
        atWake: Float? = 78f,
        recharged: Float = 41f,
        exercise: Float = 9f,
        stress: Float = 3f,
        living: Float = 21f,
        highest: Float? = 88f,
        lowest: Float? = 41f,
        anchored: Boolean = true,
        points: List<Recovery.ChargePoint> = emptyList(),
    ) = Recovery.ChargeDay(
        points = points,
        latest = latest,
        atWake = atWake,
        recharged = recharged,
        drainedExercise = exercise,
        drainedStress = stress,
        drainedLiving = living,
        highest = highest,
        lowest = lowest,
        anchored = anchored,
    )

    private fun result(
        readiness: Recovery.Readiness = readiness(),
        charge: Recovery.ChargeDay = charge(),
        trend: List<Pair<LocalDate, Int?>> = emptyList(),
        threshold: Recovery.StressThreshold = Recovery.StressThreshold(60, null, 2),
    ) = RecoveryResult(
        date = day,
        readiness = readiness,
        charge = charge,
        readinessTrend = trend,
        load = 0f,
        chronicLoad = null,
        restingHr = 55f,
        maxHr = 190f,
        maxHrFromAge = true,
        stressThreshold = threshold,
    )

    private fun strap(score: Int? = 81, hrv: Int? = 70, rhr: Int? = 66, temp: Int? = null, breathing: Int? = null) =
        StrapReadinessRow(
            day = epoch(0),
            updatedAt = epoch(7),
            score = score,
            hrvScore = hrv,
            restingHrScore = rhr,
            skinTemperatureScore = temp,
            breathingScore = breathing,
            sleepHrv = null,
            sleepRestingHr = null,
            algorithmVersion = "1",
        )

    private fun stat(date: LocalDate, value: Float) = DayStat(date, value, value, value, 1)

    // ---- RecoveryCard's header note (GlowRecoveryScreen.kt:118-128) ------------------------------

    @Test
    fun withheldShowsTheNightsTowardAScore() {
        val card = RecoveryGlow.card(result(readiness(score = null, nights = 3, withheld = true)), null)
        assertEquals("Learning 3/7 nights", card.trailing)
    }

    @Test
    fun learningShowsTheNightsTowardASettledBaseline() {
        val card = RecoveryGlow.card(result(readiness(score = 68, nights = 9)), null)
        assertEquals("Learning 9/14 nights", card.trailing)
    }

    @Test
    fun calibratedSaysSoAndHowFarItMoved() {
        val trend = listOf(
            LocalDate(2026, 10, 19) to 70,
            LocalDate(2026, 10, 20) to 68,
            day to 74,
        )
        val card = RecoveryGlow.card(result(readiness(score = 74, nights = 30), trend = trend), null)
        assertEquals("Calibrated · +6 vs yesterday", card.trailing)
    }

    @Test
    fun aScoredDaySaysHowFarItMovedFromYesterday() {
        val trend = listOf(LocalDate(2026, 10, 20) to 74, day to 71)
        val card = RecoveryGlow.card(result(readiness(score = 71, nights = 20), trend = trend), null)
        assertEquals("−3 vs yesterday", card.trailing)
    }

    @Test
    fun anUnchangedScoreIsSteady() {
        val trend = listOf(LocalDate(2026, 10, 20) to 72, day to 72)
        val card = RecoveryGlow.card(result(readiness(score = 72), trend = trend), null)
        assertEquals("Steady vs yesterday", card.trailing)
    }

    @Test
    fun withNoYesterdayThereIsNoComparison() {
        val trend = listOf<Pair<LocalDate, Int?>>(day to 72)
        val card = RecoveryGlow.card(result(readiness(score = 72), trend = trend), null)
        assertNull(card.trailing)
    }

    // ---- The no-score sentences (GlowRecoveryScreen.kt:129-155) ----------------------------------

    @Test
    fun noSleepSaysToWearTheStrapToBed() {
        val card = RecoveryGlow.card(result(readiness(score = null, withheld = false)), null)
        assertEquals("Recovery is scored each morning from the night's sleep. Wear the strap to bed.", card.body)
    }

    @Test
    fun withheldSaysWhatTheScoreNeeds() {
        val card = RecoveryGlow.card(result(readiness(score = null, nights = 5, withheld = true)), null)
        assertEquals(
            "OpenStrap scores Recovery once it has 7 good nights of your own HRV and resting heart rate.",
            card.body,
        )
    }

    @Test
    fun aNightWithoutCleanReadingsSaysSo() {
        val card = RecoveryGlow.card(result(readiness(score = null, nights = 20, withheld = true)), null)
        assertEquals(
            "Last night's HRV and resting heart rate didn't have enough clean readings, so there's no Recovery "
                + "score today. Sleep alone isn't readiness.",
            card.body,
        )
    }

    @Test
    fun onlyTheWithheldStateCallsItselfLearning() {
        // Before enough nights: the learning state the UI test walks back to.
        val learning = RecoveryGlow.card(result(readiness(score = null, nights = 4, withheld = true)), null)
        assertTrue(learning.learning)
        // A scored day is not learning, whatever its phase.
        assertTrue(!RecoveryGlow.card(result(readiness(score = 72, nights = 9)), null).learning)
        // No sleep at all, and a night whose readings failed: neither is the learning state.
        assertTrue(!RecoveryGlow.card(result(readiness(score = null, withheld = false)), null).learning)
        assertTrue(!RecoveryGlow.card(result(readiness(score = null, nights = 20, withheld = true)), null).learning)
    }

    @Test
    fun theSleepContributorIsNotedUnderTheSentence() {
        val contributors = listOf(Recovery.Contributor(Recovery.Factor.Sleep, 64, "6h 52m asleep"))
        val card = RecoveryGlow.card(result(readiness(score = null, withheld = false, contributors = contributors)), null)
        assertEquals("Sleep 64 · 6h 52m asleep", card.sleepNote)
    }

    @Test
    fun theStrapsEstimateShowsItsPartsOnlyWhileOursIsWithheld() {
        val ready = readiness(score = null, nights = 4, withheld = true)
        val withStrap = RecoveryGlow.card(result(ready), strap(score = 81, hrv = 70, rhr = 66))
        assertEquals("81", withStrap.strapValue)
        assertEquals(
            "HRV 70 · resting HR 66. Shown for reference; it never enters OpenStrap's score.",
            withStrap.strapNote,
        )
        // Past the withheld phase the strap's score stays out of this card entirely.
        val settled = RecoveryGlow.card(result(readiness(score = 72, nights = 20)), strap(score = 81))
        assertNull(settled.strapValue)
        assertNull(settled.strapNote)
    }

    @Test
    fun theStrapsFallbacksAreNotedWhenItsOwnReadingsFailed() {
        val card = RecoveryGlow.card(
            result(readiness(score = null, nights = 20, withheld = true, fallbackHrv = 61.4f, fallbackRestingHr = 54.6f)),
            null,
        )
        assertEquals(
            listOf(
                "HRV 61 ms (strap fallback)",
                "Resting HR 54 bpm (strap fallback)",
            ),
            card.notes,
        )
    }

    @Test
    fun theHeroCarriesItsHeadlineAndBody() {
        val ready = readiness(score = 74)
        val card = RecoveryGlow.card(result(ready), null)
        assertEquals(74, card.score)
        assertEquals(listOf(Narrative.readinessHeadline(74, false)), card.heroFacts)
        assertEquals(Narrative.readinessBody(ready), card.heroBody)
        assertNull(card.body)
    }

    // ---- The seven-day trend (GlowRecoveryScreen.kt:159-165) -------------------------------------

    @Test
    fun theTrendIsSevenTwoLetterWeekdaysOldestFirst() {
        val trend = (6 downTo 0).map { back -> day.minusDays(back.toLong()) to (60 + back) }
        val card = RecoveryGlow.card(result(readiness(score = 74, nights = 30), trend = trend), null)
        assertEquals(listOf("Th", "Fr", "Sa", "Su", "Mo", "Tu", "We"), card.trend.map { it.label })
        assertEquals(listOf(66f, 65f, 64f, 63f, 62f, 61f, 60f), card.trend.map { it.value })
        assertTrue(card.hasTrend)
    }

    @Test
    fun aTrendWithNoScoresDrawsNoChart() {
        val trend = (2 downTo 0).map { back -> day.minusDays(back.toLong()) to null }
        val card = RecoveryGlow.card(result(readiness(score = null, withheld = false), trend = trend), null)
        assertTrue(card.trend.all { it.value == null })
        assertTrue(!card.hasTrend)
    }

    // ---- Charge (GlowRecoveryScreen.kt:191-216) --------------------------------------------------

    @Test
    fun chargeSaysWhenYouWokeAndWhatFilledAndDrained() {
        val model = RecoveryGlow.chargeCard(charge(atWake = 78.6f), epoch(0), epoch(0) + 86_399)
        assertEquals("78 at waking", model.trailing)
        assertEquals("64", model.value)
        assertEquals("/ 100", model.unit)
        // "${Narrative.chargeHeadline(64)}", "${signed(41,+1)} · ${signed(33,-1)}"
        assertEquals(listOf("Good energy", "+41 · −33"), model.facts)
    }

    @Test
    fun chargeHintsWhenItHasNothingToShow() {
        val unanchored = RecoveryGlow.chargeCard(charge(anchored = false), epoch(0), epoch(0) + 86_399)
        assertEquals(
            "Charge starts after your first night wearing the strap. Sleep is what fills it, so it needs one to measure from.",
            unanchored.hint,
        )
        val empty = RecoveryGlow.chargeCard(charge(latest = null), epoch(0), epoch(0) + 86_399)
        assertEquals("Charge appears once the strap has a few hours of heart rate.", empty.hint)
        assertNull(empty.value)
        assertNull(RecoveryGlow.chargeCard(charge(), epoch(0), epoch(0) + 86_399).hint)
    }

    @Test
    fun aZeroAmountPrintsWithoutASign() {
        assertEquals("0", RecoveryGlow.signed(0.4f, 1))
        assertEquals("−33", RecoveryGlow.signed(33.7f, -1))
        assertEquals("+41", RecoveryGlow.signed(41f, 1))
    }

    @Test
    fun theDayLineIsAQuarterHourBucketsAverages() {
        val points = listOf(
            Recovery.ChargePoint(epoch(7, 0), 60f, Recovery.State.Sleep),
            Recovery.ChargePoint(epoch(7, 5), 70f, Recovery.State.Sleep),
            Recovery.ChargePoint(epoch(7, 14), 80f, Recovery.State.Sleep),
            Recovery.ChargePoint(epoch(9, 30), 40f, Recovery.State.Relax),
        )
        val model = RecoveryGlow.chargeCard(charge(points = points), epoch(0), epoch(0) + 86_399)
        // 24 h at 15 minutes is 96 slots, 07:00-07:14 in the first of them and 09:30 in the tenth.
        assertEquals(96, model.values.size)
        assertEquals(70f, model.values[7 * 4])
        assertEquals(40f, model.values[9 * 4 + 2])
        assertNull(model.values[0])
        assertEquals(listOf("00", "06", "12", "18", "24"), model.labels)
    }

    @Test
    fun aChargePointReadsAsItsValueAndClock() {
        // The zone is pinned: the day starts at midnight UTC, so 07:30 is 07:30 whatever the phone's.
        assertEquals("62 · 07:30", RecoveryGlow.chargeReadout(epoch(0), 7 * 4 + 2, 62.4f, utc))
    }

    // ---- Where your charge went (GlowRecoveryScreen.kt:237-254) ---------------------------------

    @Test
    fun theFlowRowsAreSignedAndFractionedAgainstTheBiggerSide() {
        val model = RecoveryGlow.flow(result(charge = charge(recharged = 41f, exercise = 9f, stress = 3f, living = 21f)))
        assertEquals(
            listOf("Sleep and calm rest", "Movement and exercise", "Physiological stress (estimate)", "Being awake"),
            model.rows.map { it.label },
        )
        assertEquals(listOf("+41", "−9", "−3", "−21"), model.rows.map { it.value })
        val total = 41f // max(41, 9 + 3 + 21 = 33, 1)
        assertEquals(listOf(1f, 9f / total, 3f / total, 21f / total), model.rows.map { it.fraction })
        assertEquals(
            listOf(ChargeFlowAccent.Good, ChargeFlowAccent.Steps, ChargeFlowAccent.Stress, ChargeFlowAccent.SleepLight),
            model.rows.map { it.accent },
        )
    }

    @Test
    fun theFlowNotesTheHighestAndLowestAndWhereStressCounts() {
        val threshold = Recovery.StressThreshold(60, null, 1)
        val model = RecoveryGlow.flow(result(charge = charge(highest = 88.4f, lowest = 41.6f), threshold = threshold))
        assertEquals("Highest 88 · lowest 41 this day", model.highLow)
        assertEquals(Narrative.stressThreshold(threshold), model.stressNote)
        // No highest or lowest recorded: no note, rather than a made-up one.
        assertNull(RecoveryGlow.flow(result(charge = charge(highest = null, lowest = null))).highLow)
    }

    // ---- What drove it (GlowRecoveryScreen.kt:296-339) ------------------------------------------

    private fun contributors(n: Int) = Recovery.Factor.entries.take(n).map {
        Recovery.Contributor(it, 50 + n, "detail for ${it.label}")
    }

    @Test
    fun contributorsFoldToTheThreeHeaviestUntilOpened() {
        val ready = readiness(contributors = contributors(5))
        val folded = RecoveryGlow.contributors(ready, open = false)
        assertEquals("Top 3 of 5", folded.trailing)
        assertTrue(folded.foldable)
        assertEquals(3, folded.groups.sumOf { it.rows.size })
        // HRV balance (0.30), resting heart rate (0.22) and last night's sleep (0.16) are the heaviest.
        assertEquals(
            listOf("HRV balance", "Resting heart rate", "Last night's sleep"),
            folded.groups.flatMap { it.rows }.map { it.label },
        )
        val open = RecoveryGlow.contributors(ready, open = true)
        assertNull(open.trailing)
        assertEquals(5, open.groups.sumOf { it.rows.size })
    }

    @Test
    fun contributorsKeepComposesGroupOrderWithRulesBetweenThem() {
        val ready = readiness(
            contributors = listOf(
                Recovery.Contributor(Recovery.Factor.PreviousDayLoad, 44, "yesterday was hard"),
                Recovery.Contributor(Recovery.Factor.Sleep, 71, "7h 4m asleep"),
                Recovery.Contributor(Recovery.Factor.HrvBalance, 88, "HRV 62 ms"),
            ),
        )
        val model = RecoveryGlow.contributors(ready, open = true)
        assertEquals(listOf("BODY", "SLEEP", "LOAD"), model.groups.map { it.name })
        assertEquals(listOf(false, true, true), model.groups.map { it.afterDivider })
        val hrv = model.groups[0].rows.single()
        assertEquals("HRV balance", hrv.label)
        assertEquals("Optimal", hrv.rating)
        assertEquals("HRV 62 ms", hrv.detail)
        assertEquals(0.88f, hrv.fraction)
        assertEquals("Pay attention", model.groups[2].rows.single().rating)
    }

    @Test
    fun aShortContributorListDoesNotFold() {
        val model = RecoveryGlow.contributors(readiness(contributors = contributors(3)), open = false)
        assertTrue(!model.foldable)
        assertNull(model.trailing)
        assertEquals(3, model.groups.sumOf { it.rows.size })
    }

    @Test
    fun theContributorNotesCoverEveryCaveatComposePrints() {
        val ready = readiness(
            contributors = contributors(2),
            capped = true,
            hrvRange = Recovery.HrvRange.Below,
            hrvWeekNights = 4,
            fallbackHrv = 61.2f,
            fallbackRestingHr = 54.6f,
            qualityFlags = listOf("HRV and resting HR disagree"),
        )
        assertEquals(
            listOf(
                "Capped: one body signal is far from your normal, so good sleep can't fully make up for it.",
                "7-night HRV is below your normal range (thin: 4 of 7 nights).",
                "HRV 61 ms (strap fallback): our own reading wasn't clean enough last night.",
                "Resting HR 54 bpm (strap fallback): our own reading wasn't clean enough last night.",
                "Check: HRV and resting HR disagree",
            ),
            RecoveryGlow.contributors(ready, open = true).notes,
        )
        // A full week says so without the thin note.
        val full = readiness(contributors = contributors(1), hrvRange = Recovery.HrvRange.Within, hrvWeekNights = 7)
        assertEquals(listOf("7-night HRV is within your normal range."), RecoveryGlow.contributors(full, open = true).notes)
    }

    // ---- Body signals (GlowRecoveryScreen.kt:360-452) -------------------------------------------

    private fun historyOf(value: Float, days: Int = 30) =
        (0 until days).map { back -> stat(day.minusDays(back.toLong()), value) }

    private fun histories(
        rhr: Float = 55f,
        hrv: Float = 60f,
        skin: Float = 33.5f,
        breathing: Float = 15f,
        spo2: Float = 96f,
        stress: Float = 30f,
    ) = SignalHistories(
        restingHr = historyOf(rhr),
        hrv = historyOf(hrv),
        skinTemperature = historyOf(skin),
        breathing = historyOf(breathing),
        spo2 = historyOf(spo2),
        stress = historyOf(stress),
    )

    private fun stateWith(
        restingHr: Int? = 55,
        hrv: List<ChartPoint> = emptyList(),
        skin: List<ChartPoint> = emptyList(),
        breathing: List<ChartPoint> = emptyList(),
        spo2: List<ChartPoint> = emptyList(),
        stress: List<ChartPoint> = emptyList(),
    ) = DayUiState(
        date = day,
        restingHeartRate = restingHr,
        hrv = hrv,
        skinTemperature = skin,
        breathRate = breathing,
        spo2 = spo2,
        stress = stress,
        dayStart = epoch(0),
        dayEnd = epoch(0) + 86_399,
    )

    @Test
    fun theSixTilesComeInComposesOrderWithTheirUnits() {
        val model = RecoveryGlow.signals(stateWith(), histories())
        assertEquals(
            listOf("Resting HR", "HRV", "Skin temp", "Breathing", "Blood oxygen", "Stress"),
            model.tiles.map { it.label },
        )
        assertEquals(
            listOf("bpm", "ms", "°C", "br/min", "%", ""),
            model.tiles.map { it.unit },
        )
        assertEquals(
            listOf(
                RecoveryAccent.Heart, RecoveryAccent.Hrv, RecoveryAccent.Temperature,
                RecoveryAccent.SleepLight, RecoveryAccent.Spo2, RecoveryAccent.Stress,
            ),
            model.tiles.map { it.accent },
        )
        assertEquals(
            listOf(Metric.RestingHr, Metric.Hrv, Metric.SkinTemperature, Metric.Breathing, Metric.Spo2, Metric.Stress),
            model.tiles.map { it.metric },
        )
    }

    @Test
    fun heartRateIsFasterOrSlowerAndEverythingElseHigherOrLower() {
        // Above the 30-day normal for resting HR: "Faster"; the same move on HRV: "Higher".
        val high = RecoveryGlow.signals(
            stateWith(restingHr = 62, hrv = listOf(ChartPoint(epoch(3), 70f))),
            histories(rhr = 55f, hrv = 60f),
        )
        assertEquals("Faster", high.tiles[0].word)
        assertEquals("Higher", high.tiles[1].word)
        val low = RecoveryGlow.signals(
            stateWith(restingHr = 48, hrv = listOf(ChartPoint(epoch(3), 50f))),
            histories(rhr = 55f, hrv = 60f),
        )
        assertEquals("Slower", low.tiles[0].word)
        assertEquals("Lower", low.tiles[1].word)
        // Inside the normal: the pill says Normal, in the good colour.
        val normal = RecoveryGlow.signals(stateWith(restingHr = 55), histories())
        assertEquals("Normal", normal.tiles[0].word)
        // No value at all: no word, so the tile keeps the slot with nothing in it.
        assertNull(RecoveryGlow.signals(stateWith(restingHr = null), histories()).tiles[0].word)
    }

    @Test
    fun skinTemperatureIsShownAsItsDeviationFromYourNormal() {
        val model = RecoveryGlow.signals(
            stateWith(skin = listOf(ChartPoint(epoch(3), 34f), ChartPoint(epoch(4), 34.4f))),
            histories(skin = 33.5f),
        )
        // mean 34.2 against a normal of 33.5: "+0.7", one decimal as Metric.SkinTemperature formats.
        assertEquals("+0.7", model.tiles[2].value)
        // Before there is a normal, it shows the level itself.
        val early = RecoveryGlow.signals(stateWith(skin = listOf(ChartPoint(epoch(3), 34f))), SignalHistories())
        assertEquals("34.0", early.tiles[2].value)
    }

    @Test
    fun aTileWithNoReadingTodayHasNoValueAndNoWord() {
        val model = RecoveryGlow.signals(stateWith(restingHr = null), histories())
        assertNull(model.tiles[0].value)
        assertNull(model.tiles[0].word)
    }

    @Test
    fun theWeekUnderEachTileIsSevenDaysOldestFirstWithGaps() {
        val stats = listOf(
            stat(day.minusDays(6), 54f),
            stat(day.minusDays(4), 56f),
            stat(day, 58f),
        )
        val model = RecoveryGlow.signals(
            stateWith(restingHr = 58),
            SignalHistories(restingHr = stats),
        )
        assertEquals(listOf(54f, null, 56f, null, null, null, 58f), model.tiles[0].week)
    }

    @Test
    fun theNormalNoteOnlyShowsBeforeAnyNormalExists() {
        val early = RecoveryGlow.signals(stateWith(), SignalHistories())
        assertEquals(
            "Your normal builds over the first week of wear; until then each signal shows its week.",
            early.note,
        )
        assertNull(RecoveryGlow.signals(stateWith(), histories()).note)
    }

    // ---- Body signals off baseline (GlowRecoveryScreen.kt:175-187) ------------------------------

    private fun bodyDay(state: org.openstrap.app.data.BodySignals.State, causes: List<String> = emptyList()) =
        org.openstrap.app.data.BodySignals.Day(
            day = epoch(0),
            state = state,
            flags = emptySet(),
            signals = listOf("resting heart rate up 6 bpm", "skin temperature up 0.6 °C"),
            families = listOf("resting heart rate"),
            nightsOff = 1,
            causes = causes,
            baselineNights = 20,
        )

    @Test
    fun aWatchStatesTheSignalsAndThatItIsBeingWatched() {
        val model = RecoveryGlow.bodySignals(bodyDay(org.openstrap.app.data.BodySignals.State.Watch))
        assertTrue(!model.alert)
        assertEquals("Watching", model.trailing)
        assertEquals(
            "Resting heart rate up 6 bpm, skin temperature up 0.6 °C. One signal is outside your usual range. "
                + "OpenStrap is keeping an eye on it.",
            model.text,
        )
        assertNull(model.cause)
        assertEquals("This is a wellness estimate, not a medical assessment.", model.disclaimer)
    }

    @Test
    fun anAlertLeadsWithTheCausesAndTheAdvice() {
        val model = RecoveryGlow.bodySignals(bodyDay(org.openstrap.app.data.BodySignals.State.Alert, listOf("a hard training day")))
        assertTrue(model.alert)
        assertNull(model.trailing)
        assertTrue(model.text.endsWith(". " + org.openstrap.app.data.BodySignalsCopy.CAUSES + " " + org.openstrap.app.data.BodySignalsCopy.ADVICE))
        assertEquals("Likely cause: a hard training day.", model.cause)
    }

    // ---- How it works (GlowRecoveryScreen.kt:466-496) -------------------------------------------

    @Test
    fun howItWorksComparesTheStrapsScoreWithOurs() {
        val model = RecoveryGlow.howItWorks(readiness(score = 74), strap(score = 81))
        assertEquals("The strap's own score", model.strapLabel)
        assertEquals("81", model.strapValue)
        assertEquals("Ours", model.oursLabel)
        assertEquals("74", model.oursValue)
        assertTrue(model.strapNote!!.startsWith("The strap's score doesn't count sleep length"))
        // With no strap row the comparison is left out entirely.
        val alone = RecoveryGlow.howItWorks(readiness(score = 74), null)
        assertNull(alone.strapValue)
        assertNull(alone.oursValue)
        assertNull(alone.strapNote)
    }

    @Test
    fun howItWorksCarriesComposesTwoParagraphsAndTheDisclaimer() {
        val model = RecoveryGlow.howItWorks(readiness(), null)
        assertTrue(model.explainer.startsWith("Each morning OpenStrap compares your night with what's normal for you"))
        assertTrue(model.explainer.endsWith("The score is a weighted mix; one signal far from normal caps it."))
        assertEquals(
            "Charge is a running battery: sleep and calm rest fill it, movement, stress and simply being awake drain it.",
            model.chargeExplainer,
        )
        assertEquals("This is a wellness estimate, not a medical assessment.", model.disclaimer)
    }

    // ---- History's window (the tile's normal is the 30 days Compose asks for) -------------------

    @Test
    fun theResultShownIsTheSelectedDays() {
        val state = stateWith()
        assertEquals(day, RecoveryGlow.forDay(state, recovery = result())?.date)
        // A slower answer for a day the user has left is not the day the screen is showing.
        val stale = RecoveryResult(
            date = day.minusDays(1), readiness = readiness(), charge = charge(),
            readinessTrend = emptyList(), load = 0f, chronicLoad = null, restingHr = 55f, maxHr = 190f,
            maxHrFromAge = true, stressThreshold = Recovery.StressThreshold(60, null, 2),
        )
        assertNull(RecoveryGlow.forDay(state, recovery = stale))
        assertNull(RecoveryGlow.forDay(state, recovery = null))
    }

    @Test
    fun theBodySignalPanelIsTitledAsBodySignalsCopyWordsIt() {
        val model = RecoveryGlow.bodySignals(bodyDay(org.openstrap.app.data.BodySignals.State.Watch))
        assertEquals("Body signals off baseline", model.title)
    }

    @Test
    fun theEmptyHistoriesAreSixEmptyMonths() {
        val empty = RecoveryGlow.noSignalHistories()
        assertTrue(
            listOf(
                empty.restingHr, empty.hrv, empty.skinTemperature, empty.breathing, empty.spo2, empty.stress,
            ).all { it.isEmpty() },
        )
    }

    @Test
    fun theNormalIsTheThirtyDayWindowComposeReads() {
        val stats = historyOf(55f, History.NORMAL_DAYS)
        val normal = History.normal(stats, day)!!
        assertEquals(30, normal.days)
        assertEquals(55f, normal.mean)
    }
}
