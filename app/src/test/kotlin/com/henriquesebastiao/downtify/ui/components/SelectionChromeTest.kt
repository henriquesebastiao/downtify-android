package com.henriquesebastiao.downtify.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.SelectionBottomBar
import com.henriquesebastiao.downtify.core.designsystem.component.SelectionCheckbox
import com.henriquesebastiao.downtify.core.designsystem.component.SelectionTopBar
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class SelectionChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theTopBarSaysHowManyAndClosesOrPicksAll() {
        var closed = 0
        var toggled = 0
        compose.setContent {
            DowntifyTheme {
                SelectionTopBar(
                    title = "2 selected",
                    closeLabel = "Close selection",
                    selectAllLabel = "Select all",
                    clearLabel = "Clear selection",
                    allSelected = false,
                    onClose = { closed++ },
                    onToggleAll = { toggled++ },
                )
            }
        }
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithContentDescription("Select all").performClick()
        compose.onNodeWithContentDescription("Close selection").performClick()

        assertEquals(listOf(1, 1), listOf(toggled, closed))
    }

    @Test
    fun withEverythingPickedTheTopBarClears() {
        compose.setContent {
            DowntifyTheme {
                SelectionTopBar(
                    title = "3 selected",
                    closeLabel = "Close selection",
                    selectAllLabel = "Select all",
                    clearLabel = "Clear selection",
                    allSelected = true,
                    onClose = {},
                    onToggleAll = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Clear selection").assertExists()
    }

    @Test
    fun theBottomBarRunsOneAction() {
        var acted = 0
        compose.setContent {
            DowntifyTheme {
                SelectionBottomBar(
                    actionLabel = "Add 2 songs to playlist",
                    icon = DowntifyIcons.AddToQueue,
                    onClick = { acted++ },
                )
            }
        }
        compose.onNodeWithText("Add 2 songs to playlist").performClick()

        assertEquals(1, acted)
    }

    @Test
    fun aDisabledBottomBarDoesNothing() {
        var acted = 0
        compose.setContent {
            DowntifyTheme {
                SelectionBottomBar(
                    actionLabel = "Add 2 songs to playlist",
                    icon = DowntifyIcons.AddToQueue,
                    onClick = { acted++ },
                    enabled = false,
                )
            }
        }
        compose.onNodeWithText("Add 2 songs to playlist").performClick()

        assertEquals(0, acted)
    }

    @Test
    fun theCheckHasNoTextOfItsOwn() {
        compose.setContent { DowntifyTheme { SelectionCheckbox(selected = true) } }
        // Nothing to read out: the row says what it is picked for.
        compose.onNodeWithContentDescription("Add 2 songs to playlist").assertDoesNotExist()
    }
}
