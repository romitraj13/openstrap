package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.Activity
import org.openstrap.app.data.EnergyExpenditure
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.db.DailyPai
import org.openstrap.app.data.db.PaiSample
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.atZone
import org.openstrap.app.data.minusDays
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.data.Vo2max
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.glow.ActivityAccent
import org.openstrap.app.ui.glow.ActivityGlow

/**
 * What `GlowActivityScreen.kt` computes, copied into `ActivityGlow` so the iPhone's Activity tab
 * reads exactly what the Android screen reads (standing instruction 5). Every expected value below
 * is worked out from the Compose expression it names, by hand, not from this port's own output.
 */
class ActivityGlowTest {
    private val utc = TimeZone.UTC

    /** The demo day: a Wednesday. */
    private val day = LocalDate(2026, 10, 21)

    private fun epoch(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).atZone(utc).epochSeconds

    private val goals = Goals(steps = 8000, sleepMinutes = 480, activeHours = 8)

    // ---- StepsCard (GlowActivityScreen.kt:105-132) ----------------------------------------------

    @Test
    fun stepsGoalIsAPercentageOfTheGoal() {
        // "%d%% of %,d".format(state.steps * 100 / goals.steps, goals.steps): 6543*100/8000 = 81.
        val model = ActivityGlow.steps(DayUiState(date = day, steps = 6543), null, goals)
        assertEquals("81% of 8,000", model.goal)
        assertEquals(6543f / 8000f, model.progress)
    }

    @Test
    fun stepsGoalIsReached() {
        // state.steps >= goals.steps -> "Goal reached" (:112).
        val model = ActivityGlow.steps(DayUiState(date = day, steps = 8120), null, goals)
        assertEquals("Goal reached", model.goal)
    }

    @Test
    fun stepsFactsFallBackToTheStrapsOwnRecord() {
        // Distance: our estimate from step length and height, else the strap's, else "—" (:122).
        val own = ActivityGlow.steps(
            DayUiState(date = day, steps = 6543, distanceMeters = 4234, activeCalories = 412, activeHours = 6),
            null,
            goals,
        )
        assertEquals("6 of 8", own.activeHours)
        assertEquals("≈ 4.2 km", own.distance)
        assertEquals("412", own.activeKcal)
        val none = ActivityGlow.steps(DayUiState(date = day, steps = 12), null, goals)
        assertEquals("—", none.distance)
        assertEquals("—", none.activeKcal)
    }

    @Test
    fun stepsNotesCarryTheStepGoalAndTheDiversion() {
        // `Activity.stepsDiverge`: 443 of 6543 is 6.8 %, over 5 % (:125-129).
        val activity = Activity.Day(
            steps = 6543,
            mvpa = Activity.Mvpa(96, 12, 8),
            weekEquivalent = 120,
            distanceMeters = 4234f,
            stepGoal = 8000,
        )
        val model = ActivityGlow.steps(
            DayUiState(date = day, steps = 6543, minuteSteps = 6100), activity, goals,
        )
        assertEquals(
            "The strap's step counter (6,543) and its minute records (6,100) differ by more than 5 %.",
            model.divergeNote,
        )
        assertEquals(
            "Most of the benefit is reached by about 8,000 steps a day (Paluch et al. 2022).",
            model.stepGoalNote,
        )
        // Inside 5 %: no note (:125).
        assertNull(ActivityGlow.steps(DayUiState(date = day, steps = 6543, minuteSteps = 6500), activity, goals).divergeNote)
        // No activity record: no step-goal sentence either (:129).
        assertNull(ActivityGlow.steps(DayUiState(date = day, steps = 6543), null, goals).stepGoalNote)
    }

    // ---- MvpaPanel (GlowActivityScreen.kt:165-181) ----------------------------------------------

