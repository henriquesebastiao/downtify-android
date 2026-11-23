package com.henriquesebastiao.downtify.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Selection
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The library's selection: a press-and-hold picks, a tap picks or unpicks, the bar sends them. */
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class LibrarySelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: LibraryUiState,
        editActions: LibraryEditActions = LibraryEditActions(),
        onPlaySong: (Int) -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = state,
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = onPlaySong,
                    editActions = editActions,
                )
            }
        }
    }

    private fun songsState(selection: Selection = Selection()) = LibraryUiState(
        loading = false,
        tab = LibraryTab.Songs,
        songs = PreviewData.tracks,
        selection = selection,
        canEditPlaylists = true,
        editablePlaylists = PreviewData.playlists,
    )

    @Test
    fun aPressAndHoldPicksTheSong() {
        var started: String? = null
        show(songsState(), LibraryEditActions(onStartSelection = { started = it.id }))

        compose.onNodeWithText("Low Tide").performTouchInput { longClick() }

        assertEquals("t1", started)
    }

    @Test
    fun withAPickTheBarSaysHowManyAndAddingOpensTheSheet() {
        var opened = 0
        show(
            songsState(Selection(listOf("t1", "t2"))),
            LibraryEditActions(onOpenAddSheet = { opened++ }),
        )
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Add 2 songs to playlist").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun pickingAnotherSongWhileSelectingAsksTheViewModel() {
        var picked: String? = null
        var played = 0
        show(
            songsState(Selection(listOf("t1"))),
            LibraryEditActions(onToggleSelection = { picked = it.id }),
            onPlaySong = { played++ },
        )
        compose.onNodeWithText("Undertow").performClick()

        // A tap picks now; it must not start playing.
        assertEquals("t2", picked)
        assertEquals(0, played)
    }

    @Test
    fun withoutAPickASongStillPlays() {
        var played = -1
        show(songsState(), onPlaySong = { played = it })

        compose.onNodeWithText("Low Tide").performClick()

        assertEquals(0, played)
    }

    @Test
    fun newPlaylistIsOfferedOnlyOnThePlaylistsTab() {
        var asked = 0
        show(
            LibraryUiState(loading = false, tab = LibraryTab.Playlists, canEditPlaylists = true),
            LibraryEditActions(onNewPlaylist = { asked++ }),
        )
        compose.onNodeWithContentDescription("New playlist").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun newPlaylistIsHiddenWhenTheServerCannotEdit() {
        show(LibraryUiState(loading = false, tab = LibraryTab.Playlists, canEditPlaylists = false))
        compose.onNodeWithContentDescription("New playlist").assertDoesNotExist()
    }
}
