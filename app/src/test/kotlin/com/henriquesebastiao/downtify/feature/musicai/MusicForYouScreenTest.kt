package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiStatusDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class MusicForYouScreenTest {
    @get:Rule val compose = createComposeRule()
    private val mix = MusicAiPlaylistDto("daily", "calme", "2026-11-06", 40)
    private val track = MusicAiTrackDto("synthetic-one", "Synthetic title", "Synthetic artist")
    private val caps =
        MusicAiCapabilitiesDto(
            version = 1,
            features = mapOf("daily" to true, "personalization" to true),
            model = MusicAiStatusDto(ready = true),
        )

    @Test fun openingAMixShowsItsTracksAndReturningKeepsTheCards() {
        var state by mutableStateOf(MusicAiUiState(playlists = listOf(mix)))
        var played = ""
        compose.setContent {
            DowntifyTheme {
                LazyColumn {
                    musicForYouItems(
                        state,
                        caps,
                        MusicForYouActions(
                            mood = { cadence, mood ->
                                state =
                                    state.copy(tracks = listOf(track), source = cadence, selectedMood = mood)
                            },
                            clearSelection = { state = state.copy(tracks = emptyList()) },
                            play = { value, _ -> played = value.id },
                        ),
                    )
                }
            }
        }
        compose.onNodeWithText("Calme").performClick()
        compose.onAllNodesWithText("Mix du jour · Calme").assertCountEquals(2)
        compose.onNodeWithText("Calme").assertDoesNotExist()
        compose.onNodeWithText("Tout écouter").performClick()
        assertEquals(track.id, played)
        compose.onNodeWithText("← Tous mes mixes").performClick()
        compose.onNodeWithText("Calme").assertExists()
    }

    @Test fun disablingTheModuleHidesExistingCardsAndPlaybackCommands() {
        val state =
            MusicAiUiState(playlists = listOf(mix), tracks = listOf(track), source = "daily", selectedMood = "calme")
        compose.setContent {
            DowntifyTheme {
                LazyColumn { musicForYouItems(state, caps.copy(features = emptyMap()), MusicForYouActions()) }
            }
        }
        compose.onNodeWithText("Calme").assertDoesNotExist()
        compose.onNodeWithText("Tout écouter").assertDoesNotExist()
        compose.onNodeWithText("À écouter maintenant").assertDoesNotExist()
    }
}
