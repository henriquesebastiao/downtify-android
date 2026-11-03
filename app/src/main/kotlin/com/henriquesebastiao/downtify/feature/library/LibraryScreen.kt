package com.henriquesebastiao.downtify.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.settings.LibraryLayout
import com.henriquesebastiao.downtify.core.data.settings.LibrarySort
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.IconAvatar
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.PillTabs
import com.henriquesebastiao.downtify.core.designsystem.component.ScrollbarTouchWidth
import com.henriquesebastiao.downtify.core.designsystem.component.VerticalScrollbar
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.trash.LocalMoveToTrash
import com.henriquesebastiao.downtify.ui.common.ArtistCard
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.CoverItem
import com.henriquesebastiao.downtify.ui.common.LocalBottomBarsInset
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackItem
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.artistSubtitle
import com.henriquesebastiao.downtify.ui.common.plusBottomBars
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
        onSortDescending = viewModel::setSortDescending,
        onToggleLayout = viewModel::toggleLayout,
        onShuffle = viewModel::shuffle,
        onRefresh = viewModel::refresh,
        onPlaySong = viewModel::playSong,
        trackActions = TrackActions(
            onToggleLike = viewModel::toggleLike,
            onAddToQueue = viewModel::addToQueue,
            onGoToAlbum = { navigation.onAlbum(it.albumId) },
            onGoToArtist = { navigation.onArtist(it.artistId) },
            onFindSimilar = { artist, title -> navigation.onSimilar(artist, title) },
            onMoveToTrash = LocalMoveToTrash.current,
        ),
        modifier = modifier,
    )
}

/**
 * The library, after PixelPlayer's: the title on one row with its button, the pill tabs, and the
 * content on its own panel with a scrollbar down its end.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    navigation: LibraryNavigation,
    onSelectTab: (LibraryTab) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onShuffle: () -> Unit = {},
    onSortDescending: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    trackActions: TrackActions = TrackActions(),
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // One wash of primary behind the title and the tabs, fading into the surface.
            val wash = Brush.verticalGradient(
                listOf(scheme.primary.copy(alpha = TINT_ALPHA).compositeOver(scheme.surface), scheme.surface),
            )
            Column(Modifier.background(wash)) {
                Header(onSearch = navigation.onSearch)
                PillTabs(
                    tabs = LibraryTab.entries.map { stringResource(it.label) },
                    selectedIndex = LibraryTab.entries.indexOf(state.tab),
                    onSelect = { onSelectTab(LibraryTab.entries[it]) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The content sits on its own panel under the tinted header, as the v3 library draws it.
            Surface(
                color = scheme.surfaceContainerLowest,
                shape = RoundedCornerShape(topStart = PANEL_CORNER, topEnd = PANEL_CORNER),
                modifier = Modifier.fillMaxSize().padding(top = Spacing.xs),
            ) {
                Column {
                    Toolbar(state, onSort, onSortDescending, onToggleLayout, onShuffle)
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
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
    }
}

/**
 * The title in 36 sp bold primary on the row of its search button, as in PixelPlayer: one row,
 * not a large title under a bar. A long word steps down to fit.
 */
@Composable
private fun Header(onSearch: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(start = Spacing.xl, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.displayMedium,
            color = scheme.primary,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 24.sp,
                maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                stepSize = 2.sp,
            ),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        CircleButton(
            DowntifyIcons.Search,
            stringResource(R.string.library_search),
            onSearch,
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
    }
}

