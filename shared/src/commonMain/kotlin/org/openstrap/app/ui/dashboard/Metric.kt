package org.openstrap.app.ui.dashboard

import kotlin.math.round
import org.openstrap.app.data.DailyMetric
import org.openstrap.app.data.format

/**
 * A metric with its own detail screen: its title, unit, decimals shown, and the per-day series behind
 * it (`Routes.kt` lines 9-21).
 *
 * Copied as is; what changed is only what Compose and `java.util` needed. `Math.round` became
 * `round(...).toLong()` and the format strings go through `shared/`'s `String.format`, which prints
 * `Locale.ROOT` exactly as `java.util.Formatter` did.
 */
enum class Metric(val title: String, val unit: String, val decimals: Int, val daily: DailyMetric) {
    HeartRate("Heart rate", "bpm", 0, DailyMetric.HeartRate),
    RestingHr("Resting heart rate", "bpm", 0, DailyMetric.RestingHr),
    Hrv("Heart-rate variability", "ms", 0, DailyMetric.Hrv),
    Spo2("Blood oxygen", "%", 0, DailyMetric.Spo2),
    Stress("Stress", "", 0, DailyMetric.Stress),
    SkinTemperature("Skin temperature", "°C", 1, DailyMetric.SkinTemperature),
    Breathing("Breathing rate", "br/min", 0, DailyMetric.Breathing),
    Steps("Steps", "steps", 0, DailyMetric.Steps),
    ;

    fun format(v: Float): String = if (this == Steps) "%,d".format(round(v).toLong()) else "%.${decimals}f".format(v)
}

/**
 * The glow palette's metric accent, by name only: `MetricInfo.kt:18-25` returns a Compose `Color`,
 * which `shared/` has no type for, so the Swift view maps the name to `Theme.swift`.
 */
enum class MetricColor { Heart, Hrv, Spo2, Stress, Temperature, Steps }

/** `MetricInfo.kt:18`'s `colorOf`, as a property so Swift reads `metric.color`. */
val Metric.color: MetricColor
    get() = when (this) {
        Metric.HeartRate, Metric.RestingHr -> MetricColor.Heart
        Metric.Hrv, Metric.Breathing -> MetricColor.Hrv
        Metric.Spo2 -> MetricColor.Spo2
        Metric.Stress -> MetricColor.Stress
        Metric.SkinTemperature -> MetricColor.Temperature
        Metric.Steps -> MetricColor.Steps
    }

/** How far back a metric detail looks: the selected day, or one value per day (per week, for a year). */
enum class Range(val label: String, val days: Int) { Day("Day", 1), Week("Week", 7), Month("Month", 30), Year("Year", 365) }

/** The metrics each detail screen offers underneath it (`MetricInfo.kt:27-36`). */
val RELATED = mapOf(
    Metric.HeartRate to listOf(Metric.RestingHr, Metric.Hrv, Metric.Stress),
    Metric.RestingHr to listOf(Metric.Hrv, Metric.HeartRate, Metric.SkinTemperature),
    Metric.Hrv to listOf(Metric.RestingHr, Metric.Stress, Metric.Breathing),
    Metric.Spo2 to listOf(Metric.Breathing, Metric.RestingHr),
    Metric.Stress to listOf(Metric.Hrv, Metric.HeartRate),
    Metric.SkinTemperature to listOf(Metric.RestingHr, Metric.Breathing),
    Metric.Breathing to listOf(Metric.Spo2, Metric.SkinTemperature, Metric.Hrv),
    Metric.Steps to listOf(Metric.HeartRate),
)

/** What the metric is, and what moves it, as the About panel prints them (`MetricInfo.kt:38-71`). */
val ABOUT = mapOf(
    Metric.HeartRate to (
        "How fast your heart beats, measured all day by the strap." to
            "Movement, stress, caffeine and heat raise it; rest and sleep bring it down."
        ),
    Metric.RestingHr to (
        "Your heart rate at its calmest, which the strap works out each day. Lower usually means a fitter, well-rested heart." to
            "It rises after hard training, late meals, alcohol, poor sleep or when your body is fighting something off, and falls as fitness builds."
        ),
    Metric.Hrv to (
        "Heart-rate variability: how much the time between beats varies. Higher than your usual means your body has recovered." to
            "Good sleep and rest raise it. Stress, hard training and alcohol lower it. Compare it with your own normal, not other people's."
        ),
    Metric.Spo2 to (
        "Blood oxygen: how much of your blood's capacity is carrying oxygen. Most people sit at 95–100%." to
            "Altitude and breathing pauses in sleep can lower it. A loose strap can also give low readings."
        ),
    Metric.Stress to (
        "The strap's 0–100 stress level, worked out from your heart rate and its variability while you're still." to
            "Pressure, caffeine and too little sleep raise it; calm breathing, rest and time outdoors bring it down."
        ),
    Metric.SkinTemperature to (
        "The temperature of your skin under the strap. The level depends on fit and room; what matters is the change from your normal." to
            "It moves with alcohol, a warm room, hard training, the menstrual cycle, or when your body is fighting something off."
        ),
    Metric.Breathing to (
        "How many breaths you take a minute while asleep. It stays very steady for most people." to
            "It can rise above your normal after a hard training day, in a warm, stuffy room, or when your body is fighting something off."
        ),
    Metric.Steps to (
        "Steps counted by the strap through the day." to
            "Short walks spread across the day add up; most people aim for 7,000–10,000."
        ),
)
