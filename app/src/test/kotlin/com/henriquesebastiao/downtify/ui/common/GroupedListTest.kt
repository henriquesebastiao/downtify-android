package com.henriquesebastiao.downtify.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class GroupedListTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aClickRowRunsItsAction() {
        var clicks = 0
        compose.setContent {
            DowntifyTheme {
                GroupedList(listOf(GroupedRow("Theme", RowAction.Click({ clicks++ }), "Follow system")))
            }
        }
        compose.onNodeWithText("Follow system").assertExists()
        compose.onNodeWithText("Theme").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun aToggleRowFlipsFromAnywhereOnTheRow() {
        var checked = false
        compose.setContent {
            DowntifyTheme {
                GroupedList(listOf(GroupedRow("Pure black", RowAction.Toggle(checked) { checked = it })))
            }
        }
        compose.onNodeWithText("Pure black").performClick()
        assertEquals(true, checked)
    }

    @Test
    fun aDisabledRowDoesNothing() {
        var clicks = 0
        compose.setContent {
            DowntifyTheme {
                GroupedList(listOf(GroupedRow("Mobile", RowAction.Click({ clicks++ }), enabled = false)))
            }
        }
        compose.onNodeWithText("Mobile").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun everyRowOfAGroupIsShown() {
        compose.setContent {
            DowntifyTheme {
                GroupedList(
                    listOf(
                        GroupedRow("One", RowAction.Click({})),
                        GroupedRow("Two", RowAction.Click({})),
                        GroupedRow("Three", RowAction.Click({})),
                    ),
                )
            }
        }
        listOf("One", "Two", "Three").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun eachStyleKeepsItsGapBetweenRows() {
        val styles = listOf(ListStyle.Segmented, ListStyle.Separated, ListStyle.Compact)
        var style by mutableStateOf(styles.first())
        compose.setContent {
            DowntifyTheme {
                GroupedList(
                    listOf(GroupedRow("One", RowAction.Click({})), GroupedRow("Two", RowAction.Click({}))),
                    style = style,
                )
            }
        }
        styles.forEach { current ->
            style = current
            compose.waitForIdle()
            val rows = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            val gap = with(compose.density) { (rows[1].top - rows[0].bottom).toDp() }
            assertEquals(current.gap.value, gap.value, 0.5f)
        }
    }
}
