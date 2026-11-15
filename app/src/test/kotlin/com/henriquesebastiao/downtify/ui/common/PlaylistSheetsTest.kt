package com.henriquesebastiao.downtify.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class PlaylistSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSheetOffersNewPlaylistAndTheOnesThatTakeSongs() {
        var picked: String? = null
        var created = 0
        compose.setContent {
            DowntifyTheme {
                AddToPlaylistContent(
                    targets = listOf(PlaylistTarget("Mix", "Mix", 3)),
                    onPick = { picked = it },
                    onCreateNew = { created++ },
                )
            }
        }
        compose.onNodeWithText("Mix").performClick()
        compose.onNodeWithText("New playlist").performClick()

        assertEquals("Mix", picked)
        assertEquals(1, created)
    }

    @Test
    fun addSongsTakesThePickUntilNothingIsPicked() {
        var added: List<String>? = null
        compose.setContent {
            DowntifyTheme {
                AddSongsContent(songs = PreviewData.tracks, onConfirm = { added = it })
            }
        }
        compose.onNodeWithText("Add 0 songs to playlist").assertIsNotEnabled()
        compose.onNodeWithText("Low Tide").performClick()
        compose.onNodeWithText("Undertow").performClick()
        // Picking a song again unpicks it.
        compose.onNodeWithText("Low Tide").performClick()
        compose.onNodeWithText("Add 1 song to playlist").assertIsEnabled().performClick()

        assertEquals(listOf("t2"), added)
    }

    @Test
    fun namingNeedsSomethingToSend() {
        var named: String? = null
        compose.setContent {
            DowntifyTheme {
                PlaylistNameDialog(
                    title = "New playlist",
                    confirmLabel = "Create",
                    onDismiss = {},
                    onConfirm = { named = it },
                )
            }
        }
        compose.onNodeWithText("Create").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("  Road trip  ")
        compose.onNodeWithText("Create").assertIsEnabled().performClick()

        assertEquals("Road trip", named)
    }
}
