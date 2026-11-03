package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingEpisode
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

/**
 * The transport (an expressive button group) and the toggles under it keep a gap between their
 * buttons on every width, and the play button keeps its size where there is room for it.
 */
@RunWith(AndroidJUnit4::class)
class TransportLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val track = PreviewData.tracks[1]
    private val songState = PlayerState(track = track, isPlaying = true, positionMs = 1_000, durationMs = 200_000)
    private val episodeState = PlayerState(
        episode = PlayingEpisode(7, 1, "The Sweetest Thing", "Radiolab", "", "Podcasts/R/e.mp3"),
        isPlaying = true,
        durationMs = 1_862_000,
    )

    private fun show(state: PlayerState) {
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(state = state, isLiked = false, serverName = "nas", actions = NowPlayingActions())
            }
        }
    }

    private fun assertGaps(descriptions: List<String>, minGapPx: Float) {
        val boxes = descriptions.map { compose.onNodeWithContentDescription(it).bounds() }
        boxes.zipWithNext().forEachIndexed { i, (left, right) ->
            val gap = right.left - left.right
            assertTrue("${descriptions[i]} → ${descriptions[i + 1]}: gap $gap px < $minGapPx px", gap >= minGapPx)
        }
    }

    // xhdpi is 2x: 8 dp = 16 px.
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    @Test
    fun aSongsButtonsDoNotTouchOnA360dpScreen() {
        show(songState)
        assertGaps(listOf("Previous", "Pause", "Next"), 7.5f * 2)
        assertGaps(listOf("Shuffle, off", "Repeat, off", "Add to Liked songs"), 7.5f * 2)
    }

    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    @Test
    fun anEpisodesButtonsDoNotTouchOnA360dpScreen() {
        show(episodeState)
        assertGaps(listOf("Back 10 seconds", "Pause", "Forward 30 seconds"), 7.5f * 2)
    }

    @Config(qualifiers = "w320dp-h700dp-xhdpi")
    @Test
    fun andOnAnEvenNarrowerOne() {
        show(songState)
        assertGaps(listOf("Previous", "Pause", "Next"), 7.5f * 2)
    }

    @Config(qualifiers = "w411dp-h914dp-xhdpi")
    @Test
    fun aTallScreenKeepsTheFullSizeButtonsWithPlayTheWidest() {
        show(songState)
        val play = compose.onNodeWithContentDescription("Pause").bounds()
        val previous = compose.onNodeWithContentDescription("Previous").bounds()
        assertEquals(76f * 2, play.height, 1f)
        assertTrue(play.width > previous.width)
    }

    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test
    fun aShortScreenGetsTheCompactTransport() {
        show(songState)
        assertEquals(56f * 2, compose.onNodeWithContentDescription("Pause").bounds().height, 1f)
        assertGaps(listOf("Previous", "Pause", "Next"), 7.5f * 2)
    }

    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    @Test
    fun theMiniPlayersContentIsCentredInTheCard() {
        compose.setContent {
            DowntifyTheme {
                MiniPlayer(state = songState, onOpen = {}, onTogglePlay = {})
            }
        }
        val pause = compose.onNodeWithContentDescription("Pause").bounds()
        val card = compose.onNodeWithContentDescription(
            "Pause",
        ).fetchSemanticsNode().layoutInfo.coordinates.parentLayoutCoordinates
        // The 48 dp button sits in the middle of the card's height.
        val cardBounds = generateSequence(card) { it.parentLayoutCoordinates }.last().size
        assertTrue(cardBounds.height > 0)
        val topGap = pause.top
        val bottomGap = cardBounds.height - pause.bottom
        // The card sits at the top of the test root, so what's above the button is the card's own padding.
        assertEquals("above the button vs below it (px)", bottomGap, topGap + 0f, 1f)
    }
}
