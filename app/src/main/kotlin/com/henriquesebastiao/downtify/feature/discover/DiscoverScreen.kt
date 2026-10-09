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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.SectionHeader
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.DiscoverAlbum
import com.henriquesebastiao.downtify.core.model.DiscoverArtist
import com.henriquesebastiao.downtify.core.model.DiscoverCollections
import com.henriquesebastiao.downtify.core.model.DiscoverInput
import com.henriquesebastiao.downtify.core.model.DiscoverPlaylist
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.CoverRow
import com.henriquesebastiao.downtify.ui.common.LoadError

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
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(DowntifyIcons.Back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(
                            painterResource(DowntifyIcons.Refresh),
                            contentDescription = stringResource(R.string.discover_refresh),
                        )
                    }
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

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.discover_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), modifier = Modifier.fillMaxSize()) {
        if (state.partial) {
            item(key = "partial") {
                Text(
                    stringResource(R.string.discover_partial),
                    style = MaterialTheme.typography.bodySmall,
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
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

private fun androidx.compose.foundation.lazy.LazyListScope.collections(
    collections: DiscoverCollections?,
    loading: Boolean,
    onOpen: (String) -> Unit,
) {
    if (loading) {
        item(key = "collections-loading") {
            Text(
                stringResource(R.string.discover_loading_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            )
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

@Composable
private fun ArtistRow(artist: DiscoverArtist, onOpen: () -> Unit, onHide: () -> Unit, onPreview: () -> Unit) {
    CoverRow(
        title = artist.name,
        subtitle = artist.because.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.discover_because, it.joinToString(", ")) },
        coverUrl = artist.pictureUrl.ifBlank { null },
        onClick = onOpen,
        round = true,
        placeholderIcon = DowntifyIcons.Artist,
        // Why it was suggested is the point of the row: let it wrap.
        subtitleLines = 2,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreview) {
                    Icon(
                        painterResource(DowntifyIcons.Play),
                        contentDescription = stringResource(R.string.discover_preview, artist.name),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(
                            painterResource(DowntifyIcons.MoreVert),
                            contentDescription = stringResource(R.string.discover_artist_options, artist.name),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(albums, key = { it.url.ifBlank { it.name + it.artist } }) { album ->
                CoverCard(
                    title = album.name,
                    subtitle = listOf(album.artist, album.year).filter { it.isNotBlank() }.joinToString(" · "),
                    coverUrl = album.coverUrl.ifBlank { null },
                    onClick = { if (album.url.isNotBlank()) onOpen(album.url) },
                    shape = MaterialTheme.shapes.extraLarge,
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(playlists, key = { it.url.ifBlank { it.name } }) { playlist ->
                CoverCard(
                    title = playlist.name,
                    subtitle = playlist.owner.ifBlank { null },
                    coverUrl = playlist.coverUrl.ifBlank { null },
                    onClick = { if (playlist.url.isNotBlank()) onOpen(playlist.url) },
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholderIcon = DowntifyIcons.Playlist,
                    modifier = Modifier.width(CARD_WIDTH),
                )
            }
        }
    }
}

private val CARD_WIDTH = 172.dp

@PreviewLightDark
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