/**
 * Shuffle at the start, on every tab (see [shufflePick]); at the end, as icons, list or grid
 * (covers) and the sort, which opens [SortSheet]. Side by side they're one connected button group.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Toolbar(
    state: LibraryUiState,
    onSort: (LibrarySort) -> Unit,
    onSortDescending: (Boolean) -> Unit,
    onToggleLayout: () -> Unit,
    onShuffle: () -> Unit,
) {
    var sorting by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val height = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = onShuffle,
            enabled = state.canShuffle,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
            ),
            modifier = Modifier.heightIn(min = height),
        ) {
            Icon(painterResource(DowntifyIcons.Shuffle), contentDescription = null)
            Spacer(Modifier.size(Spacing.sm))
            Text(stringResource(R.string.collection_shuffle), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            val sortLabel = stringResource(R.string.library_sort, stringResource(state.sort.label))
            if (state.tab == LibraryTab.Songs) {
                // Songs are always a list: the sort stands alone.
                ToolbarButton(DowntifyIcons.Sort, sortLabel, IconButtonDefaults.shapes()) { sorting = true }
            } else {
                val grid = state.layout == LibraryLayout.Grid
                ToolbarButton(
                    icon = if (grid) DowntifyIcons.ListView else DowntifyIcons.GridView,
                    label = stringResource(if (grid) R.string.library_show_list else R.string.library_show_grid),
                    shapes = IconButtonShapes(
                        ButtonGroupDefaults.connectedLeadingButtonShape,
                        ButtonGroupDefaults.connectedLeadingButtonPressShape,
                    ),
                    onClick = onToggleLayout,
                )
                ToolbarButton(
                    icon = DowntifyIcons.Sort,
                    label = sortLabel,
                    shapes = IconButtonShapes(
                        ButtonGroupDefaults.connectedTrailingButtonShape,
                        ButtonGroupDefaults.connectedTrailingButtonPressShape,
                    ),
                ) { sorting = true }
            }
        }
    }
    if (sorting) {
        SortSheet(
            sort = state.sort,
            descending = state.sortDescending,
            onSort = onSort,
            onDescending = onSortDescending,
            onDismiss = { sorting = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolbarButton(icon: Int, label: String, shapes: IconButtonShapes, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shapes = shapes, modifier = Modifier.size(TOOLBAR_BUTTON)) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

/**
 * How the library is sorted, after PixelPlayer's sheet: the order (ascending or descending) as one
 * card that flips it, then the sorts as a segmented list of radio rows. It stays open, so both can
 * change; the lists behind it follow at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortSheet(
    sort: LibrarySort,
    descending: Boolean,
    onSort: (LibrarySort) -> Unit,
    onDescending: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaceContainerHigh) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.library_sort_by),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.xs).semantics { heading() },
            )
            OrderCard(descending, onToggle = { onDescending(!descending) })
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListStyle.Segmented.gap)) {
                LibrarySort.entries.forEachIndexed { index, option ->
                    val selected = option == sort
                    val shape = ListStyle.Segmented.shapeOf(index, LibrarySort.entries.size)
                    SegmentedListItem(
                        selected = selected,
                        onClick = { onSort(option) },
                        shapes = ListItemShapes(shape, shape, shape, shape, shape, shape),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = scheme.surfaceContainerLow,
                            selectedContainerColor = scheme.secondaryContainer,
                        ),
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                    ) {
                        Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** "Order · Ascending" in primaryContainer: a tap flips it, and the arrow turns half a circle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderCard(descending: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val turn by animateFloatAsState(
        if (descending) HALF_TURN else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "sortOrderArrow",
    )
    Surface(
        onClick = onToggle,
        shape = DowntifyShape.Section,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            IconAvatar(
                DowntifyIcons.ArrowUpward,
                modifier = Modifier.rotate(turn),
                container = scheme.onPrimaryContainer.copy(alpha = ORDER_ICON_ALPHA),
                content = scheme.onPrimaryContainer,
                size = 48.dp,
            )
            Column {
                Text(
                    stringResource(R.string.library_sort_order),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer.copy(alpha = ORDER_LABEL_ALPHA),
                )
                Text(
                    stringResource(
                        if (descending) R.string.library_sort_descending else R.string.library_sort_ascending,
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
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

// The end leaves room for the scrollbar; the bottom clears the mini player and the bar.
private val gridPadding: PaddingValues
    @Composable get() =
        PaddingValues(start = Spacing.screen, end = ScrollbarTouchWidth, top = Spacing.sm, bottom = Spacing.xl)
            .plusBottomBars()
private val listPadding: PaddingValues
    @Composable get() = gridPadding
private val listGap = Arrangement.spacedBy(ListStyle.Separated.gap)

/** Songs are a long list: closer together, as PixelPlayer's. */
private val songsGap = Arrangement.spacedBy(Spacing.xs)
private val PANEL_CORNER = 32.dp
private val TOOLBAR_BUTTON = 48.dp
private const val TINT_ALPHA = 0.18f
private const val HALF_TURN = 180f
private const val ORDER_ICON_ALPHA = 0.12f
private const val ORDER_LABEL_ALPHA = 0.8f

/** The scrollbar along the panel's end, from the first row to just above the mini player and the bar. */
@Composable
private fun BoxScope.Scrollbar(state: LazyListState) {
    VerticalScrollbar(state, scrollbarPlacement())
}

