package com.henriquesebastiao.downtify.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyLogo
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.SectionHeader
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.songsCount
import java.time.LocalTime

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenRecent: (RecentContext) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenSimilar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state,
        onOpenSettings,
        onOpenRecent,
        onOpenAlbum,
        onSeeAll,
        viewModel::refresh,
        modifier,
        onOpenDiscover,
        onOpenPodcasts,
        onOpenSimilar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenRecent: (RecentContext) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onSeeAll: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDiscover: () -> Unit = {},
    onOpenPodcasts: () -> Unit = {},
    onOpenSimilar: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        DowntifyLogo(size = 32.dp)
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(DowntifyIcons.Settings),
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), modifier = Modifier.fillMaxSize()) {
                item { Greeting(state) }
                when {
                    state.loading -> item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.libraryEmpty -> item {
                        if (state.favoritePlaylists.isNotEmpty()) HomeAccess(state, onOpenRecent)
                        EmptyState(
                            icon = DowntifyIcons.Library,
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.home_empty_body),
                        )
                    }

                    else -> {
                        if (state.jumpBackIn.isNotEmpty() || state.favoritePlaylists.isNotEmpty()) {
                            item { HomeAccess(state, onOpenRecent) }
                        }
                        if (state.recentlyAdded.isNotEmpty()) {
                            item {
                                RecentlyAdded(state.recentlyAdded, onOpenAlbum, onSeeAll)
                            }
                        }
                    }
                }
                if (!state.loading) {
                    item(key = "shortcuts") {
                        Shortcuts(
                            showDiscover = state.showDiscover && !state.libraryEmpty,
                            showPodcasts = state.showPodcasts,
                            onOpenDiscover = onOpenDiscover,
                            onOpenPodcasts = onOpenPodcasts,
                            onOpenSimilar = onOpenSimilar,
                        )
                    }
                }
            }
        }
    }
}

/** Where Discover, Podcasts and Similar live: the four tabs stay as designed. */
@Composable
private fun Shortcuts(
    showDiscover: Boolean,
    showPodcasts: Boolean,
    onOpenDiscover: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenSimilar: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (showDiscover) {
            Shortcut(
                icon = DowntifyIcons.Explore,
                title = stringResource(R.string.home_shortcut_discover),
                body = stringResource(R.string.home_shortcut_discover_body),
                onClick = onOpenDiscover,
            )
        }
        Shortcut(
            icon = DowntifyIcons.Graphic,
            title = stringResource(R.string.home_shortcut_similar),
            body = stringResource(R.string.home_shortcut_similar_body),
            onClick = onOpenSimilar,
        )
        if (showPodcasts) {
            Shortcut(
                icon = DowntifyIcons.Podcasts,
                title = stringResource(R.string.home_shortcut_podcasts),
                body = stringResource(R.string.home_shortcut_podcasts_body),
                onClick = onOpenPodcasts,
            )
        }
    }
}

