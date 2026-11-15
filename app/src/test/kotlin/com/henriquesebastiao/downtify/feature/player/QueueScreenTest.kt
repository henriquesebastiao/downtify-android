package com.henriquesebastiao.downtify.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class QueueScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tracks = PreviewData.tracks

    private fun state(queuedCount: Int) = PlayerState(
        track = tracks[0],
        isPlaying = true,
        context = PlaybackContext(PlaybackContextType.Album, "alb", "Glass Harbor"),
        upNext = tracks.drop(1).mapIndexed { i, t ->
            QueueEntry(
                index = i + 1,
                trackId = t.id,
                title = t.displayTitle,
                artist = t.displayArtist,
                queued =
                    i < queuedCount,
            )
        },
    )

    @Test
    fun queuedSongsComeFirstUnderTheirOwnHeading() {
        var cleared = 0
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 1),
                    actions = QueueActions(onClearQueued = { cleared++ }),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Now playing").assertExists()
        compose.onNodeWithText("Next in queue").assertExists()
        compose.onNodeWithText("Next from: Glass Harbor").assertExists()
        compose.onNodeWithText("Clear queue").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun aStreamingSongShowsAsNowPlaying() {
        val stream =
            PlayingStream(
                videoId = "dQw4w9WgXcQ",
                title = "Night Drive",
                artist = "Neon Coast",
                coverUrl = "",
                raw = "{}",
            )
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 1).copy(track = null, stream = stream),
                    actions = QueueActions(),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Now playing").assertExists()
        compose.onNodeWithText("Night Drive").assertExists()
        compose.onNodeWithText("Next in queue").assertExists()
    }

    @Test
    fun withNothingQueuedThereIsNoQueueHeading() {
        compose.setContent {
            DowntifyTheme {
                QueueScreen(state = state(queuedCount = 0), actions = QueueActions(), controls = NowPlayingActions())
            }
        }
        compose.onNodeWithText("Next in queue").assertDoesNotExist()
        compose.onNodeWithText("Clear queue").assertDoesNotExist()
    }

    @Test
    fun tappingASongPlaysItAndTheButtonCloses() {
        var skippedTo = -1
        var closed = 0
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 0),
                    actions = QueueActions(onSkipTo = { skippedTo = it }, onClose = { closed++ }),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText(tracks[2].displayTitle).performClick()
        compose.onNodeWithContentDescription("Close queue").performClick()
        assertEquals(2, skippedTo)
        assertEquals(1, closed)
    }

    @Test
    fun saysWhenNothingComesNext() {
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = PlayerState(track = tracks[0]),
                    actions = QueueActions(),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Nothing plays after this song.").assertExists()
    }

    /** A row's drag handle, which a TV remote focuses to move the row. */
    private fun handle(row: Int): SemanticsNodeInteraction =
        compose.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true)[row].onParent()

    /** Whatever has focus: the carried row's handle, wherever the row now is. */
    private fun focused(): SemanticsNodeInteraction = compose.onNode(isFocused(), useUnmergedTree = true)

    private fun showForRemote(onMove: (Int, Int, Boolean) -> Unit) {
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 0),
                    actions = QueueActions(onMove = onMove),
                    controls = NowPlayingActions(),
                )
            }
        }
    }

    @Test
    fun aRemoteCarriesARowDownWithOkAndTheArrows() {
        val moves = mutableListOf<Triple<Int, Int, Boolean>>()
        showForRemote { from, after, queued -> moves += Triple(from, after, queued) }
        val top = handle(0).requestFocus().assertIsFocused().getUnclippedBoundsInRoot().top
        handle(0).performKeyInput { pressKey(Key.DirectionCenter) }
        handle(0).performKeyInput { pressKey(Key.DirectionDown) }
        // Carried one row down, focus with it, but not moved yet: the player hears of it once it's put down.
        assertTrue(focused().getUnclippedBoundsInRoot().top > top)
        assertEquals(emptyList<Triple<Int, Int, Boolean>>(), moves)
        focused().performKeyInput { pressKey(Key.DirectionCenter) }
        // The first song (player index 1) now plays after the second (index 2).
        assertEquals(listOf(Triple(1, 2, false)), moves)
    }

    @Test
    fun untilPickedUpTheArrowsOnlyMoveFocus() {
        val moves = mutableListOf<Triple<Int, Int, Boolean>>()
        showForRemote { from, after, queued -> moves += Triple(from, after, queued) }
        handle(0).requestFocus()
        handle(0).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(emptyList<Triple<Int, Int, Boolean>>(), moves)
    }

    @Test
    fun backPutsACarriedRowWhereItWas() {
        val moves = mutableListOf<Triple<Int, Int, Boolean>>()
        showForRemote { from, after, queued -> moves += Triple(from, after, queued) }
        val top = handle(0).requestFocus().getUnclippedBoundsInRoot().top
        handle(0).performKeyInput { pressKey(Key.DirectionCenter) }
        handle(0).performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        // The row went back to the top, still focused, and the player heard of nothing.
        assertEquals(emptyList<Triple<Int, Int, Boolean>>(), moves)
        assertEquals(top, focused().getUnclippedBoundsInRoot().top)
    }
}
