package org.openstrap.shared.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.Units
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.ui.device.GoalsRules
import org.openstrap.app.ui.device.ProfileRules
import kotlin.time.Clock

/**
 * The profile and goals screens' numbers, copied out of `ProfileScreen.kt` and `GoalsDialog.kt` so
 * Swift calls the same arithmetic instead of re-implementing it (standing instruction 5).
 *
 * Every expectation below is a line of the Compose function named beside it.
 */
class DeviceRulesTest {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val now = 1_760_000_000L

    @Test
    fun theSheetOpensOnADefaultWhenNothingIsStoredYet() {
        // Swift cannot call Kotlin's default arguments, so the screens ask for the blanks.
        assertEquals(Goals(), GoalsRules.defaults())
        assertEquals(UserProfile(), ProfileRules.blank())
    }

    // ---- UserProfile.summary() (ProfileScreen.kt:56-63) -----------------------------------------

    @Test
    fun anEmptyProfileAsksForAgeAndSex() {
        // `if (isEmpty) "Add age and sex for accurate effort and recovery"`.
        assertEquals("Add age and sex for accurate effort and recovery", ProfileRules.summary(UserProfile()))
    }

    @Test
    fun theSummaryIsAgeSexWeightAndHeightInTheChosenUnits() {
        val profile = UserProfile(
            birthYear = today.year - 34,
            sex = UserProfile.Sex.Male,
            weightKg = 72f,
            heightCm = 178,
            imperial = false,
        )
        assertEquals("34 years, male, 72 kg, 178 cm", ProfileRules.summary(profile))

        // `Units.formatWeight(it, usesImperial)` and `Units.formatHeight(it, usesImperial)`.
        assertEquals(
            "34 years, male, 159 lb, 5′10″",
            ProfileRules.summary(profile.copy(imperial = true)),
        )
    }

    @Test
    fun fieldsTheProfileDoesNotHaveDropOutRatherThanPrintingNull() {
        // `listOfNotNull(...)`: no sex, no weight, and a height alone still reads as one line.
        assertEquals(
            "34 years, 178 cm",
            ProfileRules.summary(UserProfile(birthYear = today.year - 34, heightCm = 178, imperial = false)),
        )
    }

    // ---- The number fields' own formatting (ProfileScreen.kt:250) --------------------------------

    @Test
    fun weightTextDropsATrailingPointZeroAndFollowsTheUnits() {
        // `"%.1f".format(Locale.ROOT, v).removeSuffix(".0")`.
        assertEquals("72", ProfileRules.weightText(72f, imperial = false))
        assertEquals("158.7", ProfileRules.weightText(158.7f, imperial = false))
        // The initial entry text of an imperial profile is kgToLb, one decimal.
        assertEquals("158.7", ProfileRules.weightText(72f, imperial = true))
    }

    @Test
    fun heightTextIsTheStoredCentimetres() {
        assertEquals("178", ProfileRules.heightText(178))
        assertEquals("168", ProfileRules.heightText(168))
    }

    @Test
    fun theImperialHeightFieldsOpenAsFeetAndInches() {
        // `profile.heightCm?.let { Units.cmToFeetInches(it) }` (ProfileScreen.kt:79).
        assertEquals("5", ProfileRules.feetText(178))
        assertEquals("10", ProfileRules.inchesText(178))
        assertEquals("0", ProfileRules.inchesText(152))
    }

    // ---- valid (ProfileScreen.kt:88-102) ---------------------------------------------------------

    @Test
    fun fieldsReportWhichFieldIsInErrorAndTheParsedValues() {
        // The screen draws a red outline on each field that is filled but out of range
        // (`age.isNotBlank() && ageValue == null`, ProfileScreen.kt:171 and the same per field).
        val empty = ProfileRules.fields("", false, "", "", "", "", "", "")
        assertTrue(empty.valid)
        assertFalse(empty.ageError)
        assertFalse(empty.weightError)
        assertFalse(empty.heightError)
        assertFalse(empty.maxHrError)
        assertFalse(empty.sleepNeedError)
        assertNull(empty.weightKg)
        assertNull(empty.heightCm)

        assertTrue(ProfileRules.fields("9", false, "", "", "", "", "", "").ageError)
        assertTrue(ProfileRules.fields("", false, "24", "", "", "", "", "").weightError)
        assertTrue(ProfileRules.fields("", true, "", "", "5", "12", "", "").heightError)
        assertTrue(ProfileRules.fields("", false, "", "", "", "", "100", "").maxHrError)
        assertTrue(ProfileRules.fields("", false, "", "", "", "", "", "6").sleepNeedError)

        // In range: no error, and the parsed value the unit switch carries across.
        val good = ProfileRules.fields("34", false, "72", "178", "", "", "180", "7.5")
        assertTrue(good.valid)
        assertFalse(good.weightError)
        assertEquals(72f, good.weightKg)
        assertEquals(178, good.heightCm)
    }

