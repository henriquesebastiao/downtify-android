package com.henriquesebastiao.downtify.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SleepTimerStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countdownIsReadableAndCanBeCancelled() {
        var cancelled = false
        val deadline = System.currentTimeMillis() + 20 * 60_000
        compose.setContent { DowntifyTheme { SleepTimerStatus(deadline) { cancelled = true } } }
        compose.onNodeWithText("Minuterie").assertIsDisplayed()
        compose.onNodeWithText("Arrêt dans", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Annuler").performClick()
        assertTrue(cancelled)
    }

    @Test fun countdownRoundsUpAndNeverBecomesNegative() {
        assertEquals("20:00", sleepRemainingLabel(1_200_000))
        assertEquals("1:01", sleepRemainingLabel(60_001))
        assertEquals("0:01", sleepRemainingLabel(1))
        assertEquals("0:00", sleepRemainingLabel(-1))
    }
}
