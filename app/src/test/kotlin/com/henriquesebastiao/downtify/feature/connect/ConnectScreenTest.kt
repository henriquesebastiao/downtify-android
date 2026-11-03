package com.henriquesebastiao.downtify.feature.connect

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A short phone: every step must fit it without scrolling. Native graphics, so text is measured
// as on a device and the fit checks mean something.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = "http://192.168.1.20:8000"

    /** A server from before app sign-in: it only pairs. */
    private val info = ServerInfo("cf12", "nas.local", "Downtify", "3.2.0", 1, true, Capabilities())
    private val signInInfo = info.copy(capabilities = Capabilities(pairing = true, passwordSignIn = true))

    @Suppress("LongParameterList")
    private fun show(
        state: ConnectUiState,
        onAddressChange: (String) -> Unit = {},
        onConnect: () -> Unit = {},
        onUsernameChange: (String) -> Unit = {},
        onSignIn: () -> Unit = {},
        onUsePairingCode: () -> Unit = {},
        onUsePassword: () -> Unit = {},
        onPair: () -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme {
            ConnectScreen(
                state = state,
                onAddressChange = onAddressChange,
                onPickDiscovered = {},
                onConnect = onConnect,
                onUsernameChange = onUsernameChange,
                onPasswordChange = {},
                onSignIn = onSignIn,
                onUsePairingCode = onUsePairingCode,
                onUsePassword = onUsePassword,
                onCodeChange = {},
                onPair = onPair,
                onBackToAddress = {},
                onScan = {},
            )
        }
    }

    @Test
    fun connectIsDisabledUntilAnAddressIsTyped() {
        var typed = ""
        var connected = false
        show(ConnectUiState(searching = false), onAddressChange = { typed = it }, onConnect = { connected = true })
        compose.onNodeWithContentDescription("Connect").assertIsNotEnabled()
        compose.onNodeWithText("Step 1 of 2").assertExists()
        compose.onNodeWithText("Server address").performTextInput("192.168.1.20")
        assertEquals("192.168.1.20", typed)
        assertTrue(!connected)
    }

    @Test
    fun connectCallsBackWhenAnAddressIsPresent() {
        var connected = false
        show(ConnectUiState(address = base, searching = false), onConnect = { connected = true })
        compose.onNodeWithContentDescription("Connect").assertIsEnabled().performClick()
        assertTrue(connected)
    }

    @Test
    fun signInNeedsAUsernameAndAPassword() {
        var typed = ""
        show(ConnectUiState(step = ConnectStep.SignIn(base, signInInfo)), onUsernameChange = { typed = it })
        compose.onNodeWithText("Step 2 of 2").assertExists()
        compose.onNodeWithText("Sign in to nas.local").assertExists()
        compose.onNodeWithContentDescription("Sign in").assertIsNotEnabled()
        compose.onNodeWithText("Username").performTextInput("maria")
        assertEquals("maria", typed)
    }

    @Test
    fun aFilledFormSignsInFromTheFooter() {
        var signedIn = false
        show(
            ConnectUiState(step = ConnectStep.SignIn(base, signInInfo), username = "maria", password = "correct horse"),
            onSignIn = { signedIn = true },
        )
        compose.onNodeWithContentDescription("Sign in").assertIsEnabled().performClick()
        assertTrue(signedIn)
    }

    @Test
    fun aWrongPasswordIsShownAndThePasswordCanBeShown() {
        show(
            ConnectUiState(
                step = ConnectStep.SignIn(base, signInInfo),
                username = "maria",
                password = "nope",
                error = ConnectError.WrongPassword,
            ),
        )
        compose.onNodeWithText("Wrong username or password.").assertExists()
        // Hidden behind dots until asked.
        compose.onNodeWithText("••••").assertExists()
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithText("••••").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun thePairingCodeStaysOneTapAway() {
        var usedCode = false
        show(ConnectUiState(step = ConnectStep.SignIn(base, signInInfo)), onUsePairingCode = { usedCode = true })
        compose.onNodeWithText("Pair with a code").performClick()
        assertTrue(usedCode)
    }

    @Test
    fun pairingLeadsBackToThePasswordWhenTheServerTakesOne() {
        var usedPassword = false
        show(ConnectUiState(step = ConnectStep.Pair(base, signInInfo)), onUsePassword = { usedPassword = true })
        compose.onNodeWithText("Sign in with your password instead").performClick()
        assertTrue(usedPassword)
    }

    @Test
    fun aServerThatOnlyPairsOffersNoPassword() {
        show(ConnectUiState(step = ConnectStep.Pair(base, info)))
        compose.onNodeWithText("Sign in with your password instead").assertDoesNotExist()
    }

    @Test
    fun pairStepShowsTheWrongCodeErrorAndNeedsAFullCode() {
        show(ConnectUiState(step = ConnectStep.Pair(base, info), code = "K7QM", error = ConnectError.WrongCode))
        compose.onNodeWithContentDescription("Pair").assertIsNotEnabled()
        compose.onNodeWithText("Step 2 of 2").assertExists()
        compose.onNodeWithText("Pairing code").assertExists()
    }

    @Test
    fun aFullCodePairsFromTheFooter() {
        var paired = false
        show(ConnectUiState(step = ConnectStep.Pair(base, info), code = "K7QM2XPD"), onPair = { paired = true })
        compose.onNodeWithContentDescription("Pair").assertIsEnabled().performClick()
        assertTrue(paired)
    }

    @Test
    fun whileConnectingTheFooterWaitsInsteadOfConnectingAgain() {
        var connected = 0
        show(ConnectUiState(address = base, searching = false, busy = true), onConnect = { connected++ })
        compose.onNodeWithContentDescription("Connect").assertIsNotEnabled().performClick()
        assertEquals(0, connected)
    }

    /** Fully shown, and above the footer: nothing hides behind it or below the screen. */
    private fun SemanticsNodeInteraction.assertClearOfTheFooter(footerTop: Dp) {
        assertIsDisplayed()
        val bounds = getUnclippedBoundsInRoot()
        assertTrue("$bounds runs into the footer at $footerTop", bounds.bottom <= footerTop)
    }

    /** Where the footer starts: its button, [next], sits 16 dp below its top. */
    private fun footerTop(next: String) =
        compose.onNodeWithContentDescription(next).getUnclippedBoundsInRoot().top - FOOTER_PADDING

    @Test
    fun theSignInStepFitsWithoutScrolling() {
        show(
            ConnectUiState(
                step = ConnectStep.SignIn(base, signInInfo),
                username = "maria",
                password = "nope",
                error = ConnectError.WrongPassword,
            ),
        )
        val footer = footerTop("Sign in")
        compose.onNodeWithText("Sign in to nas.local").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Use your account from the web page.").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Wrong username or password.").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Pair with a code").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Use another server").assertClearOfTheFooter(footer)
        compose.onNodeWithContentDescription("Sign in").assertIsDisplayed()
    }

    @Test
    fun thePairStepFitsWithoutScrolling() {
        show(ConnectUiState(step = ConnectStep.Pair(base, signInInfo), code = "K7QM", error = ConnectError.WrongCode))
        val footer = footerTop("Pair")
        compose.onNodeWithText("Pair with nas.local").assertClearOfTheFooter(footer)
        compose.onNodeWithText("From Settings › Apps on the web page.").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Scan the QR code").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Sign in with your password instead").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Use another server").assertClearOfTheFooter(footer)
    }

    @Test
    fun theAddressStepFitsWithoutScrolling() {
        show(ConnectUiState(address = base, searching = false, revoked = true))
        val footer = footerTop("Connect")
        compose.onNodeWithText("This phone was signed out of the server. Sign in again to keep listening.")
            .assertClearOfTheFooter(footer)
        compose.onNodeWithText("Server address").assertClearOfTheFooter(footer)
        compose.onNodeWithText("Scan a pairing QR code").assertClearOfTheFooter(footer)
    }

    private companion object {
        val FOOTER_PADDING = 16.dp
    }
}
