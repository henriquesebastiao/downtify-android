package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiStatusDto
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
class WeeklyDjShelfTest {
    @get:Rule val compose = createComposeRule()
    private val session =
        MusicDjSessionDto(
            "a".repeat(32),
            state = "ready",
            theme = "energie",
            weeklyTheme = "rock",
            duration = 1800.0,
            trackCount = 25,
        )
    private val weekly =
        MusicDjWeeklyDto(
            cards = listOf(MusicDjWeeklyCardDto("rock", session), MusicDjWeeklyCardDto("pop")),
            featured = listOf("rock"),
        )
    private val caps = MusicAiCapabilitiesDto(
        version = 1,
        features = mapOf(
            "weekly" to true,
            "dj_prepared" to true,
            "dj_weekly" to true,
        ),
        djWeeklySets = true,
        djAssetCopies = true,
        dj = MusicAiStatusDto(preparedReady = true),
    )

    @Test fun oldServersAndDisabledFlagsHideAllWeeklyActions() {
        var current by mutableStateOf(caps.copy(djWeeklySets = false))
        compose.setContent { DowntifyTheme { WeeklyDjShelf(MusicDjUiState(weekly = weekly), current, true) } }
        compose.onNodeWithText("Pour vous DJ").assertDoesNotExist()
        compose.runOnIdle { current = caps }
        compose.onNodeWithText("Pour vous DJ").assertExists()
        compose.onNodeWithText("Préparer Pop").assertDoesNotExist()
        compose.runOnIdle { current = caps.copy(features = emptyMap()) }
        compose.onNodeWithText("Pour vous DJ").assertDoesNotExist()
    }

    @Test fun copyingIsExplicitNamedAndNeverTriggeredByOpeningASet() {
        var saved = ""
        var played = ""
        compose.setContent {
            DowntifyTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    WeeklyDjShelf(
                        MusicDjUiState(weekly = weekly),
                        caps,
                        true,
                        onPlay = { played = it.id },
                        onSave = { value, name -> saved = "${value.id}:$name" },
                    )
                }
            }
        }
        assertEquals("", saved)
        compose.onNodeWithContentDescription("Détails de Set DJ · Rock").performClick()
        assertEquals("", played)
        assertEquals("", saved)
        compose.onNodeWithText("Écouter Set DJ · Rock").performClick()
        assertEquals(session.id, played)
        assertEquals("", saved)
        compose.onNodeWithText("Enregistrer une copie").performScrollTo().performClick()
        compose.onNodeWithText("Nom de la copie").performScrollTo().performTextReplacement("Mon rock préféré")
        compose.onNodeWithText("Conserver l’audio").performScrollTo().performClick()
        assertEquals("${session.id}:Mon rock préféré", saved)
    }

    @Test fun missingAudioCanOnlyBePreparedByAnExplicitAction() {
        var prepared = ""
        var busy by mutableStateOf(false)
        val onlyPop = weekly.copy(cards = listOf(MusicDjWeeklyCardDto("pop")))
        compose.setContent {
            DowntifyTheme {
                WeeklyDjShelf(MusicDjUiState(weekly = onlyPop, busy = busy), caps, false, onPrepare = {
                    prepared =
                        it
                })
            }
        }
        assertEquals("", prepared)
        compose.onNodeWithContentDescription("Détails de Set DJ · Pop").performClick()
        assertEquals("", prepared)
        compose.onNodeWithText("Préparer Pop").performClick()
        assertEquals("pop", prepared)
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Préparer Pop").assertIsNotEnabled()
    }

    @Test fun openDetailsFollowTheLatestWeeklySessionWithoutStartingPlayback() {
        var current by mutableStateOf(weekly.copy(cards = listOf(MusicDjWeeklyCardDto("pop"))))
        var played = ""
        compose.setContent {
            DowntifyTheme {
                WeeklyDjShelf(MusicDjUiState(weekly = current), caps, false, onPlay = { played = it.id })
            }
        }
        compose.onNodeWithContentDescription("Détails de Set DJ · Pop").performClick()
        compose.onNodeWithText("Écouter Set DJ · Pop").assertDoesNotExist()
        val preparing = session.copy(id = "b".repeat(32), state = "rendering", progress = 0.6)
        compose.runOnIdle { current = current.copy(cards = listOf(MusicDjWeeklyCardDto("pop", preparing))) }
        compose.onNodeWithText("Préparer Pop").assertDoesNotExist()
        compose.onNodeWithText("Écouter Set DJ · Pop").assertDoesNotExist()
        val ready = preparing.copy(id = "c".repeat(32), state = "ready", progress = 1.0)
        compose.runOnIdle { current = current.copy(cards = listOf(MusicDjWeeklyCardDto("pop", ready))) }
        assertEquals("", played)
        compose.onNodeWithText("Écouter Set DJ · Pop").performClick()
        assertEquals(ready.id, played)
    }

    @Test fun unavailableSavedCopyCannotPrepareOrSaveAnotherWeeklySet() {
        var prepared = ""
        val copy = session.copy(id = "d".repeat(32), title = "Mon set", state = "expired", immutableCopy = true)
        compose.setContent {
            DowntifyTheme {
                WeeklyDjShelf(
                    MusicDjUiState(weekly = weekly.copy(copies = listOf(copy))),
                    caps,
                    false,
                    onPrepare = { prepared = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Détails de Mon set").performClick()
        compose.onNodeWithText("Préparer Rock").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer une copie").assertDoesNotExist()
        compose.onNodeWithText("Écouter Mon set").assertDoesNotExist()
        assertEquals("", prepared)
    }
}
