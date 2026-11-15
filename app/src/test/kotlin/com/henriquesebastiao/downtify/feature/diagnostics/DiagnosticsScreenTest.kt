package com.henriquesebastiao.downtify.feature.diagnostics

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h914dp", sdk = [34])
class DiagnosticsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `checks are manual and there is no report to export before running`() {
        var runs = 0
        var exports = 0
        compose.setContent {
            DowntifyTheme {
                DiagnosticsScreen(DiagnosticsUiState(), { runs++ }, { exports++ }, {})
            }
        }
        assertEquals(0, runs)
        compose.onNodeWithText("Export diagnostic report").assertIsNotEnabled()
        compose.onNodeWithText("Run quick checks").performClick()
        assertEquals(1, runs)
        assertEquals(0, exports)
    }
}
