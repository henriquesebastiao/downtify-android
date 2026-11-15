package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlaybackError
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.core.player.RepeatMode
import com.henriquesebastiao.downtify.feature.player.LyricsState
import com.henriquesebastiao.downtify.feature.player.NowPlayingActions
import com.henriquesebastiao.downtify.feature.player.artworkOf
import com.henriquesebastiao.downtify.feature.player.playingCoverUrl
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The player on a TV, after the TV music apps: the cover fills the screen, dimmed, with the cover,
 * the title and the artist at the bottom left; under them the seek bar (left and right seek), then
 * the transport and the toggles, and lyrics and the queue at the end. Focus lands on play/pause
 * ([playFocus]).
 *
 * While it plays and no key is pressed for [autoHideMs], the controls fade and only the picture,
 * the cover and the title stay, as an ambient screen; the next key only brings them back. Back is
 * left to the caller (it closes the player).
 *
 * With [lyrics] (not null while they're open) the screen takes the cover's colors instead of its
 * picture and the lyrics fill the room above the controls, the more of it once the controls fade
 * ([TvLyrics]); the Lyrics button shows it's on, and pressed again it calls [onCloseLyrics].
 *
 * In a song's last [NEXT_UP_MS], what plays next shows at the top end.
 *
 * [darkMode], for a dark room: no picture or cover colors behind, everything dimmed, and dimmer
 * still once the controls fade; turning it on says so at the top end. Under the controls, the
 * artist ("About the artist") peeks in at the bottom edge and slides up when it takes focus; OK
 * opens their page.
 */
@Suppress("LongMethod")
@Composable
fun TvPlayerScreen(
    state: PlayerState,
    isLiked: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    hasLibraryCopy: Boolean = false,
    playFocus: FocusRequester? = null,
    autoHideMs: Long = AUTO_HIDE_MS,
    lyrics: LyricsState? = null,
    lyricsOffsetMs: Long = 0L,
    onCloseLyrics: () -> Unit = {},
    darkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    bannerUrl: String? = null,
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    // The lyrics take the cover's colors; the picture keeps the app's.
    CoverTheme(playingCoverUrl(state).takeIf { lyrics != null && !darkMode }) {
        PlayerContent(
            state,
            isLiked,
            actions,
            modifier,
            PlayerOptions(
                hasLibraryCopy,
                playFocus,
                autoHideMs,
                lyrics,
                lyricsOffsetMs,
                onCloseLyrics,
                darkMode,
                onToggleDarkMode,
                bannerUrl,
            ),
        )
    }
}

/** What [TvPlayerScreen] passes on beyond the state and the actions. */
private data class PlayerOptions(
    val hasLibraryCopy: Boolean,
    val playFocus: FocusRequester?,
    val autoHideMs: Long,
    val lyrics: LyricsState?,
    val lyricsOffsetMs: Long,
    val onCloseLyrics: () -> Unit,
    val darkMode: Boolean,
    val onToggleDarkMode: () -> Unit,
    val bannerUrl: String?,
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun PlayerContent(
    state: PlayerState,
    isLiked: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier,
    options: PlayerOptions,
) {
    val hasLibraryCopy = options.hasLibraryCopy
    val playFocus = options.playFocus
    val autoHideMs = options.autoHideMs
    val lyrics = options.lyrics
    val lyricsOffsetMs = options.lyricsOffsetMs
    val onCloseLyrics = options.onCloseLyrics
    val darkMode = options.darkMode
    val onToggleDarkMode = options.onToggleDarkMode
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    // How tall the bottom block is now (its controls fold away when they fade), for the lyrics above it.
    var blockHeight by remember { mutableStateOf(0.dp) }

    var controlsShown by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(touches, controlsShown, state.isPlaying) {
        if (controlsShown && state.isPlaying) {
            delay(autoHideMs)
            controlsShown = false
        }
    }
    val controlsAlpha by animateFloatAsState(
        if (controlsShown) 1f else 0f,
        MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "controls",
    )
    // Faded, the controls also fold away, so the title goes down to the bottom as on an ambient screen.
    val controlsRoom by animateFloatAsState(
        if (controlsShown) 1f else 0f,
        MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "controlsRoom",
    )
    // Dark mode dims everything, the more once the controls fade.
    val dim by animateFloatAsState(
        when {
            !darkMode -> 1f
            controlsShown -> DARK_ALPHA
            else -> DARK_AMBIENT_ALPHA
        },
        MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "dim",
    )
    // Turning dark mode on says so for a few seconds.
    var darkNotices by remember { mutableIntStateOf(0) }
    var darkNotice by remember { mutableStateOf(false) }
    LaunchedEffect(darkNotices) {
        if (darkNotices == 0) return@LaunchedEffect
        darkNotice = true
        delay(DARK_NOTICE_MS)
        darkNotice = false
    }
    // The artist under the controls: only its heading peeks in at the bottom until it takes focus.
    var aboutFocused by remember { mutableStateOf(false) }
    var aboutHeight by remember { mutableIntStateOf(0) }
    val peek = with(density) { AboutPeek.roundToPx() }
    val aboutHidden by animateFloatAsState(
        if (aboutFocused) 0f else 1f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "about",
    )
    val blockShift = (
        (aboutHeight - peek).coerceAtLeast(0) *
            aboutHidden.coerceIn(0f, 1f) *
            controlsRoom.coerceIn(0f, 1f)
        ).roundToInt()

    Box(
        modifier
            .fillMaxSize()
            .background(if (lyrics != null && !darkMode) scheme.primaryContainer else scheme.surface)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key !in RemoteKeys) return@onPreviewKeyEvent false
                touches++
                if (controlsShown) {
                    false
                } else {
                    // The first key only wakes the controls: it doesn't seek, skip or pause.
                    controlsShown = true
                    true
                }
            },
    ) {
        if (lyrics != null) {
            val room = blockHeight - with(density) { blockShift.toDp() }
            TvLyrics(
                lyrics,
                state,
                lyricsOffsetMs,
                Modifier
                    .fillMaxSize()
                    .padding(top = Spacing.xl, bottom = room.coerceAtLeast(0.dp))
                    .graphicsLayer { alpha = dim },
            )
        } else if (!darkMode) {
            Picture(state, options.bannerUrl)
        }

        // Near the end of a song, what plays next, at the top end, as the TV music apps do.
        val next = state.upNext.firstOrNull()
        val remaining = state.durationMs - state.positionMs
        AnimatedVisibility(
            visible = episode == null && next != null && state.repeat != RepeatMode.One &&
                state.durationMs > 0 && remaining in 1..NEXT_UP_MS,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = TvMargin, vertical = Spacing.xl),
        ) {
            next?.let { NextUpCard(it) }
        }
        AnimatedVisibility(
            visible = darkNotice,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = TvMargin, vertical = Spacing.xl),
        ) {
            DarkModeNotice()
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .onSizeChanged { blockHeight = with(density) { it.height.toDp() } }
                .offset { IntOffset(0, blockShift) }
                .graphicsLayer { alpha = dim }
                .padding(horizontal = TvMargin, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Artwork(
                    url = playingCoverUrl(state, ServerUrls.COVER_LARGE),
                    contentDescription = null,
                    shape = CoverShape,
                    placeholderIcon = if (episode != null) DowntifyIcons.Podcasts else DowntifyIcons.Song,
                    modifier = Modifier.size(CoverSize),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.error?.let { error ->
                        Text(
                            stringResource(error.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error,
                        )
                    }
                }
            }

            Column(
                Modifier
                    .foldAway { controlsRoom }
                    .graphicsLayer { alpha = controlsAlpha },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TvSeekBar(state.positionMs, state.durationMs, actions.onSeek)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (episode != null) {
                        TvIconButton(
                            DowntifyIcons.SkipBack,
                            stringResource(R.string.player_skip_back),
                            actions.onSkipBack,
                        )
                    } else {
                        TvIconButton(
                            DowntifyIcons.Previous,
                            stringResource(R.string.player_previous),
                            actions.onPrevious,
                        )
                    }
                    TvPlayButton(
                        playing = state.isPlaying,
                        onClick = actions.onTogglePlay,
                        modifier = playFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
                    )
                    if (episode != null) {
                        TvIconButton(
                            DowntifyIcons.SkipForward,
                            stringResource(R.string.player_skip_forward),
                            actions.onSkipForward,
                        )
                    } else {
                        TvIconButton(DowntifyIcons.Next, stringResource(R.string.player_next), actions.onNext)
                        TvToggle(
                            icon = DowntifyIcons.Shuffle,
                            label = stringResource(
                                if (state.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off,
                            ),
                            checked = state.shuffle,
                            onClick = actions.onToggleShuffle,
                        )
                        TvToggle(
                            icon = if (state.repeat ==
                                RepeatMode.One
                            ) {
                                DowntifyIcons.RepeatOne
                            } else {
                                DowntifyIcons.Repeat
                            },
                            label = stringResource(state.repeat.label),
                            checked = state.repeat != RepeatMode.Off,
                            onClick = actions.onCycleRepeat,
                        )
                        // Nothing to like on a stream the library doesn't have yet.
                        if (stream == null || hasLibraryCopy) {
                            TvToggle(
                                icon = if (isLiked) DowntifyIcons.FavoriteFilled else DowntifyIcons.Favorite,
                                label = stringResource(if (isLiked) R.string.player_unlike else R.string.player_like),
                                checked = isLiked,
                                onClick = actions.onToggleLike,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (episode == null && (stream == null || hasLibraryCopy)) {
                        TvLabeledButton(
                            DowntifyIcons.Lyrics,
                            stringResource(R.string.player_lyrics),
                            if (lyrics != null) onCloseLyrics else actions.onOpenLyrics,
                            selected = lyrics != null,
                        )
                    }
                    if (episode == null) {
                        TvLabeledButton(DowntifyIcons.Queue, stringResource(R.string.player_queue), actions.onOpenQueue)
                    }
                    TvLabeledButton(
                        DowntifyIcons.DarkMode,
                        stringResource(R.string.tv_dark_mode),
                        {
                            if (!darkMode) darkNotices++
                            onToggleDarkMode()
                        },
                        selected = darkMode,
                    )
                }
                val artistId = track?.artistId.orEmpty()
                if (artistId.isNotEmpty()) {
                    val artistName = track?.albumArtist.orEmpty().ifBlank { track?.displayArtist.orEmpty() }
                    AboutTheArtist(
                        name = track?.displayArtist.orEmpty(),
                        photoUrl = LocalCoverUrls.current.artist(artistName),
                        coverUrl = playingCoverUrl(state),
                        onClick = { actions.onGoToArtist(artistId) },
                        modifier = Modifier
                            .onSizeChanged { aboutHeight = it.height }
                            .onFocusChanged { aboutFocused = it.hasFocus },
                    )
                }
            }
        }
    }
}

/**
 * The artist's hero banner (the one the web app shows over their page, [bannerUrl]) or, without
 * one or when it fails, the cover, as large as the screen, dimmed, darker towards the text at the
 * bottom.
 */
@Composable
private fun Picture(state: PlayerState, bannerUrl: String?) {
    val scheme = MaterialTheme.colorScheme
    val cover = playingCoverUrl(state, ServerUrls.COVER_LARGE)
    Artwork(
        url = bannerUrl ?: cover,
        fallbackUrl = cover.takeIf { bannerUrl != null },
        contentDescription = null,
        shape = RectangleShape,
        placeholderIcon = if (state.episode != null) DowntifyIcons.Podcasts else DowntifyIcons.Song,
        crossfade = true,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = PICTURE_ALPHA },
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to scheme.surface.copy(alpha = 0f),
                    SCRIM_START to scheme.surface.copy(alpha = SCRIM_MIDDLE_ALPHA),
                    1f to scheme.surface.copy(alpha = SCRIM_BOTTOM_ALPHA),
                ),
            ),
    )
}

