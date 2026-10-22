package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.atZone
import org.openstrap.app.data.db.SportEffectRow
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate
import org.openstrap.app.data.minusDays
import org.openstrap.app.ui.glow.ActivityAccent
import org.openstrap.app.ui.glow.WorkoutGlow
import org.openstrap.app.ui.glow.signedDrop

/**
 * What `GlowWorkoutScreen.kt` computes, copied into `WorkoutGlow` so the iPhone's workout screens
 * read exactly what the Android ones read (standing instruction 5). Every expected value below is
 * worked out from the Compose expression it names, by hand, not from this port's own output.
 */
class WorkoutGlowTest {
    private val utc = TimeZone.UTC

    private val day = LocalDate(2026, 10, 21)

    private fun epoch(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).atZone(utc).epochSeconds

    private val start: Long = epoch(day, 17, 2)
    private val end: Long = start + 45 * 60

    private fun analysis(
        heartRate: List<WorkoutHeartRate> = emptyList(),
        effect: SportEffectRow? = null,
        recovery60: Int? = 24,
        recovery120: Int? = 38,
        endHeartRate: Int? = 150,
        zoneSeconds: List<Int> = List(5) { 0 },
        maxHeartRate: Int = 180,
        actualEnd: Long = end,
    ) = WorkoutAnalysis(
        workout = Workout(
            start = start,
            end = end,
            sportType = 1,
            exerciseSeconds = 43 * 60,
            calories = 420,
            averageHeartRate = 132,
            maxHeartRate = 165,
            minHeartRate = 96,
            aerobicTrainingEffect = 3.4f,
            anaerobicTrainingEffect = 1.2f,
            trainingLoad = 142,
            vo2max = 48,
            steps = 1240,
        ),
        end = actualEnd,
        heartRate = heartRate,
        effect = effect,
        recovery60 = recovery60,
        recovery120 = recovery120,
        endHeartRate = endHeartRate,
        zoneSeconds = zoneSeconds,
        maxHeartRate = maxHeartRate,
    )

    // ---- HeartCard (GlowWorkoutScreen.kt:66-106) -------------------------------------------------

    @Test
    fun heartCardWithoutAStreamSaysSo() {
        val model = WorkoutGlow.heart(analysis(), utc)
        assertEquals("Every second", model.trailing)
        assertEquals("132", model.average)
        // "Peak 165", then the inner list joined: "45m · 420 kcal" (:74).
        assertEquals(listOf("Peak 165", "45m · 420 kcal"), model.facts)
        assertFalse(model.hasHeartRate)
        assertEquals("The strap hasn't sent this workout's heart rate yet.", model.emptyNote)
        assertTrue(model.values.isEmpty())
        assertNull(model.tailNote)
        assertEquals(132, model.heartbeat)
    }

    @Test
    fun heartCardBucketsTheStreamIntoTenSeconds() {
        // Seconds 0, 1 and 10 of the workout: buckets 0, 0 and 1 (:84-94). A bucket with no sample is
        // a gap, which the line chart draws as a break.
        val hr = listOf(
            WorkoutHeartRate(start, start, 120),
            WorkoutHeartRate(start, start + 1, 130),
            WorkoutHeartRate(start, start + 10, 140),
            // A sample half a minute in, so the last bucket is the one the workout ends in.
            WorkoutHeartRate(start, start + 30, 150),
        )
        val model = WorkoutGlow.heart(analysis(heartRate = hr, actualEnd = start + 30), utc)
        assertTrue(model.hasHeartRate)
        assertEquals(10L, model.stepSeconds)
        // n = 30/10 + 1 = 4 buckets: 0 -> (120+130)/2, 1 -> 140, 2 -> no sample, 3 -> 150.
        assertEquals(4, model.values.size)
        assertEquals(125f, model.values[0])
        assertEquals(140f, model.values[1])
        assertNull(model.values[2])
        assertEquals(150f, model.values[3])
        assertEquals(listOf("17:02", "17:02"), model.labels)
        assertEquals(start, model.start)
    }

    @Test
    fun heartReadoutNamesTheRateAndTheClock() {
        // "${v.toInt()} bpm · ${clock(first + i * step)}" (:97-99).
        assertEquals("141 bpm · 17:04", WorkoutGlow.heartReadout(start, index = 12, value = 141.7f, zone = utc))
    }

