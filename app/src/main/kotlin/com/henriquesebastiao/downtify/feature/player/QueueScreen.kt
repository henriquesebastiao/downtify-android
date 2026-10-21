package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.CoverColors
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.component.rememberCoverColors
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.PlayQueue
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.SwipeToRemove
import kotlinx.coroutines.delay

/** What the queue can ask for, besides the playback controls. */
data class QueueActions(
    val onClose: () -> Unit = {},
    val onSkipTo: (Int) -> Unit = {},
    val onRemove: (Int) -> Unit = {},
    val onClearQueued: () -> Unit = {},
    /** Play the item at `from` right after the item at `after` (player indices); `queued` = "Next in queue". */
    val onMove: (from: Int, after: Int, queued: Boolean) -> Unit = { _, _, _ -> },
)

/**
 * The queue, as Spotify shows it: what's playing, the songs the user queued
 * ("Next in queue"), then the rest of the album or playlist. Tap a row to play
 * it; remove it with its ✕ (or a swipe left); drag its handle to reorder.
 * The playback controls stay at the bottom.
 */
@Composable
fun QueueScreen(
    state: PlayerState,
    actions: QueueActions,
    controls: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    val now = nowPlayingRow(state) ?: return
    val colors = rememberCoverColors(now.artworkUrl)
    val listState = rememberLazyListState()
    val reorder = rememberQueueReorder(state.upNext, state.currentIndex, listState, actions.onMove)
    val entries = reorder.entries
    val queued = entries.takeWhile { it.queued }
    val rest = entries.drop(queued.size)

    Surface(color = colors.surface, contentColor = colors.onSurface, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(state.context, colors, actions.onClose)
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                item(key = "now-header") { QueueHeader(stringResource(R.string.player_queue_now_playing)) }
                item(key = "now") {
                    QueueRow(
                        title = now.title,
                        artist = now.artist,
                        artworkUrl = now.artworkUrl,
                        colors = colors,
                        current = true,
                        isPlaying = state.isPlaying,
                        onClick = controls.onTogglePlay,
                    )
                }
                if (queued.isNotEmpty()) {
                    item(key = "queued-header") {
                        QueueHeader(
                            stringResource(R.string.player_queue_next_in_queue),
                            actionLabel = stringResource(R.string.player_queue_clear),
                            onAction = actions.onClearQueued,
                            colors = colors,
                        )
                    }
                    entries(queued, colors, actions, reorder)
                }
                if (rest.isNotEmpty()) {
                    item(key = "rest-header") { QueueHeader(nextFromLabel(state.context)) }
                    entries(rest, colors, actions, reorder)
                }
                if (entries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.player_queue_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                        )
                    }
                }
            }
            Box(Modifier.padding(horizontal = Spacing.lg)) { Controls(state, colors, controls) }
        }
    }
}

private fun LazyListScope.entries(
    entries: List<QueueEntry>,
    colors: CoverColors,
    actions: QueueActions,
    reorder: QueueReorder,
) {
    items(entries, key = ::entryKey) { entry ->
        val key = entryKey(entry)
        val dragging = reorder.draggingKey == key
        val moveUp = stringResource(R.string.player_queue_move_up)
        val moveDown = stringResource(R.string.player_queue_move_down)
        SwipeToRemove(
            onRemove = { actions.onRemove(entry.index) },
            modifier = reorderableModifier(dragging, reorder)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(moveUp) { reorder.step(key, -1) },
                        CustomAccessibilityAction(moveDown) { reorder.step(key, 1) },
                    )
                },
        ) {
            QueueRow(
                title = entry.title,
                artist = entry.artist,
                artworkUrl = artworkOf(entry),
                colors = colors,
                current = false,
                isPlaying = false,
                onClick = { actions.onSkipTo(entry.index) },
                containerColor = colors.surface,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { actions.onRemove(entry.index) }) {
                            Icon(
                                painterResource(DowntifyIcons.Close),
                                contentDescription = stringResource(R.string.player_queue_remove),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                        DragHandle(key, colors, reorder)
                    }
                },
            )
        }
    }
}

/** The dragged row floats over the others, which slide out of its way. */
private fun LazyItemScope.reorderableModifier(dragging: Boolean, reorder: QueueReorder): Modifier = if (dragging) {
    Modifier.zIndex(1f).graphicsLayer { translationY = reorder.dragOffset }.shadow(8.dp)
} else {
    Modifier.animateItem()
}