@Composable
private fun BoxScope.Scrollbar(state: LazyGridState) {
    VerticalScrollbar(state, scrollbarPlacement())
}

@Composable
private fun BoxScope.scrollbarPlacement(): Modifier = Modifier
    .align(Alignment.TopEnd)
    .padding(top = Spacing.sm, bottom = Spacing.xl + LocalBottomBarsInset.current)

@Composable
private fun Albums(state: LibraryUiState, navigation: LibraryNavigation) {
    val covers = LocalCoverUrls.current
    if (state.albums.isEmpty()) return Empty(R.string.library_empty_albums)
    Box(Modifier.fillMaxSize()) {
        if (state.layout == LibraryLayout.Grid) {
            val grid = rememberLazyGridState()
            LazyVerticalGrid(
                columns = gridCells,
                state = grid,
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
            Scrollbar(grid)
        } else {
            val list = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = listPadding, verticalArrangement = listGap) {
                items(state.albums, key = { it.id }) { album ->
                    CoverItem(
                        album.title,
                        albumSubtitle(album),
                        covers.track(album.coverTrackId, ServerUrls.COVER_SMALL),
                        { navigation.onAlbum(album.id) },
                    )
                }
            }
            Scrollbar(list)
        }
    }
}

@Composable
private fun Artists(state: LibraryUiState, navigation: LibraryNavigation) {
    val covers = LocalCoverUrls.current
    if (state.artists.isEmpty()) return Empty(R.string.library_empty_artists)
    Box(Modifier.fillMaxSize()) {
        if (state.layout == LibraryLayout.Grid) {
            val grid = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = grid,
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
            Scrollbar(grid)
        } else {
            val list = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = listPadding, verticalArrangement = listGap) {
                items(state.artists, key = { it.id }) { artist ->
                    CoverItem(
                        artist.name,
                        artistSubtitle(artist),
                        covers.track(artist.coverTrackId, ServerUrls.COVER_SMALL),
                        { navigation.onArtist(artist.id) },
                        round = true,
                        placeholderIcon = DowntifyIcons.Artist,
                    )
                }
            }
            Scrollbar(list)
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

    fun placeholder(item: PlaylistItem) =
        if (item.playlist == null) DowntifyIcons.FavoriteFilled else DowntifyIcons.Playlist

    Box(Modifier.fillMaxSize()) {
        if (state.layout == LibraryLayout.Grid) {
            val grid = rememberLazyGridState()
            LazyVerticalGrid(
                columns = gridCells,
                state = grid,
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
                        placeholderIcon = placeholder(item),
                    )
                }
            }
            Scrollbar(grid)
        } else {
            val list = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = listPadding, verticalArrangement = listGap) {
                items(state.playlists, key = { it.playlist?.name ?: "" }) { item ->
                    CoverItem(
                        title = item.playlist?.title ?: liked,
                        subtitle = songsCount(item.count),
                        coverUrl = cover(item, 150),
                        onClick = { open(item) },
                        placeholderIcon = placeholder(item),
                    )
                }
            }
            Scrollbar(list)
        }
    }
}

@Composable
private fun Songs(state: LibraryUiState, onPlay: (Int) -> Unit, actions: TrackActions) {
    val covers = LocalCoverUrls.current
    if (state.songs.isEmpty()) return Empty(R.string.library_empty_songs)
    Box(Modifier.fillMaxSize()) {
        val list = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = listPadding, verticalArrangement = songsGap) {
            itemsIndexed(state.songs, key = { _, track: Track -> track.id }) { index, track ->
                TrackItem(
                    track = track,
                    onClick = { onPlay(index) },
                    coverUrl = covers.track(track.id.takeIf { track.hasCover }, 150),
                    isCurrent = track.id == state.currentTrackId,
                    isPlaying = state.isPlaying,
                    isLiked = track.id in state.likedIds,
                    actions = actions,
                    compact = true,
                )
            }
        }
        Scrollbar(list)
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
                hero = true,
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

@PhonePreviews
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

@PhonePreviews
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
            trackActions = TrackActions(onAddToQueue = {}),
        )
    }
}

@PhonePreviews
@Composable
private fun OrderCardPreview() {
    DowntifyTheme {
        Surface {
            OrderCard(descending = false, onToggle = {})
        }
    }
}
