package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.model.DjLocalProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDjWindowTest {
    @Test fun `queue limitations are distinct from missing profiles and unsupported speed`() {
        val window = LocalDjWindow(1, 1, listOf(0, 1), listOf("a", "b"), false, false, 1f, 1f)
        assertNull(localDjWindowProblem(window, 1, true))
        assertEquals(DjLocalProblem.UnsupportedQueue, localDjWindowProblem(window.copy(shuffled = true), 1, true))
        assertEquals(DjLocalProblem.UnsupportedQueue, localDjWindowProblem(window.copy(repeatOne = true), 1, false))
        assertEquals(DjLocalProblem.UnsupportedQueue, localDjWindowProblem(window.copy(ids = listOf("a")), 1, true))
        assertEquals(DjLocalProblem.MissingAnalysis, localDjWindowProblem(window, 1, false))
        assertEquals(DjLocalProblem.UnsupportedPlayback, localDjWindowProblem(window.copy(speed = 1.1f), 1, true))
        assertEquals(DjLocalProblem.UnsupportedPlayback, localDjWindowProblem(window.copy(pitch = 1.1f), 1, true))
        assertEquals(DjLocalProblem.Unsupported, localDjWindowProblem(window, 0, true))
    }

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
