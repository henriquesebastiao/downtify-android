package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.iconToggleButtonColors
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.CoverColors
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.rememberCoverColors
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.codecLabel
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlaybackError
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingEpisode
import com.henriquesebastiao.downtify.core.player.RepeatMode
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData

/** Everything Now Playing can ask for. */
data class NowPlayingActions(
    val onCollapse: () -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onCycleRepeat: () -> Unit = {},
    val onToggleLike: () -> Unit = {},
    val onSimilar: (artist: String, title: String) -> Unit = { _, _ -> },
    val onOpenLyrics: () -> Unit = {},
    val onOpenQueue: () -> Unit = {},
    val onOpenContext: (PlaybackContext) -> Unit = {},
    val onSkipBack: () -> Unit = {},
    val onSkipForward: () -> Unit = {},
    val onCycleSpeed: () -> Unit = {},
    val onGoToShow: (Long) -> Unit = {},
    val onGoToAlbum: (String) -> Unit = {},
    val onGoToArtist: (String) -> Unit = {},
    val onDownloadStream: () -> Unit = {},
)

/**
 * [hasLibraryCopy] is true when the playing stream has since been downloaded (a library track
 * matches it, passed as [libraryTrack]): the stream keeps playing, but the buttons switch to
 * the library set (like, lyrics, the header menu) and the download button goes away. A finished
 * server job ([ServerJobStatus.Done]) hides the download button at once; like, lyrics and the
 * menu appear with the library sync, when there is a track id behind them to act on.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    isLiked: Boolean,
    serverName: String,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    downloadJob: ServerJob? = null,
    hasLibraryCopy: Boolean = false,
    libraryTrack: Track? = null,
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    val downloaded = hasLibraryCopy || downloadJob?.status == ServerJobStatus.Done
    // What the header menu and the artist tap navigate to: the library copy behind a stream.
    val menuTrack = track ?: libraryTrack.takeIf { stream != null }
    val coverUrls = LocalCoverUrls.current
    // An episode's artwork is the publisher's own, at whatever size they serve; a stream's cover
    // is the absolute address the search gave, not a server cover.
    val coverUrl = episode?.artworkUrl?.ifBlank { null }
        ?: stream?.coverUrl?.ifBlank { null }
        ?: track?.let { coverUrls.track(it.id.takeIf { _ -> it.hasCover }, ServerUrls.COVER_LARGE) }
    val colors = rememberCoverColors(
        episode?.artworkUrl?.ifBlank { null }
            ?: stream?.coverUrl?.ifBlank { null }
            ?: track?.let { coverUrls.track(it.id.takeIf { _ -> it.hasCover }, ServerUrls.COVER_SMALL) },
    )

    Surface(color = colors.surface, contentColor = colors.onSurface, modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compactHeight = maxHeight < 640.dp
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(
                    state.context,
                    menuTrack?.albumId.orEmpty(),
                    menuTrack?.artistId.orEmpty(),
                    episode,
                    colors,
                    actions,
                )
                Spacer(Modifier.weight(0.3f))
                CoverArt(
                    url = coverUrl,
                    contentDescription = null,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .widthIn(max = if (compactHeight) 240.dp else 420.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(24.dp, MaterialTheme.shapes.extraLarge),
                )
                Spacer(Modifier.weight(0.4f))
                TitleRow(
                    title = track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
                    artist = track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
                    isLiked = isLiked.takeIf { episode == null && (stream == null || hasLibraryCopy) },
                    colors = colors,
                    onArtist = when {
                        episode != null -> ({ actions.onGoToShow(episode.showId) })
                        menuTrack?.artistId?.isNotEmpty() == true -> ({ actions.onGoToArtist(menuTrack.artistId) })
                        else -> null
                    },
                    onToggleLike = actions.onToggleLike,
                )
                SeekBar(state, colors, actions.onSeek)
                if (episode != null) EpisodeControls(state, colors, actions) else Controls(state, colors, actions)
                Text(
                    if (track != null) {
                        sourceLine(serverName, track.codec, state.quality, state.fromPhone)
                    } else if (stream != null) {
                        stringResource(R.string.player_source_stream, serverName)
                    } else {
                        stringResource(R.string.player_source_unknown, serverName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                if (state.readAheadTarget > 0) {
                    Text(
                        stringResource(R.string.player_buffer_ready, state.readAheadReady, state.readAheadTarget),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                state.error?.let { error ->
                    Text(
                        errorText(error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                Spacer(Modifier.weight(0.3f))
                // Similar lives here, next to lyrics and the queue: the transport
                // row stays five buttons wide (shuffle, previous, play, next,
                // repeat), so the last one is never squeezed off-screen.
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.End) {
                    val similarArtist = state.track?.displayArtist ?: state.stream?.artist.orEmpty()
                    val similarTitle = state.track?.displayTitle ?: state.stream?.title.orEmpty()
                    if (episode == null && similarArtist.isNotBlank() && similarTitle.isNotBlank()) {
                        IconButton(onClick = { actions.onSimilar(similarArtist, similarTitle) }) {
                            Icon(
                                painterResource(DowntifyIcons.Explore),
                                contentDescription = stringResource(R.string.search_similar, similarTitle),
                            )
                        }
                    }
                    if (stream != null && !downloaded) {
                        StreamDownloadButton(
                            job = downloadJob,
                            title = stream.title,
                            onDownload = actions.onDownloadStream,
                        )
                    }
                    if (episode == null && (stream == null || hasLibraryCopy)) {
                        IconButton(onClick = actions.onOpenLyrics) {
                            Icon(
                                painterResource(DowntifyIcons.Lyrics),
                                contentDescription = stringResource(R.string.player_lyrics),
                            )
                        }
                    }
                    if (episode == null) {
                        IconButton(onClick = actions.onOpenQueue) {
                            Icon(
                                painterResource(DowntifyIcons.Queue),
                                contentDescription = stringResource(R.string.player_queue),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    context: PlaybackContext?,
    albumId: String,
    artistId: String,
    episode: PlayingEpisode?,
    colors: CoverColors,
    actions: NowPlayingActions,
) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onCollapse) {
            Icon(painterResource(DowntifyIcons.Collapse), contentDescription = stringResource(R.string.player_close))
        }
        Column(
            Modifier
                .weight(1f)
                .then(
                    when {
                        episode != null -> Modifier.clickable { actions.onGoToShow(episode.showId) }
                        context != null -> Modifier.clickable { actions.onOpenContext(context) }
                        else -> Modifier
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (episode != null) {
                Text(
                    stringResource(R.string.player_playing_from_podcast).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    episode.showName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (context != null) {
                Text(
                    stringResource(playingFromLabel(context.type)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    context.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A stream with no library copy goes nowhere: no album, no artist, no show.
        if (episode == null && albumId.isEmpty() && artistId.isEmpty()) {
            Spacer(Modifier.size(48.dp))
            return@Row
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(DowntifyIcons.MoreVert), contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (episode != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_go_to_show)) },
                        leadingIcon = { Icon(painterResource(DowntifyIcons.Podcasts), contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.onGoToShow(episode.showId)
                        },
                    )
                }
                if (albumId.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.track_go_to_album)) },
                        leadingIcon = { Icon(painterResource(DowntifyIcons.Album), contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.onGoToAlbum(albumId)
                        },
                    )
                }
                if (artistId.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.track_go_to_artist)) },
                        leadingIcon = { Icon(painterResource(DowntifyIcons.Artist), contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.onGoToArtist(artistId)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleRow(
    title: String,
    artist: String,
    isLiked: Boolean?,
    colors: CoverColors,
    onArtist: (() -> Unit)?,
    onToggleLike: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                artist,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtist != null) {
                    Modifier.clickable(onClick = onArtist).basicMarquee()
                } else {
                    Modifier.basicMarquee()
                },
            )
        }
        // Nothing to like on an episode.
        if (isLiked != null) {
            IconToggleButton(
                checked = isLiked,
                onCheckedChange = { onToggleLike() },
                colors = iconToggleButtonColors(checkedContentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painterResource(if (isLiked) DowntifyIcons.FavoriteFilled else DowntifyIcons.Favorite),
                    contentDescription = stringResource(if (isLiked) R.string.player_unlike else R.string.player_like),
                )
            }
        }
    }
}

/** How big the transport buttons are. The side buttons (shuffle, repeat, speed) stay 48 dp. */
private data class TransportSizes(val skip: Dp, val playWidth: Dp, val playHeight: Dp, val playIcon: Dp)

