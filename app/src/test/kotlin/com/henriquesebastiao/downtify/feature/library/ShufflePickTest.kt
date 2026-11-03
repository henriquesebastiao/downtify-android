package com.henriquesebastiao.downtify.feature.library

import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.ui.common.PreviewData
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShufflePickTest {
    private val library = LibraryUiState(
        loading = false,
        songs = PreviewData.tracks,
        albums = PreviewData.albums,
        artists = PreviewData.artists,
    )

    @Test
    fun songsShufflesEverySong() {
        val pick = shufflePick(library.copy(tab = LibraryTab.Songs))
        assertEquals(PreviewData.tracks, pick?.tracks)
        assertEquals(PlaybackContext(PlaybackContextType.Songs, "", ""), pick?.context)
    }

    @Test
    fun albumsShufflesOneAlbumAsThatAlbum() {
        val pick = shufflePick(library.copy(tab = LibraryTab.Albums), Random(7))!!
        val album = PreviewData.albums.single { it.id == pick.context.refId }
        assertEquals(PlaybackContext(PlaybackContextType.Album, album.id, album.title), pick.context)
        assertEquals(album.tracks, pick.tracks)
    }

    @Test
    fun artistsShufflesOneArtistsSongs() {
        val pick = shufflePick(library.copy(tab = LibraryTab.Artists), Random(3))!!
        val artist = PreviewData.artists.single { it.id == pick.context.refId }
        assertEquals(PlaybackContextType.Artist, pick.context.type)
        assertEquals(artist.tracks, pick.tracks)
    }

    @Test
    fun likedSongsArePlayedAsLikedAndEmptyPlaylistsAreSkipped() {
        val liked = PreviewData.tracks.take(2)
        val state = library.copy(
            tab = LibraryTab.Playlists,
            playlists = listOf(
                PlaylistItem(null, "", liked.size, null, liked),
                PlaylistItem(PreviewData.playlists.first(), "Deep Focus", 3, null, emptyList()),
            ),
        )
        repeat(5) { seed ->
            val pick = shufflePick(state, Random(seed))!!
            assertEquals(PlaybackContext(PlaybackContextType.Liked, "", ""), pick.context)
            assertEquals(liked, pick.tracks)
        }
    }

    @Test
    fun aTabWithNothingToPlayHasNoShuffle() {
        val empty = LibraryUiState(loading = false, tab = LibraryTab.Artists)
        assertNull(shufflePick(empty))
        assertFalse(empty.canShuffle)
        assertTrue(library.copy(tab = LibraryTab.Albums).canShuffle)
    }
}
