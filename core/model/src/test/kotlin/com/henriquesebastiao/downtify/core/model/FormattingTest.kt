package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun `a sample rate in kHz keeps its decimals and drops trailing zeros`() {
        assertEquals("44.1", kilohertz(44_100))
        assertEquals("48", kilohertz(48_000))
        assertEquals("96", kilohertz(96_000))
        assertEquals("22.05", kilohertz(22_050))
        assertEquals("176.4", kilohertz(176_400))
    }

    @Test
    fun `a bitrate in kbps is rounded to the nearest`() {
        assertEquals(941, kilobits(941_302))
        assertEquals(942, kilobits(941_500))
        assertEquals(320, kilobits(320_000))
        assertEquals(0, kilobits(0))
    }
}
