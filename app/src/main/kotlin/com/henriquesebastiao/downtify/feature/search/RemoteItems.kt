package com.henriquesebastiao.downtify.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.component.SectionCard
import com.henriquesebastiao.downtify.core.designsystem.component.SectionTone
import com.henriquesebastiao.downtify.core.designsystem.component.SourceBadge
import com.henriquesebastiao.downtify.core.designsystem.component.SourceBrand
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
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
 * A song the server can download, as a card of a separated list: tapping anywhere plays it in
 * full from the server. Its source and artist under the title, its duration in tabular figures.
 * Everything else lives in the row's menu: what sounds like it, the download (then its progress),
 * and — once the song is in the library — the library actions (like, lyrics) instead of the
 * download.
 */
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
    isPlaying: Boolean = false,
    onToggleLike: (Track) -> Unit = {},
    onShowLyrics: (Track) -> Unit = {},
) {
    val active = job?.status == ServerJobStatus.Queued || job?.status == ServerJobStatus.Downloading
    MediaListItem(
        title = song.title,
        subtitle = song.artist,
        meta = song.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toDouble()) },
        onClick = onPlay,
        active = playing,
        supportingLeading = { SourceLabel(song.source) },
        leading = { PlayCover(song, playing, isPlaying, resolving, onPlay) },
        trailing = {
            if (job?.status == ServerJobStatus.Done) {
                Icon(
                    painterResource(DowntifyIcons.CheckCircle),
                    contentDescription = stringResource(R.string.search_job_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (active) {
                JobProgress(job)
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
        },
        modifier = modifier,
    )
}

/** A download on the server: a long wait, so the wavy indicator — filling once the server says how far it got. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JobProgress(job: ServerJob) {
    val label = stringResource(
        if (job.status == ServerJobStatus.Queued) R.string.search_job_queued else R.string.search_job_downloading,
    )
    Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (job.status == ServerJobStatus.Downloading && job.progress > 0f) {
            CircularWavyProgressIndicator(progress = { job.progress / PERCENT }, modifier = Modifier.size(32.dp))
        } else {
            CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
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
        CircleButton(DowntifyIcons.MoreVert, stringResource(R.string.track_more, song.title), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_similar, song.title)) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Explore), contentDescription = null) },
                onClick = {
                    open = false
                    onSimilar()
                },
            )
            if (job == null && libraryTrack == null) {
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
            if (libraryTrack != null) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayCover(song: RemoteSong, playing: Boolean, isPlaying: Boolean, resolving: Boolean, onPlay: () -> Unit) {
    val label = stringResource(
        if (playing) R.string.search_stop_full else R.string.search_play_full,
        song.title,
    )
    Box(
        Modifier
            .size(COVER)
            .clip(DowntifyShape.ArtworkThumb)
            .clickable(role = Role.Button, onClick = onPlay)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Artwork(url = song.coverUrl.ifBlank { null }, contentDescription = null, modifier = Modifier.size(COVER))
        // Like a library row: the row that's playing shows the three bars (still when paused);
        // anything else is a bare cover, tapped to play.
        if (playing) PlayingBars(animate = isPlaying)
        if (resolving) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun SourceLabel(source: CatalogSource) {
    val gap = Modifier.padding(end = 6.dp)
    when (source) {
        CatalogSource.Spotify -> SourceBadge(stringResource(R.string.search_source_spotify), SourceBrand.Spotify, gap)

        CatalogSource.YouTube -> SourceBadge(stringResource(R.string.search_source_youtube), SourceBrand.YouTube, gap)

        CatalogSource.TextSearch -> SourceBadge(
            stringResource(R.string.search_source_soulseek),
            SourceBrand.Neutral,
            gap,
        )

        CatalogSource.Other -> Unit
    }
}

/**
 * An album on YouTube Music, as a card: tapping opens its tracks (listen first), the button
 * downloads it all, then "Downloading 4/9".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RemoteAlbumRow(
    album: RemoteAlbum,
    progress: ServerDownloadProgress?,
    onDownload: () -> Unit,
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    MediaListItem(
        title = album.title,
        subtitle = listOf(
            album.releaseType.ifBlank {
                stringResource(R.string.search_kind_album)
            },
            album.artist,
            album.year,
        )
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        // The progress gets its own line: in the trailing slot it squeezed the title to a word.
        meta = progress?.let {
            if (it.isActive) {
                stringResource(R.string.search_album_progress, it.done, it.total)
            } else {
                stringResource(R.string.search_album_done)
            }
        },
        onClick = onOpen,
        leading = {
            Artwork(
                url = album.coverUrl.ifBlank { null },
                contentDescription = null,
                placeholderIcon = DowntifyIcons.Album,
                modifier = Modifier.size(COVER),
            )
        },
        trailing = {
            when {
                progress == null -> CircleButton(
                    DowntifyIcons.Downloads,
                    stringResource(R.string.search_download_to_server, album.title),
                    onDownload,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                progress.isActive -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total.coerceAtLeast(1) },
                        modifier = Modifier.size(32.dp),
                    )
                }

                else -> Icon(
                    painterResource(DowntifyIcons.CheckCircle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * What a pasted link points at, as the screen's hero card: its cover, kind and name, then listen
 * to all of it first or download it all.
 */
@Composable
internal fun LinkHeader(
    link: ResolvedLink,
    requested: Boolean,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        hero = true,
        tone = SectionTone.High,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = link.coverUrl.ifBlank { null },
                contentDescription = null,
                shape = if (link.kind == LinkKind.Artist) CircleShape else DowntifyShape.ArtworkThumb,
                placeholderIcon = when (link.kind) {
                    LinkKind.Artist -> DowntifyIcons.Artist
                    LinkKind.Playlist -> DowntifyIcons.Playlist
                    else -> DowntifyIcons.Album
                },
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
                Text(
                    link.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (link.kind != LinkKind.Artist && link.subtitle.isNotBlank()) {
                    Text(
                        listOf(link.subtitle, link.year).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (link.tracks.size > 1) {
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(painterResource(DowntifyIcons.Play), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.search_link_play_all), modifier = Modifier.padding(start = Spacing.sm))
            }
            FilledTonalButton(
                onClick = onDownload,
                enabled = !requested,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(
                    painterResource(DowntifyIcons.Downloads),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    pluralStringResource(R.plurals.search_link_download_all, link.tracks.size, link.tracks.size),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

private val COVER = 64.dp
private const val PERCENT = 100f
