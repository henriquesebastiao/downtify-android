package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

// The player's pieces assume the content-tinted background of a CoverTheme: primaryContainer.

/**
 * The collapsed player above the navigation bar: a primaryContainer card with the floating bar's
 * corners ([DowntifyShape.BarCorner]), the cover in a circle, the title (sliding when it doesn't
 * fit) over the artist, then [actions] ([MiniPlayerButton]s). Tapping the cover or the text opens
 * Now Playing. [attached] is for when the floating navigation bar sits right under it: the two
 * read as one group, so its bottom corners tighten (on the spatial spring) to the bar's top ones,
 * [DowntifyShape.JoinedCorner]. [coverModifier] goes on the cover (Now Playing's cover flies from
 * it).
 */
@Composable
fun MiniPlayerCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onOpen: () -> Unit,
    openLabel: String,
    modifier: Modifier = Modifier,
    attached: Boolean = false,
    coverModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bottom by animateDpAsState(
        if (attached) DowntifyShape.JoinedCorner else DowntifyShape.BarCorner,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "miniBottomCorners",
    )
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = RoundedCornerShape(
            topStart = DowntifyShape.BarCorner,
            topEnd = DowntifyShape.BarCorner,
            // The spring overshoots; a negative corner would throw.
            bottomEnd = bottom.coerceAtLeast(0.dp),
            bottomStart = bottom.coerceAtLeast(0.dp),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = MINI_PLAYER_HEIGHT).padding(start = Spacing.md, end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Artwork(
                    url = coverUrl,
                    contentDescription = null,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp).then(coverModifier),
                )
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = SUBTITLE_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), content = actions)
        }
    }
}

/**
 * A 48 dp button of the [MiniPlayerCard]. Previous and next sit in a darker circle (onPrimary on
 * the primaryContainer card); [main] (play/pause) is filled with primary, and while [playing] it
 * is a squircle instead of a circle, as on Now Playing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    main: Boolean = false,
    playing: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateFloatAsState(
        if (main && playing) PLAYING_PERCENT else PILL_PERCENT,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "miniButtonCorner",
    )
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonShapes(
            shape = RoundedCornerShape(percent = corner.coerceIn(0f, PILL_PERCENT).roundToInt()),
            pressedShape = RoundedCornerShape(16.dp),
        ),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (main) scheme.primary else scheme.onPrimary,
            contentColor = if (main) scheme.onPrimary else scheme.onPrimaryContainer,
        ),
        modifier = modifier.size(48.dp),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription)
    }
}

/** What the transport buttons say to a screen reader. */
@Immutable
data class PlaybackLabels(val play: String, val pause: String, val previous: String, val next: String)

/**
 * Now Playing's transport (Material 3 Expressive button group): wide previous/next pills around
 * play/pause, which is a circle while paused and a 28 dp squircle while playing. The pressed
 * button widens and its neighbours make room. [compact] is 56 dp tall instead of 76. An episode
 * swaps previous/next for its skips with [previousIcon] and [nextIcon] (and their [labels]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackControls(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    labels: PlaybackLabels,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    previousIcon: Int = DowntifyIcons.Previous,
    nextIcon: Int = DowntifyIcons.Next,
) {
    val height = if (compact) 56.dp else 76.dp
    val scheme = MaterialTheme.colorScheme
    val skipColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = scheme.onPrimaryContainer.copy(alpha = SKIP_ALPHA),
        contentColor = scheme.onPrimaryContainer,
    )
    val playColors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
    val playCorner by animateDpAsState(
        if (playing) PLAYING_CORNER else height / 2,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playCorner",
    )
    val skipShapes = ButtonShapes(shape = RoundedCornerShape(height / 2), pressedShape = RoundedCornerShape(28.dp))
    val playShapes = ButtonShapes(shape = RoundedCornerShape(playCorner), pressedShape = RoundedCornerShape(20.dp))
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                TransportButton(
                    previousIcon,
                    labels.previous,
                    onPrevious,
                    skipShapes,
                    skipColors,
                    interaction,
                    Modifier.weight(1f).animateWidth(interaction).height(height),
                    SKIP_ICON,
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                TransportButton(
                    if (playing) DowntifyIcons.Pause else DowntifyIcons.Play,
                    if (playing) labels.pause else labels.play,
                    onTogglePlay, playShapes, playColors, interaction,
                    Modifier.weight(PLAY_WEIGHT).animateWidth(interaction).height(height), PLAY_ICON,
                    filled = true,
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                TransportButton(
                    nextIcon,
                    labels.next,
                    onNext,
                    skipShapes,
                    skipColors,
                    interaction,
                    Modifier.weight(1f).animateWidth(interaction).height(height),
                    SKIP_ICON,
                )
            },
            menuContent = {},
        )
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    shapes: ButtonShapes,
    colors: ButtonColors,
    interaction: MutableInteractionSource,
    modifier: Modifier,
    iconSize: Dp,
    filled: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(iconSize))
    }
    if (filled) {
        Button(
            onClick = onClick,
            shapes = shapes,
            colors = colors,
            contentPadding = PaddingValues(0.dp),
            interactionSource = interaction,
            modifier = modifier,
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            colors = colors,
            contentPadding = PaddingValues(0.dp),
            interactionSource = interaction,
            modifier = modifier,
            content = content,
        )
    }
}

/** One of Now Playing's toggles (shuffle, repeat, like): [label] says its state to a screen reader. */
@Immutable
data class PlayerToggle(val icon: Int, val label: String, val checked: Boolean, val onClick: () -> Unit)

