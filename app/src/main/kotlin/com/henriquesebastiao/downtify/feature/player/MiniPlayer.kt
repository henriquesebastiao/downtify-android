package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.rememberCoverColors
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData

/** Sits above the navigation bar while something is loaded; tapping it opens Now Playing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    onSimilar: (artist: String, title: String) -> Unit = { _, _ -> },
    downloadJob: ServerJob? = null,
    onDownloadStream: () -> Unit = {},
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    val similarArtist = track?.displayArtist ?: stream?.artist.orEmpty()
    val similarTitle = track?.displayTitle ?: stream?.title.orEmpty()
    val canSimilar = episode == null && similarArtist.isNotBlank() && similarTitle.isNotBlank()
    val coverUrl = episode?.artworkUrl?.ifBlank { null }
        ?: stream?.coverUrl?.ifBlank { null }
        ?: track?.let { LocalCoverUrls.current.track(it.id.takeIf { _ -> it.hasCover }, ServerUrls.COVER_SMALL) }
    val colors = rememberCoverColors(coverUrl)
    Surface(
        color = colors.surface,
        contentColor = colors.onSurface,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs).fillMaxWidth().height(64.dp),
    ) {
        Box {
            Row(
                Modifier.padding(start = Spacing.sm, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = stringResource(R.string.player_open),
                            role = Role.Button,
                            onClick = onOpen,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CoverArt(url = coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
                if (canSimilar) {
                    IconButton(onClick = { onSimilar(similarArtist, similarTitle) }) {
                        Icon(
                            painterResource(DowntifyIcons.Explore),
                            contentDescription = stringResource(R.string.search_similar, similarTitle),
                        )
                    }
                }
                if (stream != null) {
                    MiniStreamDownloadButton(job = downloadJob, title = stream.title, onDownload = onDownloadStream)
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        painterResource(if (state.isPlaying) DowntifyIcons.Pause else DowntifyIcons.Play),
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.player_pause else R.string.player_play,
                        ),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { state.progress },
                color = colors.onSurface,
                trackColor = colors.onSurface.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.align(
                    Alignment.BottomCenter,
                ).padding(horizontal = Spacing.md).fillMaxWidth().height(2.dp),
            )
        }
    }
}

/**
 * Download the playing stream, a spinner while the server works on it,
 * and a plain (blocked, not clickable) check once it is on the server:
 * tapping it must not queue the download again.
 */
@Composable
private fun MiniStreamDownloadButton(job: ServerJob?, title: String, onDownload: () -> Unit) {
    val active = job?.status == ServerJobStatus.Queued || job?.status == ServerJobStatus.Downloading
    if (active) {
        val label = stringResource(R.string.search_job_downloading)
        Box(
            Modifier.size(48.dp).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    } else if (job?.status == ServerJobStatus.Done) {
        val label = stringResource(R.string.search_job_done)
        Box(
            Modifier.size(48.dp).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(DowntifyIcons.CheckCircle),
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = onDownload) {
            Icon(
                painterResource(DowntifyIcons.Downloads),
                contentDescription = stringResource(R.string.search_download_to_server, title),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerPreview() {
    DowntifyTheme {
        Surface {
            MiniPlayer(
                state = PlayerState(
                    track = PreviewData.tracks[1],
                    isPlaying = true,
                    positionMs = 70_000,
                    durationMs = 239_000,
                ),
                onOpen = {},
                onTogglePlay = {},
            )
        }
    }
}
