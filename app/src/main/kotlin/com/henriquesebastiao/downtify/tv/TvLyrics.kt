package com.henriquesebastiao.downtify.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.model.LyricLine
import com.henriquesebastiao.downtify.core.model.LyricsTiming
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.feature.player.LyricsState
import com.henriquesebastiao.downtify.feature.player.rememberActiveLine
import kotlinx.coroutines.launch

/**
 * The lyrics as the TV player shows them, after the TV music apps: large bold lines in a column in
 * the middle of the screen. Synced, the line being sung is bright and stays near the top as the
 * song goes on, the ones after it dimmer and fading out at the bottom. Plain lyrics scroll with up
 * and down once focused. The timing adjustment stays on the phone.
 */
@Composable
internal fun TvLyrics(lyrics: LyricsState, state: PlayerState, offsetMs: Long, modifier: Modifier = Modifier) {
    val loaded = (lyrics as? LyricsState.Loaded)?.lyrics
    BoxWithConstraints(modifier.fadingEdges()) {
        val side = maxWidth * SIDE_FRACTION
        when {
            loaded == null -> Message(stringResource(R.string.player_lyrics_loading), side)

            loaded.isEmpty -> Message(stringResource(R.string.player_lyrics_none), side)

            loaded.isSynced -> {
                val lines = remember(loaded.synced, offsetMs) { LyricsTiming.shift(loaded.synced, offsetMs) }
                SyncedLines(lines, rememberActiveLine(lines, state), side, top = maxHeight * ACTIVE_FRACTION)
            }

            else -> PlainLines(loaded.text.lines(), side)
        }
    }
}

@Composable
private fun Message(text: String, side: Dp) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = UPCOMING_ALPHA),
        modifier = Modifier.padding(horizontal = side, vertical = 64.dp),
    )
}

/** The synced lines; the one being sung scrolls to [top] below the list's start. */
@Composable
private fun SyncedLines(lines: List<LyricLine>, active: Int, side: Dp, top: Dp) {
    val listState = rememberLazyListState()
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        val target = active.coerceAtLeast(0)
        // The first time the list opens on the line; after that it glides to the next one.
        if (placed) listState.animateScrollToItem(target) else listState.scrollToItem(target)
        placed = true
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        contentPadding = PaddingValues(top = top, bottom = 400.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lines) { index, line ->
            val alpha by animateFloatAsState(
                when {
                    index == active -> 1f
                    index < active -> PAST_ALPHA
                    else -> UPCOMING_ALPHA
                },
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "line",
            )
            LyricLineText(line.text.ifBlank { "♪" }, alpha, side)
        }
    }
}

/** Lyrics without times: all lines at one brightness, scrolled with up and down once focused. */
@Composable
private fun PlainLines(lines: List<String>, side: Dp) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { SCROLL_STEP.toPx() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 32.dp, bottom = 200.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = side - TvRingSpace)
            .tvFocusFrame(focused, RoundedCornerShape(16.dp))
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val by = when (event.key) {
                    Key.DirectionDown -> step
                    Key.DirectionUp -> -step
                    else -> return@onKeyEvent false
                }
                // At the top, up leaves the lyrics (to the controls' row above, if any).
                if (by < 0 && !listState.canScrollBackward) return@onKeyEvent false
                if (by > 0 && !listState.canScrollForward) return@onKeyEvent false
                scope.launch { listState.animateScrollBy(by) }
                true
            }
            .focusable(interactionSource = interaction),
    ) {
        itemsIndexed(lines) { _, line -> LyricLineText(line.ifBlank { " " }, 1f, 0.dp) }
    }
}

@Composable
private fun LyricLineText(text: String, alpha: Float, side: Dp) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = side, vertical = LINE_GAP)
            .graphicsLayer { this.alpha = alpha },
    )
}

/** The lines fade out at the top and the bottom instead of being cut. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // A mask, not a color: only its alpha counts.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                FADE to Color.Black,
                1f - FADE * 2 to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** The line being sung sits this far down the lyrics. */
private const val ACTIVE_FRACTION = 0.08f

/** The column of lines starts and ends this far in from the screen's sides. */
private const val SIDE_FRACTION = 0.2f
private const val PAST_ALPHA = 0.5f
private const val UPCOMING_ALPHA = 0.4f
private const val FADE = 0.06f
private val LINE_GAP = 12.dp
private val SCROLL_STEP = 160.dp
