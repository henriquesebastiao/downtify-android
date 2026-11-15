package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvQueuePanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val track = PreviewData.tracks.first()
    private val queuedSong = QueueEntry(4, "q1", "A song I queued", "Someone", queued = true)
    private val albumSong = QueueEntry(2, "a2", "The album's next song", "The band")

    private fun show(state: PlayerState, onSkipTo: (Int) -> Unit = {}, onNext: () -> Unit = {}) = compose.setContent {
        DowntifyTheme(darkTheme = true) { TvQueuePanel(state, onSkipTo, onNext) }
    }

    @Test
    fun itShowsWhatPlaysThenWhatWasQueuedThenWhatComesFromTheAlbum() {
        show(
            PlayerState(
                track = track,
                upNext = listOf(queuedSong, albumSong),
                context = PlaybackContext(PlaybackContextType.Album, "alb", "Glass Harbor"),
            ),
        )

        compose.onNodeWithText("Now playing").assertExists()
        compose.onNodeWithText(track.displayTitle).assertExists()
        compose.onNodeWithText("Next in queue").assertExists()
        compose.onNodeWithText("Next from: Glass Harbor").assertExists()
        compose.onNodeWithText(albumSong.title).assertExists()
    }

    @Test
    fun okOnASongPlaysItAndTheButtonSkips() {
        val skipped = mutableListOf<Int>()
        var nexts = 0
        show(PlayerState(track = track, upNext = listOf(albumSong)), onSkipTo = { skipped += it }, onNext = { nexts++ })

        compose.onNodeWithText(albumSong.title).performClick()
        compose.onNodeWithContentDescription("Next").performClick()

        assertEquals(listOf(albumSong.index), skipped)
        assertEquals(1, nexts)
    }

    @Test
    fun withNothingToComeThereIsNoSkip() {
        show(PlayerState(track = track))

        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
    }
}
