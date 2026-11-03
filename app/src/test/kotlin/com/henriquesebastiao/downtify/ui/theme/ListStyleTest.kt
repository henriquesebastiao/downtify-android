package com.henriquesebastiao.downtify.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ListStyleTest {
    private val style = ListStyle.Segmented

    @Test
    fun aLoneItemIsRoundedAllAround() {
        assertEquals(RoundedCornerShape(24.dp), style.shapeOf(0, 1))
    }

    @Test
    fun theEndsOfAGroupTakeTheOuterCornersAndTheRestTheInnerOnes() {
        assertEquals(corners(top = 24, bottom = 8), style.shapeOf(0, 3))
        assertEquals(corners(top = 8, bottom = 8), style.shapeOf(1, 3))
        assertEquals(corners(top = 8, bottom = 24), style.shapeOf(2, 3))
    }

    @Test
    fun separatedItemsAreAllTheSameCard() {
        val separated = ListStyle.Separated
        (0 until 3).forEach { assertEquals(RoundedCornerShape(24.dp), separated.shapeOf(it, 3)) }
    }

    @Test
    fun theThreeStylesKeepTheirCornersAndGaps() {
        assertEquals(ListStyle(24.dp, 8.dp, 4.dp), ListStyle.Segmented)
        assertEquals(ListStyle(24.dp, 24.dp, 8.dp), ListStyle.Separated)
        assertEquals(ListStyle(16.dp, 4.dp, 2.dp), ListStyle.Compact)
    }

    private fun corners(top: Int, bottom: Int) =
        RoundedCornerShape(topStart = top.dp, topEnd = top.dp, bottomStart = bottom.dp, bottomEnd = bottom.dp)
}