    @Test
    fun heartCardNotesHowLongTheStrapKeptRecording() {
        // A sample more than 30 s past the end means a recovery tail (:100-103).
        val last = end + 4 * 60
        val hr = listOf(WorkoutHeartRate(start, start, 120), WorkoutHeartRate(start, last, 90))
        val model = WorkoutGlow.heart(analysis(heartRate = hr, actualEnd = end), utc)
        // (4m + 30 s) / 60 = 4 whole minutes, printed as a duration.
        assertEquals(
            "The strap kept recording for 4m after you stopped, for recovery heart rate.",
            model.tailNote,
        )
        val noTail = WorkoutGlow.heart(analysis(heartRate = listOf(WorkoutHeartRate(start, start, 120))), utc)
        assertNull(noTail.tailNote)
    }

    // ---- SummaryPanel (GlowWorkoutScreen.kt:108-121) ---------------------------------------------

    @Test
    fun summarySplitsActiveFromPausedTime() {
        // exerciseSeconds 43m of a 45m workout: 2m paused (:115-117).
        val model = WorkoutGlow.summary(analysis())
        assertEquals("43m", model.active)
        assertEquals("2m", model.paused)
        assertEquals("96", model.lowestHr)
        assertEquals("1,240 steps.", model.stepsNote)
    }

    @Test
    fun summaryWithoutExerciseTimeHasNoSplit() {
        val a = analysis().let { it.copy(workout = it.workout.copy(exerciseSeconds = null, steps = null)) }
        val model = WorkoutGlow.summary(a)
        assertEquals("—", model.active)
        assertEquals("—", model.paused)
        assertNull(model.stepsNote)
    }

    // ---- RecoveryPanel (GlowWorkoutScreen.kt:123-151) --------------------------------------------

    @Test
    fun recoveryRatesABigDropGood() {
        // drop 24: under 25, so "Good" (:129-132).
        val model = WorkoutGlow.recovery(analysis(recovery60 = 24, recovery120 = 38))!!
        assertEquals("Good", model.label)
        assertEquals(ActivityAccent.Good, model.accent)
        assertEquals("−24 bpm", model.afterOne)
        assertEquals("−38 bpm", model.afterTwo)
        assertTrue(model.rated)
        assertEquals(24f / 40f, model.fraction)
        assertEquals(
            "How fast your heart rate fell from 150 bpm once you stopped. A bigger drop usually means a " +
                "fitter heart; under 12 bpm in the first minute is on the low side. Compare it across similar workouts.",
            model.note,
        )
    }

    @Test
    fun recoveryRatesTheWholeRange() {
        assertEquals("Excellent", WorkoutGlow.recovery(analysis(recovery60 = 25))?.label)
        assertEquals("Fair", WorkoutGlow.recovery(analysis(recovery60 = 12))?.label)
        assertEquals("Low", WorkoutGlow.recovery(analysis(recovery60 = 11))?.label)
        assertEquals(ActivityAccent.Warn, WorkoutGlow.recovery(analysis(recovery60 = 11))?.accent)
        // No recovery recording at all: no panel (:125).
        assertNull(WorkoutGlow.recovery(analysis(recovery60 = null)))
    }

    @Test
    fun recoveryLeavesAnEasyEffortUnrated() {
        // `recoveryIsMeaningful`: 100 bpm against a 180 bpm max is 55 %, under the 65 % rule.
        val model = WorkoutGlow.recovery(analysis(recovery60 = 20, endHeartRate = 100))!!
        assertNull(model.label)
        assertNull(model.accent)
        assertFalse(model.rated)
        assertEquals(
            "You finished at 100 bpm, close to resting, so there was little to recover from. " +
                "Recovery heart rate is rated after harder efforts.",
            model.note,
        )
    }

    @Test
    fun signedDropUsesAMinusForADropAndAPlusForARise() {
        // WorkoutStyle.kt:10.
        assertEquals("−24", signedDrop(24))
        assertEquals("+3", signedDrop(-3))
        assertEquals("0", signedDrop(0))
    }

    // ---- ZonesPanel (GlowWorkoutScreen.kt:153-173) -----------------------------------------------

