package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.ScrollMetrics
import com.henriquesebastiao.downtify.core.model.ScrollTarget
import com.henriquesebastiao.downtify.core.model.ScrollThumb
import kotlinx.coroutines.launch

/**
 * A scrollbar for a lazy list, after PixelPlayer's: a primary pill (the thumb) on a tonal track
 * that parts around it, as Material 3 Expressive's progress indicators do. Dragging it or tapping
 * the track jumps there, and the thumb widens under the finger. It shows only while the content
 * scrolls. It's [ScrollbarTouchWidth] wide with the bar in its middle: give it the list's end
 * padding, as tall as the list.
 */
@Composable
fun VerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val source = remember(state) { ListSource(state) }
    VerticalScrollbar(source, visible = state.canScrollForward || state.canScrollBackward, modifier)
}

/** [VerticalScrollbar] for a lazy grid: it moves a row at a time. */
@Composable
fun VerticalScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    val source = remember(state) { GridSource(state) }
    VerticalScrollbar(source, visible = state.canScrollForward || state.canScrollBackward, modifier)
}

/** How wide the scrollbar's touch area is: the room to leave for it at the list's end. */
val ScrollbarTouchWidth = 24.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VerticalScrollbar(source: ScrollSource, visible: Boolean, modifier: Modifier) {
    if (!visible) return
    val scheme = MaterialTheme.colorScheme
    val thumbColor = scheme.primary
    val trackColor = scheme.secondaryContainer
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val thumbWidth by animateDpAsState(
        if (dragging) THUMB_WIDTH_DRAGGED else BAR_WIDTH,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "scrollbarThumbWidth",
    )

    fun jump(y: Float, trackLength: Float, minThumb: Float) {
        val metrics = source.metrics() ?: return
        val thumb = ScrollThumb.of(trackLength, metrics.visibleShare, metrics.progress, minThumb)
        val target = metrics.target(ScrollThumb.progressAt(y, trackLength, thumb.length))
        // A newer jump cancels the one still running: the content follows the finger.
        scope.launch { source.scrollTo(target) }
    }

    Box(
        modifier
            .fillMaxHeight()
            .width(ScrollbarTouchWidth)
            .pointerInput(source) {
                detectTapGestures { jump(it.y, size.height.toFloat(), MIN_THUMB.toPx()) }
            }
            .pointerInput(source) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragging = true
                        jump(it.y, size.height.toFloat(), MIN_THUMB.toPx())
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    jump(change.position.y, size.height.toFloat(), MIN_THUMB.toPx())
                }
            }
            // Drawn from the list's state in the draw phase: scrolling redraws it, never recomposes it.
            .drawBehind {
                val metrics = source.metrics() ?: return@drawBehind
                val thumb = ScrollThumb.of(size.height, metrics.visibleShare, metrics.progress, MIN_THUMB.toPx())
                val gap = GAP.toPx()
                pill(0f, thumb.top - gap, BAR_WIDTH.toPx(), trackColor)
                pill(thumb.top + thumb.length + gap, size.height, BAR_WIDTH.toPx(), trackColor)
                pill(thumb.top, thumb.top + thumb.length, thumbWidth.toPx(), thumbColor)
            },
    )
}

/** A vertical pill from [top] to [bottom], centered; none when there's no room for its round ends. */
private fun DrawScope.pill(top: Float, bottom: Float, width: Float, color: Color) {
    if (bottom - top < width) return
    drawRoundRect(
        color = color,
        topLeft = Offset((size.width - width) / 2, top),
        size = Size(width, bottom - top),
        cornerRadius = CornerRadius(width / 2),
    )
}

/** What the scrollbar reads from a lazy layout and how it moves it. */
private interface ScrollSource {
    /** Where the content stands; null before anything is laid out. */
    fun metrics(): ScrollMetrics?

    suspend fun scrollTo(target: ScrollTarget)
}

private class ListSource(private val state: LazyListState) : ScrollSource {
    override fun metrics(): ScrollMetrics? {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return null
        val first = visible.first()
        return ScrollMetrics(
            firstLine = first.index,
            firstLineOffset = first.offset,
            lineSize = visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing,
            lineCount = info.totalItemsCount,
            spacing = info.mainAxisItemSpacing,
            before = info.beforeContentPadding,
            after = info.afterContentPadding,
            viewport = info.viewportSize.height,
        )
    }

    override suspend fun scrollTo(target: ScrollTarget) = state.scrollToItem(target.line, target.offset)
}

private class GridSource(private val state: LazyGridState) : ScrollSource {
    /** Items per row: as many as share the first visible one's row. */
    private val columns: Int
        get() {
            val visible = state.layoutInfo.visibleItemsInfo
            val firstRow = visible.firstOrNull()?.row ?: return 1
            return visible.count { it.row == firstRow }.coerceAtLeast(1)
        }

    override fun metrics(): ScrollMetrics? {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return null
        val first = visible.first()
        val rows = visible.groupBy { it.row }.values
        val columns = columns
        return ScrollMetrics(
            firstLine = first.row,
            firstLineOffset = first.offset.y,
            lineSize = rows.sumOf { row -> row.maxOf { it.size.height } }.toFloat() / rows.size +
                info.mainAxisItemSpacing,
            lineCount = (info.totalItemsCount + columns - 1) / columns,
            spacing = info.mainAxisItemSpacing,
            before = info.beforeContentPadding,
            after = info.afterContentPadding,
            viewport = info.viewportSize.height,
        )
    }

    override suspend fun scrollTo(target: ScrollTarget) = state.scrollToItem(target.line * columns, target.offset)
}

private val BAR_WIDTH = 8.dp
private val THUMB_WIDTH_DRAGGED = 12.dp
private val MIN_THUMB = 48.dp
private val GAP = 4.dp

@PhonePreviews
@Composable
private fun VerticalScrollbarPreview() {
    DowntifyTheme {
        Surface {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = 12)
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(start = Spacing.screen, end = ScrollbarTouchWidth),
                ) {
                    items(40) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = DowntifyShape.Section,
                            modifier = Modifier.fillMaxWidth().height(72.dp).padding(vertical = Spacing.xs),
                        ) {}
                    }
                }
                VerticalScrollbar(state, Modifier.align(Alignment.TopEnd).padding(vertical = Spacing.sm))
            }
        }
    }
}
