package com.henriquesebastiao.downtify.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.feature.musicai.DailyMusicShelf
import com.henriquesebastiao.downtify.feature.musicai.MusicAiUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class DailyMusicShelfTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `four pending cards stay usable and motivation preserves the energy id`() {
        var selected: Pair<String, String>? = null
        var all = 0
        compose.setContent {
            DowntifyTheme {
                DailyMusicShelf(
                    MusicAiUiState(),
                    MusicAiCapabilitiesDto(version = 1, features = mapOf("daily" to true)),
                    onOpen = { cadence, mood -> selected = cadence to mood },
                    onSeeAll = { all++ },
                )
            }
        }
        listOf("Détente", "Concentration", "Motivation", "Calme").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("En préparation").assertCountEquals(4)
        compose.onNodeWithText("Motivation").performClick()
        assertEquals("daily" to "energie", selected)
        compose.onNodeWithText("Tout voir").performClick()
        assertEquals(1, all)
    }
}
