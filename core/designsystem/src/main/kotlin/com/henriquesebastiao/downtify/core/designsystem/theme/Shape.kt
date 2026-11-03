package com.henriquesebastiao.downtify.core.designsystem.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The Material 3 Expressive corner scale: extraSmall 4 · small 8 · medium 12 · large 16
 * · largeIncreased 20 · extraLarge 28 · extraLargeIncreased 32 · extraExtraLarge 48.
 * Buttons and the search bar are fully rounded.
 */
val DowntifyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** Shapes the M3 scale doesn't name, by what they're for. */
object DowntifyShape {
    /** "large-increased": the FAB-like play button. */
    val Fab = RoundedCornerShape(20.dp)

    /** A section card (a settings group's card, a shortcut). */
    val Section = RoundedCornerShape(24.dp)

    /** The screen's one hero card, and the content panel under a tinted header. */
    val Hero = RoundedCornerShape(32.dp)

    /** Cover art in a list row: a squircle, 22 % of its side. */
    val ArtworkThumb = RoundedCornerShape(percent = 22)

    /** Big cover art: Now Playing, an album's header. */
    val Artwork = RoundedCornerShape(28.dp)

    /**
     * The floating bar's and the mini player's outer corners: the bar's bottom, the mini player's
     * top, and all four of either when it's alone.
     */
    val BarCorner = 28.dp

    /**
     * Where the mini player meets the floating bar right under it: their facing corners (the
     * mini player's bottom, the bar's top), so the two read as one group.
     */
    val JoinedCorner = 10.dp

    /** The floating navigation bar. */
    val FloatingNavBar = RoundedCornerShape(BarCorner)

    /** What's playing in a list of songs: the row rounds into a pill, as in PixelPlayer. */
    val PlayingRow = RoundedCornerShape(percent = 50)

    /** The side menu's sheet: flush with the screen's start edge, extra-large corners at its end. */
    val Drawer = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

    /**
     * Two buttons side by side as a pair (Now Playing's lyrics and queue): round on the outside,
     * 6 dp where they face each other.
     */
    val PairStart = RoundedCornerShape(CornerSize(50), CornerSize(6.dp), CornerSize(6.dp), CornerSize(50))
    val PairEnd = RoundedCornerShape(CornerSize(6.dp), CornerSize(50), CornerSize(50), CornerSize(6.dp))

    /** The footer of a multi-step flow (connect): rounded on top, flush with the screen's bottom. */
    val OnboardingFooter = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
}

/**
 * How a list groups its items: [outer] corners at the ends of the group, [inner] corners between
 * items, and the [gap] between them.
 */
@Immutable
data class ListStyle(val outer: Dp, val inner: Dp, val gap: Dp) {
    /** The corners of the [index]th item of [count]. */
    fun shapeOf(index: Int, count: Int): RoundedCornerShape {
        val top = if (index == 0) outer else inner
        val bottom = if (index == count - 1) outer else inner
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }

    companion object {
        /** Settings and menus: 24 at the ends, 8 between, 4 apart. */
        val Segmented = ListStyle(outer = 24.dp, inner = 8.dp, gap = 4.dp)

        /** Content (songs, files, downloads): every item a 24 dp card, 8 apart. */
        val Separated = ListStyle(outer = 24.dp, inner = 24.dp, gap = 8.dp)

        /** Dense lists: 16 at the ends, 4 between, 2 apart. */
        val Compact = ListStyle(outer = 16.dp, inner = 4.dp, gap = 2.dp)
    }
}

/**
 * A shape [fraction] of the way from [start] to [stop], for corners that move on a spring (a row
 * rounding into a pill, a squircle into a circle). Both should be rounded rectangles; any other
 * outline snaps from one to the other halfway. The spring's overshoot is clamped.
 */
fun morphShape(start: Shape, stop: Shape, fraction: Float): Shape = when {
    fraction <= 0f -> start
    fraction >= 1f -> stop
    else -> MorphedShape(start, stop, fraction)
}

private class MorphedShape(val start: Shape, val stop: Shape, val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val from = start.createOutline(size, layoutDirection, density).roundRect()
        val to = stop.createOutline(size, layoutDirection, density).roundRect()
        if (from == null || to == null) {
            return (if (fraction < HALF) start else stop).createOutline(size, layoutDirection, density)
        }
        return Outline.Rounded(
            RoundRect(
                left = from.left,
                top = from.top,
                right = from.right,
                bottom = from.bottom,
                topLeftCornerRadius = lerp(from.topLeftCornerRadius, to.topLeftCornerRadius, fraction),
                topRightCornerRadius = lerp(from.topRightCornerRadius, to.topRightCornerRadius, fraction),
                bottomRightCornerRadius = lerp(from.bottomRightCornerRadius, to.bottomRightCornerRadius, fraction),
                bottomLeftCornerRadius = lerp(from.bottomLeftCornerRadius, to.bottomLeftCornerRadius, fraction),
            ),
        )
    }

    private fun Outline.roundRect(): RoundRect? = when (this) {
        is Outline.Rounded -> roundRect
        is Outline.Rectangle -> RoundRect(rect)
        is Outline.Generic -> null
    }

    private companion object {
        const val HALF = 0.5f
    }
}

/** Spacing on the 4 dp grid. */
object Spacing {
    /** Between the items of a compact list. */
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Screen margins. */
    val screen = 16.dp
}
