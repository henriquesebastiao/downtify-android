package com.henriquesebastiao.downtify.ui.navigation

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class FloatingNavBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsEveryTabAndReportsTheOneTapped() {
        var tapped: TopLevelDestination? = null
        compose.setContent {
            DowntifyTheme { FloatingNavBar(selected = TopLevelDestination.Home, onSelect = { tapped = it }) }
        }
        compose.onNodeWithText("Home").assertIsSelected()
        listOf("Search", "Library").forEach { compose.onNodeWithText(it).assertIsNotSelected() }
        // Downloads lives in the side menu now.
        compose.onNodeWithText("Downloads").assertDoesNotExist()
        compose.onNodeWithText("Library").performClick()
        assertEquals(TopLevelDestination.Library, tapped)
    }
}