    @Test
    fun theSexToggleOpensOnTheProfilesAnswer() {
        // `when (profile.sex) { Female -> 0; Male -> 1; null -> 2 }` (ProfileScreen.kt:74).
        assertEquals(0, ProfileRules.sexIndex(UserProfile(sex = UserProfile.Sex.Female)))
        assertEquals(1, ProfileRules.sexIndex(UserProfile(sex = UserProfile.Sex.Male)))
        assertEquals(2, ProfileRules.sexIndex(UserProfile()))
    }

    @Test
    fun theSleepNeedFieldOpensOnHours() {
        // `profile.sleepNeedMinutes?.let { formatNumber(it / 60f) }` (ProfileScreen.kt:86).
        assertEquals("7.5", ProfileRules.sleepNeedText(450))
        assertEquals("8", ProfileRules.sleepNeedText(480))
    }

    @Test
    fun everyFieldIsOptional() {
        // `(age.isBlank() || ageValue != null) && ...` with all of them blank.
        assertTrue(ProfileRules.valid("", imperial = false, weight = "", heightCm = "", feet = "", inches = "", maxHr = "", sleepNeed = ""))
    }

    @Test
    fun theAcceptedRangesAreTheComposeOnes() {
        // Age 10..100, weight 25..300 kg, height 100..250 cm, max HR 120..230, sleep 7-9 h.
        assertTrue(ProfileRules.valid("10", false, "", "", "", "", "", ""))
        assertTrue(ProfileRules.valid("100", false, "", "", "", "", "", ""))
        assertFalse(ProfileRules.valid("9", false, "", "", "", "", "", ""))
        assertFalse(ProfileRules.valid("101", false, "", "", "", "", "", ""))

        assertTrue(ProfileRules.valid("", false, "72", "", "", "", "", ""))
        assertFalse(ProfileRules.valid("", false, "24", "", "", "", "", ""))
        assertFalse(ProfileRules.valid("", false, "301", "", "", "", "", ""))
        // A comma is a decimal point here, as `replace(',', '.')` has it.
        assertTrue(ProfileRules.valid("", false, "72,5", "", "", "", "", ""))

        assertTrue(ProfileRules.valid("", false, "", "178", "", "", "", ""))
        assertFalse(ProfileRules.valid("", false, "", "99", "", "", "", ""))
        assertFalse(ProfileRules.valid("", false, "", "251", "", "", "", ""))

        // Imperial: feet and inches -> cm, and inches only 0..11.
        assertTrue(ProfileRules.valid("", true, "", "", "5", "10", "", ""))
        assertTrue(ProfileRules.valid("", true, "", "", "5", "", "", ""))
        assertFalse(ProfileRules.valid("", true, "", "", "5", "12", "", ""))
        // Both imperial height fields blank is a blank height, not an error.
        assertTrue(ProfileRules.valid("", true, "", "", "", "", "", ""))

        assertTrue(ProfileRules.valid("", false, "", "", "", "", "120", ""))
        assertFalse(ProfileRules.valid("", false, "", "", "", "", "119", ""))
        assertFalse(ProfileRules.valid("", false, "", "", "", "", "231", ""))

        assertTrue(ProfileRules.valid("", false, "", "", "", "", "", "7.5"))
        assertFalse(ProfileRules.valid("", false, "", "", "", "", "", "6.9"))
        assertFalse(ProfileRules.valid("", false, "", "", "", "", "", "9.1"))
    }

    // ---- toProfile (ProfileScreen.kt:130-149) ----------------------------------------------------

    @Test
    fun savingTurnsAgeIntoABirthYearAndKeepsUntouchedFields() {
        val existing = UserProfile(maxHeartRate = 190, pregnant = true)
        val saved = ProfileRules.toProfile(
            profile = existing,
            age = "34",
            sex = 1,
            imperial = false,
            weight = "72",
            heightCm = "178",
            feet = "",
            inches = "",
            activity = 2,
            betaBlocker = true,
            pregnant = false,
            maxHr = "180",
            sleepNeed = "7.5",
            todayIso = today.toString(),
            nowEpochSeconds = now,
        )
        // `birthYear = ageValue?.let { LocalDate.now().year - it }`.
        assertEquals(today.year - 34, saved.birthYear)
        assertEquals(UserProfile.Sex.Male, saved.sex)
        assertEquals(72f, saved.weightKg)
        assertEquals(178, saved.heightCm)
        assertEquals(false, saved.imperial)
        // 7.5 h -> round(7.5 * 60) = 450, inside Recovery.SLEEP_NEED_MIN..MAX.
        assertEquals(450, saved.sleepNeedMinutes)
        assertEquals(180, saved.maxHeartRate)
        assertEquals(2, saved.activityLevel)
        // `activityAnsweredAt = if (activity in 0..4) now else null`.
        assertEquals(now, saved.activityAnsweredAt)
        // `bodyMeasuredAt` only when weight and height both landed.
        assertEquals(now, saved.bodyMeasuredAt)
        assertEquals(true, saved.betaBlocker)
        assertEquals(false, saved.pregnant)
    }

