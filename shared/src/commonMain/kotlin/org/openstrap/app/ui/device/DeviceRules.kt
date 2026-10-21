package org.openstrap.app.ui.device

import kotlinx.datetime.LocalDate
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.Units
import org.openstrap.app.data.settings.UserProfile
import kotlin.math.roundToInt

/*
 * The profile and goals screens' arithmetic, copied from the Compose screens so the iPhone calls
 * the same code instead of re-implementing it (standing instruction 5).
 *
 * Each member names the Compose function it came from; the JVM test `DeviceRulesTest` pins every
 * range and every line of the copy() calls. Nothing here is new behaviour: these are
 * `ProfileScreen.kt:56-63, 88-149, 250` and `GoalsDialog.kt:28-31, 52-58` verbatim, with
 * `String.format`'s one decimal written by hand because common Kotlin has no Locale.
 */

/** One line for the settings row: "34 years, male, 72 kg, 178 cm", or a prompt when empty. */
object ProfileRules {
    /** `UserProfile.summary()` (ProfileScreen.kt:56). */
    fun summary(profile: UserProfile): String =
        if (profile.isEmpty) "Add age and sex for accurate effort and recovery"
        else listOfNotNull(
            profile.age()?.let { "$it years" },
            profile.sex?.name?.lowercase(),
            profile.weightKg?.let { Units.formatWeight(it, profile.usesImperial) },
            profile.heightCm?.let { Units.formatHeight(it, profile.usesImperial) },
        ).joinToString(", ")

    /**
     * `valid` (ProfileScreen.kt:101): every field optional, but a filled one has to be in range.
     * Age 10..100, weight 25..300 kg (or the same in lb), height 100..250 cm (or feet/inches that
     * convert into it), max heart rate 120..230, sleep need 7-9 h.
     */
    fun valid(
        age: String,
        imperial: Boolean,
        weight: String,
        heightCm: String,
        feet: String,
        inches: String,
        maxHr: String,
        sleepNeed: String,
    ): Boolean = fields(age, imperial, weight, heightCm, feet, inches, maxHr, sleepNeed).valid

    /** The same ranges, per field: the screen reddens only the field that is filled and out of
     * range (`age.isNotBlank() && ageValue == null`, ProfileScreen.kt:171), and the unit switch
     * needs the parsed weight and height to carry across.
     */
    fun fields(
        age: String,
        imperial: Boolean,
        weight: String,
        heightCm: String,
        feet: String,
        inches: String,
        maxHr: String,
        sleepNeed: String,
    ): ProfileFields {
        val ageValue = ageValue(age)
        val weightValue = weightValue(weight, imperial)
        val heightValue = heightValue(heightCm, feet, inches, imperial)
        val maxHrValue = maxHrValue(maxHr)
        val sleepNeedValue = sleepNeedValue(sleepNeed)
        val ageError = age.isNotBlank() && ageValue == null
        val weightError = weight.isNotBlank() && weightValue == null
        val heightError = !heightBlank(heightCm, feet, inches, imperial) && heightValue == null
        val maxHrError = maxHr.isNotBlank() && maxHrValue == null
        val sleepNeedError = sleepNeed.isNotBlank() && sleepNeedValue == null
        return ProfileFields(
            valid = !ageError && !weightError && !heightError && !maxHrError && !sleepNeedError,
            ageError = ageError,
            weightError = weightError,
            heightError = heightError,
            maxHrError = maxHrError,
            sleepNeedError = sleepNeedError,
            weightKg = weightValue,
            heightCm = heightValue,
        )
    }

    /** `UserProfile()`: Swift cannot call Kotlin's default arguments. */
    fun blank(): UserProfile = UserProfile()

    /** `when (profile.sex) { Female -> 0; Male -> 1; null -> 2 }` (ProfileScreen.kt:74). */
    fun sexIndex(profile: UserProfile): Int = when (profile.sex) {
        UserProfile.Sex.Female -> 0
        UserProfile.Sex.Male -> 1
        null -> 2
    }

