package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
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
 * [hasLibraryCopy] is true when the playing stream has since been
 * downloaded: the stream keeps playing, but the buttons switch to the
 * library set (like, lyrics) and the download button goes away.
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
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
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
                Header(state.context, track?.albumId.orEmpty(), track?.artistId.orEmpty(), episode, colors, actions)
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
                    onArtist = {
                        if (episode !=
                            null
                        ) {
                            actions.onGoToShow(episode.showId)
                        } else {
                            actions.onGoToArtist(track?.artistId.orEmpty())
                        }
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
                // Repeat lives here, next to lyrics and the queue: the transport
                // row stays five buttons wide (shuffle, previous, play, next,
                // similar), so the last one is never squeezed off-screen.
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.End) {
                    if (episode == null) {
                        IconToggleButton(
                            checked = state.repeat != RepeatMode.Off,
                            onCheckedChange = { actions.onCycleRepeat() },
                            colors = iconToggleButtonColors(
                                contentColor = colors.onSurfaceVariant,
                                checkedContentColor = MaterialTheme.colorScheme.primary,
                            ),
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
                    if (stream != null && !hasLibraryCopy) {
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
    onArtist: () -> Unit,
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
                modifier = Modifier.clickable(onClick = onArtist).basicMarquee(),
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

/** An episode: speed, 10 s back, play, 30 s forward — in place of shuffle, previous, next and repeat. */
@Composable
private fun EpisodeControls(state: PlayerState, colors: CoverColors, actions: NowPlayingActions) {
    val round = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.onSurface.copy(alpha = 0.12f),
        contentColor = colors.onSurface,
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val speedText = speedLabel(state.speed)
        val speedDescription = stringResource(R.string.player_speed, speedText)
        TextButton(
            onClick = actions.onCycleSpeed,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
            modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = speedDescription },
        ) { Text(speedText, style = MaterialTheme.typography.labelLarge) }
        FilledTonalIconButton(onClick = actions.onSkipBack, colors = round, modifier = Modifier.size(64.dp)) {
            Icon(
                painterResource(DowntifyIcons.SkipBack),
                contentDescription = stringResource(R.string.player_skip_back),
            )
        }
        FilledIconButton(
            onClick = actions.onTogglePlay,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(width = 104.dp, height = 80.dp),
        ) {
            Icon(
                painterResource(if (state.isPlaying) DowntifyIcons.Pause else DowntifyIcons.Play),
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.player_pause else R.string.player_play,
                ),
                modifier = Modifier.size(36.dp),
            )
        }
        FilledTonalIconButton(onClick = actions.onSkipForward, colors = round, modifier = Modifier.size(64.dp)) {
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
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.onSurface,
                activeTrackColor = colors.onSurface,
                inactiveTrackColor = colors.onSurface.copy(alpha = 0.22f),
            ),
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
private fun Controls(state: PlayerState, colors: CoverColors, actions: NowPlayingActions) {
    val toggleColors = iconToggleButtonColors(
        contentColor = colors.onSurfaceVariant,
        checkedContentColor = MaterialTheme.colorScheme.primary,
    )
    val round = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.onSurface.copy(alpha = 0.12f),
        contentColor = colors.onSurface,
    )
    // Transport order: shuffle, previous, play, next — and tracks like this
    // one last on the right (episodes have none).
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        FilledTonalIconButton(onClick = actions.onPrevious, colors = round, modifier = Modifier.size(64.dp)) {
            Icon(painterResource(DowntifyIcons.Previous), contentDescription = stringResource(R.string.player_previous))
        }
        FilledIconButton(
            onClick = actions.onTogglePlay,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(width = 104.dp, height = 80.dp),
        ) {
            Icon(
                painterResource(if (state.isPlaying) DowntifyIcons.Pause else DowntifyIcons.Play),
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.player_pause else R.string.player_play,
                ),
                modifier = Modifier.size(36.dp),
            )
        }
        FilledTonalIconButton(onClick = actions.onNext, colors = round, modifier = Modifier.size(64.dp)) {
            Icon(painterResource(DowntifyIcons.Next), contentDescription = stringResource(R.string.player_next))
        }
        val similarArtist = state.track?.displayArtist ?: state.stream?.artist.orEmpty()
        val similarTitle = state.track?.displayTitle ?: state.stream?.title.orEmpty()
        if (state.episode == null && similarArtist.isNotBlank() && similarTitle.isNotBlank()) {
            IconButton(onClick = { actions.onSimilar(similarArtist, similarTitle) }) {
                Icon(
                    painterResource(DowntifyIcons.Explore),
                    contentDescription = stringResource(R.string.search_similar, similarTitle),
                    tint = colors.onSurfaceVariant,
                )
            }
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
