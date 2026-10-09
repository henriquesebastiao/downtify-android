package com.henriquesebastiao.downtify.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.SourceBadge
import com.henriquesebastiao.downtify.core.designsystem.component.SourceBrand
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.LinkKind
import com.henriquesebastiao.downtify.core.model.RemoteAlbum
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ResolvedLink
import com.henriquesebastiao.downtify.core.model.ServerDownloadProgress
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.formatDuration

/**
 * A song the server can download: tapping anywhere plays it in full from
 * the server. Everything else lives in the row's menu: what sounds like
 * it, the download (then its progress), and — once the song is on the
 * server — the library actions (like, lyrics) instead of the download.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RemoteSongRow(
    song: RemoteSong,
    job: ServerJob?,
    playing: Boolean,
    resolving: Boolean,
    onPlay: () -> Unit,
    onSimilar: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    libraryTrack: Track? = null,
    isLiked: Boolean = false,
    onToggleLike: (Track) -> Unit = {},
    onShowLyrics: (Track) -> Unit = {},
) {
    val active = job?.status == ServerJobStatus.Queued || job?.status == ServerJobStatus.Downloading
    ListItem(
        headlineContent = {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (playing) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.basicMarquee(),
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceLabel(song.source)
                Text(
                    listOf(song.artist, song.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toDouble()) })
                        .filterNot { it.isNullOrBlank() }
                        .joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        },
        leadingContent = {
            PlayCover(song, playing, resolving, onPlay)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (job?.status == ServerJobStatus.Done) {
                    Icon(
                        painterResource(DowntifyIcons.CheckCircle),
                        contentDescription = stringResource(R.string.search_job_done),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (active) {
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
                        if (job.status == ServerJobStatus.Downloading && job.progress > 0f) {
                            CircularProgressIndicator(
                                progress = { job.progress / PERCENT },
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    RemoteSongMenu(
                        song = song,
                        job = job,
                        libraryTrack = libraryTrack,
                        isLiked = isLiked,
                        onSimilar = onSimilar,
                        onDownload = onDownload,
                        onToggleLike = onToggleLike,
                        onShowLyrics = onShowLyrics,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.search_play_full, song.title),
            role = Role.Button,
            onClick = onPlay,
        ),
    )
}

/** What a track row's menu can do: similar and download, then library actions once downloaded. */
@Composable
private fun RemoteSongMenu(
    song: RemoteSong,
    job: ServerJob?,
    libraryTrack: Track?,
    isLiked: Boolean,
    onSimilar: () -> Unit,
    onDownload: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onShowLyrics: (Track) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(DowntifyIcons.MoreVert),
                contentDescription = stringResource(R.string.track_more, song.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_similar, song.title)) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Explore), contentDescription = null) },
                onClick = {
                    open = false
                    onSimilar()
                },
            )
            if (job == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_download_to_server, song.title)) },
                    leadingIcon = {
                        Icon(
                            painterResource(DowntifyIcons.Downloads),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        open = false
                        onDownload()
                    },
                )
            }
            if (job?.status == ServerJobStatus.Error) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_retry)) },
                    leadingIcon = {
                        Icon(
                            painterResource(DowntifyIcons.Refresh),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        open = false
                        onDownload()
                    },
                )
            }
            if (job?.status == ServerJobStatus.Done && libraryTrack != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (isLiked) R.string.track_unlike else R.string.track_like)) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (isLiked) DowntifyIcons.FavoriteFilled else DowntifyIcons.Favorite),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        open = false
                        onToggleLike(libraryTrack)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_lyrics)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Lyrics), contentDescription = null) },
                    onClick = {
                        open = false
                        onShowLyrics(libraryTrack)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayCover(song: RemoteSong, playing: Boolean, resolving: Boolean, onPlay: () -> Unit) {
    val label = stringResource(
        if (playing) R.string.search_stop_full else R.string.search_play_full,
        song.title,
    )
    Box(
        Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onPlay)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            CoverArt(url = song.coverUrl.ifBlank { null }, contentDescription = null, modifier = Modifier.size(56.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    resolving -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                    )

                    playing -> Icon(
                        painterResource(DowntifyIcons.Pause),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )

                    else -> Icon(
                        painterResource(DowntifyIcons.Play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceLabel(source: CatalogSource) {
    when (source) {
        CatalogSource.Spotify -> SourceBadge(stringResource(R.string.search_source_spotify), SourceBrand.Spotify)
        CatalogSource.YouTube -> SourceBadge(stringResource(R.string.search_source_youtube), SourceBrand.YouTube)
        CatalogSource.TextSearch -> SourceBadge(stringResource(R.string.search_source_soulseek), SourceBrand.Neutral)
        CatalogSource.Other -> Unit
    }
}

/**
 * An album on YouTube Music: tapping opens its tracks (listen first),
 * the button downloads it all, then "Downloading 4/9".
 */
@Composable
internal fun RemoteAlbumRow(
    album: RemoteAlbum,
    progress: ServerDownloadProgress?,
    onDownload: () -> Unit,
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                listOf(
                    album.releaseType.ifBlank {
                        stringResource(R.string.search_kind_album)
                    },
                    album.artist,
                    album.year,
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            CoverArt(url = album.coverUrl.ifBlank { null }, contentDescription = null, modifier = Modifier.size(56.dp))
        },
        trailingContent = {
            when {
                progress == null -> IconButton(onClick = onDownload) {
                    Icon(
                        painterResource(DowntifyIcons.Downloads),
                        contentDescription = stringResource(R.string.search_download_to_server, album.title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                else -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                ) {
                    Text(
                        if (progress.isActive) {
                            stringResource(R.string.search_album_progress, progress.done, progress.total)
                        } else {
                            stringResource(R.string.search_album_done)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(
            onClickLabel = album.title,
            role = Role.Button,
            onClick = onOpen,
        ),
    )
}

/** What a pasted link points at: listen to all of it first, then download it all. */
@Composable
internal fun LinkHeader(
    link: ResolvedLink,
    requested: Boolean,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            url = link.coverUrl.ifBlank { null },
            contentDescription = null,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(96.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(
                    when (link.kind) {
                        LinkKind.Track -> R.string.search_kind_song
                        LinkKind.Album -> R.string.search_kind_album
                        LinkKind.Playlist -> R.string.search_kind_playlist
                        LinkKind.Artist -> R.string.search_kind_artist
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(link.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (link.kind != LinkKind.Artist && link.subtitle.isNotBlank()) {
                Text(
                    listOf(link.subtitle, link.year).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (link.tracks.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FilledTonalButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painterResource(DowntifyIcons.Play),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.search_link_play_all),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                    FilledTonalButton(onClick = onDownload, enabled = !requested, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painterResource(DowntifyIcons.Downloads),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.search_link_download_all,
                                link.tracks.size,
                                link.tracks.size,
                            ),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

private const val SCRIM = 0.6f
private const val PERCENT = 100f
