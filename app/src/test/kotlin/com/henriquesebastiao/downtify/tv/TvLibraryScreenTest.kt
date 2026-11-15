package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.feature.library.LibraryNavigation
import com.henriquesebastiao.downtify.feature.library.LibraryTab
import com.henriquesebastiao.downtify.feature.library.LibraryUiState
import com.henriquesebastiao.downtify.feature.library.PlaylistItem
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The TV's 960 × 540 dp screen.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvLibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: LibraryUiState,
        navigation: LibraryNavigation = LibraryNavigation(),
        onSelectTab: (LibraryTab) -> Unit = {},
        onPlaySong: (Int) -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) {
            TvLibraryScreen(state, navigation, onSelectTab = onSelectTab, onPlaySong = onPlaySong)
        }
    }

    @Test
    fun theSectionsAreThePhonesWithTheSelectedOneMarked() {
        val picked = mutableListOf<LibraryTab>()
        show(LibraryUiState(loading = false, tab = LibraryTab.Albums), onSelectTab = { picked += it })

        compose.onNodeWithText("Albums").assertIsSelected()
        compose.onNodeWithText("Artists").performClick()
        compose.onNodeWithText("Playlists").performClick()

        assertEquals(listOf(LibraryTab.Artists, LibraryTab.Playlists), picked)
    }

    @Test
    fun anAlbumOpensItsPage() {
        val album = PreviewData.albums.first()
        val opened = mutableListOf<String>()
        show(
            LibraryUiState(loading = false, tab = LibraryTab.Albums, albums = PreviewData.albums),
            navigation = LibraryNavigation(onAlbum = { opened += it }),
        )

        compose.onNodeWithText(album.title).performClick()

        assertEquals(listOf(album.id), opened)
    }

    @Test
    fun anArtistOpensItsPage() {
        val artist = PreviewData.artists.first()
        val opened = mutableListOf<String>()
        show(
            LibraryUiState(loading = false, tab = LibraryTab.Artists, artists = PreviewData.artists),
            navigation = LibraryNavigation(onArtist = { opened += it }),
        )

        compose.onNodeWithText(artist.name).performClick()

        assertEquals(listOf(artist.id), opened)
    }

    @Test
    fun aSongPlaysFromItsPlaceInTheList() {
        val songs = PreviewData.tracks
        val played = mutableListOf<Int>()
        show(LibraryUiState(loading = false, tab = LibraryTab.Songs, songs = songs), onPlaySong = { played += it })

        compose.onNodeWithText(songs[1].displayTitle).performClick()

        assertEquals(listOf(1), played)
    }

    @Test
    fun likedSongsLeadsThePlaylists() {
        var liked = 0
        show(
            LibraryUiState(
                loading = false,
                tab = LibraryTab.Playlists,
                playlists = listOf(PlaylistItem(null, "", 3, null)),
            ),
            navigation = LibraryNavigation(onLiked = { liked++ }),
        )

        compose.onNodeWithText("Liked songs").performClick()

        assertEquals(1, liked)
    }

    @Test
    fun anEmptySectionSaysSo() {
        show(LibraryUiState(loading = false, tab = LibraryTab.Artists))

        compose.onNodeWithText("No artists yet").assertExists()
    }
}
