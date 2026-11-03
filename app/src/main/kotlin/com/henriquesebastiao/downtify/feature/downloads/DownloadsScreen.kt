package com.henriquesebastiao.downtify.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.offline.DownloadActivity
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.ArtworkTone
import com.henriquesebastiao.downtify.core.designsystem.component.ChoiceGroup
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SectionCard
import com.henriquesebastiao.downtify.core.designsystem.component.SectionTone
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.designsystem.theme.withDataValues
import com.henriquesebastiao.downtify.core.model.OfflinePlanner
import com.henriquesebastiao.downtify.core.model.OfflineProgress
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.ServerQueueSummary
import com.henriquesebastiao.downtify.core.model.formatBytes
import com.henriquesebastiao.downtify.ui.common.GroupedList
import com.henriquesebastiao.downtify.ui.common.GroupedRow
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.RowAction
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

@Composable
fun DownloadsRoute(
    onBack: () -> Unit,
    onOpen: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(
        state = state,
        onKeepLiked = viewModel::setKeepLiked,
        onOpen = onOpen,
        onRemove = viewModel::remove,
        onTab = viewModel::selectTab,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * What's kept on the phone: the storage it takes, Liked songs, and each album or playlist. Opened
 * from the side menu, above Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onKeepLiked: (Boolean) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    onTab: (DownloadsTab) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PageHeader(
                title = stringResource(R.string.downloads_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "tabs") { Tabs(state.tab, onTab) }
            if (state.tab == DownloadsTab.Server) {
                serverTab(state)
                return@LazyColumn
            }
            item(key = "storage") { StorageCard(state, Modifier.padding(horizontal = Spacing.screen)) }
            item(key = "liked") {
                val body = if (state.wifiOnly) {
                    R.string.downloads_keep_liked_body_wifi
                } else {
                    R.string.downloads_keep_liked_body
                }
                GroupedList(
                    listOf(
                        GroupedRow(
                            title = stringResource(R.string.downloads_keep_liked),
                            action = RowAction.Toggle(state.keepLiked, onKeepLiked),
                            summary = stringResource(body),
                            icon = DowntifyIcons.Favorite,
                        ),
                    ),
                )
            }
            val collections = state.items
            if (collections.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = DowntifyIcons.Downloads,
                        title = stringResource(R.string.downloads_empty_title),
                        body = stringResource(R.string.downloads_empty_body),
                        hero = true,
                    )
                }
            } else {
                item(key = "header") { SectionTitle(stringResource(R.string.downloads_on_phone)) }
                items(collections, key = { "${it.type}:${it.refId}" }) { item ->
                    DownloadRow(item, state, onOpen = { onOpen(item) }, onRemove = { onRemove(item) })
                }
            }
            if (state.serverSummary.active > 0) {
                item(key = "server-card") { ServerCard(state, onClick = { onTab(DownloadsTab.Server) }) }
            }
        }
    }
}

/** Phone or server: two views of one screen, read as tabs. */
@Composable
private fun Tabs(selected: DownloadsTab, onTab: (DownloadsTab) -> Unit) {
    ChoiceGroup(
        options = DownloadsTab.entries,
        selected = selected,
        label = {
            stringResource(
                if (it ==
                    DownloadsTab.Phone
                ) {
                    R.string.downloads_tab_phone
                } else {
                    R.string.downloads_tab_server
                },
            )
        },
        onSelect = onTab,
        role = Role.Tab,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = Spacing.screen + Spacing.xs, end = Spacing.screen, top = Spacing.md)
            .semantics { heading() },
    )
}

