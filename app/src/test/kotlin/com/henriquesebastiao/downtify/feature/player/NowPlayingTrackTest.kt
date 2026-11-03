package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.NBSP
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.StreamFormat
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A library track on Now Playing: where it plays from, what the file is, and nothing else in the way. */
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class NowPlayingTrackTest {
    @get:Rule
    val compose = createComposeRule()

    // FLAC, 44.1 kHz, 912 kbps.
    private val track = PreviewData.tracks[1]

    private fun show(state: PlayerState, actions: NowPlayingActions = NowPlayingActions()) {
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(state = state, isLiked = false, serverName = "nas", actions = actions)
            }
        }
    }

    private fun playing(quality: StreamQuality? = StreamQuality.Original, context: PlaybackContext? = null) =
        PlayerState(
            track = track,
            isPlaying = true,
            positionMs = 70_000,
            durationMs = 239_000,
            quality = quality,
            context = context,
        )

    @Test
    fun theFileSaysItsSampleRateBitrateAndFormat() {
        show(playing())
        compose.onNodeWithText("44.1${NBSP}kHz · 912${NBSP}kbps · FLAC").assertIsDisplayed()
    }

    @Test
    fun aConvertedStreamSaysTheBitrateAndCodecItIsConvertedTo() {
        show(playing(quality = StreamQuality(StreamFormat.Opus, 160)))
        compose.onNodeWithText("160${NBSP}kbps · OPUS").assertIsDisplayed()
    }

    @Test
    fun theWholeLibraryIsJustNowPlaying() {
        show(playing(context = PlaybackContext(PlaybackContextType.Songs, "", "")))
        compose.onNodeWithText("Now playing").assertIsDisplayed()
    }

    @Test
    fun anAlbumSaysWhereItPlaysFromAndOpensIt() {
        val album = PlaybackContext(PlaybackContextType.Album, "alb-Glass Harbor", "Glass Harbor")
        var opened: PlaybackContext? = null
        show(playing(context = album), NowPlayingActions(onOpenContext = { opened = it }))
        compose.onNodeWithText("PLAYING FROM ALBUM").assertIsDisplayed()
        compose.onNodeWithText("Now playing").assertDoesNotExist()
        compose.onNodeWithText("Glass Harbor").performClick()
        assertEquals(album, opened)
    }

    @Test
    fun theTogglesHaveNoMenuOrSimilarTracksBesideThem() {
        show(playing())
        compose.onNodeWithContentDescription("Add to Liked songs").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tracks like ${track.displayTitle}").assertDoesNotExist()
    }
}
