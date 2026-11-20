package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentContextTest {
    private val song = track("t1", title = "Way Too Self Aware")

    @Test
    fun `a loose song is remembered as that song`() {
        val recent = PlaybackContext(PlaybackContextType.Songs, "", "").recentFor(song)

        assertEquals(PlaybackContext(PlaybackContextType.Songs, "t1", "Way Too Self Aware"), recent)
    }

    @Test
    fun `two loose songs are two recents`() {
        val loose = PlaybackContext(PlaybackContextType.Songs, "", "")
        val first = loose.recentFor(song)
        val second = loose.recentFor(track("t2"))

        assertTrue(first.refId != second.refId)
    }

    @Test
    fun `albums, artists, playlists and liked songs are remembered as themselves`() {
        listOf(
            PlaybackContext(PlaybackContextType.Album, "alb", "Album"),
            PlaybackContext(PlaybackContextType.Artist, "art", "Artist"),
            PlaybackContext(PlaybackContextType.Playlist, "mix", "Mix"),
            PlaybackContext(PlaybackContextType.Liked, "", ""),
        ).forEach { assertEquals(it, it.recentFor(song)) }
    }

    @Test
    fun `only a loose song without its id is bare`() {
        assertTrue(RecentContext(PlaybackContextType.Songs, "", "", null, 0).isBareSongs)
        assertFalse(RecentContext(PlaybackContextType.Songs, "t1", "Song", "t1", 0).isBareSongs)
        assertFalse(RecentContext(PlaybackContextType.Liked, "", "", null, 0).isBareSongs)
    }
}