/** "The server is downloading 13 songs · Harbor Nights · 4 of 9" — opens the server's side. */
@Composable
private fun ServerCard(state: DownloadsUiState, onClick: () -> Unit) {
    val summary = state.serverSummary
    SectionCard(
        tone = SectionTone.Secondary,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        // The sentence wraps whole: as a card title it lost its end beside the icon and the chevron.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(painterResource(DowntifyIcons.Server), contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    pluralStringResource(R.plurals.downloads_server_card, summary.active, summary.active),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (summary.currentAlbum.isNotBlank() && summary.currentAlbumTotal > 1) {
                    Text(
                        stringResource(
                            R.string.downloads_server_card_detail,
                            summary.currentAlbum,
                            summary.currentAlbumDone,
                            summary.currentAlbumTotal,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(painterResource(DowntifyIcons.ChevronRight), contentDescription = null)
        }
    }
}

private fun LazyListScope.serverTab(state: DownloadsUiState) {
    if (state.serverJobs.isEmpty()) {
        item(key = "server-empty") {
            EmptyState(
                icon = DowntifyIcons.Server,
                title = stringResource(R.string.downloads_server_empty_title),
                body = stringResource(R.string.downloads_server_empty_body),
                hero = true,
            )
        }
        return
    }
    if (state.serverSummary.active > 0) {
        item(key = "server-summary") {
            SectionTitle(
                pluralStringResource(
                    R.plurals.downloads_server_card,
                    state.serverSummary.active,
                    state.serverSummary.active,
                ),
            )
        }
    }
    items(state.serverJobs, key = { "job:${it.songId}" }) { job -> ServerJobRow(job) }
}

/** A song the server is fetching: its progress as a wavy bar (a long wait), its place in line. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerJobRow(job: ServerJob) {
    MediaListItem(
        title = job.title,
        subtitle = listOf(job.artist, jobStatus(job)).filter { it.isNotBlank() }.joinToString(" · "),
        subtitleLines = 2,
        onClick = {},
        leading = {
            Artwork(url = job.coverUrl.ifBlank { null }, contentDescription = null, modifier = Modifier.size(COVER))
        },
        extra = if (job.status == ServerJobStatus.Downloading) {
            {
                LinearWavyProgressIndicator(
                    progress = { job.progress / PERCENT },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, end = Spacing.sm),
                )
            }
        } else {
            null
        },
        supportingLeading = if (job.status == ServerJobStatus.Error) {
            {
                Icon(
                    painterResource(DowntifyIcons.Error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp),
                )
            }
        } else {
            null
        },
        trailing = when {
            job.status == ServerJobStatus.Done -> {
                {
                    Icon(
                        painterResource(DowntifyIcons.CheckCircle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                }
            }

            job.status.isActive && job.position != null -> {
                { Tag("#${job.position}", small = true) }
            }

            else -> null
        },
        modifier = Modifier.padding(horizontal = Spacing.screen),
    )
}

@Composable
private fun jobStatus(job: ServerJob): String = when (job.status) {
    ServerJobStatus.Queued -> stringResource(R.string.downloads_server_job_queued)

    ServerJobStatus.Downloading -> stringResource(R.string.downloads_server_job_downloading, job.progress.toInt())

    ServerJobStatus.Done -> stringResource(R.string.downloads_server_job_done)

    ServerJobStatus.Error -> if (job.message.isBlank()) {
        stringResource(R.string.downloads_server_job_error)
    } else {
        stringResource(R.string.downloads_server_job_error_detail, job.message)
    }
}

/** How much of the phone the offline copies take: the screen's one hero card, its values in tabular figures. */
@Composable
private fun StorageCard(state: DownloadsUiState, modifier: Modifier = Modifier) {
    val unlimited = state.limitBytes == OfflinePlanner.UNLIMITED
    val capacity = if (unlimited) state.usedBytes + state.freeBytes else state.limitBytes
    val fraction = if (capacity > 0) (state.usedBytes.toFloat() / capacity).coerceIn(0f, 1f) else 0f
    val a11y = stringResource(R.string.downloads_storage_a11y)
    val used = formatBytes(state.usedBytes)
    val limit = formatBytes(state.limitBytes)
    val free = formatBytes(state.freeBytes)
    val scheme = MaterialTheme.colorScheme
    SectionCard(tone = SectionTone.Primary, hero = true, modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                withDataValues(stringResource(R.string.downloads_used, used), used),
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
            Text(
                if (unlimited) {
                    AnnotatedString(stringResource(R.string.downloads_no_limit))
                } else {
                    withDataValues(stringResource(R.string.downloads_of_limit, limit), limit)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(MUTED_ALPHA),
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = scheme.primary,
            trackColor = scheme.onPrimaryContainer.copy(alpha = TRACK_ALPHA),
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = a11y },
        )
        Text(
            withDataValues(
                pluralStringResource(R.plurals.downloads_storage_body, state.songCount, state.songCount, free),
                free,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(MUTED_ALPHA),
        )
    }
}

/** An album, playlist or Liked songs kept here: its status whole, its download as a wavy bar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadRow(item: DownloadItem, state: DownloadsUiState, onOpen: () -> Unit, onRemove: () -> Unit) {
    val title = if (item.type == PlaybackContextType.Liked) stringResource(R.string.liked_songs) else item.title
    val p = item.progress
    val inProgress = p.total > 0 && !p.isComplete && p.overLimit == 0
    MediaListItem(
        title = title,
        subtitle = statusText(item, state),
        subtitleLines = 2,
        onClick = onOpen,
        leading = {
            Artwork(
                url = LocalCoverUrls.current.track(item.coverTrackId),
                contentDescription = null,
                placeholderIcon = when (item.type) {
                    PlaybackContextType.Liked -> DowntifyIcons.FavoriteFilled
                    PlaybackContextType.Playlist -> DowntifyIcons.Playlist
                    else -> DowntifyIcons.Album
                },
                tone = if (item.type == PlaybackContextType.Liked) ArtworkTone.Primary else ArtworkTone.Neutral,
                modifier = Modifier.size(COVER),
            )
        },
        supportingLeading = if (p.isComplete) {
            {
                Icon(
                    painterResource(DowntifyIcons.CheckCircle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp),
                )
            }
        } else {
            null
        },
        extra = if (inProgress) {
            {
                // The wave means work under way: waiting for Wi-Fi or a connection, the bar lies flat.
                val moving = state.activity == DownloadActivity.Downloading
                LinearWavyProgressIndicator(
                    progress = { p.downloaded.toFloat() / p.total },
                    amplitude = { if (moving) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, end = Spacing.sm),
                )
            }
        } else {
            null
        },
        trailing = { RowMenu(title, onRemove) },
        modifier = Modifier.padding(horizontal = Spacing.screen),
    )
}

@Composable
private fun RowMenu(title: String, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CircleButton(DowntifyIcons.MoreVert, stringResource(R.string.downloads_options, title), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_remove)) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Delete), contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

private val COVER = 64.dp
private const val PERCENT = 100f
private const val MUTED_ALPHA = 0.8f
private const val TRACK_ALPHA = 0.18f

@Composable
private fun statusText(item: DownloadItem, state: DownloadsUiState): String {
    val p = item.progress
    return when {
        p.total == 0 -> stringResource(R.string.downloads_status_missing)

        p.overLimit > 0 -> pluralStringResource(
            R.plurals.downloads_status_over_limit,
            p.overLimit,
            p.downloaded,
            p.total,
            p.overLimit,
        )

        p.isComplete -> {
            val done = pluralStringResource(R.plurals.downloads_status_done, p.total, p.total, formatBytes(p.bytes))
            if (item.type ==
                PlaybackContextType.Liked
            ) {
                stringResource(R.string.downloads_status_done_synced, done)
            } else {
                done
            }
        }

        else -> {
            val progress = stringResource(R.string.downloads_status_progress, p.downloaded, p.total)
            when (state.activity) {
                DownloadActivity.Downloading -> progress

                else -> {
                    val waiting = if (state.wifiOnly) {
                        R.string.downloads_status_waiting_wifi
                    } else {
                        R.string.downloads_status_waiting_network
                    }
                    stringResource(waiting, progress)
                }
            }
        }
    }
}

private val previewState = DownloadsUiState(
    loading = false,
    usedBytes = 2_400_000_000,
    limitBytes = 8_000_000_000,
    freeBytes = 5_600_000_000,
    songCount = 212,
    keepLiked = true,
    activity = DownloadActivity.WaitingForNetwork,
    items = listOf(
        DownloadItem(PlaybackContextType.Liked, "", "", null, OfflineProgress(118, 118, 1_100_000_000, 0)),
        DownloadItem(PlaybackContextType.Album, "a", "Glass Harbor", null, OfflineProgress(10, 10, 412_000_000, 0)),
        DownloadItem(PlaybackContextType.Playlist, "Deep Focus", "Deep Focus", null, OfflineProgress(6, 4, 0, 0)),
    ),
)

@PhonePreviews
@Composable
private fun DownloadsPreview() {
    DowntifyTheme {
        DownloadsScreen(state = previewState, onKeepLiked = {}, onOpen = {}, onRemove = {})
    }
}

@PhonePreviews
@Composable
private fun DownloadsServerPreview() {
    val jobs = listOf(
        ServerJob("1", "Harbor Song", "Nora Vale", "Harbor Nights", "", ServerJobStatus.Downloading, 42f, ""),
        ServerJob("2", "Low Lights", "Nora Vale", "Harbor Nights", "", ServerJobStatus.Queued, 0f, ""),
        ServerJob("3", "Tides", "Lumen Field", "Tides", "", ServerJobStatus.Done, 100f, ""),
    )
    DowntifyTheme {
        DownloadsScreen(
            state = previewState.copy(
                tab = DownloadsTab.Server,
                serverJobs = jobs,
                serverSummary = ServerQueueSummary.of(jobs),
            ),
            onKeepLiked = {},
            onOpen = {},
            onRemove = {},
        )
    }
}

@PhonePreviews
@Composable
private fun DownloadsEmptyPreview() {
    DowntifyTheme {
        DownloadsScreen(
            state = DownloadsUiState(loading = false, limitBytes = 8_000_000_000, freeBytes = 50_000_000_000),
            onKeepLiked = {},
            onOpen = {},
            onRemove = {},
        )
    }
}
