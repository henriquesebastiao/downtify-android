package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class QueueScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val tracks = PreviewData.tracks

    private fun state(queuedCount: Int) = PlayerState(
        track = tracks[0],
        isPlaying = true,
        context = PlaybackContext(PlaybackContextType.Album, "alb", "Glass Harbor"),
        upNext = tracks.drop(1).mapIndexed { i, t ->
            QueueEntry(
                index = i + 1,
                trackId = t.id,
                title = t.displayTitle,
                artist = t.displayArtist,
                queued =
                    i < queuedCount,
            )
        },
    )

    @Test
    fun queuedSongsComeFirstUnderTheirOwnHeading() {
        var cleared = 0
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 1),
                    actions = QueueActions(onClearQueued = { cleared++ }),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Now playing").assertExists()
        compose.onNodeWithText("Next in queue").assertExists()
        compose.onNodeWithText("Next from: Glass Harbor").assertExists()
        compose.onNodeWithText("Clear queue").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun aStreamingSongShowsAsNowPlaying() {
        val stream =
            PlayingStream(
                videoId = "dQw4w9WgXcQ",
                title = "Night Drive",
                artist = "Neon Coast",
                coverUrl = "",
                raw = "{}",
            )
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 1).copy(track = null, stream = stream),
                    actions = QueueActions(),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Now playing").assertExists()
        compose.onNodeWithText("Night Drive").assertExists()
        compose.onNodeWithText("Next in queue").assertExists()
    }

    @Test
    fun withNothingQueuedThereIsNoQueueHeading() {
        compose.setContent {
            DowntifyTheme {
                QueueScreen(state = state(queuedCount = 0), actions = QueueActions(), controls = NowPlayingActions())
            }
        }
        compose.onNodeWithText("Next in queue").assertDoesNotExist()
        compose.onNodeWithText("Clear queue").assertDoesNotExist()
    }

    @Test
    fun tappingASongPlaysItAndTheButtonCloses() {
        var skippedTo = -1
        var closed = 0
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(queuedCount = 0),
                    actions = QueueActions(onSkipTo = { skippedTo = it }, onClose = { closed++ }),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText(tracks[2].displayTitle).performClick()
        compose.onNodeWithContentDescription("Close queue").performClick()
        assertEquals(2, skippedTo)
        assertEquals(1, closed)
    }

    @Test
    fun saysWhenNothingComesNext() {
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = PlayerState(track = tracks[0]),
                    actions = QueueActions(),
                    controls = NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Nothing plays after this song.").assertExists()
    }

    @Test
    fun unavailableDjCanBeRecheckedAndExplainsWhy() {
        var checked = 0
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(0),
                    actions = QueueActions(onToggleDj = { checked++ }),
                    controls = NowPlayingActions(),
                    dj = QueueDjState(available = false, reason = "Activez l’essai bêta dans Paramètres → IA."),
                )
            }
        }
        compose.onNodeWithText("Activez l’essai bêta dans Paramètres → IA.").assertExists()
        compose.onNodeWithText("Vérifier le DJ").performClick()
        assertEquals(1, checked)
    }

    @Test
    fun preparingDjCanBeCancelledEvenWhileChecking() {
        var cancelled = 0
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(0),
                    actions = QueueActions(onToggleDj = { cancelled++ }),
                    controls = NowPlayingActions(),
                    dj = QueueDjState(available = true, active = true, preparing = true, checking = true),
                )
            }
        }
        compose.onNodeWithText("Annuler la préparation DJ").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun readyRelayDoesNotPretendAudioIsAlreadyDj() {
        compose.setContent {
            DowntifyTheme {
                QueueScreen(
                    state = state(0),
                    actions = QueueActions(),
                    controls = NowPlayingActions(),
                    dj = QueueDjState(
                        available = true,
                        ready = true,
                        active = true,
                        waiting = true,
                        message = "Le DJ prendra le relais à la fin du titre en cours.",
                    ),
                )
            }
        }
        compose.onNodeWithText("Annuler le relais DJ").assertExists()
        compose.onNodeWithText("Le DJ prendra le relais à la fin du titre en cours.").assertExists()
        compose.onNodeWithText("Désactiver le mode DJ").assertDoesNotExist()
    }
}
