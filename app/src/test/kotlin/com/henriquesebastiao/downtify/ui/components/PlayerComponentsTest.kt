package com.henriquesebastiao.downtify.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PlaybackControls
import com.henriquesebastiao.downtify.core.designsystem.component.PlaybackLabels
import com.henriquesebastiao.downtify.core.designsystem.component.PlayerToggle
import com.henriquesebastiao.downtify.core.designsystem.component.PlayerToggles
import com.henriquesebastiao.downtify.core.designsystem.component.WavySeekBar
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class PlayerComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val labels = PlaybackLabels(play = "Play", pause = "Pause", previous = "Previous", next = "Next")

    @Test
    fun theTransportButtonsDoWhatTheySayAndPlaySaysPauseWhilePlaying() {
        val calls = mutableListOf<String>()
        compose.setContent {
            DowntifyTheme {
                PlaybackControls(
                    playing = true,
                    onTogglePlay = { calls += "toggle" },
                    onPrevious = { calls += "previous" },
                    onNext = { calls += "next" },
                    labels = labels,
                )
            }
        }
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        assertEquals(listOf("previous", "toggle", "next"), calls)
    }

    @Test
    fun onANarrowPhoneTheTransportButtonsFitWithAGapBetweenThem() {
        // A 360 dp phone with its 16 dp margins leaves 328 dp.
        compose.setContent {
            DowntifyTheme {
                PlaybackControls(
                    playing = false,
                    onTogglePlay = {},
                    onPrevious = {},
                    onNext = {},
                    labels = labels,
                    modifier = Modifier.width(328.dp),
                )
            }
        }
        val previous = compose.onNodeWithContentDescription("Previous").button()
        val play = compose.onNodeWithContentDescription("Play").button()
        val next = compose.onNodeWithContentDescription("Next").button()
        assertTrue(previous.right < play.left)
        assertTrue(play.right < next.left)
        assertTrue(next.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
        // Play is the widest: its weight is larger.
        assertTrue(play.width > previous.width)
    }

    @Test
    fun theTogglesReportTheirTapsAndState() {
        var shuffles = 0
        compose.setContent {
            DowntifyTheme {
                PlayerToggles(
                    listOf(
                        PlayerToggle(DowntifyIcons.Shuffle, "Shuffle off", checked = false) { shuffles++ },
                        PlayerToggle(DowntifyIcons.FavoriteFilled, "Liked", checked = true) {},
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Shuffle off").performClick()
        assertEquals(1, shuffles)
    }

    @Test
    fun seekingWithTheSliderSendsWhereItLetGo() {
        var seekedTo: Float? = null
        compose.setContent {
            DowntifyTheme {
                WavySeekBar(
                    progress = 0.1f,
                    onSeek = { seekedTo = it },
                    playing = true,
                    timeAt = { "${(it * 100).toInt()}" },
                    duration = "100",
                    contentDescription = "Position",
                )
            }
        }
        compose.onNodeWithContentDescription("Position")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(0.5f, seekedTo!!, 0.01f)
    }

    // Buttons merge their icon's label into themselves: the labelled node is the button.
    private fun SemanticsNodeInteraction.button() = fetchSemanticsNode().boundsInRoot
}
