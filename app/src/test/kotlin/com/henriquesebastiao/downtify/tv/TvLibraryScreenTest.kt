package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.NameIndex
import com.henriquesebastiao.downtify.core.model.NameSection
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
        onPlaySong: (String) -> Unit = {},
        onZtoA: (LibraryTab, Boolean) -> Unit = { _, _ -> },
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) {
            TvLibraryScreen(
                state,
                navigation,
                onSelectTab = onSelectTab,
                onPlaySong = onPlaySong,
                onZtoA = onZtoA,
            )
        }
    }

    private fun artistsState(zToA: Boolean = false) = LibraryUiState(
        loading = false,
        tab = LibraryTab.Artists,
        artists = PreviewData.artists,
        artistSections = NameIndex.sections(PreviewData.artists, zToA) { it.name },
        zToATabs = if (zToA) setOf(LibraryTab.Artists) else emptySet(),
    )

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
            LibraryUiState(
                loading = false,
                tab = LibraryTab.Albums,
                albums = PreviewData.albums,
                albumSections = NameIndex.sections(PreviewData.albums) { it.title },
            ),
            navigation = LibraryNavigation(onAlbum = { opened += it }),
        )

        compose.onNodeWithText(album.title).performClick()

        assertEquals(listOf(album.id), opened)
    }

    @Test
    fun anArtistOpensItsPage() {
        val artist = PreviewData.artists.first()
        val opened = mutableListOf<String>()
        show(artistsState(), navigation = LibraryNavigation(onArtist = { opened += it }))

        compose.onNodeWithText(artist.name).performClick()

        assertEquals(listOf(artist.id), opened)
    }

    @Test
    fun anArtistSitsBesideItsInitial() {
        show(artistsState())

        // Dani Rivera, Kenji Aoki, Nora Vale, Orsa: D, first, beside Dani; not a key, a label.
        compose.onNodeWithText("D").assertIsDisplayed().assert(hasClickAction().not())
        compose.onNodeWithText("Dani Rivera").assertIsDisplayed()
    }

    @Test
    fun goToListsTheLettersAndJumpsToOne() {
        show(artistsState())

        compose.onNodeWithText("Go to").performClick()
        listOf("D", "K", "N", "O").forEach { compose.onNode(letterKey(it)).assertExists() }
        compose.onNode(letterKey("O")).performClick()

        // The letters close and O's first artist is there, focused.
        compose.onNode(letterKey("K")).assertDoesNotExist()
        compose.onNodeWithText("Orsa").assertIsDisplayed().assertIsFocused()
    }

    private fun letterKey(initial: String) = hasText(initial) and hasClickAction()

    @Test
    fun thePillFlipsThatSectionsOrder() {
        val flipped = mutableListOf<Pair<LibraryTab, Boolean>>()
        show(artistsState(), onZtoA = { tab, zToA -> flipped += tab to zToA })

        compose.onNodeWithText("A–Z").performClick()

        assertEquals(listOf(LibraryTab.Artists to true), flipped)
    }

    @Test
    fun zToAIsSaidOnThePill() {
        show(artistsState(zToA = true))

        compose.onNodeWithText("Z–A").assertExists()
    }

    @Test
    fun aSongPlaysFromItself() {
        val songs = PreviewData.tracks
        val played = mutableListOf<String>()
        show(
            LibraryUiState(
                loading = false,
                tab = LibraryTab.Songs,
                songs = songs,
                songSections = NameIndex.sections(songs) { it.displayTitle },
            ),
            onPlaySong = { played += it },
        )

        // Afterglow, Cassette Weather, Harbor Lights…: A heads the songs, by name.
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithText("Cassette Weather").performClick()

        assertEquals(listOf("t7"), played)
    }

    @Test
    fun likedSongsLeadsThePlaylists() {
        val liked = PlaylistItem(null, "", 3, null)
        val deepFocus = PreviewData.playlists.first().let { PlaylistItem(it, it.title, 3, null) }
        var likedOpened = 0
        show(
            LibraryUiState(
                loading = false,
                tab = LibraryTab.Playlists,
                playlists = listOf(liked, deepFocus),
                playlistSections = listOf(NameSection("♥", listOf(liked)), NameSection("D", listOf(deepFocus))),
            ),
            navigation = LibraryNavigation(onLiked = { likedOpened++ }),
        )

        compose.onNodeWithText("♥").assertIsDisplayed()
        compose.onNodeWithText("Liked songs").performClick()

        assertEquals(1, likedOpened)
    }

    @Test
    fun anEmptySectionSaysSo() {
        show(LibraryUiState(loading = false, tab = LibraryTab.Artists))

        compose.onNodeWithText("No artists yet").assertExists()
    }
}