/** The design's sizes, in dp: the skip buttons, and play's width, height and icon. */
private const val SKIP_DP = 64f
private const val PLAY_WIDTH_DP = 104f
private const val PLAY_HEIGHT_DP = 80f
private const val PLAY_ICON_DP = 36f

/** Two side buttons of 48 dp, and the three big ones at full size: 64 + 104 + 64 dp. */
private const val SIDE_BUTTONS_DP = 2 * 48f
private const val BIG_BUTTONS_DP = SKIP_DP * 2 + PLAY_WIDTH_DP

/** The least space kept between two buttons, so they never touch. */
private const val MIN_GAP_DP = 8f

/** Never smaller than this fraction: the skip buttons stay at 48 dp, the touch target minimum. */
private const val MIN_SCALE = 0.75f

/**
 * The big buttons at the design's size when there is room for the design's spacing, and smaller —
 * down to [MIN_SCALE] — when there isn't. A 360 dp phone leaves 328 dp, exactly what the full-size
 * buttons take, so they used to touch.
 */
private fun transportSizes(rowWidth: Dp): TransportSizes {
    val room = rowWidth.value - SIDE_BUTTONS_DP - MIN_GAP_DP * 4
    val scale = (room / BIG_BUTTONS_DP).coerceIn(MIN_SCALE, 1f)
    return TransportSizes(
        skip = (SKIP_DP * scale).dp,
        playWidth = (PLAY_WIDTH_DP * scale).dp,
        playHeight = (PLAY_HEIGHT_DP * scale).dp,
        playIcon = (PLAY_ICON_DP * scale).dp,
    )
}

