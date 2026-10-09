package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class NowPlayingStreamTest {
    @get:Rule
    val compose = createComposeRule()

    private val stream = PlayingStream(
        videoId = "dQw4w9WgXcQ",
        title = "Believe",
        artist = "Cher",
        coverUrl = "",
        raw = "{}",
    )
    private val state = PlayerState(
        stream = stream,
        isPlaying = true,
        positionMs = 83_000,
        durationMs = 224_000,
    )

    @Test
    fun aStreamShowsItselfWithADownloadButton() {
        var downloaded = 0
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(
                    state = state,
                    isLiked = false,
                    serverName = "nas",
                    actions = NowPlayingActions(onDownloadStream = { downloaded++ }),
                )
            }
        }
        compose.onNodeWithText("Believe").assertIsDisplayed()
        compose.onNodeWithText("Cher").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download Believe to the server").performClick()
        assertEquals(1, downloaded)

        // Nothing to like and no lyrics on a stream.
        compose.onNodeWithContentDescription("Add to Liked songs").assertDoesNotExist()
        compose.onNodeWithContentDescription("Lyrics").assertDoesNotExist()
    }

    @Test
    fun aDownloadedStreamShowsLibraryActionsInsteadOfDownload() {
        var liked = 0
        var lyrics = 0
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(
                    state = state,
                    isLiked = false,
                    serverName = "nas",
                    actions = NowPlayingActions(
                        onToggleLike = { liked++ },
                        onOpenLyrics = { lyrics++ },
                    ),
                    hasLibraryCopy = true,
                )
            }
        }
        compose.onNodeWithContentDescription("Download Believe to the server").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add to Liked songs").performClick()
        compose.onNodeWithContentDescription("Lyrics").performClick()
        assertEquals(1, liked)
        assertEquals(1, lyrics)
    }

    @Test
    fun similarOpensTracksLikeThePlayingOne() {
        var similar: Pair<String, String>? = null
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(
                    state = state,
                    isLiked = false,
                    serverName = "nas",
                    actions = NowPlayingActions(
                        onSimilar = { artist, title -> similar = artist to title },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Tracks like Believe").performClick()
        assertEquals("Cher" to "Believe", similar)
    }

    @Test
    fun transportEndsWithSimilarOnTheRight() {
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(
                    state = state,
                    isLiked = false,
                    serverName = "nas",
                    actions = NowPlayingActions(),
                )
            }
        }
        fun x(description: String): Float =
            compose.onNodeWithContentDescription(description).fetchSemanticsNode().positionInRoot.x
        val order = listOf(
            x("Shuffle, off"),
            x("Previous"),
            x("Pause"),
            x("Next"),
            x("Tracks like Believe"),
        )
        assertEquals(order.sorted(), order)
    }
}
