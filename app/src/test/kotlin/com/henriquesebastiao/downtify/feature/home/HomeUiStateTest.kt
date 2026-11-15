package com.henriquesebastiao.downtify.feature.home

import com.henriquesebastiao.downtify.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    private val favorite =
        Playlist("Road", liked = false, cover = "covers/road.jpg", trackIds = listOf("t1"), favorite = true)
    private val other = Playlist("Night", liked = false, cover = "covers/night.jpg", trackIds = listOf("t2"))

    @Test
    fun aRecentPlaylistThatIsNotAFavouriteIsStillKnownForItsCover() {
        val state = HomeUiState(
            favoritePlaylists = listOf(favorite),
            playlistsByName = listOf(favorite, other).associateBy { it.name },
        )

        assertEquals(other, state.knownPlaylists["Night"])
        assertEquals(favorite, state.knownPlaylists["Road"])
    }

    @Test
    fun favouritesAloneAreKnownToo() {
        assertEquals(favorite, HomeUiState(favoritePlaylists = listOf(favorite)).knownPlaylists["Road"])
    }
}
