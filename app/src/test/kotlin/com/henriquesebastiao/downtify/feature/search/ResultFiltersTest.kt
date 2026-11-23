package com.henriquesebastiao.downtify.feature.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ResultFiltering
import com.henriquesebastiao.downtify.core.model.ResultFilters
import com.henriquesebastiao.downtify.core.model.ResultSort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The chips under the search field: they narrow and order the page the server returned, and they
 * say so while something is set.
 */
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class ResultFiltersTest {
    @get:Rule
    val compose = createComposeRule()

    private fun song(id: String, title: String, artists: List<String>, album: String, year: String, duration: Int) =
        RemoteSong(
            id = id,
            title = title,
            artists = artists,
            album = album,
            coverUrl = "",
            durationSeconds = duration,
            url = "https://music.youtube.com/watch?v=$id",
            source = CatalogSource.YouTube,
            previewUrl = "",
            year = year,
            raw = "{}",
        )

    // Every song has two artists, so a chip's word never repeats in a row's subtitle.
    private val page = listOf(
        song("a", "Harbor Song", listOf("Nora Vale", "Ivo Marsh"), "Harbor Nights", "2021", 242),
        song("b", "Lights Out", listOf("Kai Sato", "Rae Lin"), "Static Bloom", "2019", 190),
    )

    /** The screen with the chips wired back into its own state, as the ViewModel does it. */
    private fun show(initial: ResultFilters, changes: MutableList<ResultFilters>) {
        val state = mutableStateOf(initial)
        compose.setContent {
            DowntifyTheme {
                val filters = state.value
                SearchScreen(
                    query = QUERY,
                    state = SearchUiState(
                        query = QUERY,
                        server = ServerResults.Found(
                            songs = ResultFiltering.apply(page, filters),
                            albums = emptyList(),
                            page = page,
                        ),
                        filters = filters,
                        filterOptions = ResultFiltering.options(page),
                    ),
                    navigation = SearchNavigation(),
                    onQueryChange = {},
                    onFilterChange = {},
                    onPlaySong = {},
                    onFiltersChange = {
                        changes += it
                        state.value = it
                    },
                )
            }
        }
    }

    @Test
    fun anArtistChipNarrowsThePageAndClearPutsItBack() {
        val changes = mutableListOf<ResultFilters>()
        show(ResultFilters(), changes)

        compose.onNodeWithText("Harbor Song").assertExists()
        compose.onNodeWithText("Lights Out").assertExists()
        compose.onNodeWithText("Clear filters").assertDoesNotExist()

        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Ivo Marsh").performClick()
        assertEquals(listOf(ResultFilters(artist = "Ivo Marsh")), changes)
        // The other artist's row is gone: it is the page itself that was narrowed.
        compose.onNodeWithText("Lights Out").assertDoesNotExist()
        compose.onNodeWithText("Harbor Song").assertExists()

        compose.onNodeWithText("Clear filters").performClick()
        assertEquals(ResultFilters(), changes.last())
        compose.onNodeWithText("Lights Out").assertExists()
    }

    @Test
    fun anAlbumAndAYearNarrowTogether() {
        val changes = mutableListOf<ResultFilters>()
        show(ResultFilters(), changes)

        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Static Bloom").performClick()
        assertEquals(ResultFilters(album = "Static Bloom"), changes.last())

        compose.onNodeWithText("2019").performClick()
        assertEquals(ResultFilters(album = "Static Bloom", year = "2019"), changes.last())
        compose.onNodeWithText("Harbor Song").assertDoesNotExist()
        compose.onNodeWithText("Lights Out").assertExists()

        compose.onNodeWithText("Clear filters").performClick()
        assertEquals(ResultFilters(), changes.last())
        compose.onNodeWithText("Harbor Song").assertExists()
    }

    @Test
    fun theOrderIsItsOwnChipWithAMenu() {
        val changes = mutableListOf<ResultFilters>()
        show(ResultFilters(), changes)

        compose.onNodeWithText("Sort: Relevance").performClick()
        compose.onNodeWithText("Length").performClick()
        assertEquals(listOf(ResultFilters(sort = ResultSort.Duration)), changes)
        // The chip now says which order the rows are in; the shortest song comes first.
        compose.onNodeWithText("Sort: Length").assertExists()
    }

    @Test
    fun oneRowIsNothingToNarrowOrOrder() {
        compose.setContent {
            DowntifyTheme {
                SearchScreen(
                    query = QUERY,
                    state = SearchUiState(
                        query = QUERY,
                        server = ServerResults.Found(listOf(page[0]), emptyList()),
                        filterOptions = ResultFiltering.options(listOf(page[0])),
                    ),
                    navigation = SearchNavigation(),
                    onQueryChange = {},
                    onFilterChange = {},
                    onPlaySong = {},
                )
            }
        }
        compose.onNodeWithText("Harbor Song").assertExists()
        compose.onNodeWithText("Filters").assertDoesNotExist()
        compose.onNodeWithText("Sort: Relevance").assertDoesNotExist()
    }

    private companion object {
        const val QUERY = "harbor"
    }
}