/** The transport buttons in one row with an even gap between them (the design's spacing). */
@Composable
private fun TransportRow(content: @Composable RowScope.(TransportSizes) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        val sizes = transportSizes(maxWidth)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) { content(sizes) }
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, sizes: TransportSizes, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.size(width = sizes.playWidth, height = sizes.playHeight),
    ) {
        Icon(
            painterResource(if (isPlaying) DowntifyIcons.Pause else DowntifyIcons.Play),
            contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            modifier = Modifier.size(sizes.playIcon),
        )
    }
}

/** An episode: speed, 10 s back, play, 30 s forward — in place of shuffle, previous, next and repeat. */
@Composable
private fun EpisodeControls(state: PlayerState, colors: CoverColors, actions: NowPlayingActions) {
    val round = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.onSurface.copy(alpha = 0.12f),
        contentColor = colors.onSurface,
    )
    TransportRow { sizes ->
        val speedText = speedLabel(state.speed)
        val speedDescription = stringResource(R.string.player_speed, speedText)
        TextButton(
            onClick = actions.onCycleSpeed,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
            modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = speedDescription },
        ) { Text(speedText, style = MaterialTheme.typography.labelLarge) }
        FilledTonalIconButton(onClick = actions.onSkipBack, colors = round, modifier = Modifier.size(sizes.skip)) {
            Icon(
                painterResource(DowntifyIcons.SkipBack),
                contentDescription = stringResource(R.string.player_skip_back),
            )
        }
        PlayButton(state.isPlaying, sizes, actions.onTogglePlay)
        FilledTonalIconButton(onClick = actions.onSkipForward, colors = round, modifier = Modifier.size(sizes.skip)) {
            Icon(
                painterResource(DowntifyIcons.SkipForward),
                contentDescription = stringResource(R.string.player_skip_forward),
            )
        }
        Spacer(Modifier.size(48.dp))
    }
}

