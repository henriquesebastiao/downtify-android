package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.feature.collection.CollectionKind
import com.henriquesebastiao.downtify.feature.collection.CollectionUiState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [34], qualifiers = "w360dp-h1000dp")
@RunWith(AndroidJUnit4::class)
class TemporaryMusicPlaylistTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `temporary selection uses native playback controls and exposes saving`() {
        val model = mockk<MusicAiViewModel>(relaxed = true)
        val tracks = PreviewData.albums.first().tracks
        val title = mixTitle("daily", "detente")
        every { model.collectionState } returns MutableStateFlow(
            CollectionUiState(
                loading = false,
                kind = CollectionKind.Playlist,
                title = title,
                tracks = tracks,
            ),
        )
        val state = MusicAiUiState(
            source = "daily",
            selectedMood = "detente",
            selectionId = "fixture",
            tracks = tracks.map { MusicAiTrackDto(id = it.id) },
        )
        compose.setContent { DowntifyTheme { TemporaryMusicPlaylist(model, state, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Play $title").performClick()
        verify(exactly = 1) { model.playOrPause() }
        compose.onNodeWithContentDescription("Shuffle").performClick()
        verify(exactly = 1) { model.playSelection(0, true) }
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer en playlist").assertHasClickAction()
        verify(exactly = 0) { model.save(any()) }
    }

    @Ignore(
        "Robolectric dialog focus does not become idle; opening/name editing checked on physical Android 17; " +
            "confirmation still needs instrumentation",
    )
    @Test
    fun `temporary selection uses native playlist controls and saves its named copy`() {
        val model = mockk<MusicAiViewModel>(relaxed = true)
        val tracks = PreviewData.albums.first().tracks
        val title = mixTitle("daily", "detente")
        every { model.collectionState } returns MutableStateFlow(
            CollectionUiState(
                loading = false,
                kind = CollectionKind.Playlist,
                title = title,
                tracks = tracks,
            ),
        )
        val state = MusicAiUiState(
            source = "daily",
            selectedMood = "detente",
            selectionId = "fixture",
            tracks = tracks.map { MusicAiTrackDto(id = it.id) },
        )
        compose.setContent { DowntifyTheme { TemporaryMusicPlaylist(model, state, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Play $title").assertExists()
        compose.onNodeWithContentDescription("Shuffle").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
        // Bound dialog/focus animations explicitly under Robolectric.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Enregistrer en playlist").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Enregistrer", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(500)
        verify(exactly = 1) { model.save(title) }
        verify(exactly = 0) { model.playSelection(any(), any()) }
    }
}
