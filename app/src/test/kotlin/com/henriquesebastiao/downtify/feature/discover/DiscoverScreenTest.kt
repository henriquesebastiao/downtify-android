package com.henriquesebastiao.downtify.feature.discover

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.DiscoverAlbum
import com.henriquesebastiao.downtify.core.model.DiscoverArtist
import com.henriquesebastiao.downtify.core.model.DiscoverCollections
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val hoover = DiscoverArtist(
        "Hooverphonic",
        "",
        listOf("Portishead", "Massive Attack"),
        "https://open.spotify.com/artist/1",
    )
    private val archive = DiscoverArtist("Archive", "", listOf("Portishead"), "")

    private fun show(
        state: DiscoverUiState,
        onOpen: (String) -> Unit = {},
        onHide: (DiscoverArtist) -> Unit = {},
        onPreview: (DiscoverArtist) -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme {
                DiscoverScreen(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onShowAll = {},
                    onOpenInSearch = onOpen,
                    onHide = onHide,
                    onPreview = onPreview,
                )
            }
        }
    }

    @Test
    fun aSuggestionSaysWhyAndOpensItsSpotifyPage() {
        var opened: String? = null
        show(DiscoverUiState(loading = false, artists = listOf(hoover)), onOpen = { opened = it })
        compose.onNodeWithText("Because you like Portishead, Massive Attack").assertIsDisplayed()
        compose.onNodeWithText("Hooverphonic").performClick()
        assertEquals("https://open.spotify.com/artist/1", opened)
    }

    @Test
    fun aSuggestionSpotifyDidNotFindOpensASearchForItsName() {
        var opened: String? = null
        show(DiscoverUiState(loading = false, artists = listOf(archive)), onOpen = { opened = it })
        compose.onNodeWithText("Archive").performClick()
        assertEquals("Archive", opened)
    }

    @Test
    fun anArtistCanBePreviewed() {
        var previewed: DiscoverArtist? = null
        show(DiscoverUiState(loading = false, artists = listOf(archive)), onPreview = { previewed = it })
        compose.onNodeWithContentDescription("Preview Archive").performClick()
        assertEquals(archive, previewed)
    }

    @Test
    fun anArtistCanBeHidden() {
        var hidden: DiscoverArtist? = null
        show(DiscoverUiState(loading = false, artists = listOf(archive)), onHide = { hidden = it })
        compose.onNodeWithContentDescription("Options for Archive").performClick()
        compose.onNodeWithText("Not interested").performClick()
        assertEquals(archive, hidden)
    }

    @Test
    fun anAlbumOpensItsLink() {
        var opened: String? = null
        val album =
            DiscoverAlbum(
                "Grace",
                "Jeff Buckley",
                "1994",
                "",
                "https://open.spotify.com/album/7",
                "similar",
                emptyList(),
            )
        show(
            DiscoverUiState(
                loading = false,
                artists = listOf(archive),
                collections = DiscoverCollections(listOf(album), emptyList(), emptyList(), emptyMap(), false),
            ),
            onOpen = { opened = it },
        )
        compose.onNodeWithText("Albums for you").assertIsDisplayed()
        compose.onNodeWithText("Grace").performClick()
        assertEquals("https://open.spotify.com/album/7", opened)
    }

    @Test
    fun aPartialAnswerSaysTheListIsShorter() {
        show(DiscoverUiState(loading = false, artists = listOf(archive), partial = true))
        compose.onNodeWithText("shorter than usual", substring = true).assertIsDisplayed()
    }

    @Test
    fun withoutALibraryItSaysToAddMusicFirst() {
        show(DiscoverUiState(loading = false, libraryEmpty = true))
        compose.onNodeWithText("Nothing to go on yet").assertIsDisplayed()
    }
}
