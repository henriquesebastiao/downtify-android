package com.henriquesebastiao.downtify.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.component.FocusRingWidth

/*
 * How the TV interface shows the remote's focus, after the TV music apps: a ring in the content
 * color around the focused element, a small gap away from it, along its shape (round on an
 * artist, rounded on a cover or a pill). No tint over the content.
 */

/** Between the ring and what it surrounds. */
internal val TvRingGap = 5.dp

/** The space a [tvFocusFrame] takes on each side, ring and gap. */
internal val TvRingSpace = TvRingGap + FocusRingWidth

/** A cover's corners on the TV, and those of the ring around it ([TvRingSpace] further out). */
internal val TvCoverShape = RoundedCornerShape(12.dp)
internal val TvCoverRingShape = RoundedCornerShape(12.dp + TvRingSpace)

/**
 * Leaves [TvRingSpace] around the content and, while [focused], draws the ring along [ringShape]
 * at the outer edge. The space is kept when not focused, so focus never shifts the layout. Put it
 * before the padding or the background of what it rings.
 */
@Composable
internal fun Modifier.tvFocusFrame(focused: Boolean, ringShape: Shape): Modifier {
    val color = if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent
    return this.border(FocusRingWidth, color, ringShape).padding(TvRingSpace)
}

/**
 * Scrolls a TV grid so the focused row lands at its top, [top] down (the grid's top padding), as
 * the TV music apps do: the next rows show below it, nothing focused is ever cut off at the bottom
 * and nothing of the row above peeks in. An item taller than the viewport goes to its top.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberTvGridBringIntoViewSpec(top: Dp): BringIntoViewSpec {
    val topPx = with(LocalDensity.current) { top.toPx() }
    return remember(topPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (size + topPx >= containerSize) offset else offset - topPx
        }
    }
}
