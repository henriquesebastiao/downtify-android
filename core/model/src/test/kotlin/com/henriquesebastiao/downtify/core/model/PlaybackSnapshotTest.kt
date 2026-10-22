package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSnapshotTest {
    private val item = SavedPlaybackItem("a", "Title", "Artist", durationMs = 60_000)

    @Test
    fun `duplicate tracks remain separate positions in the shuffle permutation`() {
        val state = PlaybackSnapshot(
            listOf(item, item, item.copy(mediaId = "b")),
            1,
            12_345,
            shuffle = true,
            shuffleOrder = listOf(2, 0, 1),
        )
        assertEquals(state, state.validated())
    }

    @Test
    fun `invalid indices and empty media identities do not restore`() {
        assertNull(PlaybackSnapshot(emptyList(), 0, 0).validated())
        assertNull(PlaybackSnapshot(listOf(item), 4, 0).validated())
        assertNull(PlaybackSnapshot(listOf(item.copy(mediaId = "")), 0, 0).validated())
    }

    @Test
    fun `invalid optional state falls back without dropping the queue`() {
        val state = PlaybackSnapshot(listOf(item, item), 0, -1, true, listOf(0, 0), 99, Float.NaN).validated()!!
        assertEquals(listOf(0, 1), state.shuffleOrder)
        assertEquals(0L, state.positionMs)
        assertEquals(0, state.repeatMode)
        assertEquals(1f, state.speed)
    }

    @Test
    fun `position cannot exceed the saved duration`() {
        assertEquals(60_000L, PlaybackSnapshot(listOf(item), 0, Long.MAX_VALUE).validated()!!.positionMs)
    }
}
