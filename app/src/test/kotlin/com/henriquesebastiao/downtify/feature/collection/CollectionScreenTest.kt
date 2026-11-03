package com.henriquesebastiao.downtify.feature.collection

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class CollectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val album = PreviewData.albums.first()
    private val state = CollectionUiState(
        loading = false,
        title = album.title,
        artist = album.artist,
        artistId = album.artistId,
        year = "2023",
        tracks = album.tracks,
    )

    private fun show(
        state: CollectionUiState,
        onPlay: (Int) -> Unit = {},
        onPlayOrPause: () -> Unit = {},
        onShuffle: () -> Unit = {},
        onToggleOffline: () -> Unit = {},
        onToggleFavorite: () -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme {
                CollectionScreen(
                    state = state,
                    onBack = {},
                    onArtist = {},
                    onPlay = onPlay,
                    onPlayOrPause = onPlayOrPause,
                    onShuffle = onShuffle,
                    onToggleOffline = onToggleOffline,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }

    @Test
    fun theHeaderPlaysShufflesAndKeepsTheAlbumOffline() {
        var played = 0
        var shuffled = 0
        var offline = 0
        show(state, onPlayOrPause = { played++ }, onShuffle = { shuffled++ }, onToggleOffline = { offline++ })
        compose.onNodeWithContentDescription("Play ${album.title}").performClick()
        compose.onNodeWithContentDescription("Shuffle").performClick()
        compose.onNodeWithText("Download").performClick()
        assertEquals(listOf(1, 1, 1), listOf(played, shuffled, offline))
    }

    @Test
    fun whileItPlaysThePlayButtonPauses() {
        show(state.copy(isCurrentContext = true, isPlaying = true, currentTrackId = album.tracks.first().id))
        compose.onNodeWithContentDescription("Pause ${album.title}").assertExists()
    }

    @Test
    fun theDetailsAreTagsAndASongTapPlaysIt() {
        var played = -1
        show(state, onPlay = { played = it })
        compose.onNodeWithText("2023").assertExists()
        compose.onNodeWithText("${album.tracks.size} songs").assertExists()
        compose.onNodeWithText(album.tracks[1].displayTitle).performClick()
        assertEquals(1, played)
    }

    @Test
    fun aPlaylistTheServerCanPinOffersTheFavorite() {
        var toggled = 0
        val playlist =
            Playlist("mix", false, "", album.tracks.map { it.id }, favorite = false, favoriteSupported = true)
        show(state.copy(kind = CollectionKind.Playlist, playlist = playlist), onToggleFavorite = { toggled++ })
        compose.onNodeWithContentDescription("Add playlist to favorites").performClick()
        assertEquals(1, toggled)
    }
}