@Composable
private fun DragHandle(key: String, colors: CoverColors, reorder: QueueReorder) {
    Box(
        Modifier
            .size(48.dp)
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { reorder.start(key) },
                    onDragEnd = { reorder.end() },
                    onDragCancel = { reorder.end() },
                    onDrag = { change, amount ->
                        change.consume()
                        reorder.dragBy(amount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(DowntifyIcons.DragHandle),
            contentDescription = stringResource(R.string.player_queue_reorder),
            tint = colors.onSurfaceVariant,
        )
    }
}

private fun entryKey(entry: QueueEntry): String = "entry:${entry.index}:${entry.trackId}"

/**
 * Drag-to-reorder over the queue: a local copy of the rows follows the finger,
 * and the move goes to the player once, when the finger lifts. The copy starts
 * over whenever the player's queue changes.
 */
@Stable
private class QueueReorder(
    initial: List<QueueEntry>,
    private val currentIndex: () -> Int,
    private val listState: LazyListState,
    private val onMove: () -> (Int, Int, Boolean) -> Unit,
) {
    var entries by mutableStateOf(initial)
        private set
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var original: List<QueueEntry> = initial

    fun reset(upNext: List<QueueEntry>) {
        if (draggingKey != null) return
        entries = upNext
        original = upNext
    }

    fun start(key: String) {
        draggingKey = key
        dragOffset = 0f
    }

    fun dragBy(dy: Float) {
        dragOffset += dy
        swapIfOverAnother()
    }

    /** While dragging near the top or bottom edge: how far to scroll the list this frame. */
    fun edgeScroll(): Float {
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == draggingKey } ?: return 0f
        val top = dragged.offset + dragOffset
        val bottom = top + dragged.size
        return when {
            bottom > layout.viewportEndOffset - EDGE_PX -> EDGE_SPEED_PX
            top < layout.viewportStartOffset + EDGE_PX -> -EDGE_SPEED_PX
            else -> 0f
        }
    }

    /** The list scrolled by [by] under a still finger: keep the row under it. */
    fun scrolled(by: Float) {
        dragOffset += by
        swapIfOverAnother()
    }

    fun end() {
        val key = draggingKey ?: return
        draggingKey = null
        dragOffset = 0f
        commit(key)
    }

    /** Moves a row one place up ([delta] -1) or down (+1), for accessibility services. */
    fun step(key: String, delta: Int): Boolean {
        val from = entries.indexOfFirst { entryKey(it) == key }
        val to = from + delta
        if (from < 0 || to !in entries.indices) return false
        entries = moved(entries, from, to)
        commit(key)
        return true
    }

    private fun swapIfOverAnother() {
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == draggingKey } ?: return
        val center = dragged.offset + dragOffset + dragged.size / 2f
        val target = layout.visibleItemsInfo.firstOrNull { info ->
            info.key != draggingKey &&
                (info.key as? String)?.startsWith("entry:") == true &&
                center >= info.offset && center < info.offset + info.size
        } ?: return
        val from = entries.indexOfFirst { entryKey(it) == draggingKey }
        val to = entries.indexOfFirst { entryKey(it) == target.key }
        if (from < 0 || to < 0) return
        entries = moved(entries, from, to)
        // The row is laid out at the target's place from now on: keep it under the finger.
        dragOffset -= target.offset - dragged.offset
    }

    private fun commit(key: String) {
        val position = entries.indexOfFirst { entryKey(it) == key }
        val entry = entries.getOrNull(position) ?: return
        val before = original.indexOfFirst { entryKey(it) == key }
        if (position == before && entry.queued == original.getOrNull(before)?.queued) return
        val after = if (position == 0) currentIndex() else entries[position - 1].index
        original = entries
        onMove()(entry.index, after, entry.queued)
    }

    private companion object {
        const val EDGE_PX = 120
        const val EDGE_SPEED_PX = 24f

        /** [list] with the row at [from] moved to [to], its "Next in queue" mark following its new place. */
        fun moved(list: List<QueueEntry>, from: Int, to: Int): List<QueueEntry> {
            val result = list.toMutableList()
            val entry = result.removeAt(from)
            val queued = PlayQueue.queuedAfterMove(
                previousQueued = result.getOrNull(to - 1)?.queued,
                nextQueued = result.getOrNull(to)?.queued,
                wasQueued = entry.queued,
            )
            result.add(to, entry.copy(queued = queued))
            return result
        }
    }
}

