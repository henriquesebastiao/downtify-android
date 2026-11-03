package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingEpisode
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
    fun aTrackHasPreviousAndNextAroundPlay() {
        var previous = 0
        var next = 0
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(track = PreviewData.tracks[1], isPlaying = false),
                    onOpen = {},
                    onTogglePlay = {},
                    onPrevious = { previous++ },
                    onNext = { next++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Play").assertExists()
        compose.onNodeWithContentDescription("Next").performClick()
        assertEquals(1, previous)
        assertEquals(1, next)
    }

    @Test
    fun aStreamKeepsOnlyTheTransport() {
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(
                        stream = PlayingStream("dQw4w9WgXcQ", "Believe", "Cher", "", "{}"),
                        isPlaying = true,
                    ),
                    onOpen = {},
                    onTogglePlay = {},
                )
            }
        }
        // Similar tracks and saving to the server are on Now Playing, not here.
        compose.onNodeWithContentDescription("Tracks like Believe").assertDoesNotExist()
        compose.onNodeWithContentDescription("Download Believe to the server").assertDoesNotExist()
        listOf("Previous", "Pause", "Next").forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun anEpisodeSkipsInsteadOfChangingTrack() {
        var back = 0
        var forward = 0
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(episode = PlayingEpisode(7, 1, "Zoozve", "Radiolab", "", "Podcasts/R/e.mp3")),
                    onOpen = {},
                    onTogglePlay = {},
                    onSkipBack = { back++ },
                    onSkipForward = { forward++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Back 10 seconds").performClick()
        compose.onNodeWithContentDescription("Forward 30 seconds").performClick()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        assertEquals(1, back)
        assertEquals(1, forward)
    }

    @Test
    fun aTitleTooLongToFitIsStillReadWhole() {
        val title = "January 4, 2024: Quadrantids, the meteor shower of the new year"
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(
                    state = PlayerState(
                        stream = PlayingStream("id", title, "Sleeping At Last", "", "{}"),
                        isPlaying = true,
                    ),
                    onOpen = {},
                    onTogglePlay = {},
                )
            }
        }
        // It slides instead of ending in an ellipsis: the whole title is there to read.
        compose.onNodeWithText(title).assertExists()
    }
}
