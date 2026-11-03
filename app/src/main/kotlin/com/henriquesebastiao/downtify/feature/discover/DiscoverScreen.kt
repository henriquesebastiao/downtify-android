package com.henriquesebastiao.downtify.feature.discover

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.component.SectionHeader
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.DiscoverAlbum
import com.henriquesebastiao.downtify.core.model.DiscoverArtist
import com.henriquesebastiao.downtify.core.model.DiscoverCollections
import com.henriquesebastiao.downtify.core.model.DiscoverInput
import com.henriquesebastiao.downtify.core.model.DiscoverPlaylist
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.CoverItem
import com.henriquesebastiao.downtify.ui.common.LoadError
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

@Composable
fun DiscoverRoute(
    onBack: () -> Unit,
    onOpenInSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is DiscoverMessage.Hidden -> {
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.discover_hidden, message.artist.name),
                        actionLabel = resources.getString(R.string.discover_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoHide(message.artist)
                }

                DiscoverMessage.Failed -> snackbar.showSnackbar(resources.getString(R.string.load_error_failed))
            }
        }
    }
    DiscoverScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onShowAll = viewModel::showAll,
        onOpenInSearch = onOpenInSearch,
        onHide = viewModel::hide,
        onPreview = viewModel::preview,
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

/**
 * Artists you don't have yet, and albums and playlists built on them. Each opens in Search,
 * ready to preview and download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowAll: () -> Unit,
    onOpenInSearch: (String) -> Unit,
    onHide: (DiscoverArtist) -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (DiscoverArtist) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PageHeader(
                title = stringResource(R.string.discover_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
                actions = {
                    CircleButton(
                        DowntifyIcons.Refresh,
                        stringResource(R.string.discover_refresh),
                        onRefresh,
                        enabled = !state.loading,
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Loading()

                state.libraryEmpty -> EmptyState(
                    icon = DowntifyIcons.Explore,
                    title = stringResource(R.string.discover_empty_title),
                    body = stringResource(R.string.discover_empty_body),
                    hero = true,
                )

                state.artists.isEmpty() && state.error != null -> EmptyState(
                    icon = DowntifyIcons.Explore,
                    title = stringResource(
                        if (state.error ==
                            LoadError.Unreachable
                        ) {
                            R.string.load_error_unreachable
                        } else {
                            R.string.load_error_failed
                        },
                    ),
                    body = stringResource(R.string.discover_try_again),
                )

                else -> Content(state, onShowAll, onOpenInSearch, onHide, onPreview)
            }
        }
    }
}

/** The first suggestions take the server a while to build: a long wait, so the wavy indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularWavyProgressIndicator(Modifier.size(56.dp))
        Text(
            stringResource(R.string.discover_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Content(
    state: DiscoverUiState,
    onShowAll: () -> Unit,
    onOpen: (String) -> Unit,
    onHide: (DiscoverArtist) -> Unit,
    onPreview: (DiscoverArtist) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
        verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.partial) {
            item(key = "partial") {
                Text(
                    stringResource(R.string.discover_partial),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
        }
        if (state.artists.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    icon = DowntifyIcons.Explore,
                    title = stringResource(R.string.discover_none_title),
                    body = stringResource(R.string.discover_none_body),
                    hero = true,
                )
            }
        } else {
            item(key = "artists-title") {
                SectionHeader(stringResource(R.string.discover_artists), modifier = Modifier.padding(top = Spacing.sm))
            }
            items(state.shownArtists, key = { "artist:${it.name}" }) { artist ->
                ArtistRow(
                    artist,
                    onOpen = { onOpen(DiscoverInput.searchFor(artist)) },
                    onHide = { onHide(artist) },
                    onPreview = { onPreview(artist) },
                    previewing = state.previewingArtist == artist.name,
                    previewPlaying = state.previewPlaying,
                )
            }
            if (!state.showAll && state.artists.size > DiscoverUiState.TOP_ARTISTS) {
                item(key = "show-all") {
                    TextButton(onClick = onShowAll, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                        Text(stringResource(R.string.discover_show_all, state.artists.size))
                    }
                }
            }
        }
        collections(state.collections, state.collectionsLoading, onOpen)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.collections(collections: DiscoverCollections?, loading: Boolean, onOpen: (String) -> Unit) {
    if (loading) {
        item(key = "collections-loading") {
            Row(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                LoadingIndicator(Modifier.size(40.dp))
                Text(
                    stringResource(R.string.discover_loading_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (collections == null) return
    if (collections.albums.isNotEmpty()) {
        item(key = "albums") { AlbumRow(R.string.discover_albums, collections.albums, onOpen) }
    }
    if (collections.moreAlbums.isNotEmpty()) {
        item(key = "more-albums") { AlbumRow(R.string.discover_more_albums, collections.moreAlbums, onOpen) }
    }
    if (collections.playlists.isNotEmpty()) {
        item(key = "playlists") { PlaylistRow(collections.playlists, onOpen) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistRow(
    artist: DiscoverArtist,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onPreview: () -> Unit,
    previewing: Boolean = false,
    previewPlaying: Boolean = false,
) {
    CoverItem(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        title = artist.name,
        subtitle = artist.because.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.discover_because, it.joinToString(", ")) },
        coverUrl = artist.pictureUrl.ifBlank { null },
        onClick = onOpen,
        round = true,
        placeholderIcon = DowntifyIcons.Artist,
        // Why it was suggested is the point of the row: let it wrap, beside two buttons.
        subtitleLines = 3,
        trailing = {
            if (previewing) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    PlayingBars(animate = previewPlaying)
                }
            } else {
                CircleButton(
                    DowntifyIcons.Play,
                    stringResource(R.string.discover_preview, artist.name),
                    onPreview,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            run {
                var open by remember { mutableStateOf(false) }
                Box {
                    CircleButton(
                        DowntifyIcons.MoreVert,
                        stringResource(R.string.discover_artist_options, artist.name),
                        { open = true },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.discover_not_interested)) },
                            onClick = {
                                open = false
                                onHide()
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AlbumRow(title: Int, albums: List<DiscoverAlbum>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(albums, key = { it.url.ifBlank { it.name + it.artist } }) { album ->
                CoverCard(
                    title = album.name,
                    subtitle = listOf(album.artist, album.year).filter { it.isNotBlank() }.joinToString(" · "),
                    coverUrl = album.coverUrl.ifBlank { null },
                    onClick = { if (album.url.isNotBlank()) onOpen(album.url) },
                    shape = DowntifyShape.Artwork,
                    modifier = Modifier.width(CARD_WIDTH),
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlists: List<DiscoverPlaylist>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.discover_playlists))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(playlists, key = { it.url.ifBlank { it.name } }) { playlist ->
                CoverCard(
                    title = playlist.name,
                    subtitle = playlist.owner.ifBlank { null },
                    coverUrl = playlist.coverUrl.ifBlank { null },
                    onClick = { if (playlist.url.isNotBlank()) onOpen(playlist.url) },
                    shape = DowntifyShape.Artwork,
                    placeholderIcon = DowntifyIcons.Playlist,
                    modifier = Modifier.width(CARD_WIDTH),
                )
            }
        }
    }
}

private val CARD_WIDTH = 172.dp

@PhonePreviews
@Composable
private fun DiscoverPreview() {
    DowntifyTheme {
        DiscoverScreen(
            state = DiscoverUiState(
                loading = false,
                artists = listOf(
                    DiscoverArtist("Hooverphonic", "", listOf("Portishead", "Massive Attack"), ""),
                    DiscoverArtist("Archive", "", listOf("Portishead"), ""),
                ),
                collections = DiscoverCollections(
                    albums = listOf(
                        DiscoverAlbum("Grace", "Jeff Buckley", "1994", "", "https://x", "similar", emptyList()),
                    ),
                    moreAlbums = emptyList(),
                    playlists = listOf(
                        DiscoverPlaylist("Portishead Radio", "Spotify", "", "https://y", "radio", "Portishead"),
                    ),
                    artistUrls = emptyMap(),
                    partial = false,
                ),
            ),
            onBack = {},
            onRefresh = {},
            onShowAll = {},
            onOpenInSearch = {},
            onHide = {},
        )
    }
}
