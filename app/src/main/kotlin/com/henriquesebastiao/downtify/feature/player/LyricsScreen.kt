package com.henriquesebastiao.downtify.feature.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SeekBarPill
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.LrcParser
import com.henriquesebastiao.downtify.core.model.LyricLine
import com.henriquesebastiao.downtify.core.model.Lyrics
import com.henriquesebastiao.downtify.core.model.LyricsTiming
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The lyrics over the whole window, in the cover's colors: back and the title, Synced / Static
 * tabs when the server has an LRC, then the words — synced, the line being sung in bold primary
 * and kept in the middle, the others faded, a tap on one jumps to it; static, the plain text. A
 * big play/pause and the seek bar in a pill float over the bottom, the words scrolling under them.
 *
 * An LRC can be timed to another cut of the song (a live take, a video's longer intro): the
 * timing button opens earlier / later buttons that move the synced lines by [offsetMs], kept per
 * track ([NowPlayingActions.onShiftLyrics]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsScreen(
    lyrics: LyricsState,
    state: PlayerState,
    controls: NowPlayingActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    offsetMs: Long = 0L,
) {
    var static by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    // How much of the bottom the floating controls cover: the words can scroll out from under them.
    var controlsHeight by remember { mutableIntStateOf(0) }
    val covered = with(LocalDensity.current) { controlsHeight.toDp() }

    CoverTheme(playingCoverUrl(state)) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            modifier = modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter,
            ) {
                val loaded = (lyrics as? LyricsState.Loaded)?.lyrics
                val synced = loaded?.isSynced == true && !static
                Column(Modifier.widthIn(max = MAX_WIDTH).fillMaxSize()) {
                    TopBar(onBack, adjusting = adjusting, onAdjust = { adjusting = !adjusting }.takeIf { synced })
                    if (loaded?.isSynced == true) ModeTabs(static, onSelect = { static = it })
                    AnimatedVisibility(
                        visible = synced && adjusting,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        TimingBar(offsetMs, controls.onShiftLyrics, controls.onResetLyrics)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            loaded == null -> Box(
                                Modifier.fillMaxSize().padding(bottom = covered),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(color = scheme.onPrimaryContainer)
                            }

                            loaded.isEmpty -> Text(
                                stringResource(R.string.player_lyrics_none),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .padding(horizontal = LINE_INSET, vertical = Spacing.lg)
                                    .alpha(INACTIVE_ALPHA),
                            )

                            synced -> {
                                val lines = remember(loaded.synced, offsetMs) {
                                    LyricsTiming.shift(loaded.synced, offsetMs)
                                }
                                SyncedLyrics(lines, rememberActiveLine(lines, state), controls.onSeek, covered)
                            }

                            else -> StaticLyrics(loaded.text, covered)
                        }
                    }
                }
                FloatingControls(
                    state,
                    controls,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = MAX_WIDTH)
                        .onSizeChanged { controlsHeight = it.height },
                )
            }
        }
    }
}

/**
 * Back in a dark circle at the start, "Lyrics" in the middle and, with [onAdjust] (synced lyrics
 * showing), the timing button at the end, filled with primary while [adjusting].
 */
@Composable
private fun TopBar(onBack: () -> Unit, adjusting: Boolean, onAdjust: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm)
            .height(TOP_BAR_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        CircleButton(
            DowntifyIcons.Back,
            stringResource(R.string.action_back),
            onBack,
            modifier = Modifier.align(Alignment.CenterStart),
            containerColor = scheme.surfaceContainerLow,
            contentColor = scheme.onSurface,
        )
        Text(
            stringResource(R.string.player_lyrics),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = TOP_BAR_HEIGHT).semantics { heading() },
        )
        if (onAdjust != null) {
            CircleButton(
                DowntifyIcons.LyricsTiming,
                stringResource(R.string.player_lyrics_timing),
                onAdjust,
                modifier = Modifier.align(Alignment.CenterEnd).semantics { selected = adjusting },
                containerColor = if (adjusting) scheme.primary else scheme.surfaceContainerLow,
                contentColor = if (adjusting) scheme.onPrimary else scheme.onSurface,
            )
        }
    }
}