@Composable
private fun Shortcut(icon: Int, title: String, body: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(DowntifyIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Greeting(state: HomeUiState) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> R.string.home_good_morning
        in 12..17 -> R.string.home_good_afternoon
        else -> R.string.home_good_evening
    }
    Column(
        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(greeting),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics {
                heading()
            },
        )
        val (dot, text) = when (state.status) {
            ServerStatus.Streaming ->
                MaterialTheme.colorScheme.primary to
                    stringResource(R.string.home_streaming_from, state.serverName)

            ServerStatus.Syncing ->
                MaterialTheme.colorScheme.primary to
                    stringResource(R.string.home_syncing, state.serverName)

            ServerStatus.Unreachable ->
                MaterialTheme.colorScheme.outline to
                    stringResource(R.string.home_unreachable, state.serverName)

            ServerStatus.DifferentServer ->
                MaterialTheme.colorScheme.error to
                    stringResource(R.string.home_different_server)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeAccess(state: HomeUiState, onOpen: (RecentContext) -> Unit) {
    val favorites = state.favoritePlaylists.map { playlist ->
        RecentContext(PlaybackContextType.Playlist, playlist.name, playlist.title, playlist.trackIds.firstOrNull(), 0)
    }
    val playlists = state.favoritePlaylists.associateBy { it.name }
    BoxWithConstraints {
        val columns = if (maxWidth >= 480.dp) 3 else 2
        val recentTitle = stringResource(R.string.home_jump_back_in)
        val favoriteTitle = stringResource(R.string.favorite_playlists)
        if (maxWidth >= 840.dp && state.jumpBackIn.isNotEmpty() && favorites.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                QuickAccessSection(recentTitle, state.jumpBackIn, playlists, columns, onOpen, Modifier.weight(1f))
                QuickAccessSection(favoriteTitle, favorites, playlists, columns, onOpen, Modifier.weight(1f))
            }
        } else {
            Column {
                if (state.jumpBackIn.isNotEmpty()) {
                    QuickAccessSection(recentTitle, state.jumpBackIn, playlists, columns, onOpen)
                }
                if (favorites.isNotEmpty()) QuickAccessSection(favoriteTitle, favorites, playlists, columns, onOpen)
            }
        }
    }
}

@Composable
private fun QuickAccessSection(
    title: String,
    entries: List<RecentContext>,
    playlists: Map<String, Playlist>,
    columns: Int,
    onOpen: (RecentContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics {
                    heading()
                },
            )
            TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.home_see_all)) }
        }
        entries.take(6).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { entry -> QuickAccessTile(entry, playlists, onOpen, Modifier.weight(1f)) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(entries) { entry ->
                        QuickAccessTile(entry, playlists, {
                            expanded = false
                            onOpen(it)
                        }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun QuickAccessTile(
    entry: RecentContext,
    playlists: Map<String, Playlist>,
    onOpen: (RecentContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    val covers = LocalCoverUrls.current
    val playlist = if (entry.type == PlaybackContextType.Playlist) playlists[entry.refId] else null
    val title = if (entry.type == PlaybackContextType.Liked) stringResource(R.string.liked_songs) else entry.title
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.height(64.dp).clickable { onOpen(entry) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CoverArt(
                url = if (playlist !=
                    null
                ) {
                    covers.playlist(playlist, playlist.trackIds.firstOrNull())
                } else {
                    covers.track(entry.coverTrackId, 150)
                },
                contentDescription = null,
                shape = RectangleShape,
                placeholderIcon = when (entry.type) {
                    PlaybackContextType.Liked -> DowntifyIcons.FavoriteFilled
                    PlaybackContextType.Artist -> DowntifyIcons.Artist
                    PlaybackContextType.Playlist -> DowntifyIcons.Playlist
                    else -> DowntifyIcons.Album
                },
                modifier = Modifier.size(48.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.sm),
            )
        }
    }
}

@Composable
private fun RecentlyAdded(albums: List<Album>, onOpenAlbum: (String) -> Unit, onSeeAll: () -> Unit) {
    val covers = LocalCoverUrls.current
    Column(Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(
            stringResource(R.string.home_recently_added),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = onSeeAll,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(albums, key = { it.id }) { album ->
                CoverCard(
                    title = album.title,
                    subtitle = albumSubtitle(album),
                    coverUrl = covers.track(album.coverTrackId),
                    onClick = { onOpenAlbum(album.id) },
                    shape = MaterialTheme.shapes.extraLarge,
                    titleColor = Color.Unspecified,
                    modifier = Modifier.width(172.dp),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomePreview() {
    DowntifyTheme {
        HomeScreen(
            state = HomeUiState(
                loading = false,
                serverName = "nas.local",
                jumpBackIn = listOf(RecentContext(PlaybackContextType.Liked, "", "", null, 0)) +
                    PreviewData.albums.map { RecentContext(PlaybackContextType.Album, it.id, it.title, null, 0) },
                recentlyAdded = PreviewData.albums,
            ),
            onOpenSettings = {},
            onOpenRecent = {},
            onOpenAlbum = {},
            onSeeAll = {},
            onRefresh = {},
        )
    }
}
