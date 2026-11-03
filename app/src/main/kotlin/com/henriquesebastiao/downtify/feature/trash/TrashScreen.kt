package com.henriquesebastiao.downtify.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.ChoiceGroup
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashState
import com.henriquesebastiao.downtify.core.model.formatBytes
import com.henriquesebastiao.downtify.ui.common.formatDate
import com.henriquesebastiao.downtify.ui.common.plusBottomBars
import kotlinx.coroutines.delay

@Composable
fun TrashRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The server moves files in the background: ask again while the page is in view, often while
    // something is still on its way.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inFlight by rememberUpdatedState(state.inFlight)
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(if (inFlight) POLL_BUSY_MS else POLL_IDLE_MS)
                viewModel.reload()
            }
        }
    }
    TrashScreen(
        state = state,
        search = viewModel.search,
        onSearch = viewModel::onSearch,
        onTab = viewModel::selectTab,
        onRestore = viewModel::restore,
        onRetry = viewModel::retry,
        onAllowDownload = viewModel::allowDownload,
        onLoadMore = viewModel::loadMore,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The music trash, for an admin: what is in it and until when, restoring it, and the songs whose time
 * ran out that automatic downloads still skip. Opened from the side menu, above Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    state: TrashUiState,
    search: String,
    onSearch: (String) -> Unit,
    onTab: (TrashTab) -> Unit,
    onRestore: (TrashItem) -> Unit,
    onRetry: (TrashItem) -> Unit,
    onAllowDownload: (TrashItem) -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var allowing by remember { mutableStateOf<TrashItem?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PageHeader(
                title = stringResource(R.string.trash_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (!state.canManage) {
                item(key = "admin-only") {
                    EmptyState(
                        icon = DowntifyIcons.LockOpen,
                        title = stringResource(R.string.trash_admin_only),
                        body = stringResource(R.string.trash_admin_only_body),
                        hero = true,
                    )
                }
                return@LazyColumn
            }
            item(key = "tabs") {
                ChoiceGroup(
                    options = TrashTab.entries,
                    selected = state.tab,
                    label = {
                        stringResource(
                            if (it ==
                                TrashTab.Trash
                            ) {
                                R.string.trash_tab_trash
                            } else {
                                R.string.trash_tab_kept_out
                            },
                        )
                    },
                    onSelect = onTab,
                    role = Role.Tab,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
            when (state.tab) {
                TrashTab.Trash -> trashTab(state, search, onSearch, onRestore, onRetry, onLoadMore, nowMillis)
                TrashTab.Exclusions -> exclusionsTab(state, onAllow = { allowing = it })
            }
        }
    }
    allowing?.let { item ->
        AlertDialog(
            onDismissRequest = { allowing = null },
            title = { Text(stringResource(R.string.trash_allow_title, item.displayTitle)) },
            text = { Text(stringResource(R.string.trash_allow_body)) },
            confirmButton = {
                TextButton(onClick = {
                    allowing = null
                    onAllowDownload(item)
                }) { Text(stringResource(R.string.trash_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { allowing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    state.error?.let { code ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.trash_failed)) },
            text = { Text(trashMessage(code)) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

private fun LazyListScope.trashTab(
    state: TrashUiState,
    search: String,
    onSearch: (String) -> Unit,
    onRestore: (TrashItem) -> Unit,
    onRetry: (TrashItem) -> Unit,
    onLoadMore: () -> Unit,
    nowMillis: Long,
) {
    item(key = "search") {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.trash_search)) },
            leadingIcon = { Icon(painterResource(DowntifyIcons.Search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        )
    }
    item(key = "summary") {
        Text(
            pluralStringResource(R.plurals.trash_summary, state.total, state.total, formatBytes(state.bytes)) +
                " · " + pluralStringResource(R.plurals.trash_kept_days, state.retentionDays, state.retentionDays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen + Spacing.xs, vertical = Spacing.xs),
        )
    }
    if (state.items.isEmpty()) {
        if (!state.loading) {
            item(key = "empty") {
                EmptyState(
                    icon = DowntifyIcons.Delete,
                    title = stringResource(if (search.isBlank()) R.string.trash_empty else R.string.trash_no_match),
                    body = if (search.isBlank()) stringResource(R.string.trash_empty_body) else null,
                    hero = true,
                )
            }
        }
        return
    }
    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
        TrashRow(
            item = item,
            shape = index to state.items.size,
            working = item.id in state.working,
            nowMillis = nowMillis,
            onRestore = { onRestore(item) },
            onRetry = { onRetry(item) },
        )
    }
    if (state.items.size < state.total) {
        item(key = "more") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onLoadMore) { Text(stringResource(R.string.trash_show_more)) }
            }
        }
    }
}

private fun LazyListScope.exclusionsTab(state: TrashUiState, onAllow: (TrashItem) -> Unit) {
    item(key = "kept-out-hint") {
        Text(
            stringResource(R.string.trash_kept_out_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen + Spacing.xs, vertical = Spacing.xs),
        )
    }
    if (state.exclusions.isEmpty()) {
        if (!state.loading) {
            item(key = "kept-out-empty") {
                EmptyState(icon = DowntifyIcons.Downloads, title = stringResource(R.string.trash_kept_out_empty))
            }
        }
        return
    }
    itemsIndexed(state.exclusions, key = { _, item -> "x:" + item.id }) { index, item ->
        MediaListItem(
            title = item.displayTitle,
            subtitle = listOf(item.artist, item.format).filter { it.isNotBlank() }.joinToString(" · "),
            onClick = {},
            shape = ListStyle.Separated.shapeOf(index, state.exclusions.size),
            modifier = Modifier.padding(horizontal = Spacing.screen),
            trailing = {
                TextButton(onClick = { onAllow(item) }, enabled = item.id !in state.working) {
                    Text(stringResource(R.string.trash_allow))
                }
            },
        )
    }
}

@Composable
private fun TrashRow(
    item: TrashItem,
    shape: Pair<Int, Int>,
    working: Boolean,
    nowMillis: Long,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
) {
    val details = listOf(item.artist, item.format, formatBytes(item.size)).filter { it.isNotBlank() }
    val expires = item.expiresAt?.takeIf { item.state == TrashState.Trashed }
    MediaListItem(
        title = item.displayTitle,
        subtitle = details.joinToString(" · "),
        meta = if (expires != null) stringResource(R.string.trash_kept_until, formatDate(expires)) else null,
        onClick = {},
        shape = ListStyle.Separated.shapeOf(shape.first, shape.second),
        modifier = Modifier.padding(horizontal = Spacing.screen),
        extra = {
            Tag(
                trashStateLabel(item.state),
                tone = if (item.state == TrashState.Error) TagTone.Error else TagTone.Surface,
                small = true,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (item.error.isNotBlank()) {
                Text(
                    trashMessage(item.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs, end = Spacing.sm),
                )
            }
        },
        trailing = {
            when {
                item.state == TrashState.Error -> TextButton(onClick = onRetry, enabled = !working) {
                    Text(stringResource(R.string.action_retry))
                }

                item.restorable(nowMillis) -> FilledTonalButton(onClick = onRestore, enabled = !working) {
                    Text(stringResource(R.string.trash_restore))
                }
            }
        },
    )
}

private const val POLL_BUSY_MS = 3_000L
private const val POLL_IDLE_MS = 15_000L

private val previewItems = listOf(
    TrashItem(
        "1",
        "Nora Vale/Harbor Nights/01 - Harbor Song.flac",
        title = "Harbor Song",
        artist = "Nora Vale",
        codec = "flac",
        state = TrashState.Trashed,
        size = 31_457_280,
        expiresAt = 1_795_000_000_000,
    ),
    TrashItem(
        "2",
        "Nora Vale/Harbor Nights/02 - Low Lights.mp3",
        title = "Low Lights",
        artist = "Nora Vale",
        codec = "mp3",
        state = TrashState.Pending,
        size = 8_400_000,
    ),
    TrashItem(
        "3",
        "Lumen Field/Tides.m4a",
        title = "Tides",
        artist = "Lumen Field",
        codec = "aac",
        state = TrashState.Error,
        size = 6_100_000,
        error = "restore_collision",
    ),
)

@PhonePreviews
@Composable
private fun TrashPreview() {
    DowntifyTheme {
        TrashScreen(
            state = TrashUiState(loading = false, items = previewItems, total = 3, bytes = 45_957_280),
            search = "",
            onSearch = {},
            onTab = {},
            onRestore = {},
            onRetry = {},
            onAllowDownload = {},
            onLoadMore = {},
            onDismissError = {},
            nowMillis = 1_790_000_000_000,
        )
    }
}

@PhonePreviews
@Composable
private fun TrashEmptyPreview() {
    DowntifyTheme {
        TrashScreen(
            state = TrashUiState(loading = false),
            search = "",
            onSearch = {},
            onTab = {},
            onRestore = {},
            onRetry = {},
            onAllowDownload = {},
            onLoadMore = {},
            onDismissError = {},
        )
    }
}

@PhonePreviews
@Composable
private fun TrashKeptOutPreview() {
    DowntifyTheme {
        TrashScreen(
            state = TrashUiState(
                loading = false,
                tab = TrashTab.Exclusions,
                exclusions = previewItems.map { it.copy(state = TrashState.Purged, expiresAt = null) },
            ),
            search = "",
            onSearch = {},
            onTab = {},
            onRestore = {},
            onRetry = {},
            onAllowDownload = {},
            onLoadMore = {},
            onDismissError = {},
        )
    }
}
