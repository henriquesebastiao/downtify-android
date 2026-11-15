package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvTopBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theTabsSwitchAndSettingsOpens() {
        val picked = mutableListOf<TopLevelDestination>()
        var settings = 0
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvTopBar(
                    selected = TopLevelDestination.Home,
                    onSelect = { picked += it },
                    onSettings = { settings++ },
                )
            }
        }

        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Library").assertIsNotSelected().performClick()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(listOf(TopLevelDestination.Library, TopLevelDestination.Search), picked)
        assertEquals(1, settings)
    }

    @Test
    fun whatIsPlayingOpensThePlayer() {
        val track = PreviewData.tracks[1]
        var opened = 0
        compose.setContent {
            DowntifyTheme(darkTheme = true) {
                TvNowPlayingCard(PlayerState(track = track, isPlaying = true), onOpen = { opened++ })
            }
        }

        compose.onNodeWithText(track.displayTitle).assertExists()
        compose.onNodeWithText(track.displayArtist).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun nothingLoadedShowsNoCard() {
        compose.setContent {
            DowntifyTheme(darkTheme = true) { TvNowPlayingCard(PlayerState(), onOpen = {}) }
        }

        compose.onNodeWithContentDescription("Open Now playing").assertDoesNotExist()
        compose.onNodeWithText(PreviewData.tracks[1].displayTitle).assertDoesNotExist()
    }
}
