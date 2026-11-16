package com.henriquesebastiao.downtify.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.feature.settings.SettingsActions
import com.henriquesebastiao.downtify.feature.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = SettingsUiState(
        serverName = "nas.local",
        username = "admin",
        address = "192.168.1.20:8000",
        version = "3.2.0",
        connection = ConnectionState.Connected,
    )

    /** As with a remote: rows can take focus. */
    @Composable
    private fun RemoteMode(content: @Composable () -> Unit) {
        val inputMode = LocalInputModeManager.current
        remember { inputMode.requestInputMode(InputMode.Keyboard) }
        content()
    }

    private fun show(actions: SettingsActions = SettingsActions()) = compose.setContent {
        DowntifyTheme(darkTheme = true) { RemoteMode { TvSettingsScreen(state, actions) } }
    }

    @Test
    fun itOpensOnTheServerWithTheFloatingBarNowhere() {
        show()

        compose.onNodeWithText("nas.local").assertExists()
        compose.onNodeWithText("Server").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText("Font").assertExists()
        compose.onNodeWithText("Floating navigation bar").assertDoesNotExist()
        compose.onNodeWithText("Streaming quality on mobile data").assertDoesNotExist()
    }

    @Test
    fun focusOnASectionShowsItsOptionsAndRightEntersThemAtTheFirst() {
        show()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText("Playback").assertIsFocused()
        compose.onNodeWithText("Normalize volume").assertExists()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithText("Theme").assertIsFocused()
    }

    @Test
    fun downPastTheLastOptionStaysAndLeftGoesBackToItsSection() {
        show()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()

        // Further down than there are options (how many depends on the Android version).
        repeat(8) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        // Still in Appearance's options: the sections below it weren't reached.
        compose.onNodeWithText("Theme").assertExists()
        compose.onNodeWithText("About").assertIsNotFocused()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").assertIsFocused()
    }

    @Test
    fun aChoiceOpensItsListAndPickingSavesIt() {
        val fonts = mutableListOf<AppFont>()
        show(SettingsActions(onFont = { fonts += it }))

        // To Appearance with the remote, as on a TV.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText("Font").performClick()
        compose.onNodeWithText("System font").performClick()

        assertEquals(listOf(AppFont.System), fonts)
        compose.onNodeWithText("System font").assertDoesNotExist()
    }

    @Test
    fun signingOutAsksFirst() {
        var unpaired = 0
        show(SettingsActions(onUnpair = { unpaired++ }))

        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Sign out of this server?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, unpaired)

        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Sign out of this server?").assertExists()
        // The confirm button repeats the row's label: the dialog's is the last one.
        compose.onAllNodesWithText("Sign out").onLast().performClick()
        assertEquals(1, unpaired)
    }
}
