package com.henriquesebastiao.downtify.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.component.focusTrap
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.ui.navigation.AppDrawer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A TV remote's focus: buttons only take it out of touch mode (as a D-pad press puts a TV), so
 * each test switches to keyboard mode first.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class RemoteFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun RemoteMode(content: @Composable () -> Unit) {
        val inputMode = LocalInputModeManager.current
        remember { inputMode.requestInputMode(InputMode.Keyboard) }
        content()
    }

    private fun showOverlay(trapped: Boolean) {
        compose.setContent {
            DowntifyTheme {
                RemoteMode {
                    val overlay = remember { FocusRequester() }
                    LaunchedEffect(Unit) { overlay.requestFocusWhenPlaced() }
                    Column {
                        Button(onClick = {}) { Text("Under") }
                        Column(Modifier.focusRequester(overlay).focusTrap(trapped)) {
                            Button(onClick = {}) { Text("First") }
                            Button(onClick = {}) { Text("Second") }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun anOverlayTakesFocusOnItsFirstButton() {
        showOverlay(trapped = true)
        compose.onNodeWithText("First").assertIsFocused()
    }

    @Test
    fun aTrapKeepsFocusFromWhatIsUnderIt() {
        showOverlay(trapped = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("First").assertIsFocused()
        compose.onNodeWithText("Under").assertIsNotFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Second").assertIsFocused()
    }

    @Test
    fun withoutTheTrapFocusLeaves() {
        showOverlay(trapped = false)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Under").assertIsFocused()
    }

    @Test
    fun theOpenSideMenuTakesFocus() {
        compose.setContent {
            DowntifyTheme {
                RemoteMode {
                    AppDrawer(rememberDrawerState(DrawerValue.Open), selected = null, onSelect = {}) {
                        Button(onClick = {}) { Text("Home page") }
                    }
                }
            }
        }
        compose.onNodeWithText("Downloads").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Home page").assertIsNotFocused()
    }

    @Test
    fun theClosedSideMenuIsOutOfTheRemotesReach() {
        compose.setContent {
            DowntifyTheme {
                RemoteMode {
                    AppDrawer(rememberDrawerState(DrawerValue.Closed), selected = null, onSelect = {}) {
                        Button(onClick = {}) { Text("Home page") }
                    }
                }
            }
        }
        // Laid out off screen to the left: left from the content must not land in it.
        compose.onNodeWithText("Home page").requestFocus().assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Home page").assertIsFocused()
        compose.onNodeWithText("Downloads").assertIsNotFocused()
    }
}
