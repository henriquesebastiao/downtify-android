package com.henriquesebastiao.downtify.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.AppFont
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp", sdk = [34])
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: SettingsUiState = SettingsUiState(), actions: SettingsActions = SettingsActions()) {
        compose.setContent { DowntifyTheme { SettingsScreen(state, actions) } }
    }

    @Test
    fun groupsTheSettings() {
        show()
        listOf("Playback", "Downloads", "Appearance", "About", "Server").forEach {
            compose.onNodeWithText(it).performScrollTo().assertExists()
        }
    }

    @Test
    fun pureBlackIsASwitch() {
        var value: Boolean? = null
        show(actions = SettingsActions(onAmoled = { value = it }))
        compose.onNodeWithText("Pure black").performScrollTo().performClick()
        assertEquals(true, value)
    }

    @Test
    fun theFontDialogOffersThreeFontsAndReportsTheChoice() {
        var font: AppFont? = null
        show(
            state = SettingsUiState(settings = UserSettings(font = AppFont.System)),
            actions = SettingsActions(onFont = { font = it }),
        )
        // The row shows the current choice.
        compose.onNodeWithText("System font").performScrollTo().assertExists()
        compose.onNodeWithText("Font").performScrollTo().performClick()
        compose.onNodeWithText("Downtify").assertExists()
        compose.onNodeWithText("Google Sans Flex").performClick()
        assertEquals(AppFont.GoogleSansFlex, font)
    }

    @Test
    fun theAboutRowOpensTheAboutScreen() {
        var opened = false
        show(actions = SettingsActions(onOpenAbout = { opened = true }))
        compose.onNodeWithText("About Downtify").performScrollTo().performClick()
        assertEquals(true, opened)
    }

    @Test
    fun unpairingAsksFirst() {
        var unpaired = false
        show(actions = SettingsActions(onUnpair = { unpaired = true }))
        compose.onNodeWithText("Unpair this phone").performScrollTo().performClick()
        assertEquals(false, unpaired)
        compose.onNodeWithText("Unpair this phone?").assertExists()
        compose.onNodeWithText("Unpair").performClick()
        assertEquals(true, unpaired)
    }

    @Test
    fun theLanguageRowOpensAndroidsAppLanguageSettings() {
        var opened = false
        show(actions = SettingsActions(onOpenLanguage = { opened = true }))
        compose.onNodeWithText("Language").performScrollTo().performClick()
        assertEquals(true, opened)
    }

    @Test
    fun readAheadOffersOffAndThreeCounts() {
        var count: Int? = null
        show(actions = SettingsActions(onReadAhead = { count = it }))
        compose.onNodeWithText("Preload upcoming songs").performScrollTo().performClick()
        compose.onNodeWithText("Songs ahead: 1").assertExists()
        compose.onNodeWithText("Songs ahead: 2").assertExists()
        compose.onNodeWithText("Songs ahead: 3").performClick()
        assertEquals(3, count)
    }

    @Test
    fun cancellingCacheLimitDoesNotPersistAnything() {
        var limit: Int? = null
        show(actions = SettingsActions(onStreamCacheMb = { limit = it }))
        compose.onNodeWithText("Temporary audio cache").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, limit)
    }
}
