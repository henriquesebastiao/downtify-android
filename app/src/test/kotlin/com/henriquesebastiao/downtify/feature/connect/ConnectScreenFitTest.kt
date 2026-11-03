package com.henriquesebastiao.downtify.feature.connect

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.ServerInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The connect screen's steps fit a short phone (360 × 640 dp) without scrolling in every language
 * the app speaks, each with its longest state: a notice, an error under the field, every button.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectScreenFitTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = "http://192.168.1.20:8000"
    private val info = ServerInfo(
        "cf12",
        "nas.local",
        "Downtify",
        "3.8.0",
        1,
        true,
        Capabilities(pairing = true, passwordSignIn = true),
    )

    private fun string(@StringRes id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun everyStepFits() {
        var state by mutableStateOf(ConnectUiState(address = base, searching = false, revoked = true))
        compose.setContent {
            DowntifyTheme {
                ConnectScreen(
                    state = state,
                    onAddressChange = {},
                    onPickDiscovered = {},
                    onConnect = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onSignIn = {},
                    onUsePairingCode = {},
                    onUsePassword = {},
                    onCodeChange = {},
                    onPair = {},
                    onBackToAddress = {},
                    onScan = {},
                )
            }
        }
        assertFits(R.string.connect_action, R.string.connect_revoked, R.string.connect_scan)
        assertWholeAbove(R.string.connect_none_found, R.string.connect_address_label)

        state = ConnectUiState(
            step = ConnectStep.SignIn(base, info),
            username = "maria",
            password = "nope",
            error = ConnectError.WrongPassword,
        )
        assertFits(
            R.string.sign_in_action,
            R.string.sign_in_body,
            R.string.sign_in_error_wrong,
            R.string.pair_other_server,
        )

        state = ConnectUiState(step = ConnectStep.Pair(base, info), code = "K7QM", error = ConnectError.WrongCode)
        assertFits(R.string.pair_action, R.string.pair_body, R.string.sign_in_use_password, R.string.pair_other_server)
    }

    /** Each of [texts] is shown and ends above the footer, whose [next] button sits 16 dp below its top. */
    private fun assertFits(@StringRes next: Int, @StringRes vararg texts: Int) {
        compose.waitForIdle()
        val footerTop = compose.onNodeWithContentDescription(string(next)).getUnclippedBoundsInRoot().top - 16.dp
        for (id in texts) {
            val node = compose.onNodeWithText(string(id)).assertIsDisplayed()
            val bottom = node.getUnclippedBoundsInRoot().bottom
            assertTrue("\"${string(id)}\" ends at $bottom, under the footer at $footerTop", bottom <= footerTop)
            assertNotCut(id)
        }
    }

    /** All of the text's lines are laid out: none given less height than it needs. */
    private fun assertNotCut(@StringRes id: Int) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(string(id)).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action?.invoke(layouts)
        assertFalse("\"${string(id)}\" is cut", layouts.single().didOverflowHeight)
    }

    /** [caption], when there's room to show it at all, ends above [field]: never half hidden by it. */
    private fun assertWholeAbove(@StringRes caption: Int, @StringRes field: Int) {
        val nodes = compose.onAllNodesWithText(string(caption)).fetchSemanticsNodes()
        if (nodes.isEmpty()) return
        val bottom = compose.onNodeWithText(string(caption)).getUnclippedBoundsInRoot().bottom
        val top = compose.onNodeWithText(string(field)).getUnclippedBoundsInRoot().top
        assertTrue("\"${string(caption)}\" ends at $bottom, under the field at $top", bottom <= top)
        assertNotCut(caption)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun english() = everyStepFits()

    @Test
    @Config(qualifiers = "bg-w360dp-h640dp")
    fun bulgarian() = everyStepFits()

    @Test
    @Config(qualifiers = "el-w360dp-h640dp")
    fun greek() = everyStepFits()

    @Test
    @Config(qualifiers = "es-w360dp-h640dp")
    fun spanish() = everyStepFits()

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp")
    fun french() = everyStepFits()

    @Test
    @Config(qualifiers = "hu-w360dp-h640dp")
    fun hungarian() = everyStepFits()

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun brazilianPortuguese() = everyStepFits()

    @Test
    @Config(qualifiers = "tr-w360dp-h640dp")
    fun turkish() = everyStepFits()
}
