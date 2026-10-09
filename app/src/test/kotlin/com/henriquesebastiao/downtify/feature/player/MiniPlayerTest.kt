package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTheTrackAndTogglesPlayback() {
        val track = PreviewData.tracks[1]
        var toggles = 0
        var opened = 0
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(track = track, isPlaying = true, positionMs = 1_000, durationMs = 200_000),
                    onOpen = { opened++ },
                    onTogglePlay = { toggles++ },
                )
            }
        }
        compose.onNodeWithText(track.displayTitle).assertExists().performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(1, opened)
        assertEquals(1, toggles)
    }

    @Test
    fun rendersNothingWithoutATrack() {
        compose.setContent {
            DowntifyTheme { MiniPlayer(state = PlayerState(), onOpen = {}, onTogglePlay = {}) }
        }
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun aStreamShowsSimilarAndDownload() {
        var similar: Pair<String, String>? = null
        var downloaded = 0
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(
                        stream = PlayingStream("dQw4w9WgXcQ", "Believe", "Cher", "", "{}"),
                        isPlaying = true,
                    ),
                    onOpen = {},
                    onTogglePlay = {},
                    onSimilar = { artist, title -> similar = artist to title },
                    onDownloadStream = { downloaded++ },
                )
            }
        }
        compose.onNodeWithText("Believe").assertExists()
        compose.onNodeWithContentDescription("Tracks like Believe").performClick()
        compose.onNodeWithContentDescription("Download Believe to the server").performClick()
        assertEquals("Cher" to "Believe", similar)
        assertEquals(1, downloaded)
    }

    @Test
    fun aDownloadedStreamShowsACheck() {
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(
                        stream = PlayingStream("dQw4w9WgXcQ", "Believe", "Cher", "", "{}"),
                        isPlaying = false,
                    ),
                    onOpen = {},
                    onTogglePlay = {},
                    downloadJob = ServerJob("dQw4w9WgXcQ", "Believe", "Cher", "", "", ServerJobStatus.Done, 100f, ""),
                    onDownloadStream = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Downloaded to the server").assertExists()
        compose.onNodeWithContentDescription("Download Believe to the server").assertDoesNotExist()
    }

    @Test
    fun aDownloadedStreamCheckCannotBeTapped() {
        var downloaded = 0
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(
                        stream = PlayingStream("dQw4w9WgXcQ", "Believe", "Cher", "", "{}"),
                        isPlaying = false,
                    ),
                    onOpen = {},
                    onTogglePlay = {},
                    downloadJob = ServerJob("dQw4w9WgXcQ", "Believe", "Cher", "", "", ServerJobStatus.Done, 100f, ""),
                    onDownloadStream = { downloaded++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Downloaded to the server").assertHasNoClickAction()
        assertEquals(0, downloaded)
    }
}
