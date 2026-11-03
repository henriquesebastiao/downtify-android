package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollbarGeometryTest {
    // 100 rows of 80 px with 8 px between them, 16 px padding on top and 200 at the end, in 1000 px.
    private fun at(line: Int, offset: Int) = ScrollMetrics(
        firstLine = line,
        firstLineOffset = offset,
        lineSize = 88f,
        lineCount = 100,
        spacing = 8,
        before = 16,
        after = 200,
        viewport = 1000,
    )

    @Test
    fun atTheTopTheThumbIsAtTheStartAndAsLongAsTheShareInView() {
        val top = at(0, 0)
        assertEquals(0f, top.progress)
        // 16 + 100 × 88 − 8 + 200 = 9008 px of content.
        assertEquals(1000f / 9008f, top.visibleShare, 1e-6f)
    }

    @Test
    fun halfwayDownTheProgressIsHalf() {
        val max = at(0, 0).maxScroll
        val target = at(0, 0).target(0.5f)
        val there = at(target.line, -target.offset)
        assertEquals(0.5f, there.progress, 0.001f)
        assertEquals(max / 2, there.scrolled, 1f)
    }

    @Test
    fun theEndIsAllTheWayDown() {
        val end = at(0, 0).target(1f)
        assertEquals(1f, at(end.line, -end.offset).progress, 0.001f)
    }

    @Test
    fun contentThatFitsDoesNotScroll() {
        val short = at(0, 0).copy(lineCount = 3)
        assertEquals(0f, short.maxScroll)
        assertEquals(0f, short.progress)
        assertEquals(1f, short.visibleShare)
        assertEquals(ScrollTarget(0, 0), short.target(1f))
    }

    @Test
    fun theThumbNeverShrinksBelowItsMinimumAndStaysOnTheTrack() {
        val thumb = ScrollThumb.of(trackLength = 600f, visibleShare = 0.01f, progress = 1f, minLength = 48f)
        assertEquals(48f, thumb.length)
        assertEquals(552f, thumb.top)
    }

    @Test
    fun draggingPutsTheThumbsMiddleUnderTheFinger() {
        assertEquals(0f, ScrollThumb.progressAt(y = 10f, trackLength = 600f, thumbLength = 100f))
        assertEquals(0.5f, ScrollThumb.progressAt(y = 300f, trackLength = 600f, thumbLength = 100f))
        assertEquals(1f, ScrollThumb.progressAt(y = 590f, trackLength = 600f, thumbLength = 100f))
    }
}
