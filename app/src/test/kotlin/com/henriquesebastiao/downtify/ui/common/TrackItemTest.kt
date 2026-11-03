package com.henriquesebastiao.downtify.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class TrackItemTest {
    @get:Rule
    val compose = createComposeRule()

    private val track = PreviewData.tracks[1]

    @Test
    fun theRowPlaysAndTheRoundButtonOpensTheMenuInstead() {
        var plays = 0
        var queued = 0
        compose.setContent {
            DowntifyTheme {
                TrackItem(
                    track = track,
                    onClick = { plays++ },
                    actions = TrackActions(onAddToQueue = { queued++ }),
                )
            }
        }
        compose.onNodeWithText(track.displayTitle).performClick()
        assertEquals(1, plays)
        compose.onNodeWithContentDescription("More options for ${track.displayTitle}").performClick()
        compose.onNodeWithText("Add to queue").performClick()
        assertEquals(1, queued)
        assertEquals(1, plays)
    }

    @Test
    fun aDownloadedSongSaysSo() {
        compose.setContent { DowntifyTheme { TrackItem(track = track, onClick = {}, downloaded = true) } }
        compose.onNodeWithContentDescription("Downloaded").assertExists()
    }
}
