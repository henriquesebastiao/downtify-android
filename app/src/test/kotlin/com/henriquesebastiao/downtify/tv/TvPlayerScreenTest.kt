package com.henriquesebastiao.downtify.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.LyricLine
import com.henriquesebastiao.downtify.core.model.Lyrics
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingEpisode
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.feature.player.LyricsState
import com.henriquesebastiao.downtify.feature.player.NowPlayingActions
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvPlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val track = PreviewData.tracks[1]

    /** As with a remote: buttons can take focus. */
    @Composable
    private fun RemoteMode(content: @Composable () -> Unit) {
        val inputMode = LocalInputModeManager.current
        remember { inputMode.requestInputMode(InputMode.Keyboard) }
        content()
    }

    @Test
    fun aSongHasItsControlsLyricsAndQueue() {
        val calls = mutableListOf<String>()
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(
                    state = PlayerState(track = track, positionMs = 30_000, durationMs = 200_000),
                    isLiked = false,
                    actions = NowPlayingActions(
                        onTogglePlay = { calls += "play" },
                        onNext = { calls += "next" },
                        onToggleShuffle = { calls += "shuffle" },
                        onToggleLike = { calls += "like" },
                        onOpenLyrics = { calls += "lyrics" },
                        onOpenQueue = { calls += "queue" },
                    ),
                )
            }
        }

        compose.onNodeWithText(track.displayTitle).assertExists()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Shuffle, off").performClick()
        compose.onNodeWithContentDescription("Add to Liked songs").performClick()
        compose.onNodeWithText("Lyrics").performClick()
        compose.onNodeWithText("Queue").performClick()

        assertEquals(listOf("play", "next", "shuffle", "like", "lyrics", "queue"), calls)
    }

    @Test
    fun anEpisodeSkipsAndHasNoLyricsQueueOrShuffle() {
        val episode = PlayingEpisode(7, 3, "Episode one", "A show", "", "Podcasts/A/e.mp3")
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(state = PlayerState(episode = episode), isLiked = false, actions = NowPlayingActions())
            }
        }

        compose.onNodeWithContentDescription("Back 10 seconds").assertExists()
        compose.onNodeWithContentDescription("Forward 30 seconds").assertExists()
        compose.onNodeWithText("Lyrics").assertDoesNotExist()
        compose.onNodeWithText("Queue").assertDoesNotExist()
        compose.onNodeWithContentDescription("Shuffle, off").assertDoesNotExist()
    }

    @Test
    fun leftAndRightOnTheSeekBarSeekTenSeconds() {
        val seeks = mutableListOf<Long>()
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                RemoteMode {
                    TvPlayerScreen(
                        state = PlayerState(track = track, positionMs = 60_000, durationMs = 200_000),
                        isLiked = false,
                        actions = NowPlayingActions(onSeek = { seeks += it }),
                    )
                }
            }
        }

        val bar = compose.onNodeWithContentDescription("Seek")
        bar.requestFocus()
        bar.performKeyInput { pressKey(Key.DirectionLeft) }
        bar.performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(listOf(50_000L, 70_000L), seeks)
    }

    @Test
    fun onceTheControlsHaveFadedTheFirstKeyOnlyBringsThemBack() {
        var toggles = 0
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                RemoteMode {
                    TvPlayerScreen(
                        state = PlayerState(track = track, isPlaying = true, durationMs = 200_000),
                        isLiked = false,
                        actions = NowPlayingActions(onTogglePlay = { toggles++ }),
                        autoHideMs = 1_000,
                    )
                }
            }
        }
        val pause = compose.onNodeWithContentDescription("Pause")
        pause.requestFocus()

        compose.mainClock.advanceTimeBy(3_000)
        pause.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, toggles)

        pause.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, toggles)
    }

    @Test
    fun theLyricsShowInThePlayerAndItsButtonClosesThem() {
        var closed = 0
        val lyrics = LyricsState.Loaded(
            track.id,
            Lyrics(listOf(LyricLine(0, "A first sample line"), LyricLine(5_000, "A second sample line")), ""),
        )
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(
                    state = PlayerState(track = track, positionMs = 6_000, durationMs = 200_000),
                    isLiked = false,
                    actions = NowPlayingActions(),
                    lyrics = lyrics,
                    onCloseLyrics = { closed++ },
                )
            }
        }

        compose.onNodeWithText("A first sample line").assertExists()
        compose.onNodeWithText("A second sample line").assertExists()
        compose.onNodeWithText("Lyrics").assertIsSelected().performClick()

        assertEquals(1, closed)
    }

    @Test
    fun whatPlaysNextShowsInTheLastSecondsOnly() {
        val next = QueueEntry(index = 2, trackId = "t-next", title = "The next sample", artist = "Someone")
        var position by mutableLongStateOf(100_000L)
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(
                    state = PlayerState(
                        track = track,
                        positionMs = position,
                        durationMs = 200_000,
                        upNext = listOf(next),
                    ),
                    isLiked = false,
                    actions = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("The next sample").assertDoesNotExist()

        position = 190_000L
        compose.waitForIdle()

        compose.onNodeWithText("Next up").assertExists()
        compose.onNodeWithText("The next sample").assertExists()
    }

    @Test
    fun onceTheControlsFadeTheTitleGoesDownBelowTheLyrics() {
        val lyrics = LyricsState.Loaded(track.id, Lyrics(listOf(LyricLine(0, "A sample line")), ""))
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(
                    state = PlayerState(track = track, isPlaying = true, positionMs = 1_000, durationMs = 200_000),
                    isLiked = false,
                    actions = NowPlayingActions(),
                    lyrics = lyrics,
                    autoHideMs = 1_000,
                )
            }
        }
        val title = compose.onNodeWithText(track.displayTitle)
        val before = title.getUnclippedBoundsInRoot().top

        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()

        // The controls fold away: nothing stays between the title and the bottom.
        val after = title.getUnclippedBoundsInRoot().top
        assertTrue("title went from $before to $after", after > before + 80.dp)
    }

    @Test
    fun turningDarkModeOnSaysSoAndItsButtonShowsItIsOn() {
        var toggles = 0
        var dark by mutableStateOf(false)
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(
                    state = PlayerState(track = track, durationMs = 200_000),
                    isLiked = false,
                    actions = NowPlayingActions(),
                    darkMode = dark,
                    onToggleDarkMode = {
                        toggles++
                        dark = !dark
                    },
                )
            }
        }

        compose.onNodeWithText("Dark mode").assertIsNotSelected().performClick()

        assertEquals(1, toggles)
        compose.onNodeWithText("Dark mode").assertIsSelected()
        compose.onNodeWithText("Dark mode is on").assertExists()
    }

    @Test
    fun aboutTheArtistOpensTheirPage() {
        val artists = mutableListOf<String>()
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvPlayerScreen(
                    state = PlayerState(track = track, durationMs = 200_000),
                    isLiked = false,
                    actions = NowPlayingActions(onGoToArtist = { artists += it }),
                )
            }
        }

        compose.onNodeWithText("About the artist").assertExists()
        compose.onNodeWithText("Go to artist").performClick()

        assertEquals(listOf(track.artistId), artists)
    }
}
