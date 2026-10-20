package org.openstrap.app.data

/**
 * The words the Recovery screens use, copied from
 * `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt` @ 19f2865 (the empty states in
 * `RecoveryCard`, the two hints in `ChargeCard`, and `HowItWorksPanel`'s explanation). Same wording,
 * character for character; the iOS screens print them, so the two platforms cannot drift apart.
 *
 * They live here rather than in the Swift views because the card's rule is: the Android app's
 * wording, no new copy.
 */
object RecoveryCopy {
    /** Nothing has been worn to bed yet: there is no night to score. */
    const val NO_SLEEP = "Recovery is scored each morning from the night's sleep. Wear the strap to bed."

    /** A night exists, but the HRV and resting-HR baselines are still filling up. */
    val LEARNING = "OpenStrap scores Recovery once it has ${Recovery.SCORED_NIGHTS} good nights of your own HRV and resting heart rate."

    /** Past the baseline nights, but last night's own readings failed the validity rule. */
    const val WITHHELD_TONIGHT = "Last night's HRV and resting heart rate didn't have enough clean readings, so there's no Recovery score today. Sleep alone isn't readiness."

    /** `ChargeCard`'s hint before any sleep has been simulated. */
    const val CHARGE_NEEDS_A_NIGHT = "Charge starts after your first night wearing the strap. Sleep is what fills it, so it needs one to measure from."

    /** `ChargeCard`'s hint when the charge model has no heart rate yet. */
    const val CHARGE_NEEDS_HEART_RATE = "Charge appears once the strap has a few hours of heart rate."

    /** `ContributorsPanel`'s note when a very low signal capped the weighted score. */
    const val CAPPED = "Capped: one body signal is far from your normal, so good sleep can't fully make up for it."

    /** `HowItWorksPanel`: what Readiness is. */
    const val EXPLANATION =
        "Each morning OpenStrap compares your night with what's normal for you, not with other people: HRV, resting heart " +
            "rate, skin temperature, breathing and blood oxygen, last night's sleep and the past few nights, and how hard " +
            "the previous days were. The score is a weighted mix; one signal far from normal caps it."

    /** `HowItWorksPanel`: what Charge is. */
    const val CHARGE_EXPLANATION =
        "Charge is a running battery: sleep and calm rest fill it, movement, stress and simply being awake drain it."

    /** The seven-night HRV range note, as `ContributorsPanel` prints it. */
    fun hrvRange(range: Recovery.HrvRange, thin: Boolean, nights: Int): String {
        val word = when (range) {
            Recovery.HrvRange.Below -> "below"
            Recovery.HrvRange.Within -> "within"
            Recovery.HrvRange.Above -> "above"
        }
        return "7-night HRV is $word your normal range" + if (thin) " (thin: $nights of 7 nights)." else "."
    }
}