    @Test
    fun activeMinutesAreTheWeekAgainstTheWhoTarget() {
        val activity = Activity.Day(
            steps = 6543,
            mvpa = Activity.Mvpa(96, 12, 8),
            weekEquivalent = 120,
            distanceMeters = null,
            stepGoal = 8000,
        )
        val model = ActivityGlow.activeMinutes(activity)
        assertEquals("Last 7 days", model.trailing)
        assertEquals("120 / 150", model.week)
        assertEquals("96", model.moderate)
        assertEquals("12", model.vigorous)
        assertEquals(0.8f, model.fraction)
        assertEquals(
            "Brisk walking (100+ steps a minute) is moderate, 130+ is vigorous and counts double. " +
                "Effort without steps, like cycling, counts from heart rate after 10 minutes " +
                "(8 min today, HR-assisted).",
            model.note,
        )
    }

    @Test
    fun activeMinutesSentenceEndsInAPeriodWithoutHeartRateMinutes() {
        val activity = Activity.Day(
            steps = 1, mvpa = Activity.Mvpa(30, 0, 0), weekEquivalent = 30, distanceMeters = null, stepGoal = 8000,
        )
        assertEquals(
            "Brisk walking (100+ steps a minute) is moderate, 130+ is vigorous and counts double. " +
                "Effort without steps, like cycling, counts from heart rate after 10 minutes.",
            ActivityGlow.activeMinutes(activity).note,
        )
    }

    // ---- EnergyPanel (GlowActivityScreen.kt:136-161) --------------------------------------------

    @Test
    fun energyWithoutAProfileAsksForIt() {
        val model = ActivityGlow.energy(null)
        assertFalse(model.hasEstimate)
        assertNull(model.partial)
    }

    @Test
    fun energyPrintsTheThreeFiguresAndItsNotes() {
        // totalKcal.roundToInt() = 2140; bmrKcal.roundToInt() = 1780 (:147-149).
        val e = EnergyExpenditure.Day(
            totalKcal = 2140.4f,
            bmrKcal = 1780.2f,
            minutesWithData = 1200,
            partial = true,
            pal = null,
            palImplausible = false,
            hrAssistedMinutes = 25,
        )
        val model = ActivityGlow.energy(e)
        assertTrue(model.hasEstimate)
        assertEquals("Partial day", model.partial)
        assertEquals("≈ 2140 kcal", model.total)
        assertEquals("1780 /day", model.atRest)
        assertEquals("—", model.activityLevel)
        assertEquals(
            "From your resting metabolism (Mifflin-St Jeor), your steps each minute and, for effort without steps, " +
                "your heart rate. Covers 20h of wear, so it's a partial total. 25 min HR-assisted. " +
                "Food digestion isn't included.",
            model.note,
        )
    }

    @Test
    fun energyOnAFullDayShowsTheActivityLevelAndFlagsAnImplausibleTotal() {
        val e = EnergyExpenditure.Day(
            totalKcal = 3000f,
            bmrKcal = 1700f,
            minutesWithData = 24 * 60,
            partial = false,
            pal = 1.7647f,
            palImplausible = true,
            hrAssistedMinutes = 0,
        )
        val model = ActivityGlow.energy(e)
        assertNull(model.partial)
        assertEquals("1.76", model.activityLevel)
        assertEquals(
            "From your resting metabolism (Mifflin-St Jeor), your steps each minute and, for effort without steps, " +
                "your heart rate. That's an unusual total for a full day; check your profile and how the strap was worn. " +
                "Food digestion isn't included.",
            model.note,
        )
    }

    // ---- PaiPanel (GlowActivityScreen.kt:188-266) ------------------------------------------------

    private fun paiWeek(vararg values: Pair<LocalDate, Float>): List<DailyPai> =
        values.map { (date, daily) -> DailyPai(day = date.toString(), daily = daily, total = daily, latest = 0) }

    @Test
    fun paiWithoutAReadingSaysWhenItAppears() {
        val model = ActivityGlow.pai(DayUiState(date = day), emptyList(), day, utc)
        assertFalse(model.hasPai)
        assertEquals(
            "PAI shows after the strap's next sync. It counts minutes your heart rate is raised, harder minutes counting more.",
            model.note,
        )
    }

