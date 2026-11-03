package com.henriquesebastiao.downtify.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
class AppDrawerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        initial: DrawerValue,
        selected: DrawerDestination? = null,
        onSelect: (DrawerDestination) -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme {
                AppDrawer(rememberDrawerState(initial), selected = selected, onSelect = onSelect) { Text("Home page") }
            }
        }
    }

    @Test
    fun closedItStaysOutOfTheWay() {
        show(DrawerValue.Closed)
        compose.onNodeWithText("Home page").assertIsDisplayed()
        compose.onNodeWithText("Downloads").assertIsNotDisplayed()
    }

    @Test
    fun openItListsDownloadsAndReportsTheTap() {
        var tapped: DrawerDestination? = null
        show(DrawerValue.Open, onSelect = { tapped = it })
        compose.onNodeWithText("Downtify").assertIsDisplayed()
        compose.onNodeWithText("Downloads").assertIsDisplayed().performClick()
        assertEquals(DrawerDestination.Downloads, tapped)
    }

    @Test
    fun theOpenDestinationIsSelected() {
        show(DrawerValue.Open, selected = DrawerDestination.Downloads)
        compose.onNodeWithText("Downloads").assertIsSelected()
    }
}
