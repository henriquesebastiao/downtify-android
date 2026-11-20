package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.feature.search.SearchFilter
import com.henriquesebastiao.downtify.feature.search.SearchNavigation
import com.henriquesebastiao.downtify.feature.search.SearchResults
import com.henriquesebastiao.downtify.feature.search.SearchUiState
import com.henriquesebastiao.downtify.feature.search.ServerResults
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvSearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val artist = PreviewData.artists.first { it.albums.isNotEmpty() }
    private val library = SearchResults(artists = listOf(artist), songs = artist.albums.flatMap { it.tracks })
    private val remote = RemoteSong(
        id = "yt-1", title = "Remote Song", artists = listOf("Someone"), album = "", coverUrl = "",
        durationSeconds = 200, url = "", source = CatalogSource.entries.first(), previewUrl = "", year = "", raw = "{}",
    )

    @Suppress("LongParameterList")
    private fun show(
        query: String,
        state: SearchUiState,
        navigation: SearchNavigation = SearchNavigation(),
        onFilterChange: (SearchFilter) -> Unit = {},
        onPlaySong: (Int) -> Unit = {},
        onPlayRemote: (RemoteSong, List<RemoteSong>) -> Unit = { _, _ -> },
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) {
            TvSearchScreen(
                query = query,
                state = state,
                navigation = navigation,
                onQueryChange = {},
                onFilterChange = onFilterChange,
                onPlaySong = onPlaySong,
                onPlayRemote = onPlayRemote,
            )
        }
    }

    @Test
    fun aBlankSearchAsksWhatToFind() {
        show("", SearchUiState())

        compose.onNodeWithText("Find music in your library", substring = true).assertExists()
    }

    @Test
    fun libraryResultsShowAsRowsAndOkPlaysASongOrOpensAnArtist() {
        val played = mutableListOf<Int>()
        val artists = mutableListOf<String>()
        show(
            "av",
            SearchUiState(query = "av", results = library),
            navigation = SearchNavigation(onArtist = { artists += it }),
            onPlaySong = { played += it },
        )

        // The filter pill and the row's title.
        compose.onAllNodesWithText("Artists").assertCountEquals(2)
        // The artist's card comes first; each song repeats the name under its title.
        compose.onAllNodesWithText(artist.name).onFirst().performClick()
        compose.onNodeWithText(library.songs.first().displayTitle).performClick()

        assertEquals(listOf(artist.id), artists)
        assertEquals(listOf(0), played)
    }

    @Test
    fun aFilterPillChangesTheFilter() {
        val filters = mutableListOf<SearchFilter>()
        show("av", SearchUiState(query = "av", results = library), onFilterChange = { filters += it })

        compose.onNodeWithText("Albums").performClick()

        assertEquals(listOf(SearchFilter.Albums), filters)
    }

    @Test
    fun theServersSongsSayTheyAreNotInTheLibraryAndPlayInFull() {
        val played = mutableListOf<RemoteSong>()
        show(
            "remote",
            SearchUiState(query = "remote", server = ServerResults.Found(listOf(remote), emptyList())),
            onPlayRemote = { song, _ -> played += song },
        )

        compose.onNodeWithText("Songs · Not in your library yet").assertExists()
        compose.onNodeWithText("Remote Song").performClick()

        assertEquals(listOf(remote), played)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theFieldTakesTextOnlyAfterOkAndDownLeavesIt() {
        show("av", SearchUiState(query = "av", results = library))
        val field = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))

        field.requestFocus()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
        field.performKeyInput { pressKey(Key.DirectionCenter) }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, true))

        // The remote never gets stuck in the field: Down leaves it (for the filters below).
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.assertIsNotFocused()
    }
}