    @Test
    fun paiPrintsTheTotalEachDaysPointsAndWhatDropsOff() {
        // `paiPoints` (:232): "+10", "–" for none, "+<1" under half a point, else rounded.
        val week = paiWeek(
            day.minusDays(6) to 10f,
            day.minusDays(5) to 0f,
            day.minusDays(4) to 22.5f,
            day.minusDays(3) to 0.4f,
            day.minusDays(2) to 8f,
            day.minusDays(1) to 6f,
            day to 12f,
        )
        val state = DayUiState(date = day, pai = PaiSample(timestamp = 0, daily = 12.4f, total = 63.6f, restingHeartRate = 52, maxHeartRate = 180))
        val model = ActivityGlow.pai(state, week, day, utc)
        assertTrue(model.hasPai)
        // total.roundToInt() = 64 (:197).
        assertEquals("64", model.total)
        assertEquals(" / 100", model.target)
        assertEquals("+12", model.today)
        // `getDisplayName(SHORT).take(2)` over Thu…Wed (:205).
        assertEquals(listOf("Th", "Fr", "Sa", "Su", "Mo", "Tu", "We"), model.days.map { it.name })
        assertEquals(listOf("+10", "–", "+23", "+<1", "+8", "+6", "+12"), model.days.map { it.points })
        assertEquals(listOf(false, false, false, false, false, false, true), model.days.map { it.today })
        assertEquals(listOf(false, true, false, false, false, false, false), model.days.map { it.empty })
        // 100 - 64 = 36 to go, and the oldest day's points leave tomorrow (:220-226).
        assertEquals(
            "Tomorrow Thursday's +10 drops off. 36 to go. Brisk walking adds a little; anything that gets you out of breath adds more.",
            model.note,
        )
    }

    @Test
    fun paiAboveTheTargetSaysToKeepItThere() {
        val state = DayUiState(date = day, pai = PaiSample(timestamp = 0, daily = 5f, total = 118f, restingHeartRate = 52, maxHeartRate = 180))
        val model = ActivityGlow.pai(state, paiWeek(day to 5f), day, utc)
        assertEquals(
            "You're at or above 100. Keeping it there week after week is what PAI is built around.",
            model.note,
        )
    }

    @Test
    fun paiOnlySaysWhatDropsOffWhenLookingAtToday() {
        val yesterday = day.minusDays(1)
        val week = paiWeek(yesterday to 4f, day to 5f)
        val state = DayUiState(date = yesterday, pai = PaiSample(timestamp = 0, daily = 4f, total = 40f, restingHeartRate = 52, maxHeartRate = 180))
        // state.date != today, so the dropping sentence is left out (:220).
        assertEquals(
            "60 to go. Brisk walking adds a little; anything that gets you out of breath adds more.",
            ActivityGlow.pai(state, week, day, utc).note,
        )
    }

    @Test
    fun paiSegmentsFillTheTargetWithASliverForEveryDay() {
        // 100 pt bar: 10 -> 10 wide, 22.5 -> 22.5, 0.4 -> the 3 pt sliver, 12 -> 12, 1.5 pt gaps (:249-262).
        val segments = ActivityGlow.paiSegments(listOf(10f, 0f, 22.5f, 0.4f, 8f, 6f, 12f), 100f)
        assertEquals(listOf(0f, 11.5f, 35.5f, 40f, 49.5f, 57f), segments.map { it.from })
        assertEquals(listOf(10f, 22.5f, 3f, 8f, 6f, 12f), segments.map { it.width })
        // The oldest is faintest, today is not white at all (:256-260).
        assertEquals(0.35f, segments.first().white)
        assertEquals(0.55f + 0.45f * 2 / 6, segments[1].white)
        assertTrue(segments.last().today)
        assertTrue(segments.none { it.today && it !== segments.last() })
    }