/**
 * Earlier and later around how far the lyrics are moved, in seconds ([LyricsTiming.STEP_MS] a
 * press), and back to the LRC's times.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimingBar(offsetMs: Long, onShift: (Long) -> Unit, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = scheme.onPrimaryContainer.copy(alpha = TAB_ALPHA),
        contentColor = scheme.onPrimaryContainer,
        disabledContainerColor = scheme.onPrimaryContainer.copy(alpha = TAB_ALPHA / 2),
        disabledContentColor = scheme.onPrimaryContainer.copy(alpha = INACTIVE_ALPHA),
    )
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TimingButton(DowntifyIcons.Remove, R.string.player_lyrics_earlier, colors, offsetMs > -LyricsTiming.MAX_MS) {
            onShift(-LyricsTiming.STEP_MS)
        }
        Text(
            stringResource(R.string.player_lyrics_offset, LyricsTiming.seconds(offsetMs, locale)),
            style = DowntifyType.dataLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // Read out as it changes, after each press.
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TimingButton(DowntifyIcons.Add, R.string.player_lyrics_later, colors, offsetMs < LyricsTiming.MAX_MS) {
            onShift(LyricsTiming.STEP_MS)
        }
        TimingButton(DowntifyIcons.Reset, R.string.player_lyrics_timing_reset, colors, offsetMs != 0L, onReset)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimingButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    colors: IconButtonColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors = colors,
        modifier = Modifier.size(TIMING_BUTTON_SIZE),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/** Synced or static, as two pills side by side: the one showing filled with primary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeTabs(static: Boolean, onSelect: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.onPrimaryContainer.copy(alpha = TAB_ALPHA),
        contentColor = scheme.onPrimaryContainer,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    // Pills when checked too: the stock toggle squares off its checked state.
    val shapes = ToggleButtonShapes(
        shape = CircleShape,
        pressedShape = ToggleButtonDefaults.pressedShape,
        checkedShape = CircleShape,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        listOf(false to R.string.player_lyrics_synced, true to R.string.player_lyrics_static).forEach { (option, id) ->
            val checked = option == static
            val label = stringResource(id)
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                shapes = shapes,
                colors = colors,
                contentPadding = PaddingValues(horizontal = Spacing.md),
                modifier = Modifier
                    .weight(1f)
                    .height(TAB_HEIGHT)
                    // Two views of one screen: read as "Synced, tab, selected".
                    .clearAndSetSemantics {
                        role = Role.Tab
                        selected = checked
                        text = AnnotatedString(label)
                        onClick {
                            onSelect(option)
                            true
                        }
                    },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    autoSize = TabAutoSize,
                )
            }
        }
    }
}

/**
 * The index of the line being sung, on time. The player's position comes in ticks half a second
 * apart; between them it runs on from the last tick at the playback speed, so each line lights up
 * in the frame it starts rather than at the next tick, up to half a second late.
 */
@Composable
private fun rememberActiveLine(lines: List<LyricLine>, state: PlayerState): Int {
    val positionMs = state.positionMs
    val speed = state.speed
    val running = state.isPlaying && speed > 0f
    val active = remember(lines) { mutableIntStateOf(LrcParser.activeIndex(lines, positionMs)) }
    // Each tick (or seek, pause, speed change) starts over from the position it brings.
    LaunchedEffect(lines, positionMs, running, speed) {
        active.intValue = LrcParser.activeIndex(lines, positionMs)
        if (!running) return@LaunchedEffect
        // Frame time rather than the wall clock: delay runs on the same clock, under test too.
        val start = withFrameMillis { it }
        var position = positionMs
        while (true) {
            val wait = LrcParser.nextLineInMs(lines, position) ?: break
            delay(ceil(wait / speed).toLong())
            position = positionMs + ((withFrameMillis { it } - start) * speed).toLong()
            active.intValue = LrcParser.activeIndex(lines, position)
        }
    }
    return active.intValue
}

/**
 * The synced lines, the [active] one kept in the middle of the list (as far as the list goes).
 * Room below, half the list, lets the last line reach the middle too, clear of the controls.
 */
@Composable
private fun SyncedLyrics(lines: List<LyricLine>, active: Int, onSeek: (Long) -> Unit, covered: Dp) {
    val listState = rememberLazyListState()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val scheme = MaterialTheme.colorScheme
    // The first time, the list opens on the line; after that it glides from line to line.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        listState.centerOn(active, spec.takeIf { placed })
        placed = true
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = Spacing.sm, bottom = max(covered, maxHeight / 2)),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == active
                // Same size for every line: only weight, color and opacity change, so nothing reflows.
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) scheme.primary else scheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.timeMs.coerceAtLeast(0)) }
                        .padding(horizontal = LINE_INSET, vertical = LINE_GAP)
                        .alpha(if (isActive) 1f else INACTIVE_ALPHA),
                )
            }
        }
    }
}

/** Scrolls item [index] to the middle of the list: at once without a [spec], else animated on it. */
private suspend fun LazyListState.centerOn(index: Int, spec: AnimationSpec<Float>?) {
    // Nothing to measure before the list's first layout.
    snapshotFlow { layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val distance = item.offset + item.size / 2f - (info.viewportStartOffset + info.viewportEndOffset) / 2f
    if (spec == null) scrollBy(distance) else animateScrollBy(distance, spec)
}

/** The words without their times, line by line as the synced ones, all in full color. */
@Composable
private fun StaticLyrics(text: String, covered: Dp, modifier: Modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        contentPadding = PaddingValues(top = Spacing.sm, bottom = covered + Spacing.lg),
        modifier = modifier,
    ) {
        itemsIndexed(text.lines()) { _, line ->
            if (line.isBlank()) {
                // A blank line splits the verses.
                Spacer(Modifier.height(LINE_GAP * 2))
            } else {
                Text(
                    line,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LINE_INSET, vertical = LINE_GAP),
                )
            }
        }
    }
}

