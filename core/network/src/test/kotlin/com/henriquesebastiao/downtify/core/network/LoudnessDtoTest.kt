package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.LoudnessProfile
import com.henriquesebastiao.downtify.core.network.dto.LoudnessDto
import org.junit.Assert.assertEquals
import org.junit.Test

class LoudnessDtoTest {
    @Test
    fun `the contract's profile parses with its offline binding`() {
        val profile = NetworkJson.decodeFromString<LoudnessDto>(
            """
            {"state":"ready","gain_db":-4.2,"integrated_lufs":-13.8,"true_peak_dbtp":0.4,
             "algorithm_version":1,"fingerprint":"9f2c","source_etag":"\"5d41\"","source_size":8312004}
            """.trimIndent(),
        ).toModel()
        assertEquals(
            LoudnessProfile("ready", 1, -13.8, 0.4, -4.2, "9f2c", "\"5d41\"", 8_312_004),
            profile,
        )
        assertEquals(-4.2, profile.safeGainDb(), 0.001)
    }

    @Test
    fun `an unmeasured track parses to no gain`() {
        val profile = NetworkJson.decodeFromString<LoudnessDto>("""{"state":"unanalysed","gain_db":0.0}""").toModel()
        assertEquals(0.0, profile.safeGainDb(), 0.0)
    }
}
