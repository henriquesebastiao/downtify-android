package com.henriquesebastiao.downtify.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.component.PillTabs
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class PillTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf("Albums", "Artists", "Playlists", "Songs")

    @Test
    fun tappingATabSelectsIt() {
        var picked = -1
        compose.setContent {
            DowntifyTheme {
                var selected by remember { mutableIntStateOf(0) }
                PillTabs(tabs, selected, onSelect = {
                    picked = it
                    selected = it
                })
            }
        }
        compose.onNodeWithText("Albums").assertIsSelected()
        compose.onNodeWithText("Songs").assertIsNotSelected().performClick()
        assertEquals(3, picked)
        compose.onNodeWithText("Songs").assertIsSelected()
        compose.onNodeWithText("Albums").assertIsNotSelected()
    }

    @Test
    fun aScreenReaderHearsTabsInTheirOwnCase() {
        compose.setContent { DowntifyTheme { PillTabs(tabs, 1, onSelect = {}) } }
        // Drawn in capitals, but read as the word itself, and as a tab rather than a checkbox.
        compose.onNodeWithText("Artists")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }
}
