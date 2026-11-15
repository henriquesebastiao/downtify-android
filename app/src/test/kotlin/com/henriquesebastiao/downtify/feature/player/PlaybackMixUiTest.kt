package com.henriquesebastiao.downtify.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.player.LocalDjPhase
import com.henriquesebastiao.downtify.core.player.LocalDjTransition
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingDj
import com.henriquesebastiao.downtify.core.player.QueueEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h800dp")
class PlaybackMixUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modesWrapAndRemainUsableWithLargeText() {
        val selected = mutableStateOf(PlaybackMixMode.Disabled)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DowntifyTheme { PlaybackMixSelector(selected.value) { selected.value = it } }
            }
        }
        compose.onNodeWithText("Désactivé").assertIsDisplayed()
        compose.onNodeWithText("Transitions").assertIsDisplayed().performClick()
        assertEquals(PlaybackMixMode.Transitions, selected.value)
        compose.onNodeWithText("Transitions").assertIsSelected()
        compose.onNodeWithText("DJ · Bêta").assertIsDisplayed().performClick()
        assertEquals(PlaybackMixMode.Dj, selected.value)
        compose.onNodeWithText("DJ · Bêta").assertIsSelected()
    }

    @Test fun preparedDeckIsOnlyShownDuringItsActualOverlap() {
        val transition = mutableStateOf(LocalDjTransition(LocalDjPhase.Prepared, "out", "in"))
        val state = PlayerState(upNext = listOf(QueueEntry(1, "in", "Next song", "Artist")))
        compose.setContent { DowntifyTheme { PlaybackOverlap(state, transition.value) } }
        compose.onNodeWithContentDescription("Morceau entrant : Next song").assertDoesNotExist()
        compose.runOnIdle { transition.value = transition.value.copy(phase = LocalDjPhase.Mixing, progress = .5f) }
        compose.onNodeWithContentDescription("Morceau sortant").assertIsDisplayed()
        compose.onNodeWithContentDescription("Morceau entrant : Next song").assertIsDisplayed()
        compose.runOnIdle { transition.value = transition.value.copy(phase = LocalDjPhase.Waiting) }
        compose.onNodeWithContentDescription("Morceau sortant").assertDoesNotExist()
    }

    @Test fun serverOverlapUsesTheIncomingTrackMetadata() {
        val state = PlayerState(
            dj = PlayingDj(
                "mix",
                "Set",
                trackId = "out",
                nextTrackId = "in",
                nextTitle = "Server next",
                transition = .4f,
            ),
        )
        compose.setContent { DowntifyTheme { PlaybackOverlap(state, LocalDjTransition()) } }
        compose.onNodeWithContentDescription("Morceau sortant").assertIsDisplayed()
        compose.onNodeWithContentDescription("Morceau entrant : Server next").assertIsDisplayed()
    }
}
