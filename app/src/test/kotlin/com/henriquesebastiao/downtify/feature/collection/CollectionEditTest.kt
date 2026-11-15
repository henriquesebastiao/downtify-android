package com.henriquesebastiao.downtify.feature.collection

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditError
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A playlist page's own editing: the menu, edit mode and the songs a write refused. */
@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class CollectionEditTest {
    @get:Rule
    val compose = createComposeRule()

    private val playlist = Playlist(
        name = "Mix",
        liked = false,
        cover = "",
        trackIds = listOf("t1", "t2", "t1"),
        manual = true,
        canEditTracks = true,
        mine = true,
    )

    private fun state(
        editing: Boolean = false,
        canRename: Boolean = true,
        canEditTracks: Boolean = true,
        error: PlaylistEditError? = null,
    ) = CollectionUiState(
        loading = false,
        kind = CollectionKind.Playlist,
        title = playlist.name,
        tracks = PreviewData.tracks.take(3),
        playlist = playlist,
        canEditTracks = canEditTracks,
        canRename = canRename,
        editing = editing,
        editError = error,
    )

    private fun show(state: CollectionUiState, editActions: CollectionEditActions = CollectionEditActions()) {
        compose.setContent {
            DowntifyTheme {
                CollectionScreen(
                    state = state,
                    onBack = {},
                    onArtist = {},
                    onPlay = {},
                    onPlayOrPause = {},
                    onShuffle = {},
                    editActions = editActions,
                )
            }
        }
    }

    @Test
    fun theMenuRenamesAndAddsSongs() {
        var renamed = 0
        var adding = 0
        show(state(), CollectionEditActions(onOpenRename = { renamed++ }, onOpenAddSongs = { adding++ }))
        compose.onNodeWithContentDescription("Playlist options").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("Playlist options").performClick()
        compose.onNodeWithText("Add songs").performClick()

        assertEquals(listOf(1, 1), listOf(renamed, adding))
    }

    @Test
    fun anEditorGetsSongsButNoRename() {
        show(state(canRename = false))
        compose.onNodeWithContentDescription("Playlist options").performClick()

        compose.onNodeWithText("Add songs").assertExists()
        compose.onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun aReadOnlyPlaylistHasNoMenu() {
        show(state(canRename = false, canEditTracks = false))

        compose.onNodeWithContentDescription("Playlist options").assertDoesNotExist()
    }

    @Test
    fun editModeShowsAHandlePerRowAndOneDone() {
        var toggled = 0
        show(state(editing = true), CollectionEditActions(onToggleEditing = { toggled++ }))
        // Three songs, three handles: one per row, repeated song or not.
        assertEquals(3, compose.onAllNodesWithContentDescription("Move song").fetchSemanticsNodes().size)
        compose.onNodeWithText("Done").performClick()

        assertEquals(1, toggled)
    }

    @Test
    fun aRefusedWriteSaysWhy() {
        var dismissed = 0
        show(
            state(error = PlaylistEditError.Forbidden),
            CollectionEditActions(onDismissError = { dismissed++ }),
        )
        compose.onNodeWithText("You can change this playlist's songs, but not its name.").performClick()

        assertEquals(1, dismissed)
    }
}