/**
 * Lays the content out at [fraction] of its height, 0 taking no room at all, while it stays composed
 * and its focus stays where it was (the controls once they fade). It's drawn from its top, past
 * the room it takes, so it must be invisible by then.
 */
private fun Modifier.foldAway(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    // The expressive spring overshoots past 0 and 1: a height can't.
    layout(placeable.width, (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()) { placeable.place(0, 0) }
}

/**
 * "About the artist", as the TV music apps show it: their photo in a large circle (the cover when
 * the server has none), their name and a "Go to artist" button. The circle and the button both open their page.
 */
@Composable
private fun AboutTheArtist(
    name: String,
    photoUrl: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            stringResource(R.string.tv_about_artist),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Artwork(
                url = photoUrl,
                fallbackUrl = coverUrl,
                contentDescription = null,
                shape = CircleShape,
                placeholderIcon = DowntifyIcons.Artist,
                modifier = Modifier
                    .offset(x = -TvRingSpace)
                    .tvFocusFrame(focused, CircleShape)
                    .clickable(
                        interaction,
                        indication = null,
                        onClickLabel = name,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .size(ArtistSize),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TvLabeledButton(DowntifyIcons.Artist, stringResource(R.string.track_go_to_artist), onClick)
            }
        }
    }
}

/** Said for a few seconds once dark mode is on, at the top end. */
@Composable
private fun DarkModeNotice() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .widthIn(max = NoticeWidth)
            .background(scheme.surfaceContainerHigh, NextUpShape)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Icon(
            painterResource(DowntifyIcons.DarkMode),
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(
                stringResource(R.string.tv_dark_mode_on),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.tv_dark_mode_on_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** What plays next: "Next up" over its title and artist, beside its cover. */
@Composable
private fun NextUpCard(entry: QueueEntry) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .widthIn(max = NextUpWidth)
            .background(scheme.surfaceContainerHigh.copy(alpha = PILL_ALPHA), NextUpShape)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Artwork(
            url = artworkOf(entry),
            contentDescription = null,
            shape = CoverShape,
            modifier = Modifier.size(NextUpCover),
        )
        Column(Modifier.padding(end = Spacing.sm)) {
            Text(
                stringResource(R.string.player_queue_next_up),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.artist,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A thin seek bar with the elapsed and total times under it. Focused, it thickens and shows its
 * thumb; left and right move [SEEK_STEP_MS] back or forward.
 */
@Composable
private fun TvSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val duration = durationMs.coerceAtLeast(1)
    val progress = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val label = stringResource(R.string.player_seek)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(SeekTouchHeight)
                .semantics { contentDescription = label }
                .progressSemantics(progress)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onSeek((positionMs - SEEK_STEP_MS).coerceAtLeast(0))
                        Key.DirectionRight -> onSeek((positionMs + SEEK_STEP_MS).coerceAtMost(durationMs))
                        else -> return@onKeyEvent false
                    }
                    true
                }
                .focusable(interactionSource = interaction)
                .drawBehind {
                    val thickness = (if (focused) SeekFocusedThickness else SeekThickness).toPx()
                    val top = (size.height - thickness) / 2
                    val radius = CornerRadius(thickness / 2)
                    drawRoundRect(
                        scheme.onSurface.copy(alpha = TRACK_ALPHA),
                        Offset(0f, top),
                        Size(size.width, thickness),
                        radius,
                    )
                    drawRoundRect(scheme.onSurface, Offset(0f, top), Size(size.width * progress, thickness), radius)
                    if (focused) {
                        drawCircle(
                            scheme.onSurface,
                            SeekThumb.toPx(),
                            Offset(
                                size.width * progress,
                                size.height / 2,
                            ),
                        )
                    }
                },
        )
        Row {
            Text(
                formatDuration(positionMs / MS_PER_SECOND),
                style = DowntifyType.dataSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(durationMs / MS_PER_SECOND),
                style = DowntifyType.dataSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Play/pause: the large filled disc of the row, ringed when focused. */
@Composable
private fun TvPlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(
        modifier
            .tvFocusFrame(focused, CircleShape)
            .clickable(interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .size(PlaySize)
            .background(scheme.onSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
            contentDescription = label,
            tint = scheme.surface,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** A round toggle: its icon in the accent color, with a dot under it, while on. */
@Composable
private fun TvToggle(icon: Int, label: String, checked: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val tint = if (checked) scheme.primary else scheme.onSurface
    Box(
        Modifier
            .tvFocusFrame(focused, CircleShape)
            .clickable(interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .size(ButtonSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        if (checked) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xxs)
                    .size(4.dp)
                    .background(scheme.primary, CircleShape),
            )
        }
    }
}

/** A pill with an icon and a word: lyrics, the queue. Filled while [selected] (the lyrics showing). */
@Composable
private fun TvLabeledButton(icon: Int, label: String, onClick: () -> Unit, selected: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val content = if (selected) scheme.surface else scheme.onSurface
    Row(
        Modifier
            .tvFocusFrame(focused, CircleShape)
            .selectable(
                selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .background(
                if (selected) scheme.onSurface else scheme.surfaceContainerHigh.copy(alpha = PILL_ALPHA),
                CircleShape,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

private val RepeatMode.label: Int
    get() = when (this) {
        RepeatMode.Off -> R.string.player_repeat_off
        RepeatMode.All -> R.string.player_repeat_all
        RepeatMode.One -> R.string.player_repeat_one
    }

private val PlaybackError.message: Int
    get() = when (this) {
        PlaybackError.Network, PlaybackError.Unauthorized -> R.string.player_error_network
        PlaybackError.NotFound -> R.string.player_error_not_found
        PlaybackError.OldServer -> R.string.player_error_old_server
        PlaybackError.Other -> R.string.player_error_other
    }

/** The remote's keys that wake the controls: the arrows and OK, not Back or the media keys. */
private val RemoteKeys = setOf(
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
)

private const val AUTO_HIDE_MS = 5_000L
private const val DARK_ALPHA = 0.7f
private const val DARK_AMBIENT_ALPHA = 0.3f
private const val DARK_NOTICE_MS = 4_000L
private val AboutPeek = 44.dp
private val ArtistSize = 120.dp
private val NoticeWidth = 420.dp

/** How long before the end of a song what plays next shows. */
private const val NEXT_UP_MS = 15_000L
private val NextUpWidth = 320.dp
private val NextUpCover = 48.dp
private val NextUpShape = RoundedCornerShape(12.dp)
private const val SEEK_STEP_MS = 10_000L
private const val MS_PER_SECOND = 1000.0
private const val PICTURE_ALPHA = 0.55f
private const val SCRIM_START = 0.35f
private const val SCRIM_MIDDLE_ALPHA = 0.55f
private const val SCRIM_BOTTOM_ALPHA = 0.95f
private const val TRACK_ALPHA = 0.3f
private const val PILL_ALPHA = 0.8f
private val CoverSize = 96.dp
private val CoverShape = RoundedCornerShape(8.dp)
private val PlaySize = 56.dp
private val ButtonSize = 44.dp
private val SeekTouchHeight = 16.dp
private val SeekThickness = 4.dp
private val SeekFocusedThickness = 6.dp
private val SeekThumb = 7.dp

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvPlayerPreview() {
    DowntifyTheme(darkTheme = true) {
        TvPlayerScreen(
            state = PlayerState(
                track = PreviewData.tracks[1],
                isPlaying = false,
                positionMs = 70_000,
                durationMs = 239_000,
                shuffle = true,
            ),
            isLiked = true,
            actions = NowPlayingActions(),
        )
    }
}