    @Test
    fun blankFieldsClearTheirValueAndActivityOffIsNull() {
        val existing = UserProfile(birthYear = 1990, sex = UserProfile.Sex.Female, weightKg = 61f, heightCm = 168, activityLevel = 3, activityAnsweredAt = 5L)
        val saved = ProfileRules.toProfile(
            profile = existing,
            age = "",
            sex = 2,
            imperial = true,
            weight = "",
            heightCm = "",
            feet = "",
            inches = "",
            activity = -1,
            betaBlocker = false,
            pregnant = false,
            maxHr = "",
            sleepNeed = "",
            todayIso = today.toString(),
            nowEpochSeconds = now,
        )
        // The copy() assigns every field it owns, blank or not (ProfileScreen.kt:131-147).
        assertNull(saved.birthYear)
        assertNull(saved.sex)
        assertNull(saved.weightKg)
        assertNull(saved.heightCm)
        assertNull(saved.sleepNeedMinutes)
        assertNull(saved.maxHeartRate)
        assertNull(saved.activityLevel)
        assertNull(saved.activityAnsweredAt)
        // No weight or height, so nothing was measured.
        assertNull(saved.bodyMeasuredAt)
    }

    @Test
    fun anImperialSaveConvertsBackToMetric() {
        // `weightValue = weightNumber?.let { if (imperial) Units.lbToKg(it) else it }`.
        val saved = ProfileRules.toProfile(
            profile = UserProfile(),
            age = "",
            sex = 2,
            imperial = true,
            weight = "159",
            heightCm = "",
            feet = "5",
            inches = "10",
            activity = -1,
            betaBlocker = false,
            pregnant = false,
            maxHr = "",
            sleepNeed = "",
            todayIso = today.toString(),
            nowEpochSeconds = now,
        )
        assertEquals(Units.lbToKg(159f), saved.weightKg)
        assertEquals(178, saved.heightCm)
        assertEquals(true, saved.imperial)
        // Both weight and height landed, so the body's measurement is stamped.
        assertEquals(now, saved.bodyMeasuredAt)
    }

    // ---- GoalsDialog (GoalsDialog.kt:28-31, 52-58) -----------------------------------------------

    @Test
    fun goalsAcceptTheComposeRanges() {
        assertTrue(GoalsRules.valid("8000", "8", "0", "10"))
        assertFalse(GoalsRules.valid("999", "8", "0", "10"))
        assertFalse(GoalsRules.valid("50001", "8", "0", "10"))
        assertFalse(GoalsRules.valid("8000", "3", "59", "10"))
        assertFalse(GoalsRules.valid("8000", "12", "1", "10"))
        assertFalse(GoalsRules.valid("8000", "8", "60", "10"))
        assertFalse(GoalsRules.valid("8000", "8", "0", "0"))
        assertFalse(GoalsRules.valid("8000", "8", "0", "19"))
        // `sleepM.ifEmpty { "0" }`: minutes may be left blank.
        assertTrue(GoalsRules.valid("8000", "8", "", "10"))
    }

    @Test
    fun savingGoalsBuildsThemFromHoursAndMinutes() {
        // `Goals(stepsValue!!, sleepValue!!, activeValue!!)`.
        assertEquals(
            Goals(steps = 9000, sleepMinutes = 450, activeHours = 12),
            GoalsRules.toGoals("9000", "7", "30", "12", Goals()),
        )
        assertEquals(
            Goals(steps = 8000, sleepMinutes = 480, activeHours = 10),
            GoalsRules.toGoals("8000", "8", "", "10", Goals()),
        )
    }

    @Test
    fun theGoalsFieldsOpenOnTheStoredValues() {
        // `goals.steps.toString()`, `(goals.sleepMinutes / 60).toString()`, `% 60`, `activeHours`.
        val goals = Goals(steps = 9000, sleepMinutes = 450, activeHours = 12)
        assertEquals("9000", GoalsRules.stepsText(goals))
        assertEquals("7", GoalsRules.sleepHoursText(goals))
        assertEquals("30", GoalsRules.sleepMinutesText(goals))
        assertEquals("12", GoalsRules.activeHoursText(goals))
        assertEquals("0", GoalsRules.sleepMinutesText(Goals(sleepMinutes = 480)))
    }
}
