package org.openstrap.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Routes.kt`'s `Metric` and `MetricInfo.kt`'s strings, checked against the Compose source: the
 * titles, units and decimals are the ones the detail screen prints, and every metric has the colour,
 * related list and "about" sentences the Compose panels look up (a missing map key is a blank panel
 * on screen, not a compile error).
 */
class MetricTest {
    @Test
    fun titlesUnitsAndDecimalsAreTheComposeOnes() {
        assertEquals("Heart rate", Metric.HeartRate.title)
        assertEquals("bpm", Metric.HeartRate.unit)
        assertEquals(0, Metric.HeartRate.decimals)

        assertEquals("Resting heart rate", Metric.RestingHr.title)
        assertEquals("bpm", Metric.RestingHr.unit)

        assertEquals("Heart-rate variability", Metric.Hrv.title)
        assertEquals("ms", Metric.Hrv.unit)

        assertEquals("Blood oxygen", Metric.Spo2.title)
        assertEquals("%", Metric.Spo2.unit)

        assertEquals("Stress", Metric.Stress.title)
        assertEquals("", Metric.Stress.unit)

        assertEquals("Skin temperature", Metric.SkinTemperature.title)
        assertEquals("°C", Metric.SkinTemperature.unit)
        assertEquals(1, Metric.SkinTemperature.decimals)

        assertEquals("Breathing rate", Metric.Breathing.title)
        assertEquals("br/min", Metric.Breathing.unit)

        assertEquals("Steps", Metric.Steps.title)
        assertEquals("steps", Metric.Steps.unit)
    }

    @Test
    fun eachMetricKeepsItsDailySeries() {
        assertEquals(listOf("HeartRate", "RestingHr", "Hrv", "Spo2", "Stress", "SkinTemperature", "Breathing", "Steps"), Metric.entries.map { it.name })
        assertEquals(
            listOf("HeartRate", "RestingHr", "Hrv", "Spo2", "Stress", "SkinTemperature", "Breathing", "Steps"),
            Metric.entries.map { it.daily.name },
        )
    }

    @Test
    fun formatIsTheComposeFormat() {
        // Steps are the only one with a thousands separator, as Routes.kt:20's "%,d" prints them.
        assertEquals("8,000", Metric.Steps.format(8000f))
        assertEquals("12,346", Metric.Steps.format(12345.6f))
        assertEquals("0", Metric.Steps.format(0f))
        // The rest use %.0f, so Java's half-away-from-zero rounding.
        assertEquals("62", Metric.HeartRate.format(62.4f))
        assertEquals("49", Metric.Hrv.format(48.6f))
        assertEquals("96", Metric.Spo2.format(96f))
        // Skin temperature is the one with a decimal.
        assertEquals("33.5", Metric.SkinTemperature.format(33.5f))
        assertEquals("34.0", Metric.SkinTemperature.format(34f))
    }

    @Test
    fun everyMetricHasTheComposeColour() {
        assertEquals(MetricColor.Heart, Metric.HeartRate.color)
        assertEquals(MetricColor.Heart, Metric.RestingHr.color)
        assertEquals(MetricColor.Hrv, Metric.Hrv.color)
        assertEquals(MetricColor.Hrv, Metric.Breathing.color)
        assertEquals(MetricColor.Spo2, Metric.Spo2.color)
        assertEquals(MetricColor.Stress, Metric.Stress.color)
        assertEquals(MetricColor.Temperature, Metric.SkinTemperature.color)
        assertEquals(MetricColor.Steps, Metric.Steps.color)
    }

    @Test
    fun everyMetricHasARelatedListThatNeverNamesItself() {
        Metric.entries.forEach { m ->
            val related = RELATED[m]
            assertTrue(related != null && related.isNotEmpty(), "${m.name} has no related metrics")
            assertFalse(related!!.contains(m), "${m.name} is related to itself")
        }
        // Spot checks straight from MetricInfo.kt:27-36.
        assertEquals(listOf(Metric.RestingHr, Metric.Hrv, Metric.Stress), RELATED[Metric.HeartRate])
        assertEquals(listOf(Metric.Breathing, Metric.RestingHr), RELATED[Metric.Spo2])
        assertEquals(listOf(Metric.HeartRate), RELATED[Metric.Steps])
    }

    @Test
    fun everyMetricHasBothAboutSentences() {
        Metric.entries.forEach { m ->
            val (what, why) = ABOUT[m] ?: error("${m.name} has no about text")
            assertTrue(what.isNotBlank(), "${m.name} has no 'what it is' sentence")
            assertTrue(why.isNotBlank(), "${m.name} has no 'what moves it' sentence")
        }
        assertTrue(ABOUT.getValue(Metric.Spo2).first.startsWith("Blood oxygen:"))
        assertTrue(ABOUT.getValue(Metric.Stress).first.startsWith("The strap's 0–100 stress level"))
    }

    @Test
    fun rangesAreTheScreensOnes() {
        assertEquals(listOf("Day", "Week", "Month", "Year"), Range.entries.map { it.label })
        assertEquals(listOf(1, 7, 30, 365), Range.entries.map { it.days })
    }
}