    // ---- LoadPanel (GlowActivityScreen.kt:270-329) ----------------------------------------------

    private fun recovery(
        load: Float = 142.4f,
        strain: Float = 9.44f,
        strainPercentile: Int? = 72,
        loadTrend: Recovery.LoadTrend? = Recovery.LoadTrend(ctl = 52.4f, atl = 61.2f, form = -8.8f),
        acwr: Float? = 1.234f,
        maxHrFromAge: Boolean = true,
        maxHrTested: Boolean = false,
        estimate: Vo2max.Estimate? = null,
        withheld: Vo2max.Withheld? = null,
    ) = RecoveryResult(
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
        load = load,
        chronicLoad = null,
        restingHr = 55f,
        maxHr = 190f,
        maxHrFromAge = maxHrFromAge,
        stressThreshold = Recovery.StressThreshold(60, null, 2),
        loadTrend = loadTrend,
        strain = strain,
        strainPercentile = strainPercentile,
        acwr = acwr,
        maxHrTested = maxHrTested,
        vo2max = estimate,
        vo2maxWithheld = withheld,
    )

    @Test
    fun loadPrintsStrainLoadAndWhereItSits() {
        val model = ActivityGlow.load(recovery(), null, now = 0, zone = utc)
        assertTrue(model.hasRecovery)
        assertEquals("9.4", model.strain)
        assertEquals("142", model.loadToday)
        assertEquals("72th pct", model.vsUsual)
        assertEquals("52", model.fitness)
        assertEquals("61", model.fatigue)
        // form.toInt() truncates toward zero, and only a form above 0.5 gets the plus (:283).
        assertEquals("-8", model.form)
        assertEquals(
            "Each minute counts by how hard your heart works between resting (55 bpm) and maximum " +
                "(190 bpm). Strain runs 0–21 on a fixed curve: an hour at 60 % of reserve is about 7.",
            model.effortNote,
        )
        assertTrue(model.hasAcwr)
        assertEquals("1.23", model.acwr)
        assertEquals("Contested", model.acwrWord)
        assertFalse(model.hasWeek)
        assertNull(model.maxHrNote)
    }

    @Test
    fun loadMarksATestedMaxAndAsksForAnAgeWithoutOne() {
        val tested = ActivityGlow.load(recovery(maxHrTested = true), null, now = 0, zone = utc)
        assertTrue(tested.effortNote.contains("(190 bpm, from your test)."))
        val estimated = ActivityGlow.load(recovery(maxHrFromAge = false), null, now = 0, zone = utc)
        assertEquals("Max HR is estimated. Add your age for accurate effort numbers.", estimated.maxHrNote)
    }

    @Test
    fun loadWithoutATrendOrAnAcwrDropsBoth() {
        val model = ActivityGlow.load(recovery(loadTrend = null, acwr = null), null, now = 0, zone = utc)
        assertFalse(model.hasTrend)
        assertFalse(model.hasAcwr)
        assertNull(model.maxHrNote)
    }

    @Test
    fun loadPutsTheStrapsWeekInItsProductiveRange() {
        val effect = SportEffectRow(
            end = epoch(day, 18), recoveryMinutes = 80, vo2max = null, epoc = null,
            weeklyLoad = 210f, monthlyLoad = null, optimalLoadMin = 180, optimalLoadMax = 260, maximalHeartRate = null,
        )
        val inside = ActivityGlow.load(recovery(), effect, now = 0, zone = utc)
        assertTrue(inside.hasWeek)
        assertEquals("210", inside.weekLoad)
        assertEquals("Productive", inside.weekWord)
        assertEquals(ActivityAccent.Good, inside.weekAccent)
        assertEquals(210f / 325f, inside.weekFraction)
        assertEquals(
            "Productive range 180–260, from the strap (updated Wed 21 Oct). Its load is on a different scale from today's.",
            inside.weekNote,
        )
        val below = ActivityGlow.load(recovery(), effect.copy(weeklyLoad = 150f), now = 0, zone = utc)
        assertEquals("Below productive", below.weekWord)
        assertEquals(ActivityAccent.Steps, below.weekAccent)
        val above = ActivityGlow.load(recovery(), effect.copy(weeklyLoad = 300f), now = 0, zone = utc)
        assertEquals("Above productive", above.weekWord)
    }

