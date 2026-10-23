package com.henriquesebastiao.downtify.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.settings.LibraryLayout
import com.henriquesebastiao.downtify.core.data.settings.LibrarySort
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.ui.common.ArtistCard
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.CoverRow
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackRow
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.artistSubtitle
import com.henriquesebastiao.downtify.ui.common.songsCount

/** Where the library can navigate to. */
data class LibraryNavigation(
    val onSearch: () -> Unit = {},
    val onAlbum: (String) -> Unit = {},
    val onArtist: (String) -> Unit = {},
    val onPlaylist: (String) -> Unit = {},
    val onLiked: () -> Unit = {},
    val onSimilar: (artist: String, title: String) -> Unit = { _, _ -> },
)

@Composable
fun LibraryRoute(
    navigation: LibraryNavigation,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        navigation = navigation,
        onSelectTab = viewModel::selectTab,
        onSort = viewModel::setSort,
        onToggleLayout = viewModel::toggleLayout,
        onShuffleSongs = viewModel::shuffleSongs,
        onRefresh = viewModel::refresh,
        onPlaySong = viewModel::playSong,
        trackActions = TrackActions(
            onToggleLike = viewModel::toggleLike,
            onAddToQueue = viewModel::addToQueue,
            onGoToAlbum = { navigation.onAlbum(it.albumId) },
            onGoToArtist = { navigation.onArtist(it.artistId) },
            onFindSimilar = { artist, title -> navigation.onSimilar(artist, title) },
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    navigation: LibraryNavigation,
    onSelectTab: (LibraryTab) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onShuffleSongs: () -> Unit = {},
    modifier: Modifier = Modifier,
    trackActions: TrackActions = TrackActions(),
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
                },
                actions = {
                    IconButton(onClick = navigation.onSearch) {
                        Icon(
                            painterResource(DowntifyIcons.Search),
                            contentDescription = stringResource(R.string.library_search),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabChips(state.tab, onSelectTab)
            SortRow(state, onSort, onToggleLayout, onShuffleSongs)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    else -> when (state.tab) {
                        LibraryTab.Albums -> Albums(state, navigation)
                        LibraryTab.Artists -> Artists(state, navigation)
                        LibraryTab.Playlists -> Playlists(state, navigation)
                        LibraryTab.Songs -> Songs(state, onPlaySong, trackActions)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChips(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(LibraryTab.entries) { tab ->
            val isSelected = tab == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(tab) },
                label = { Text(stringResource(tab.label)) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            painterResource(DowntifyIcons.Check),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SortRow(
    state: LibraryUiState,
    onSort: (LibrarySort) -> Unit,
    onToggleLayout: () -> Unit,
    onShuffle: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { open = true }) {
                Icon(painterResource(DowntifyIcons.Sort), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(state.sort.label))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                LibrarySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.label)) },
                        trailingIcon = if (sort == state.sort) {
                            { Icon(painterResource(DowntifyIcons.Check), contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            open = false
                            onSort(sort)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.tab == LibraryTab.Songs) {
            IconButton(onClick = onShuffle, enabled = state.songs.isNotEmpty()) {
                Icon(
                    painterResource(DowntifyIcons.Shuffle),
                    contentDescription = stringResource(R.string.collection_shuffle),
                )
            }
        } else {
            val grid = state.layout == LibraryLayout.Grid
            IconButton(onClick = onToggleLayout) {
                Icon(
                    painterResource(if (grid) DowntifyIcons.ListView else DowntifyIcons.GridView),
                    contentDescription = stringResource(
                        if (grid) R.string.library_show_list else R.string.library_show_grid,
                    ),
                )
            }
        }
    }
}

/**
 * Album and playlist covers: as many columns as fit [CoverGridMinSize] each, but never fewer than two.
 * `Adaptive(160.dp)` alone gave one huge cover on a 360 dp phone (360 - 2 x 16 padding leaves room for
 * 1.9 columns once the 16 dp gap is counted).
 */
internal class AtLeastTwoColumns(private val minSize: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = ((availableSize + spacing) / (minSize.roundToPx() + spacing)).coerceAtLeast(MIN_COLUMNS)
        val room = availableSize - spacing * (count - 1)
        // Spread the remainder pixels, so the cells add up to the available width exactly.
        return List(count) { room / count + if (it < room % count) 1 else 0 }
    }

    private companion object {
        const val MIN_COLUMNS = 2
    }
}

private val CoverGridMinSize = 160.dp
private val gridCells = AtLeastTwoColumns(CoverGridMinSize)
private val gridPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl)

@Composable
private fun Albums(state: LibraryUiState, navigation: LibraryNavigation) {
    val covers = LocalCoverUrls.current
    if (state.albums.isEmpty()) return Empty(R.string.library_empty_albums)
    if (state.layout == LibraryLayout.Grid) {
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.albums, key = { it.id }) { album ->
                CoverCard(album.title, albumSubtitle(album), covers.track(album.coverTrackId), {
                    navigation.onAlbum(album.id)
                })
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            items(state.albums, key = { it.id }) { album ->
                CoverRow(album.title, albumSubtitle(album), covers.track(album.coverTrackId, 150), {
                    navigation.onAlbum(album.id)
                })
            }
        }
    }
}

@Composable
private fun Artists(state: LibraryUiState, navigation: LibraryNavigation) {
    val covers = LocalCoverUrls.current
    if (state.artists.isEmpty()) return Empty(R.string.library_empty_artists)
    if (state.layout == LibraryLayout.Grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.artists, key = { it.id }) { artist ->
                ArtistCard(artist.name, covers.track(artist.coverTrackId), {
                    navigation.onArtist(artist.id)
                }, size = 104.dp)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            items(state.artists, key = { it.id }) { artist ->
                CoverRow(
                    artist.name,
                    artistSubtitle(artist),
                    covers.track(artist.coverTrackId, 150),
                    { navigation.onArtist(artist.id) },
                    round = true,
                    placeholderIcon = DowntifyIcons.Artist,
                )
            }
        }
    }
}

@Composable
private fun Playlists(state: LibraryUiState, navigation: LibraryNavigation) {
    val covers = LocalCoverUrls.current
    val liked = stringResource(R.string.liked_songs)
    fun open(item: PlaylistItem) = item.playlist?.let { navigation.onPlaylist(it.name) } ?: navigation.onLiked()

    @Composable
    fun cover(item: PlaylistItem, size: Int) =
        item.playlist?.let { covers.playlist(it, item.coverTrackId, size) } ?: covers.track(item.coverTrackId, size)

    if (state.layout == LibraryLayout.Grid) {
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.playlists, key = { it.playlist?.name ?: "" }) { item ->
                CoverCard(
                    title = item.playlist?.title ?: liked,
                    subtitle = songsCount(item.count),
                    coverUrl = cover(item, 300),
                    onClick = { open(item) },
                    placeholderIcon = if (item.playlist ==
                        null
                    ) {
                        DowntifyIcons.FavoriteFilled
                    } else {
                        DowntifyIcons.Playlist
                    },
                )
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            items(state.playlists, key = { it.playlist?.name ?: "" }) { item ->
                CoverRow(
                    title = item.playlist?.title ?: liked,
                    subtitle = songsCount(item.count),
                    coverUrl = cover(item, 150),
                    onClick = { open(item) },
                    placeholderIcon = if (item.playlist ==
                        null
                    ) {
                        DowntifyIcons.FavoriteFilled
                    } else {
                        DowntifyIcons.Playlist
                    },
                )
            }
        }
    }
}

