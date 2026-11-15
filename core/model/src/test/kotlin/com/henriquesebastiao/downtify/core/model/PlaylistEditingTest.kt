package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEditingTest {
    private fun playlist(
        name: String = "Mix",
        manual: Boolean = true,
        canEditTracks: Boolean = true,
        mine: Boolean = true,
        liked: Boolean = false,
    ) = Playlist(
        name = name,
        liked = liked,
        cover = "",
        trackIds = listOf("t1"),
        manual = manual,
        canEditTracks = canEditTracks,
        mine = mine,
    )

    @Test
    fun `podcast episodes never go into a playlist`() {
        val ids = listOf("t1", EpisodeIds.mediaId(42), "t2", "")

        assertEquals(listOf("t1", "t2"), playlistTrackIds(ids))
    }

    @Test
    fun `a repeated song is sent once`() {
        assertEquals(listOf("t1", "t2"), playlistTrackIds(listOf("t1", "t2", "t1")))
    }

    @Test
    fun `only manual playlists the caller may edit are offered`() {
        val offered = addablePlaylists(
            listOf(
                playlist("Mine"),
                playlist("Imported", manual = false),
                playlist("Read only", canEditTracks = false),
                playlist("Liked", liked = true),
            ),
        )

        assertEquals(listOf("Mine"), offered.map { it.name })
    }

    @Test
    fun `an editor may change songs but never rename`() {
        val shared = playlist(mine = false, canEditTracks = true)

        assertTrue(shared.isEditable(serverSupportsEditing = true))
        assertFalse(shared.isRenamable(serverSupportsEditing = true))
    }

    @Test
    fun `an older server leaves a playlist read only`() {
        val own = playlist()

        assertFalse(own.isEditable(serverSupportsEditing = false))
        assertFalse(own.isRenamable(serverSupportsEditing = false))
    }

    @Test
    fun `an imported playlist cannot be edited by track id`() {
        val imported = playlist(manual = false)

        assertFalse(imported.isEditable(serverSupportsEditing = true))
    }

    @Test
    fun `moving a song keeps the rest in order`() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").moved(0, 2))
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(1, 0))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(1, 1))
        // Out of range is a no-op, not a crash.
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, 5))
    }

    @Test
    fun `a repeated song moves by occurrence, not by id`() {
        assertEquals(listOf("t2", "t1", "t1"), listOf("t1", "t1", "t2").moved(2, 0))
    }
}
