package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.LyricLine
import com.henriquesebastiao.downtify.core.model.Lyrics
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class LyricsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val lines = listOf(
        LyricLine(0, "The tide came in slowly"),
        LyricLine(4_000, "And took what I left on the sand"),
    )

    private val state = PlayerState(
        track = PreviewData.tracks[0],
        isPlaying = true,
        positionMs = 5_000,
        durationMs = 190_000,
    )

    @Test
    fun syncedLyricsSeekToATappedLineAndSwitchToStatic() {
        val seeks = mutableListOf<Long>()
        compose.setContent {
            DowntifyTheme {
                LyricsScreen(
                    lyrics = LyricsState.Loaded("t", Lyrics(lines, "")),
                    state = state,
                    controls = NowPlayingActions(onSeek = { seeks += it }),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Lyrics").assertExists()
        compose.onNodeWithText("Synced").assertIsSelected()
        compose.onNodeWithText("The tide came in slowly").performClick()
        assertEquals(listOf(0L), seeks)

        compose.onNodeWithText("Static").performClick()
        compose.onNodeWithText("Static").assertIsSelected()
        compose.onNodeWithText("The tide came in slowly").performClick()
        // Static lines have no times to jump to.
        assertEquals(listOf(0L), seeks)
    }

    @Test
    fun timingButtonsMoveTheLyricsAndTappedLinesFollow() {
        val shifts = mutableListOf<Long>()
        val seeks = mutableListOf<Long>()
        var resets = 0
        compose.setContent {
            DowntifyTheme {
                LyricsScreen(
                    lyrics = LyricsState.Loaded("t", Lyrics(lines, "")),
                    state = state,
                    controls = NowPlayingActions(
                        onSeek = { seeks += it },
                        onShiftLyrics = { shifts += it },
                        onResetLyrics = { resets++ },
                    ),
                    onBack = {},
                    offsetMs = 1_250,
                )
            }
        }
        compose.onNodeWithContentDescription("Lyrics later").assertDoesNotExist()
        compose.onNodeWithContentDescription("Adjust timing").performClick()
        compose.onNodeWithText("+1.25\u00A0s").assertExists()
        compose.onNodeWithContentDescription("Lyrics later").performClick()
        compose.onNodeWithContentDescription("Lyrics earlier").performClick()
        compose.onNodeWithContentDescription("Reset timing").performClick()
        assertEquals(listOf(250L, -250L), shifts)
        assertEquals(1, resets)

        // A line moved later is sung, and jumped to, that much later.
        compose.onNodeWithText("And took what I left on the sand").performClick()
        assertEquals(listOf(5_250L), seeks)

        // Static lines have no times to move.
        compose.onNodeWithText("Static").performClick()
        compose.onNodeWithContentDescription("Adjust timing").assertDoesNotExist()
        compose.onNodeWithContentDescription("Lyrics later").assertDoesNotExist()
    }

    @Test
    fun resetIsOffWhileTheLyricsKeepTheirTimes() {
        compose.setContent {
            DowntifyTheme {
                LyricsScreen(
                    lyrics = LyricsState.Loaded("t", Lyrics(lines, "")),
                    state = state,
                    controls = NowPlayingActions(),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Adjust timing").performClick()
        compose.onNodeWithText("0.00\u00A0s").assertExists()
        compose.onNodeWithContentDescription("Reset timing").assertIsNotEnabled()
    }

    @Test
    fun plainLyricsHaveNoTabs() {
        compose.setContent {
            DowntifyTheme {
                LyricsScreen(
                    lyrics = LyricsState.Loaded("t", Lyrics(emptyList(), "Only words\nNo times")),
                    state = state,
                    controls = NowPlayingActions(),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("No times").assertExists()
        compose.onNodeWithText("Synced").assertDoesNotExist()
        compose.onNodeWithText("Static").assertDoesNotExist()
    }

    @Test
    fun backAndPlayPauseReachTheirCallbacks() {
        var back = 0
        var toggles = 0
        compose.setContent {
            DowntifyTheme {
                LyricsScreen(
                    lyrics = LyricsState.Loading,
                    state = state,
                    controls = NowPlayingActions(onTogglePlay = { toggles++ }),
                    onBack = { back++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(1, back)
        assertEquals(1, toggles)
    }
}
