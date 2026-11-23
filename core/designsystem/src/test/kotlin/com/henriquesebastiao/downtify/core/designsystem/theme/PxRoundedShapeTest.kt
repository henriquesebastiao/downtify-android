package com.henriquesebastiao.downtify.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The expanding player builds its layer shape from `radius / scale`, which is 0/0 on a zero-width
 * frame. A rounded shape resolved from NaN throws while the frame draws — the reported crash is
 * `Corner size in Px can't be negative(topStart = NaN, topEnd = NaN, bottomEnd = NaN, bottomStart =
 * NaN)` from `CornerBasedShape.createOutline` — so the guard has to answer with a shape, not throw.
 */
class PxRoundedShapeTest {

    @Test
    fun finiteCornersStayRounded() {
        val shape = pxRoundedShape(topStart = 8f, topEnd = 8f, bottomEnd = 24f, bottomStart = 24f)

        assertEquals(RoundedCornerShape(8f, 8f, 24f, 24f), shape)
    }

    @Test
    fun nanCornerFallsBackToARectangle() {
        val shape = pxRoundedShape(
            topStart = Float.NaN,
            topEnd = Float.NaN,
            bottomEnd = Float.NaN,
            bottomStart = Float.NaN,
        )

        assertSame(RectangleShape, shape)
    }

    @Test
    fun infiniteCornerFallsBackToARectangle() {
        val shape = pxRoundedShape(
            topStart = Float.POSITIVE_INFINITY,
            topEnd = 8f,
            bottomEnd = 8f,
            bottomStart = 8f,
        )

        assertSame(RectangleShape, shape)
    }

    @Test
    fun negativeCornerFallsBackToARectangle() {
        val shape = pxRoundedShape(topStart = 8f, topEnd = 8f, bottomEnd = -1f, bottomStart = 8f)

        assertSame(RectangleShape, shape)
    }

    @Test
    fun zeroCornersAreStillARoundedShape() {
        assertEquals(RoundedCornerShape(0f, 0f, 0f, 0f), pxRoundedShape(0f, 0f, 0f, 0f))
    }
}
