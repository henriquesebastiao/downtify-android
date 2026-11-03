package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Now Playing grows out of the mini player and shrinks back into it under a finger: a swipe up on
 * the mini player opens it, a swipe down on it closes it, and a short, slow drag springs back.
 */
@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class ExpandingPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var expansion: PlayerExpansion

    // The shell in miniature: the mini player over a navigation bar, Now Playing over everything.
    private fun show() {
        compose.setContent {
            DowntifyTheme {
                expansion = rememberPlayerExpansion()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth().height(72.dp).miniPlayerAnchor(expansion).testTag("mini")) {
                            Box(Modifier.size(52.dp).onGloballyPositioned { expansion.miniCover = it })
                        }
                        Spacer(Modifier.height(80.dp))
                    }
                    ExpandingPlayer(
                        expansion = expansion,
                        coverUrl = null,
                        largeCoverUrl = null,
                        attached = false,
                        dragEnabled = true,
                        mini = { cover -> Row { Box(Modifier.size(52.dp).then(cover)) } },
                        full = { cover ->
                            Column(Modifier.fillMaxSize().testTag("full")) {
                                Box(Modifier.size(200.dp).then(cover))
                                Text("Now Playing")
                            }
                        },
                    )
                }
            }
        }
    }

    private fun snap(fraction: Float) = compose.runOnIdle { runBlocking { expansion.snapTo(fraction) } }

    @Test
    fun aSwipeUpOnTheMiniPlayerOpensNowPlaying() {
        show()
        compose.onNodeWithText("Now Playing").assertDoesNotExist()
        compose.onNodeWithTag("mini").performTouchInput { swipeUp(startY = centerY, endY = centerY - 600f) }
        compose.waitForIdle()
        assertTrue(expansion.expanded)
        assertEquals(1f, expansion.fraction)
        compose.onNodeWithText("Now Playing").assertIsDisplayed()
    }

    @Test
    fun aSwipeDownOnNowPlayingClosesItIntoTheMiniPlayer() {
        show()
        snap(1f)
        compose.onNodeWithTag("full").performTouchInput { swipeDown(startY = centerY, endY = centerY + 600f) }
        compose.waitForIdle()
        assertFalse(expansion.expanded)
        assertEquals(0f, expansion.fraction)
        // Closed, Now Playing is gone and the mini player is back on show.
        compose.onNodeWithText("Now Playing").assertDoesNotExist()
        compose.onNodeWithTag("mini").assertIsDisplayed()
    }

    @Test
    fun aShortSlowDragSpringsBackOpen() {
        show()
        snap(1f)
        compose.onNodeWithTag("full").performTouchInput {
            swipe(start = center, end = center.copy(y = centerY + 60f), durationMillis = 1_500)
        }
        compose.waitForIdle()
        assertTrue(expansion.expanded)
        assertEquals(1f, expansion.fraction)
    }

    @Test
    fun halfwayItsTopIsHalfwayBetweenTheMiniPlayersAndTheWindows() {
        show()
        val miniTop = compose.onNodeWithTag("mini").getBoundsInRoot().top
        snap(0.5f)
        val fullTop = compose.onNodeWithTag("full").getBoundsInRoot().top
        assertEquals(miniTop.value / 2, fullTop.value, 1f)
    }
}
