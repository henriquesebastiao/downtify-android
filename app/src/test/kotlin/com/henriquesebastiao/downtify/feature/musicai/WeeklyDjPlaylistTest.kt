package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiStatusDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjWeeklyCardDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjWeeklyDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class WeeklyDjPlaylistTest {
    @get:Rule val compose = createComposeRule()
    private val session = MusicDjSessionDto(
        "a".repeat(32),
        state = "ready",
        theme = "rock",
        duration = 400.0,
        tracks = listOf(
            MusicAiTrackDto("one", "First track", "Artist A"),
            MusicAiTrackDto("two", "Second track", "Artist B"),
        ),
    )
    private val weekly =
        MusicDjWeeklyDto(cards = listOf(MusicDjWeeklyCardDto("rock", session)), featured = listOf("rock"))
    private val caps = MusicAiCapabilitiesDto(
        version = 1,
        features = mapOf("weekly" to true, "dj_prepared" to true, "dj_weekly" to true),
        djWeeklySets = true,
        djAssetCopies = true,
        dj = MusicAiStatusDto(preparedReady = true),
    )

    @Test fun shelfOnlyNavigatesToTheSelectedWeeklyPlaylist() {
        var opened = ""
        var effects = 0
        compose.setContent {
            DowntifyTheme {
                WeeklyDjShelf(
                    MusicDjUiState(weekly = weekly),
                    caps,
                    true,
                    onOpenSet = { theme, copy -> opened = "$theme:$copy" },
                    onPlay = { effects++ },
                    onPrepare = { effects++ },
                    onSave = { _, _ -> effects++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Détails de Set DJ · Rock").performClick()
        assertEquals("rock:", opened)
        assertEquals(0, effects)
        compose.onNodeWithText("Enregistrer une copie").assertDoesNotExist()
    }

    @Test fun playlistShowsItsTracksAndOnlySavesTheChosenNameExplicitly() {
        var saved = ""
        var played = ""
        compose.setContent {
            DowntifyTheme {
                WeeklyDjPlaylistPage(
                    "rock",
                    session,
                    caps,
                    MusicDjUiState(weekly = weekly),
                    false,
                    {},
                    onPlay = { played = it.id },
                    onSave = { _, name -> saved = name },
                )
            }
        }
        assertEquals("", saved)
        assertEquals("", played)
        val list = compose.onNodeWithTag("weekly-dj-playlist")
        list.performScrollToNode(hasText("Second track"))
        compose.onNodeWithText("Second track").assertExists()
        list.performScrollToNode(hasText("Enregistrer une copie"))
        compose.onNodeWithText("Enregistrer une copie").performClick()
        list.performScrollToNode(hasText("Nom de la copie"))
        compose.onNodeWithText("Nom de la copie").performTextReplacement("Ma semaine rock")
        assertEquals("", saved)
        list.performScrollToNode(hasText("Conserver l’audio"))
        compose.onNodeWithText("Conserver l’audio").performClick()
        assertEquals("Ma semaine rock", saved)
        assertEquals("", played)
    }

    @Test fun expiredCopyDoesNotPrepareOrSaveAnotherSet() {
        compose.setContent {
            DowntifyTheme {
                WeeklyDjPlaylistPage(
                    "rock",
                    session.copy(state = "expired", immutableCopy = true),
                    caps,
                    MusicDjUiState(weekly = weekly),
                    true,
                    {},
                )
            }
        }
        compose.onNodeWithText("Préparer Rock").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer une copie").assertDoesNotExist()
        compose.onNodeWithText("Écouter Set DJ · Rock").assertDoesNotExist()
    }
}