/** `1×`, `1.25×`: no trailing zeros. */
private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(state: PlayerState, colors: CoverColors, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val value = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = (value * duration).toLong()
    val positionText = formatDuration(shownMs / 1000.0)
    val durationText = formatDuration(state.durationMs / 1000.0)
    val seekLabel = stringResource(R.string.player_seek)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        val sliderColors = SliderDefaults.colors(
            thumbColor = colors.onSurface,
            activeTrackColor = colors.onSurface,
            inactiveTrackColor = colors.onSurface.copy(alpha = 0.22f),
        )
        val interactionSource = remember { MutableInteractionSource() }
        // The slider's own state follows the player's position — except while the finger is on it.
        val sliderState = rememberSliderState()
        LaunchedEffect(value) { sliderState.value = value }
        Slider(
            state = sliderState,
            onValueChange = {
                sliderState.value = it
                dragging = it
            },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = sliderColors,
            interactionSource = interactionSource,
            // Material 3's own track (16 dp, with the gap and the stop dot). Only the thumb is
            // drawn by hand: the 1.5 one shrinks to half its width while pressed.
            thumb = {
                Box(
                    Modifier
                        .size(SEEK_THUMB_WIDTH, SEEK_THUMB_HEIGHT)
                        .background(sliderColors.thumbColor, RoundedCornerShape(percent = 50)),
                )
            },
            modifier = Modifier.semantics {
                contentDescription = seekLabel
                stateDescription = "$positionText / $durationText"
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(positionText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(durationText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Controls(state: PlayerState, colors: CoverColors, actions: NowPlayingActions) {
    val toggleColors = iconToggleButtonColors(
        contentColor = colors.onSurfaceVariant,
        checkedContentColor = MaterialTheme.colorScheme.primary,
    )
    val round = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.onSurface.copy(alpha = 0.12f),
        contentColor = colors.onSurface,
    )
    // Transport order: shuffle, previous, play, next, repeat — tracks like
    // this one moved to the bottom row, next to lyrics and the queue
    // (episodes have neither).
    TransportRow { sizes ->
        IconToggleButton(checked = state.shuffle, onCheckedChange = {
            actions.onToggleShuffle()
        }, colors = toggleColors) {
            Icon(
                painterResource(DowntifyIcons.Shuffle),
                contentDescription = stringResource(
                    if (state.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off,
                ),
            )
        }
        FilledTonalIconButton(onClick = actions.onPrevious, colors = round, modifier = Modifier.size(sizes.skip)) {
            Icon(painterResource(DowntifyIcons.Previous), contentDescription = stringResource(R.string.player_previous))
        }
        PlayButton(state.isPlaying, sizes, actions.onTogglePlay)
        FilledTonalIconButton(onClick = actions.onNext, colors = round, modifier = Modifier.size(sizes.skip)) {
            Icon(painterResource(DowntifyIcons.Next), contentDescription = stringResource(R.string.player_next))
        }
        IconToggleButton(
            checked = state.repeat != RepeatMode.Off,
            onCheckedChange = { actions.onCycleRepeat() },
            colors = toggleColors,
        ) {
            Icon(
                painterResource(
                    if (state.repeat == RepeatMode.One) {
                        DowntifyIcons.RepeatOne
                    } else {
                        DowntifyIcons.Repeat
                    },
                ),
                contentDescription = stringResource(
                    when (state.repeat) {
                        RepeatMode.Off -> R.string.player_repeat_off
                        RepeatMode.All -> R.string.player_repeat_all
                        RepeatMode.One -> R.string.player_repeat_one
                    },
                ),
            )
        }
    }
}

/** Download the playing stream to the server, then what the server is doing with it. */
@Composable
private fun StreamDownloadButton(job: ServerJob?, title: String, onDownload: () -> Unit) {
    when (job?.status) {
        null -> IconButton(onClick = onDownload) {
            Icon(
                painterResource(DowntifyIcons.Downloads),
                contentDescription = stringResource(R.string.search_download_to_server, title),
            )
        }

        ServerJobStatus.Queued, ServerJobStatus.Downloading -> {
            val label = stringResource(
                if (job.status == ServerJobStatus.Queued) {
                    R.string.search_job_queued
                } else {
                    R.string.search_job_downloading
                },
            )
            Box(
                Modifier.size(48.dp).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }

        ServerJobStatus.Done -> Icon(
            painterResource(DowntifyIcons.CheckCircle),
            contentDescription = stringResource(R.string.search_job_done),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).padding(12.dp),
        )

        ServerJobStatus.Error -> IconButton(onClick = onDownload) {
            Icon(
                painterResource(DowntifyIcons.Error),
                contentDescription = stringResource(R.string.search_job_error),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun playingFromLabel(type: PlaybackContextType): Int = when (type) {
    PlaybackContextType.Album -> R.string.player_playing_from_album
    PlaybackContextType.Artist -> R.string.player_playing_from_artist
    PlaybackContextType.Playlist, PlaybackContextType.Liked -> R.string.player_playing_from_playlist
    PlaybackContextType.Songs -> R.string.player_playing_from_library
}

@Composable
private fun sourceLine(server: String, codec: String, quality: StreamQuality?, fromPhone: Boolean): String = when {
    fromPhone -> stringResource(R.string.player_source_phone, codecLabel(codec.ifBlank { "audio" }))

    quality == null -> stringResource(R.string.player_source_unknown, server)

    quality.isOriginal -> stringResource(R.string.player_source_original, server, codecLabel(codec.ifBlank { "audio" }))

    else -> stringResource(
        R.string.player_source_transcoded,
        server,
        codecLabel(quality.format.wireName),
        quality.bitrate,
    )
}

@Composable
private fun errorText(error: PlaybackError): String = stringResource(
    when (error) {
        PlaybackError.Network, PlaybackError.Unauthorized -> R.string.player_error_network
        PlaybackError.NotFound -> R.string.player_error_not_found
        PlaybackError.OldServer -> R.string.player_error_old_server
        PlaybackError.Other -> R.string.player_error_other
    },
)

@PreviewLightDark
@Composable
private fun NowPlayingPreview() {
    DowntifyTheme {
        NowPlayingScreen(
            state = PlayerState(
                track = PreviewData.tracks[1],
                isPlaying = true,
                positionMs = 70_000,
                durationMs = 239_000,
                context = PlaybackContext(PlaybackContextType.Album, "alb-Glass Harbor", "Glass Harbor"),
                quality = StreamQuality.Original,
            ),
            isLiked = true,
            serverName = "nas.local",
            actions = NowPlayingActions(),
            modifier = Modifier.background(Color.Black),
        )
    }
}

private val SEEK_THUMB_WIDTH = 4.dp
private val SEEK_THUMB_HEIGHT = 44.dp
