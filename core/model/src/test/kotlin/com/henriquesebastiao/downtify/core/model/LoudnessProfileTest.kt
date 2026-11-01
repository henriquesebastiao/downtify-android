package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessProfileTest {
    private fun ready(integrated: Double, peak: Double, gain: Double) =
        LoudnessProfile("ready", 1, integrated, peak, gain)

    @Test
    fun amplificationAndPeaksAreCappedEvenWhenTheServerRequestsMoreGain() {
        assertEquals(12.0, ready(-40.0, -20.0, 30.0).safeGainDb(), 0.001)
        assertEquals(4.0, ready(-40.0, -5.0, 12.0).safeGainDb(), 0.001)
        assertEquals(-10.0, ready(-8.0, -0.5, -10.0).safeGainDb(), 0.001)
    }

    @Test
    fun louderLevelsRecoverAttenuationWithoutExceedingMeasuredPeakHeadroom() {
        val profile = ready(-8.0, -0.5, -10.0)
        assertEquals(-10.0, profile.safeGainDb(NormalizationLevel.Normal), 0.001)
        assertEquals(-4.0, profile.safeGainDb(NormalizationLevel.Louder), 0.001)
        assertEquals(-0.5, profile.safeGainDb(NormalizationLevel.Maximum), 0.001)
        NormalizationLevel.entries.forEach { level ->
            assertEquals(0.0, LoudnessProfile().safeGainDb(level), 0.0)
            assertEquals(0.0, profile.copy(truePeakDbtp = Double.NaN).safeGainDb(level), 0.0)
            assertTrue(ready(-40.0, -20.0, 12.0).safeGainDb(level) <= 12.0)
        }
    }

    @Test
    fun everyLevelKeepsThePeakCeilingAndNeverMakesAValidProfileQuieter() {
        for (integrated in -40..-5) {
            for (peak in -20..0) {
                val original = minOf(12.0, -18.0 - integrated, -1.0 - peak)
                val profile = ready(integrated.toDouble(), peak.toDouble(), original)
                var previous = profile.safeGainDb()
                NormalizationLevel.entries.forEach { level ->
                    val gain = profile.safeGainDb(level)
                    assertTrue(gain >= previous)
                    assertTrue(peak + gain <= -1.0)
                    assertTrue(gain <= 12.0)
                    previous = gain
                }
            }
        }
    }

    @Test
    fun missingUnsupportedAndNonFiniteMeasurementsKeepOriginalVolume() {
        assertEquals(0.0, LoudnessProfile().safeGainDb(), 0.0)
        assertEquals(0.0, ready(-30.0, -20.0, 12.0).copy(algorithmVersion = 2).safeGainDb(), 0.0)
        assertEquals(0.0, ready(Double.NaN, -20.0, 12.0).safeGainDb(), 0.0)
        assertEquals(0.0, ready(-30.0, -20.0, -90.0).safeGainDb(), 0.0)
        assertEquals(0.0, LoudnessProfile("silent", 1).safeGainDb(), 0.0)
    }

    @Test
    fun onlyAMeasurementOfAKnownFileCanBeBoundToAnOfflineCopy() {
        assertTrue(ready(-20.0, -6.0, 2.0).copy(fingerprint = "f").bindable)
        assertFalse(ready(-20.0, -6.0, 2.0).bindable)
        assertFalse(LoudnessProfile("unanalysed", 1, fingerprint = "f").bindable)
    }
}
