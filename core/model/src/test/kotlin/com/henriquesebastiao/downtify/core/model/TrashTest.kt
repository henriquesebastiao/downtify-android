package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashTest {
    @Test
    fun serverStatesAreReadAndUnknownOnesDontBreakAnything() {
        TrashState.entries.filter { it != TrashState.Unknown }.forEach { assertEquals(it, TrashState.of(it.wire)) }
        assertEquals(TrashState.Unknown, TrashState.of("future_state"))
        assertEquals(TrashState.Unknown, TrashState.of(""))
    }

    @Test
    fun onlyKeptWaitingOrStoppedSongsWithinTheirTimeCanBeRestored() {
        val now = 1_000_000L
        val kept = TrashItem("1", "a.flac", state = TrashState.Trashed, expiresAt = now + 1)
        assertTrue(kept.restorable(now))
        assertFalse(kept.copy(expiresAt = now).restorable(now))
        assertTrue(TrashItem("2", "a.flac", state = TrashState.Pending).restorable(now))
        assertTrue(TrashItem("3", "a.flac", state = TrashState.Error).restorable(now))
        assertFalse(TrashItem("4", "a.flac", state = TrashState.Moving).restorable(now))
        assertFalse(TrashItem("5", "a.flac", state = TrashState.Purged).restorable(now))
        assertFalse(TrashItem("6", "a.flac", state = TrashState.Unknown).restorable(now))
    }

    @Test
    fun titleAndFormatFallBackToTheFile() {
        val bare = TrashItem("1", "Artist/Album/01 - Song.flac")
        assertEquals("01 - Song.flac", bare.displayTitle)
        assertEquals("FLAC", bare.format)
        val tagged = bare.copy(title = "Song", codec = "mp3")
        assertEquals("Song", tagged.displayTitle)
        assertEquals("MP3", tagged.format)
    }

    @Test
    fun movesStillGoingAreInFlight() {
        assertTrue(TrashState.Pending.inFlight)
        assertTrue(TrashState.Restoring.inFlight)
        assertFalse(TrashState.Trashed.inFlight)
        assertFalse(TrashState.Error.inFlight)
    }
}