@Composable
private fun Songs(state: LibraryUiState, onPlay: (Int) -> Unit, actions: TrackActions) {
    val covers = LocalCoverUrls.current
    if (state.songs.isEmpty()) return Empty(R.string.library_empty_songs)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
        itemsIndexed(state.songs, key = { _, track: Track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                onClick = { onPlay(index) },
                coverUrl = covers.track(track.id.takeIf { track.hasCover }, 150),
                isCurrent = track.id == state.currentTrackId,
                isPlaying = state.isPlaying,
                isLiked = track.id in state.likedIds,
                actions = actions,
            )
        }
    }
}

@Composable
private fun Empty(title: Int) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            EmptyState(
                icon = DowntifyIcons.Library,
                title = stringResource(title),
                body = stringResource(R.string.library_empty_body),
            )
        }
    }
}

private val LibraryTab.label: Int
    get() = when (this) {
        LibraryTab.Albums -> R.string.library_albums
        LibraryTab.Artists -> R.string.library_artists
        LibraryTab.Playlists -> R.string.library_playlists
        LibraryTab.Songs -> R.string.library_songs
    }

private val LibrarySort.label: Int
    get() = when (this) {
        LibrarySort.RecentlyAdded -> R.string.library_sort_recent
        LibrarySort.Alphabetical -> R.string.library_sort_alpha
        LibrarySort.Artist -> R.string.library_sort_artist
    }

@PreviewLightDark
@Composable
private fun LibraryAlbumsPreview() {
    DowntifyTheme {
        LibraryScreen(
            state = LibraryUiState(loading = false, albums = PreviewData.albums),
            navigation = LibraryNavigation(),
            onSelectTab = {},
            onSort = {},
            onToggleLayout = {},
            onRefresh = {},
            onPlaySong = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun LibrarySongsPreview() {
    DowntifyTheme {
        LibraryScreen(
            state = LibraryUiState(
                loading = false,
                tab = LibraryTab.Songs,
                songs = PreviewData.tracks,
                currentTrackId = "t2",
            ),
            navigation = LibraryNavigation(),
            onSelectTab = {},
            onSort = {},
            onToggleLayout = {},
            onRefresh = {},
            onPlaySong = {},
        )
    }
}
