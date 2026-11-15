package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class WeeklyDjCopyFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun standaloneFormSavesOnlyTheExplicitName() = checkForm(inDialog = false)

    @Test fun dialogFormSavesOnlyTheExplicitName() = checkForm(inDialog = true)

    private fun checkForm(inDialog: Boolean) {
        val session = MusicDjSessionDto("a".repeat(32), state = "ready", theme = "rock")
        var saved = ""
        compose.setContent {
            DowntifyTheme {
                var expanded by remember { mutableStateOf(false) }
                var name by remember { mutableStateOf("Set DJ") }
                val form: @androidx.compose.runtime.Composable () -> Unit = {
                    Column {
                        CopySetForm(session, name, expanded, false, { expanded = it }, { name = it }) { value, title ->
                            saved = "${value.id}:$title"
                        }
                    }
                }
                if (inDialog) {
                    AlertDialog(
                        onDismissRequest = {},
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                        title = { Text("Copie du set") },
                        text = form,
                        confirmButton = { TextButton(onClick = {}) { Text("Fermer") } },
                    )
                } else {
                    form()
                }
            }
        }
        assertEquals("", saved)
        compose.onNodeWithText("Enregistrer une copie").performClick()
        assertEquals("", saved)
        compose.onNodeWithText("Nom de la copie").performTextReplacement("Mon rock préféré")
        assertEquals("", saved)
        compose.onNodeWithText("Conserver l’audio").performClick()
        assertEquals("${session.id}:Mon rock préféré", saved)
    }
}
