package com.henriquesebastiao.downtify.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses stamps, repeated stamps, metadata and offset`() {
        val lines = LrcParser.parse(
            """
            [ar:Portishead]
            [offset:+500]
            [00:12.50]Can't anybody see
            [00:20.1][01:05.123]We've got a war to fight
            not a lyric line
            [01:10]
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LyricLine(12_000, "Can't anybody see"),
                LyricLine(19_600, "We've got a war to fight"),
                LyricLine(64_623, "We've got a war to fight"),
                LyricLine(69_500, ""),
            ),
            lines,
        )
    }

    @Test
    fun `static text is the plain text, else the synced lines`() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, ""), LyricLine(9_000, "c"))
        assertEquals("a\n\nc", Lyrics(lines, "").text)
        assertEquals("plain words", Lyrics(lines, "\nplain words\n").text)
        assertEquals("", Lyrics.Empty.text)
    }

    @Test
    fun `finds the active line`() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertEquals(-1, LrcParser.activeIndex(lines, 500))
        assertEquals(0, LrcParser.activeIndex(lines, 1_000))
        assertEquals(1, LrcParser.activeIndex(lines, 8_999))
        assertEquals(2, LrcParser.activeIndex(lines, 60_000))
    }

    @Test
    fun `tells how long until the next line`() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(5_000, "b2"), LyricLine(9_000, "c"))
        assertEquals(500L, LrcParser.nextLineInMs(lines, 500))
        assertEquals(4_000L, LrcParser.nextLineInMs(lines, 1_000))
        // Lines sharing a stamp start together: the next one is the line after both.
        assertEquals(4_000L, LrcParser.nextLineInMs(lines, 5_000))
        assertEquals(1L, LrcParser.nextLineInMs(lines, 8_999))
        assertNull(LrcParser.nextLineInMs(lines, 9_000))
        assertNull(LrcParser.nextLineInMs(emptyList(), 0))
    }

    @Test
    fun `moves the lines by the offset, within limits`() {
        val lines = listOf(LyricLine(0, "a"), LyricLine(4_000, "b"))
        assertSame(lines, LyricsTiming.shift(lines, 0))
        assertEquals(listOf(LyricLine(750, "a"), LyricLine(4_750, "b")), LyricsTiming.shift(lines, 750))
        assertEquals(listOf(LyricLine(-500, "a"), LyricLine(3_500, "b")), LyricsTiming.shift(lines, -500))
        // A line moved before the start is being sung from the first moment.
        assertEquals(0, LrcParser.activeIndex(LyricsTiming.shift(lines, -500), 0))
        assertEquals(LyricsTiming.MAX_MS, LyricsTiming.clamp(LyricsTiming.MAX_MS + LyricsTiming.STEP_MS))
        assertEquals(-LyricsTiming.MAX_MS, LyricsTiming.clamp(-60_000))
        assertEquals(1_250L, LyricsTiming.clamp(1_250))
    }

    @Test
    fun `writes the offset in seconds the locale's way`() {
        assertEquals("+0.25", LyricsTiming.seconds(250, Locale.US))
        assertEquals("-1,50", LyricsTiming.seconds(-1_500, Locale.forLanguageTag("pt-BR")))
        assertEquals("0.00", LyricsTiming.seconds(0, Locale.US))
    }
}
