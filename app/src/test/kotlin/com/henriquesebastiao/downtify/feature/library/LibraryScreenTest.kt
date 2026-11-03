package com.henriquesebastiao.downtify.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.data.settings.LibrarySort
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A phone-sized screen: the lazy list only composes what fits.
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun songsState() = LibraryUiState(
        loading = false,
        tab = LibraryTab.Songs,
        songs = PreviewData.tracks,
    )

    @Test
    fun songsTabOffersShuffle() {
        var shuffled = false
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                    onShuffle = { shuffled = true },
                )
            }
        }
        compose.onNodeWithText("Songs").assertIsSelected()
        compose.onNodeWithText("Shuffle").assertIsEnabled().performClick()
        assertEquals(true, shuffled)
    }

    @Test
    fun everyTabOffersShuffle() {
        var shuffled = 0
        var state by mutableStateOf(
            LibraryUiState(loading = false, tab = LibraryTab.Albums, albums = PreviewData.albums),
        )
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = state,
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                    onShuffle = { shuffled++ },
                )
            }
        }
        compose.onNodeWithText("Shuffle").assertIsEnabled().performClick()
        assertEquals(1, shuffled)
        // Nothing to pick from: no shuffle.
        state = LibraryUiState(loading = false, tab = LibraryTab.Artists)
        compose.onNodeWithText("Shuffle").assertIsNotEnabled()
    }

    @Test
    fun theTabsSwitchTheLibrarysSection() {
        var picked: LibraryTab? = null
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = { picked = it },
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                )
            }
        }
        compose.onNodeWithText("Albums").assertIsNotSelected().performClick()
        assertEquals(LibraryTab.Albums, picked)
    }

    @Test
    fun theSongsListIsCompactWithoutADurationLine() {
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                )
            }
        }
        val track = PreviewData.tracks.first()
        compose.onNodeWithText(track.displayTitle).assertExists()
        compose.onNodeWithText(formatDuration(track.duration), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSortSheetPicksASortAndFlipsTheOrder() {
        var sort: LibrarySort? = null
        var descending: Boolean? = null
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = { sort = it },
                    onSortDescending = { descending = it },
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Sort by: Recently added").performClick()
        compose.onNodeWithText("Sort by").assertExists()
        // Recently added starts newest first: the card says so, and a tap flips it.
        compose.onNodeWithText("Descending").performClick()
        assertEquals(false, descending)
        compose.onNodeWithText("Artist").performClick()
        assertEquals(LibrarySort.Artist, sort)
    }

    @Test
    fun coversSwitchBetweenGridAndListFromAnIcon() {
        var toggled = false
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = LibraryUiState(loading = false, tab = LibraryTab.Albums, albums = PreviewData.albums),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = { toggled = true },
                    onRefresh = {},
                    onPlaySong = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Show as list").performClick()
        assertEquals(true, toggled)
        // Icons only: the sort's name is the button's description, not a label.
        compose.onNodeWithText("Recently added").assertDoesNotExist()
    }

    @Test
    fun trackMenuFindsSimilarTracks() {
        var similar: Pair<String, String>? = null
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                    trackActions = TrackActions(
                        onFindSimilar = { artist, title -> similar = artist to title },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("More options for Low Tide").performClick()
        compose.onNodeWithText("Find similar").performClick()
        assertEquals("Kenji Aoki" to "Low Tide", similar)
    }
}
