package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.NBSP
import com.henriquesebastiao.downtify.core.model.SleepTimerMode
import com.henriquesebastiao.downtify.core.model.SleepTimerState
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The sleep timer's sheet and its countdown: every duration the timer offers plus the end of what
 * plays, the running one filled in, and the timer turned off from either place.
 */
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class SleepTimerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showOptions(
        mode: SleepTimerMode? = null,
        onPick: (SleepTimerMode) -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme { SleepTimerOptions(mode = mode, onPick = onPick, onCancel = onCancel) }
        }
    }

    private fun showChip(state: SleepTimerState, onCancel: () -> Unit = {}) {
        compose.setContent { DowntifyTheme { SleepTimerChip(state, onCancel) } }
    }

    @Test
    fun everyDurationItOffersIsThereWithTheEndOfTheTrackAndTheEpisode() {
        showOptions()
        listOf(5, 10, 15, 30, 45, 60, 90).forEach { minutes ->
            compose.onNodeWithText("$minutes${NBSP}min").assertIsDisplayed()
        }
        compose.onNodeWithText("End of track").assertIsDisplayed()
        compose.onNodeWithText("End of episode").assertIsDisplayed()
    }

    @Test
    fun aDurationChipSetsTheTimerForThoseMinutes() {
        var picked: SleepTimerMode? = null
        showOptions(onPick = { picked = it })
        compose.onNodeWithText("45${NBSP}min").performClick()
        assertEquals(SleepTimerMode.AfterMinutes(45), picked)
    }

    @Test
    fun theRunningDurationIsTheFilledChip() {
        showOptions(mode = SleepTimerMode.AfterMinutes(30))
        compose.onNodeWithText("30${NBSP}min").assertIsSelected()
        compose.onNodeWithText("5${NBSP}min").assertIsNotSelected()
    }

    @Test
    fun theEndOfTheEpisodeIsAChoiceOfItsOwn() {
        var picked: SleepTimerMode? = null
        showOptions(mode = SleepTimerMode.AfterMinutes(30), onPick = { picked = it })
        compose.onNodeWithText("End of episode").performClick()
        assertEquals(SleepTimerMode.EndOfEpisode, picked)
    }

    @Test
    fun aRunningTimerTurnsOffFromTheSheet() {
        var cancelled = false
        showOptions(mode = SleepTimerMode.EndOfTrack, onCancel = { cancelled = true })
        compose.onNodeWithText("End of track").assertIsSelected()
        compose.onNodeWithText("Cancel sleep timer").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun aSheetWithNoTimerRunningOffersNothingToTurnOff() {
        showOptions()
        compose.onNodeWithText("Cancel sleep timer").assertDoesNotExist()
    }

    @Test
    fun theChipCountsDownWhatIsLeftOfTheTimer() {
        // Five minutes, one second in.
        val timer = SleepTimerState.afterMinutes(5, nowMs = 0)
            .advance(nowMs = 1_000, playing = true, positionMs = 0, durationMs = 0)
        showChip(timer)
        compose.onNodeWithText("4:59 left").assertIsDisplayed()
    }

    @Test
    fun tappingTheChipTurnsTheTimerOff() {
        var cancelled = false
        showChip(SleepTimerState.endOfTrack(positionMs = 120_000, durationMs = 239_000, nowMs = 0)) {
            cancelled = true
        }
        compose.onNodeWithText("1:59 left").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun anEndOfItemTimerWithNoLengthToTimeSaysWhatItWaitsFor() {
        showChip(SleepTimerState.endOfTrack(positionMs = 0, durationMs = 0, nowMs = 0))
        compose.onNodeWithText("End of track").assertIsDisplayed()
    }

    @Test
    fun thereIsNoChipWhileNoTimerRuns() {
        showChip(SleepTimerState.Inactive)
        compose.onNodeWithText("left", substring = true).assertDoesNotExist()
    }

    @Test
    fun theMoonOnNowPlayingOpensTheTimerAndTheChipTurnsItOff() {
        var opened = false
        var cancelled = false
        compose.setContent {
            DowntifyTheme {
                NowPlayingScreen(
                    state = PlayerState(
                        track = PreviewData.tracks[1],
                        isPlaying = true,
                        positionMs = 1_000,
                        durationMs = 239_000,
                        sleepTimer = SleepTimerState.afterMinutes(15, nowMs = 0),
                    ),
                    isLiked = false,
                    serverName = "nas",
                    actions = NowPlayingActions(
                        onOpenSleepTimer = { opened = true },
                        onCancelSleepTimer = { cancelled = true },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Sleep timer").performClick()
        assertTrue(opened)
        compose.onNodeWithText("15:00 left").performClick()
        assertTrue(cancelled)
    }
}