/**
 * The toggles under the transport as a connected button group in a darker pill (onPrimary, as the
 * mini player's buttons): the group's ends round, the joins tight, each button 52 dp tall, a
 * circle tinted with primary while on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerToggles(toggles: List<PlayerToggle>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.onPrimaryContainer.copy(alpha = TOGGLE_ALPHA),
        contentColor = scheme.onPrimaryContainer,
        checkedContainerColor = scheme.primary.copy(alpha = TOGGLE_CHECKED_ALPHA),
        checkedContentColor = scheme.onPrimaryContainer,
    )
    Row(
        modifier
            .background(scheme.onPrimary, CircleShape)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        toggles.forEachIndexed { index, toggle ->
            ToggleButton(
                checked = toggle.checked,
                onCheckedChange = { toggle.onClick() },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    toggles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(painterResource(toggle.icon), contentDescription = toggle.label)
            }
        }
    }
}

/**
 * The song's position as Material's slider over a wavy track that flows while [playing] and lies
 * flat when paused. [progress] follows the player except while the finger is on it; [onSeek]
 * gets the fraction where it lets go. [timeAt] spells a fraction as a time for the labels below,
 * which follow the finger; [between] goes between them (the format, as a [Tag]). The times are
 * bold, with tabular figures so they don't jitter as they count.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    playing: Boolean,
    timeAt: (Float) -> String,
    duration: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    between: (@Composable () -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: progress.coerceIn(0f, 1f)
    val position = timeAt(value)
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        WavySlider(
            value = value,
            onDrag = { dragging = it },
            onRelease = {
                dragging?.let(onSeek)
                dragging = null
            },
            playing = playing,
            thumbSize = SEEK_THUMB,
            contentDescription = contentDescription,
            stateDescription = "$position / $duration",
            enabled = enabled,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(position, style = seekTimeStyle(), color = scheme.onPrimaryContainer, maxLines = 1)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { between?.invoke() }
            Text(duration, style = seekTimeStyle(), color = scheme.onPrimaryContainer, maxLines = 1)
        }
    }
}

/**
 * [WavySeekBar] on one line, floating over a page (the lyrics): a dark pill (surfaceContainerLow,
 * a shadow under it) with the times at its ends and a smaller thumb. Same wave, same behaviour.
 */
@Composable
fun SeekBarPill(
    progress: Float,
    onSeek: (Float) -> Unit,
    playing: Boolean,
    timeAt: (Float) -> String,
    duration: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: progress.coerceIn(0f, 1f)
    val position = timeAt(value)
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        shape = CircleShape,
        shadowElevation = PILL_ELEVATION,
        modifier = modifier.fillMaxWidth().height(SEEK_PILL_HEIGHT),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(position, style = seekTimeStyle(), maxLines = 1)
            WavySlider(
                value = value,
                onDrag = { dragging = it },
                onRelease = {
                    dragging?.let(onSeek)
                    dragging = null
                },
                playing = playing,
                thumbSize = SEEK_PILL_THUMB,
                contentDescription = contentDescription,
                stateDescription = "$position / $duration",
                modifier = Modifier.weight(1f),
            )
            Text(duration, style = seekTimeStyle(), maxLines = 1)
        }
    }
}