    @Test
    fun zonesAreCutAtTheirShareOfMaxHeartRate() {
        // maxHr 180: the ranges are printed in bpm, so zone 1 is "90–107" and zone 5 "162+" (:159-166).
        val model = WorkoutGlow.zones(analysis(zoneSeconds = listOf(600, 900, 300, 120, 60), maxHeartRate = 180))
        assertEquals("Max 180 bpm", model.trailing)
        assertTrue(model.hasZones)
        assertEquals(listOf("Maximum", "Anaerobic", "Aerobic", "Fat burn", "Warm-up"), model.rows.map { it.name })
        assertEquals(listOf("162+", "144–161", "126–143", "108–125", "90–107"), model.rows.map { it.range })
        // 1980 s in total: 60 -> 1m, 120 -> 2m, 300 -> 5m, 900 -> 15m, 600 -> 10m.
        assertEquals(listOf("1m", "2m", "5m", "15m", "10m"), model.rows.map { it.time })
        assertEquals(60f / 1980f, model.rows.first().fraction)
        assertEquals(
            listOf(
                ActivityAccent.Heart, ActivityAccent.Warn, ActivityAccent.Good, ActivityAccent.Hrv, ActivityAccent.SleepLight,
            ),
            model.rows.map { it.accent },
        )
    }

    // ---- EffectPanel (GlowWorkoutScreen.kt:175-193) ----------------------------------------------

    @Test
    fun effectPrintsTheStrapsFigures() {
        val effect = SportEffectRow(
            end = end, recoveryMinutes = 80, vo2max = 47.6f, epoc = null,
            weeklyLoad = null, monthlyLoad = null, optimalLoadMin = null, optimalLoadMax = null, maximalHeartRate = null,
        )
        val model = WorkoutGlow.effect(analysis(effect = effect))
        assertEquals("From the strap", model.trailing)
        assertEquals("3.4", model.aerobic)
        assertEquals("1.2", model.anaerobic)
        assertEquals("142", model.load)
        // The effect's VO2 max wins over the workout's (:186).
        assertEquals("48", model.vo2)
        assertEquals("1h 20m", model.recoveryTime)
        assertEquals(
            "Training effect runs 0–5 for each system. VO₂ max shows up once the strap has enough data to estimate it.",
            model.note,
        )
    }

    @Test
    fun effectWithoutASportEffectFallsBackToTheWorkout() {
        val a = analysis().let { it.copy(workout = it.workout.copy(aerobicTrainingEffect = null, trainingLoad = null)) }
        val model = WorkoutGlow.effect(a)
        assertEquals("—", model.aerobic)
        assertEquals("—", model.load)
        assertEquals("48", model.vo2)
        assertEquals("—", model.recoveryTime)
    }

    // ---- GlowWorkoutsScreen (GlowWorkoutScreen.kt:195-218) ---------------------------------------

    private fun at(hour: Int, on: LocalDate = day) = analysis().let {
        it.copy(
            workout = it.workout.copy(start = epoch(on, hour), end = epoch(on, hour) + 2700),
            end = epoch(on, hour) + 2700,
        )
    }

    @Test
    fun allWorkoutsGroupsByDayWithNamesAgainstToday() {
        val workouts = listOf(at(17), at(8), at(9, day.minusDays(1)), at(9, day.minusDays(5)))
        val model = WorkoutGlow.all(workouts, today = day, zone = utc)
        assertEquals("4 recorded", model.subtitle)
        assertNull(model.emptyNote)
        // Newest day first, as `groupBy` keeps the order it meets them in (:206).
        assertEquals(listOf("Today", "Yesterday", "Fri 16 Oct"), model.groups.map { it.title })
        assertEquals(listOf(2, 1, 1), model.groups.map { it.workouts.size })
        assertTrue(model.groups.all { it.trailing == null })
    }

    @Test
    fun allWorkoutsNamesAnotherYearByItsYear() {
        val lastYear = at(9, LocalDate(2025, 12, 31))
        val model = WorkoutGlow.all(listOf(lastYear), today = day, zone = utc)
        assertEquals("2025", model.groups.single().trailing)
        assertEquals("Wed 31 Dec", model.groups.single().title)
    }

    @Test
    fun allWorkoutsWithoutAnySaysWhereTheyComeFrom() {
        val model = WorkoutGlow.all(emptyList(), today = day, zone = utc)
        assertEquals("0 recorded", model.subtitle)
        assertEquals("Workouts you start on the strap show up here after a sync.", model.emptyNote)
        assertTrue(model.groups.isEmpty())
    }

    @Test
    fun dateLineIsTheDayAndTheMinuteItStarted() {
        // dateLine: "EEE d MMM · HH:mm" (:39).
        assertEquals("Wed 21 Oct · 17:02", WorkoutGlow.dateLine(start, utc))
    }
}