    // ---- FitnessPanel (GlowActivityScreen.kt:333-371) --------------------------------------------

    private fun workout(startHour: Int, vo2max: Int? = null, effectVo2: Float? = null) = WorkoutAnalysis(
        workout = Workout(
            start = epoch(day, startHour),
            end = epoch(day, startHour) + 2700,
            sportType = 1,
            exerciseSeconds = 2600,
            calories = 420,
            averageHeartRate = 132,
            maxHeartRate = 165,
            minHeartRate = 96,
            aerobicTrainingEffect = 3.4f,
            anaerobicTrainingEffect = 1.2f,
            trainingLoad = 142,
            vo2max = vo2max,
            steps = 1240,
        ),
        end = epoch(day, startHour) + 2700,
        heartRate = emptyList(),
        effect = effectVo2?.let {
            SportEffectRow(
                end = epoch(day, startHour) + 2700, recoveryMinutes = 80, vo2max = it, epoc = null,
                weeklyLoad = null, monthlyLoad = null, optimalLoadMin = null, optimalLoadMax = null, maximalHeartRate = null,
            )
        },
        recovery60 = null,
        recovery120 = null,
        endHeartRate = null,
        zoneSeconds = List(5) { 0 },
        maxHeartRate = 165,
    )

    @Test
    fun fitnessShowsTheLatestVo2AndAnEmptyStateWithoutWorkouts() {
        val empty = ActivityGlow.fitness(null, emptyList(), emptyList(), highestToday = null, now = 0)
        assertEquals("—", empty.vo2)
        assertEquals("—", empty.recovery)
        assertEquals("—", empty.maxHr)
        assertTrue(empty.spark.isEmpty())
        assertEquals("VO2 max appears after a workout the strap can estimate it from.", empty.note)
        // The workout's own vo2max wins over its effect's, and a single reading draws no sparkline (:334, :349).
        val one = ActivityGlow.fitness(null, listOf(workout(17, vo2max = 48)), emptyList(), highestToday = 165, now = 0)
        assertEquals("48", one.vo2)
        assertTrue(one.spark.isEmpty())
        assertEquals("Highest heart rate today 165 bpm.", one.note)
        // A VO2 max but no recovery estimate and no peak today: Compose's `ifEmpty` line (:368).
        val bare = ActivityGlow.fitness(null, listOf(workout(17, vo2max = 48)), emptyList(), highestToday = null, now = 0)
        assertEquals("Record a workout on the strap to see VO2 max and recovery time.", bare.note)
    }

    @Test
    fun fitnessTakesTheEffectsVo2WhenTheWorkoutHasNone() {
        val two = listOf(workout(8, effectVo2 = 45.6f), workout(17, vo2max = 48))
        val model = ActivityGlow.fitness(null, two, emptyList(), highestToday = null, now = 0)
        assertEquals("48", model.vo2)
        assertEquals(listOf<Float?>(46f, 48f), model.spark)
    }

    @Test
    fun fitnessCountsTheRecoveryTimeLeftFromTheStrapsEffect() {
        val effect = SportEffectRow(
            end = epoch(day, 18), recoveryMinutes = 80, vo2max = null, epoc = null,
            weeklyLoad = null, monthlyLoad = null, optimalLoadMin = null, optimalLoadMax = null, maximalHeartRate = null,
        )
        // end + 80 min - now: ten minutes left (:336-346).
        val running = ActivityGlow.fitness(null, emptyList(), listOf(effect), highestToday = null, now = epoch(day, 18) + 70 * 60)
        assertEquals("10m left", running.recovery)
        assertEquals(
            "VO2 max appears after a workout the strap can estimate it from. " +
                "Recovery time is the strap's estimate of how long your last effort needs.",
            running.note,
        )
        // end 18:00 + 80 min is 19:20: half an hour later the time is up (:342-346).
        val done = ActivityGlow.fitness(null, emptyList(), listOf(effect), highestToday = null, now = epoch(day, 19, 30))
        assertEquals("Done", done.recovery)
    }