/**
 * Material's slider over a wavy track that flows while [playing] and lies flat when paused. Its
 * own state follows [value] — except while the finger is on it: [onDrag] gets where the finger
 * is, [onRelease] says it let go.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WavySlider(
    value: Float,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    playing: Boolean,
    thumbSize: Dp,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val stroke = with(LocalDensity.current) { Stroke(width = SEEK_STROKE.toPx(), cap = StrokeCap.Round) }
    val state = rememberSliderState()
    LaunchedEffect(value) { state.value = value }
    Slider(
        state = state,
        enabled = enabled,
        onValueChange = {
            state.value = it
            onDrag(it)
        },
        onValueChangeFinished = onRelease,
        thumb = {
            Box(Modifier.size(thumbSize).background(scheme.primary, CircleShape))
        },
        track = {
            LinearWavyProgressIndicator(
                progress = { state.value },
                color = scheme.primary,
                trackColor = scheme.primary.copy(alpha = SEEK_TRACK_ALPHA),
                stroke = stroke,
                trackStroke = stroke,
                amplitude = { if (playing) 1f else 0f },
                wavelength = SEEK_WAVELENGTH,
                modifier = Modifier.fillMaxWidth().height(SEEK_WAVE_HEIGHT),
            )
        },
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            this.stateDescription = stateDescription
        },
    )
}

/** The seek bar's times: bold, with tabular figures so they don't jitter as they count. */
@Composable
private fun seekTimeStyle() = MaterialTheme.typography.bodyMedium.copy(
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

private val MINI_PLAYER_HEIGHT = 72.dp
private val PLAYING_CORNER = 28.dp
private val SKIP_ICON = 28.dp
private val PLAY_ICON = 36.dp
private const val PLAY_WEIGHT = 1.15f
private val SEEK_STROKE = 6.dp
private val SEEK_THUMB = 24.dp
private val SEEK_WAVELENGTH = 36.dp
private val SEEK_WAVE_HEIGHT = 16.dp
private val SEEK_PILL_HEIGHT = 60.dp
private val SEEK_PILL_THUMB = 18.dp
private val PILL_ELEVATION = 6.dp
private const val SUBTITLE_ALPHA = 0.8f

// A mini player button's corners in percent of its side: a circle, or a squircle while playing.
private const val PILL_PERCENT = 50f
private const val PLAYING_PERCENT = 30f
private const val SKIP_ALPHA = 0.16f
private const val TOGGLE_ALPHA = 0.09f
private const val TOGGLE_CHECKED_ALPHA = 0.28f
private const val SEEK_TRACK_ALPHA = 0.26f

@PhonePreviews
@Composable
private fun PlayerPreview() {
    DowntifyTheme {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                MiniPlayerCard(
                    title = "A song with a long title",
                    subtitle = "The Artist",
                    coverUrl = null,
                    onOpen = {},
                    openLabel = "Open player",
                ) {
                    MiniPlayerButton(DowntifyIcons.Previous, "Previous", {})
                    MiniPlayerButton(DowntifyIcons.Pause, "Pause", {}, main = true, playing = true)
                    MiniPlayerButton(DowntifyIcons.Next, "Next", {})
                }
                WavySeekBar(
                    progress = 0.35f,
                    onSeek = {},
                    playing = true,
                    timeAt = { "1:24" },
                    duration = "3:59",
                    contentDescription = "Position",
                )
                SeekBarPill(
                    progress = 0.35f,
                    onSeek = {},
                    playing = true,
                    timeAt = { "1:24" },
                    duration = "3:59",
                    contentDescription = "Position",
                )
                PlaybackControls(
                    playing = true,
                    onTogglePlay = {},
                    onPrevious = {},
                    onNext = {},
                    labels = PlaybackLabels("Play", "Pause", "Previous", "Next"),
                )
                PlayerToggles(
                    listOf(
                        PlayerToggle(DowntifyIcons.Shuffle, "Shuffle off", checked = false) {},
                        PlayerToggle(DowntifyIcons.Repeat, "Repeat all", checked = true) {},
                        PlayerToggle(DowntifyIcons.FavoriteFilled, "Liked", checked = true) {},
                    ),
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
