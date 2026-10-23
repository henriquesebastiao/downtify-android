package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.LoudnessDto
import org.junit.Assert.assertEquals
import org.junit.Test

class LoudnessDtoTest {
    @Test
    fun amplificationAndPeaksAreCappedEvenWhenTheServerRequestsMoreGain() {
        assertEquals(12.0, LoudnessDto("ready", 1, -40.0, -20.0, 30.0).safeGainDb(), 0.001)
        assertEquals(4.0, LoudnessDto("ready", 1, -40.0, -5.0, 12.0).safeGainDb(), 0.001)
        assertEquals(-10.0, LoudnessDto("ready", 1, -8.0, -0.5, -10.0).safeGainDb(), 0.001)
    }

    @Test
    fun missingUnsupportedAndNonFiniteMeasurementsKeepOriginalVolume() {
        assertEquals(0.0, LoudnessDto().safeGainDb(), 0.0)
        assertEquals(0.0, LoudnessDto("ready", 2, -30.0, -20.0, 12.0).safeGainDb(), 0.0)
        assertEquals(0.0, LoudnessDto("ready", 1, Double.NaN, -20.0, 12.0).safeGainDb(), 0.0)
        assertEquals(0.0, LoudnessDto("silent", 1).safeGainDb(), 0.0)
    }
}