    /** The sleep-need field's opening text: minutes as hours (`formatNumber(it / 60f)`, ProfileScreen.kt:86). */
    fun sleepNeedText(minutes: Int): String = formatNumber(minutes / 60f)

    /** The weight field's opening text: the stored kg, or pounds when the profile is imperial (`formatNumber`). */
    fun weightText(kg: Float, imperial: Boolean): String =
        formatNumber(if (imperial) Units.kgToLb(kg) else kg)

    /** The height field's opening text: the stored centimetres (ProfileScreen.kt:78). */
    fun heightText(cm: Int): String = cm.toString()

    /** The imperial height fields' opening text: the stored centimetres as feet (ProfileScreen.kt:79). */
    fun feetText(cm: Int): String = Units.cmToFeetInches(cm).first.toString()

    /** The inches half of the same conversion. */
    fun inchesText(cm: Int): String = Units.cmToFeetInches(cm).second.toString()

    /**
     * The `profile.copy(...)` the Save button performs (ProfileScreen.kt:130-149). A blank field
     * clears its value; saving the activity level stamps when it was answered, and a save with both
     * weight and height stamps when the body was measured.
     */
    fun toProfile(
        profile: UserProfile,
        age: String,
        sex: Int,
        imperial: Boolean,
        weight: String,
        heightCm: String,
        feet: String,
        inches: String,
        activity: Int,
        betaBlocker: Boolean,
        pregnant: Boolean,
        maxHr: String,
        sleepNeed: String,
        todayIso: String,
        nowEpochSeconds: Long,
    ): UserProfile {
        val year = runCatching { LocalDate.parse(todayIso).year }.getOrNull()
        val ageValue = ageValue(age)
        val weightValue = weightValue(weight, imperial)
        val heightValue = heightValue(heightCm, feet, inches, imperial)
        return profile.copy(
            birthYear = ageValue?.let { years -> year?.let { it - years } },
            sex = when (sex) {
                0 -> UserProfile.Sex.Female
                1 -> UserProfile.Sex.Male
                else -> null
            },
            weightKg = weightValue,
            heightCm = heightValue,
            imperial = imperial,
            sleepNeedMinutes = sleepNeedValue(sleepNeed),
            maxHeartRate = maxHrValue(maxHr),
            activityLevel = activity.takeIf { it in 0..4 },
            // Saving confirms the answers, so the fitness estimate's freshness checks restart.
            activityAnsweredAt = if (activity in 0..4) nowEpochSeconds else null,
            bodyMeasuredAt = if (weightValue != null && heightValue != null) nowEpochSeconds else null,
            betaBlocker = betaBlocker,
            pregnant = pregnant,
        )
    }

    // ---- The field parsers, one per Compose `val` -----------------------------------------------

    private fun ageValue(age: String): Int? = age.trim().toIntOrNull()?.takeIf { it in 10..100 }

    private fun weightValue(weight: String, imperial: Boolean): Float? =
        weight.trim().replace(',', '.').toFloatOrNull()
            ?.let { if (imperial) Units.lbToKg(it) else it }
            ?.takeIf { it in 25f..300f }

