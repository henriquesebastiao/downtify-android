package com.henriquesebastiao.downtify.feature.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.ArtworkTone
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PodcastEpisode
import com.henriquesebastiao.downtify.core.model.PodcastShow
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.ui.common.formatDate
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

@Composable
fun ShowRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ShowViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is ShowMessage.DownloadFailed -> resources.getString(R.string.show_download_failed, message.title)
                    ShowMessage.Unreachable -> resources.getString(R.string.load_error_unreachable)
                },
            )
        }
    }
    ShowScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        actions = EpisodeActions(viewModel::play, viewModel::download, viewModel::setPlayed),
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

/** What an episode row can do. */
data class EpisodeActions(
    val onPlay: (PodcastEpisode) -> Unit = {},
    val onDownload: (PodcastEpisode) -> Unit = {},
    val onSetPlayed: (PodcastEpisode, Boolean) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowScreen(
    state: ShowUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    actions: EpisodeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    CircleButton(
                        DowntifyIcons.Back,
                        stringResource(R.string.action_back),
                        onBack,
                        Modifier.padding(start = Spacing.xs),
                    )
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                state.show == null -> EmptyState(
                    icon = DowntifyIcons.Podcasts,
                    title = stringResource(state.error?.let(::errorTitle) ?: R.string.load_error_failed),
                    body = stringResource(R.string.podcasts_pull_to_retry),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
                    verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") { Header(state.show) }
                    if (state.episodes.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.show_no_episodes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.screen),
                            )
                        }
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            busy = episode.id in state.busy,
                            current = episode.id == state.currentEpisodeId,
                            playing = episode.id == state.currentEpisodeId && state.isPlaying,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(show: PodcastShow) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = show.artworkUrl.ifBlank { null },
                contentDescription = null,
                shape = DowntifyShape.Section,
                placeholderIcon = DowntifyIcons.Podcasts,
                tone = ArtworkTone.Tertiary,
                modifier = Modifier.size(120.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    show.name,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (show.author.isNotBlank()) {
                    Text(
                        show.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (show.description.isNotBlank()) {
            Text(
                show.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.md).clickable { expanded = !expanded },
            )
        }
    }
}

/**
 * An episode as a card: its title on up to two lines (bold until heard, then muted), the date,
 * length and where it stopped in tabular figures, and how far it was heard as a flat bar.
 */
@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    busy: Boolean,
    current: Boolean,
    playing: Boolean,
    actions: EpisodeActions,
) {
    val played = episode.played
    MediaListItem(
        title = episode.title.ifBlank { stringResource(R.string.show_untitled_episode) },
        titleLines = 2,
        subtitle = status(episode),
        meta = meta(episode),
        onClick = { if (!busy) actions.onPlay(episode) },
        active = current,
        dimmed = played,
        extra = if (!played && episode.heard > 0f) {
            {
                LinearProgressIndicator(
                    progress = { episode.heard },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, end = Spacing.sm),
                )
            }
        } else {
            null
        },
        trailing = {
            PlayOrDownload(episode, busy, playing, actions)
            EpisodeMenu(episode, actions)
        },
        modifier = Modifier.padding(horizontal = Spacing.screen),
    )
}

/** Play an episode on the phone, or have the server fetch it first (a long wait: the wavy indicator). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayOrDownload(episode: PodcastEpisode, busy: Boolean, playing: Boolean, actions: EpisodeActions) {
    val downloading = stringResource(R.string.show_downloading)
    when {
        busy -> Box(
            Modifier.size(48.dp).semantics { contentDescription = downloading },
            contentAlignment = Alignment.Center,
        ) {
            CircularWavyProgressIndicator(Modifier.size(32.dp))
        }

        episode.isDownloaded -> CircleButton(
            if (playing) DowntifyIcons.Pause else DowntifyIcons.Play,
            stringResource(if (playing) R.string.player_pause else R.string.show_play_episode, episode.title),
            { actions.onPlay(episode) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        else -> CircleButton(
            DowntifyIcons.Downloads,
            stringResource(R.string.show_download_episode, episode.title),
            { actions.onDownload(episode) },
        )
    }
}

@Composable
private fun EpisodeMenu(episode: PodcastEpisode, actions: EpisodeActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        CircleButton(
            DowntifyIcons.MoreVert,
            stringResource(R.string.show_episode_options, episode.title),
            { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (episode.played) R.string.show_mark_unplayed else R.string.show_mark_played))
                },
                leadingIcon = { Icon(painterResource(DowntifyIcons.CheckCircle), contentDescription = null) },
                onClick = {
                    open = false
                    actions.onSetPlayed(episode, !episode.played)
                },
            )
        }
    }
}

/** `Sep 18, 2026 · 31:02`: the line in tabular figures. */
@Composable
private fun meta(episode: PodcastEpisode): String = buildList {
    formatDate(episode.publishedAt)?.let(::add)
    if (episode.durationSeconds > 0) add(formatDuration(episode.durationSeconds.toDouble()))
}.joinToString(" · ")

/** "Played" or "Resume at 12:34", in words: beside the date and length it no longer fit one line. */
@Composable
private fun status(episode: PodcastEpisode): String? = when {
    episode.played -> stringResource(R.string.show_played)

    episode.resumeAtSeconds != null ->
        stringResource(R.string.show_resume_at, formatDuration(episode.resumeAtSeconds!!.toDouble()))

    else -> null
}

private const val DESCRIPTION_LINES = 4

private val previewShow = PodcastShow(
    1,
    "Radiolab",
    "WNYC Studios",
    "Investigations of the natural and the unnatural, told through science, philosophy and story.",
    "",
    671,
    3,
)

private fun previewEpisode(id: Long, title: String, date: String, file: String?, position: Double, played: Boolean) =
    PodcastEpisode(id, 1, title, "", "${date}T14:00:00+00:00", 1_862, null, 700 + id.toInt(), file, position, played)

@PhonePreviews
@Composable
private fun ShowPreview() {
    DowntifyTheme {
        ShowScreen(
            state = ShowUiState(
                loading = false,
                show = previewShow,
                episodes = listOf(
                    previewEpisode(1, "The Sweetest Thing", "2026-09-18", "Podcasts/R/a.mp3", 754.0, played = false),
                    previewEpisode(2, "Zoozve", "2026-09-11", "Podcasts/R/b.mp3", 0.0, played = true),
                    previewEpisode(3, "Space to Dream", "2026-09-04", null, 0.0, played = false),
                ),
                currentEpisodeId = 1,
                isPlaying = true,
            ),
            onBack = {},
            onRefresh = {},
            actions = EpisodeActions(),
        )
    }
}
