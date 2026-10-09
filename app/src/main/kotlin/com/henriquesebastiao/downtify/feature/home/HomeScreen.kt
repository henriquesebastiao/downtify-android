package com.henriquesebastiao.downtify.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
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
                        EmptyState(
                            icon = DowntifyIcons.Library,
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.home_empty_body),
                        )
                    }

                    else -> {
                        if (state.jumpBackIn.isNotEmpty()) item { JumpBackIn(state.jumpBackIn, onOpenRecent) }
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
private fun JumpBackIn(items: List<RecentContext>, onOpen: (RecentContext) -> Unit) {
    val covers = LocalCoverUrls.current
    val label = stringResource(R.string.home_jump_back_in)
    Column(
        Modifier
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { item ->
                    val title = if (item.type ==
                        PlaybackContextType.Liked
                    ) {
                        stringResource(R.string.liked_songs)
                    } else {
                        item.title
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(56.dp).clickable { onOpen(item) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CoverArt(
                                url = covers.track(item.coverTrackId, 150),
                                contentDescription = null,
                                shape = RectangleShape,
                                placeholderIcon = when (item.type) {
                                    PlaybackContextType.Liked -> DowntifyIcons.FavoriteFilled
                                    PlaybackContextType.Artist -> DowntifyIcons.Artist
                                    PlaybackContextType.Playlist -> DowntifyIcons.Playlist
                                    else -> DowntifyIcons.Album
                                },
                                modifier = Modifier.size(56.dp),
                            )
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = Spacing.sm),
                            )
                        }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
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
