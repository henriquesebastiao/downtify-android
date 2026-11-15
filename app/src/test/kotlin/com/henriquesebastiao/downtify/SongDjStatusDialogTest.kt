package com.henriquesebastiao.downtify

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.ui.common.SongDjStatus
import com.henriquesebastiao.downtify.ui.common.SongDjStatusDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp", sdk = [34])
@RunWith(AndroidJUnit4::class)
class SongDjStatusDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun hiddenStatusDoesNotShowADialog() {
        compose.setContent {
            DowntifyTheme { SongDjStatusDialog(SongDjStatus(), {}, {}, {}) }
        }
        compose.onNodeWithText("Mix DJ").assertDoesNotExist()
    }

    @Test fun preparationShowsProgressAndClosingDoesNotCancel() {
        var dismissed = 0
        var cancelled = 0
        compose.setContent {
            DowntifyTheme {
                SongDjStatusDialog(
                    SongDjStatus(
                        visible = true,
                        message = "Préparation des transitions",
                        preparing = true,
                        canCancel = true,
                    ),
                    onDismiss = { dismissed++ },
                    onCancel = { cancelled++ },
                    onOpenPlayer = {},
                )
            }
        }
        compose.onNodeWithText("Préparation des transitions").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        compose.onNodeWithText("Ouvrir le lecteur").assertDoesNotExist()
        compose.onNodeWithText("Fermer").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, cancelled)
        compose.onNodeWithText("Annuler").performClick()
        assertEquals(1, cancelled)
        assertEquals(1, dismissed)
    }

    @Test fun anErrorRemainsVisibleUntilExplicitDismissal() {
        val status =
            mutableStateOf(SongDjStatus(visible = true, message = "Préparation", preparing = true, canCancel = true))
        compose.setContent {
            DowntifyTheme {
                SongDjStatusDialog(status.value, { status.value = status.value.copy(visible = false) }, {}, {})
            }
        }
        compose.runOnIdle {
            status.value = status.value.copy(message = "Serveur indisponible", preparing = false, canCancel = false)
        }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("Serveur indisponible").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
        compose.onNodeWithText("Annuler").assertDoesNotExist()
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithText("Serveur indisponible").assertDoesNotExist()
    }

    @Test fun openingThePlayerDoesNotCancelOrDismissImplicitly() {
        var opened = 0
        var dismissed = 0
        var cancelled = 0
        compose.setContent {
            DowntifyTheme {
                SongDjStatusDialog(
                    SongDjStatus(visible = true, message = "Mix prêt", canCancel = true, canOpenPlayer = true),
                    onDismiss = { dismissed++ },
                    onCancel = { cancelled++ },
                    onOpenPlayer = { opened++ },
                )
            }
        }
        compose.onNodeWithText("Ouvrir le lecteur").performClick()
        assertEquals(1, opened)
        assertEquals(0, cancelled)
        assertEquals(0, dismissed)
        compose.onNodeWithText("Fermer").assertExists()
    }
}