    @Test
    fun fitnessPrintsTheEstimatedRangeWithItsBandAndFitnessAge() {
        val estimate = Vo2max.Estimate(
            value = 48,
            low = 42,
            high = 54,
            restingHr = 55.4f,
            band = Vo2max.Friend.Band.P50to70,
            fitnessAge = 30..40,
            sex = UserProfile.Sex.Male,
        )
        val model = ActivityGlow.fitness(recovery(estimate = estimate), emptyList(), emptyList(), highestToday = null, now = 0)
        assertTrue(model.hasEstimate)
        assertEquals("≈ 42–54", model.estimate)
        assertEquals(
            "From your age, sex, BMI, resting heart rate (55 bpm, while asleep) and usual activity " +
                "(Jurca et al. 2005). Typical error is about ±5 ml/kg/min. Updated weekly. " +
                "50th–70th percentile for your age and sex (FRIEND 2022, treadmill). " +
                "That's typical of a man aged about 30–40.",
            model.estimateNote,
        )
    }

    @Test
    fun fitnessSaysWhyTheEstimateIsWithheld() {
        val model = ActivityGlow.fitness(
            recovery(withheld = Vo2max.Withheld.RestingHr),
            emptyList(), emptyList(), highestToday = null, now = 0,
        )
        assertFalse(model.hasEstimate)
        assertEquals(
            "Estimated fitness: ${Vo2max.Withheld.RestingHr.reason}",
            model.withheldNote,
        )
    }

    // ---- WorkoutsPanel (GlowActivityScreen.kt:375-400) -------------------------------------------

    @Test
    fun workoutsPanelSeparatesTheDayItsRecentOnesAndTheFullList() {
        // Today at 17:00, and three on earlier days: the day's one, then the newest three others,
        // and "All workouts" once there are more than the panel lists (:377-398).
        val workouts = listOf(
            workout(17),
            workout(16).copy(workout = workout(16).workout.copy(start = epoch(day.minusDays(1), 16))),
            workout(16).copy(workout = workout(16).workout.copy(start = epoch(day.minusDays(2), 16))),
            workout(16).copy(workout = workout(16).workout.copy(start = epoch(day.minusDays(3), 16))),
            workout(16).copy(workout = workout(16).workout.copy(start = epoch(day.minusDays(4), 16))),
        )
        val model = ActivityGlow.workoutsPanel(DayUiState(date = day), workouts, utc)
        assertEquals("5 recorded", model.trailing)
        assertEquals(1, model.today.size)
        assertEquals(3, model.recent.size)
        assertTrue(model.showAll)
        assertNull(model.noTodayNote)
        assertNull(model.emptyNote)
    }

    @Test
    fun workoutsPanelOnADayWithoutOneSaysSo() {
        val model = ActivityGlow.workoutsPanel(DayUiState(date = day), listOf(workout(8).copy(
            workout = workout(8).workout.copy(start = epoch(day.minusDays(3), 8)),
        )), utc)
        assertEquals("No workouts this day.", model.noTodayNote)
        assertEquals(1, model.recent.size)
        assertFalse(model.showAll)
    }

    @Test
    fun workoutsPanelWithoutAnyWorkoutExplainsWhereTheyComeFrom() {
        val model = ActivityGlow.workoutsPanel(DayUiState(date = day), emptyList(), utc)
        assertNull(model.trailing)
        assertEquals(
            "Workouts you start on the strap show up here after a sync, with your heart rate every second and how fast it recovered.",
            model.emptyNote,
        )
        assertNull(model.noTodayNote)
        assertFalse(model.showAll)
    }
}