    private fun heightValue(heightCm: String, feet: String, inches: String, imperial: Boolean): Int? =
        if (imperial) {
            val f = feet.trim().toIntOrNull()
            val i = inches.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..11 }
            if (f != null && i != null) Units.feetInchesToCm(f, i).takeIf { it in 100..250 } else null
        } else {
            heightCm.trim().toIntOrNull()?.takeIf { it in 100..250 }
        }

    private fun heightBlank(heightCm: String, feet: String, inches: String, imperial: Boolean): Boolean =
        if (imperial) feet.isBlank() && inches.isBlank() else heightCm.isBlank()

    private fun maxHrValue(maxHr: String): Int? = maxHr.trim().toIntOrNull()?.takeIf { it in 120..230 }

    /** Hours, so "7.5" is 450 min; only 7-9 h is accepted (Recovery.SLEEP_NEED_MIN..MAX). */
    private fun sleepNeedValue(sleepNeed: String): Int? =
        sleepNeed.trim().replace(',', '.').toFloatOrNull()?.let { (it * 60).roundToInt() }
            ?.takeIf { it in Recovery.SLEEP_NEED_MIN..Recovery.SLEEP_NEED_MAX }

    /** 72.0 -> "72", 158.7 -> "158.7" (`"%.1f".format(Locale.ROOT, v).removeSuffix(".0")`, ProfileScreen.kt:250). */
    private fun formatNumber(v: Float): String {
        val rounded = (v * 10).roundToInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }
}

/** Steps, sleep and active hours to aim for each day (GoalsDialog.kt). */
object GoalsRules {

    /**
     * Whether Save is enabled (GoalsDialog.kt:52-55): steps in 1,000..50,000, sleep in 4-12 h
     * (minutes may be blank), active hours in 1..18.
     */
    fun valid(steps: String, sleepHours: String, sleepMinutes: String, activeHours: String): Boolean =
        stepsValue(steps) != null && sleepValue(sleepHours, sleepMinutes) != null && activeValue(activeHours) != null

    /**
     * `Goals(stepsValue!!, sleepValue!!, activeValue!!)` (GoalsDialog.kt:55). An invalid field keeps
     * what [current] has, because Save is disabled anyway; the screen never calls it that way.
     */
    fun toGoals(
        steps: String,
        sleepHours: String,
        sleepMinutes: String,
        activeHours: String,
        current: Goals,
    ): Goals = Goals(
        steps = stepsValue(steps) ?: current.steps,
        sleepMinutes = sleepValue(sleepHours, sleepMinutes) ?: current.sleepMinutes,
        activeHours = activeValue(activeHours) ?: current.activeHours,
    )

    /** `goals.steps.toString()` (GoalsDialog.kt:23). */
    fun stepsText(goals: Goals): String = goals.steps.toString()

    /** `(goals.sleepMinutes / 60).toString()` (GoalsDialog.kt:24). */
    fun sleepHoursText(goals: Goals): String = (goals.sleepMinutes / 60).toString()

    /** `(goals.sleepMinutes % 60).toString()` (GoalsDialog.kt:25). */
    fun sleepMinutesText(goals: Goals): String = (goals.sleepMinutes % 60).toString()

    /** `goals.activeHours.toString()` (GoalsDialog.kt:26). */
    fun activeHoursText(goals: Goals): String = goals.activeHours.toString()

    /** `Goals()`: Swift cannot call Kotlin's default arguments. */
    fun defaults(): Goals = Goals()

    private fun stepsValue(steps: String): Int? = steps.toIntOrNull()?.takeIf { it in Goals.STEPS }

    private fun sleepValue(hours: String, minutes: String): Int? =
        hours.toIntOrNull()?.let { h ->
            minutes.ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..59 }?.let { m -> h * 60 + m }
        }?.takeIf { it in Goals.SLEEP_MINUTES }

    private fun activeValue(active: String): Int? = active.toIntOrNull()?.takeIf { it in Goals.ACTIVE_HOURS }
}

/**
 * `ProfileScreen`'s per-field state: which field is filled but out of range, and the weight and
 * height a save (or the unit switch) would use. The constructor is public only because
 * Kotlin/Native does not export a class that has none, and Swift reads this type.
 */
class ProfileFields(
    val valid: Boolean,
    val ageError: Boolean,
    val weightError: Boolean,
    val heightError: Boolean,
    val maxHrError: Boolean,
    val sleepNeedError: Boolean,
    val weightKg: Float?,
    val heightCm: Int?,
)
