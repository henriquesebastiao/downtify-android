package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvExitDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun itAsksAndEachButtonDoesItsPart() {
        var stayed = 0
        var closed = 0
        compose.setContent {
            DowntifyTheme(darkTheme = true) { TvExitContent(onStay = { stayed++ }, onClose = { closed++ }) }
        }

        compose.onNodeWithText("Close Downtify?").assertExists()
        compose.onNodeWithText("The music stops when it closes.").assertExists()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Close Downtify").performClick()

        assertEquals(1, stayed)
        assertEquals(1, closed)
    }
}
