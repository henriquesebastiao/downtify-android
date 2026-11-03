package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomBarsInsetTest {
    @get:Rule
    val compose = createComposeRule()

    private val list = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 24.dp)

    private fun padded(covered: Int, direction: LayoutDirection): PaddingValues {
        lateinit var padding: PaddingValues
        compose.setContent {
            CompositionLocalProvider(
                LocalBottomBarsInset provides covered.dp,
                LocalLayoutDirection provides direction,
            ) { padding = list.plusBottomBars() }
        }
        compose.waitForIdle()
        return padding
    }

    @Test
    fun onlyTheBottomGrowsByWhatTheBarsCover() {
        val padding = padded(covered = 120, direction = LayoutDirection.Ltr)
        assertEquals(144.dp, padding.calculateBottomPadding())
        assertEquals(8.dp, padding.calculateTopPadding())
        assertEquals(16.dp, padding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(4.dp, padding.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun rightToLeftKeepsStartAndEnd() {
        val padding = padded(covered = 0, direction = LayoutDirection.Rtl)
        assertEquals(24.dp, padding.calculateBottomPadding())
        assertEquals(16.dp, padding.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(4.dp, padding.calculateEndPadding(LayoutDirection.Rtl))
    }
}
