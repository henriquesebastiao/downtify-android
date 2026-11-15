package com.henriquesebastiao.downtify.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDjWindowTest {
    @Test fun `window contains only current and at most three following occurrences`() {
        assertEquals(listOf(3, 4, 5, 6), localDjWindowIndices(300, 3, false))
        assertEquals(listOf(299), localDjWindowIndices(300, 299, false))
        assertEquals(listOf(299, 0, 1, 2), localDjWindowIndices(300, 299, true))
        assertEquals(listOf(1, 0, 1, 0), localDjWindowIndices(2, 1, true))
        assertEquals(listOf(0), localDjWindowIndices(1, 0, true))
        assertTrue(localDjWindowIndices(0, 0, true).isEmpty())
        assertTrue(localDjWindowIndices(10, -1, false).isEmpty())
    }
}