/**
 * The lyrics of a song that isn't playing (a downloaded search result) in a sheet: the words
 * without their times, as there is nothing to follow or seek.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(lyrics: LyricsState, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Text(
            stringResource(R.string.player_lyrics),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = LINE_INSET, vertical = Spacing.sm).semantics { heading() },
        )
        val loaded = (lyrics as? LyricsState.Loaded)?.lyrics
        when {
            loaded == null -> Box(
                Modifier.fillMaxWidth().heightIn(min = SHEET_MIN_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(color = scheme.onPrimaryContainer)
            }

            loaded.isEmpty -> Text(
                stringResource(R.string.player_lyrics_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = LINE_INSET, vertical = Spacing.lg).alpha(INACTIVE_ALPHA),
            )

            else -> StaticLyrics(loaded.text, Spacing.lg, Modifier.heightIn(max = SHEET_MAX_HEIGHT))
        }
    }
}

/** The big play/pause over the seek bar's pill, above the navigation bar. */
@Composable
private fun FloatingControls(state: PlayerState, controls: NowPlayingActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = LINE_INSET, end = LINE_INSET, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CONTROLS_GAP),
    ) {
        PlayButton(state.isPlaying, controls.onTogglePlay)
        val duration = state.durationMs.coerceAtLeast(1)
        SeekBarPill(
            progress = state.positionMs.toFloat() / duration,
            onSeek = { controls.onSeek((it * duration).toLong()) },
            playing = state.isPlaying,
            timeAt = { formatDuration((it * duration).toLong() / 1000.0) },
            duration = formatDuration(state.durationMs / 1000.0),
            contentDescription = stringResource(R.string.player_seek),
        )
    }
}

/**
 * Play/pause, 110 dp, in the cover's tertiary so it stands apart from the words: a circle while
 * paused, a squircle while playing (as on Now Playing), squarer still while pressed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateFloatAsState(
        if (playing) PLAYING_PERCENT else ROUND_PERCENT,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lyricsPlayCorner",
    )
    // The spring overshoots; a corner past half the side would throw.
    val shape = RoundedCornerShape(percent = corner.coerceIn(0f, ROUND_PERCENT).roundToInt())
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonShapes(shape = shape, pressedShape = RoundedCornerShape(PRESSED_CORNER)),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.tertiary,
            contentColor = scheme.onTertiary,
        ),
        // Not clipped to the shadow: the pressed shape is squarer than the one at rest.
        modifier = Modifier.size(PLAY_SIZE).shadow(PLAY_ELEVATION, shape, clip = false),
    ) {
        Icon(
            painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
            contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
            modifier = Modifier.size(PLAY_ICON),
        )
    }
}

// "Συγχρονισμένοι" in half a 360 dp phone: smaller rather than cut.
private val TabAutoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 18.sp, stepSize = 1.sp)

private val MAX_WIDTH = 640.dp
private val SHEET_MIN_HEIGHT = 200.dp
private val SHEET_MAX_HEIGHT = 520.dp
private val TOP_BAR_HEIGHT = 64.dp
private val TAB_HEIGHT = 56.dp
private val TIMING_BUTTON_SIZE = 48.dp
private val LINE_INSET = 28.dp
private val LINE_GAP = 9.dp
private val CONTROLS_GAP = 20.dp
private val PLAY_SIZE = 110.dp
private val PLAY_ICON = 40.dp
private val PRESSED_CORNER = 24.dp
private val PLAY_ELEVATION = 6.dp
private const val INACTIVE_ALPHA = 0.45f
private const val TAB_ALPHA = 0.15f

// The play button's corners in percent of its side: a circle, or a squircle while playing.
private const val ROUND_PERCENT = 50f
private const val PLAYING_PERCENT = 30f

private val previewLines = listOf(
    "The tide came in slowly",
    "And took what I left on the sand",
    "",
    "And the sky turned the sea's color",
    "Long after you'd gone",
)
    .mapIndexed { i, text -> LyricLine(i * 4_000L, text) }

private val previewState = PlayerState(
    track = PreviewData.tracks[1],
    isPlaying = true,
    positionMs = 5_000,
    durationMs = 190_000,
)

@PhonePreviews
@Composable
private fun LyricsSyncedPreview() {
    DowntifyTheme {
        LyricsScreen(
            lyrics = LyricsState.Loaded("t", Lyrics(previewLines, "")),
            state = previewState,
            controls = NowPlayingActions(),
            onBack = {},
        )
    }
}

@PhonePreviews
@Composable
private fun LyricsPlainPreview() {
    DowntifyTheme {
        LyricsScreen(
            lyrics = LyricsState.Loaded("t", Lyrics(emptyList(), previewLines.joinToString("\n") { it.text })),
            state = previewState.copy(isPlaying = false),
            controls = NowPlayingActions(),
            onBack = {},
        )
    }
}

@PhonePreviews
@Composable
private fun LyricsTimingPreview() {
    DowntifyTheme {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            TimingBar(offsetMs = 1_250, onShift = {}, onReset = {})
        }
    }
}