@Composable
private fun rememberQueueReorder(
    upNext: List<QueueEntry>,
    currentIndex: Int,
    listState: LazyListState,
    onMove: (Int, Int, Boolean) -> Unit,
): QueueReorder {
    val latestIndex by rememberUpdatedState(currentIndex)
    val latestMove by rememberUpdatedState(onMove)
    val reorder = remember(listState) { QueueReorder(upNext, { latestIndex }, listState, { latestMove }) }
    LaunchedEffect(upNext) { reorder.reset(upNext) }
    LaunchedEffect(reorder.draggingKey) {
        if (reorder.draggingKey == null) return@LaunchedEffect
        while (true) {
            val step = reorder.edgeScroll()
            if (step != 0f) reorder.scrolled(listState.scrollBy(step))
            delay(FRAME_MS)
        }
    }
    return reorder
}

private const val FRAME_MS = 16L

private data class NowRow(val title: String, val artist: String, val artworkUrl: String?)

/** The playing item's row: a library song, or a song streaming from the server (not in the library). */
@Composable
private fun nowPlayingRow(state: PlayerState): NowRow? {
    state.track?.let { track ->
        val cover =
            state.artworkUrl ?: LocalCoverUrls.current.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL)
        return NowRow(track.displayTitle, track.displayArtist, cover)
    }
    return state.stream?.let { NowRow(it.title, it.artist, it.coverUrl.ifBlank { null }) }
}

/**
 * A song's cover at list size. An episode keeps its publisher's artwork and a
 * stream the address its search result gave: neither has a server cover.
 */
@Composable
private fun artworkOf(entry: QueueEntry): String? {
    val artwork = entry.artworkUrl ?: return null
    if (artwork.startsWith("content://")) return artwork
    if (EpisodeIds.isEpisode(entry.trackId) || StreamIds.isStream(entry.trackId)) return entry.artworkUrl
    return LocalCoverUrls.current.track(entry.trackId, ServerUrls.COVER_SMALL)
}

@Composable
private fun nextFromLabel(context: PlaybackContext?): String {
    val title = context?.title.orEmpty()
    return when {
        context?.type == PlaybackContextType.Liked -> stringResource(
            R.string.player_queue_next_from,
            stringResource(R.string.liked_songs),
        )

        title.isNotBlank() -> stringResource(R.string.player_queue_next_from, title)

        else -> stringResource(R.string.player_queue_next_up)
    }
}

@Composable
private fun TopBar(context: PlaybackContext?, colors: CoverColors, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painterResource(DowntifyIcons.Collapse),
                contentDescription = stringResource(R.string.player_queue_close),
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.player_queue),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            val title = context?.title.orEmpty()
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Balances the close button, so the title stays centred.
        Box(Modifier.size(48.dp))
    }
}

@Composable
private fun QueueHeader(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    colors: CoverColors? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.sm).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors?.onSurface ?: MaterialTheme.colorScheme.primary,
                ),
            ) { Text(actionLabel) }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun QueueRow(
    title: String,
    artist: String,
    artworkUrl: String?,
    colors: CoverColors,
    current: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    containerColor: Color = Color.Transparent,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else colors.onSurface,
                fontWeight = if (current) FontWeight.SemiBold else null,
            )
        },
        supportingContent = {
            Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                CoverArt(url = artworkUrl, contentDescription = null, modifier = Modifier.size(48.dp))
                if (current) PlayingBars(animate = isPlaying)
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@PreviewLightDark
@Composable
private fun QueuePreview() {
    val tracks = PreviewData.tracks
    DowntifyTheme {
        QueueScreen(
            state = PlayerState(
                track = tracks[0],
                isPlaying = true,
                durationMs = 239_000,
                context = PlaybackContext(PlaybackContextType.Album, "alb-Glass Harbor", "Glass Harbor"),
                upNext = tracks.drop(1).mapIndexed { i, t ->
                    QueueEntry(i + 1, t.id, t.displayTitle, t.displayArtist, queued = i == 0)
                },
            ),
            actions = QueueActions(),
            controls = NowPlayingActions(),
        )
    }
}
