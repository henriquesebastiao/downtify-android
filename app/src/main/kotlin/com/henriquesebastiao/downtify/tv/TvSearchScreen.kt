package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RemoteAlbum
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.StreamVideo
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.search.SearchFilter
import com.henriquesebastiao.downtify.feature.search.SearchNavigation
import com.henriquesebastiao.downtify.feature.search.SearchResults
import com.henriquesebastiao.downtify.feature.search.SearchUiState
import com.henriquesebastiao.downtify.feature.search.SearchViewModel
import com.henriquesebastiao.downtify.feature.search.ServerResults
import com.henriquesebastiao.downtify.feature.search.label
import com.henriquesebastiao.downtify.feature.search.text
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.songsCount

/** Search on a TV: the phone's [SearchViewModel], drawn by [TvSearchScreen]. */
@Composable
fun TvSearchRoute(
    navigation: SearchNavigation,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message.text(resources)) }
    }
    Box(modifier.fillMaxSize()) {
        TvSearchScreen(
            query = query,
            state = state,
            navigation = navigation,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onPlaySong = viewModel::playSong,
            onPlayRemote = viewModel::playRemote,
            onRetry = viewModel::retry,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl))
    }
}

/**
 * Search on a TV, after the TV music apps: a wide search field and the filters stay at the top;
 * under them the results as rows of covers, what's in the library first (artists, albums,
 * playlists, songs), then what the server found (songs to play in full, albums to open), its rows
 * titled so. OK plays
 * a song or opens the rest; the focused row scrolls up under the filters. The field opens the
 * keyboard on OK only, and Up and Down always leave it, so the remote never gets stuck in it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvSearchScreen(
    query: String,
    state: SearchUiState,
    navigation: SearchNavigation,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onPlaySong: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPlayRemote: (RemoteSong, List<RemoteSong>) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        TvSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = TvMargin - TvRingSpace),
        )
        Row(
            Modifier
                .padding(start = TvMargin - TvRingSpace, end = TvMargin - TvRingSpace, top = Spacing.sm)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SearchFilter.entries.forEach { filter ->
                TvPill(
                    label = stringResource(filter.label),
                    selected = filter == state.filter,
                    onClick = { onFilterChange(filter) },
                )
            }
        }
        if (query.isBlank()) {
            Message(stringResource(R.string.search_prompt))
        } else {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(SectionTop)) {
                LazyColumn(
                    // Room after the last row, so it too can scroll up under the filters.
                    contentPadding = PaddingValues(top = Spacing.md, bottom = LastRowRoom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    libraryRows(state, navigation, onPlaySong)
                    val showServer = state.isLink || state.filter == SearchFilter.All ||
                        state.filter == SearchFilter.Songs || state.filter == SearchFilter.Albums
                    if (state.results.isEmpty && !showServer && state.query == query) {
                        item(key = "nothing") { Message(stringResource(R.string.search_no_results, query)) }
                    }
                    if (showServer) serverRows(query, state, navigation, onPlayRemote, onRetry)
                }
            }
        }
    }
}

/** The field: a wide pill. OK opens the keyboard; the keyboard's search key puts it away. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TvSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Read-only until OK: a read-only field starts no keyboard, so focus landing on it doesn't.
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editing) { if (editing) keyboard?.show() }
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        readOnly = !editing,
        singleLine = true,
        shape = CircleShape,
        placeholder = {
            Text(stringResource(R.string.search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = { Icon(painterResource(DowntifyIcons.Search), contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(DowntifyIcons.Close),
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        } else {
            null
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            editing = false
        }),
        interactionSource = interaction,
        modifier = modifier
            .tvFocusFrame(focused, CircleShape)
            .onFocusChanged { if (!it.isFocused) editing = false }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    // Up and Down always leave the field: the results are below, the tabs above.
                    Key.DirectionDown, Key.DirectionUp -> {
                        if (event.type == KeyEventType.KeyDown) {
                            keyboard?.hide()
                            editing = false
                            focusManager.moveFocus(
                                if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up,
                            )
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (editing) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) editing = true
                        true
                    }

                    // Not typing: nothing beside it, and the cursor has nowhere to go.
                    Key.DirectionLeft, Key.DirectionRight -> !editing

                    else -> false
                }
            },
    )
}

private fun LazyListScope.libraryRows(state: SearchUiState, navigation: SearchNavigation, onPlaySong: (Int) -> Unit) {
    val results: SearchResults = state.results
    artistRow(results.artists, navigation)
    albumRow(results.albums, navigation)
    playlistRow(results.playlists, navigation)
    songRow(state, onPlaySong)
}

private fun LazyListScope.artistRow(artists: List<Artist>, navigation: SearchNavigation) {
    if (artists.isEmpty()) return
    item(key = "artists") {
        TvSection(stringResource(R.string.library_artists)) {
            val covers = LocalCoverUrls.current
            TvCoverRow {
                items(artists, key = { it.id }) { artist ->
                    TvCoverCard(
                        title = artist.name,
                        subtitle = null,
                        coverUrl = covers.artist(artist.name),
                        fallbackUrl = covers.track(artist.coverTrackId, ServerUrls.COVER_LARGE),
                        onClick = { navigation.onArtist(artist.id) },
                        shape = CircleShape,
                        ringShape = CircleShape,
                        placeholderIcon = DowntifyIcons.Artist,
                        centered = true,
                        modifier = Modifier.width(CardWidth),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.albumRow(albums: List<Album>, navigation: SearchNavigation) {
    if (albums.isEmpty()) return
    item(key = "albums") {
        TvSection(stringResource(R.string.library_albums)) {
            val covers = LocalCoverUrls.current
            TvCoverRow {
                items(albums, key = { it.id }) { album ->
                    TvCoverCard(
                        title = album.title,
                        subtitle = albumSubtitle(album),
                        coverUrl = covers.track(album.coverTrackId, ServerUrls.COVER_LARGE),
                        onClick = { navigation.onAlbum(album.id) },
                        modifier = Modifier.width(CardWidth),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.playlistRow(playlists: List<Playlist>, navigation: SearchNavigation) {
    if (playlists.isEmpty()) return
    item(key = "playlists") {
        TvSection(stringResource(R.string.library_playlists)) {
            val covers = LocalCoverUrls.current
            TvCoverRow {
                items(playlists, key = { it.name }) { playlist ->
                    TvCoverCard(
                        title = playlist.title,
                        subtitle = songsCount(playlist.trackIds.size),
                        coverUrl = covers.playlist(playlist, playlist.trackIds.firstOrNull(), ServerUrls.COVER_LARGE),
                        onClick = { navigation.onPlaylist(playlist.name) },
                        placeholderIcon = DowntifyIcons.Playlist,
                        modifier = Modifier.width(CardWidth),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.songRow(state: SearchUiState, onPlaySong: (Int) -> Unit) {
    val songs = state.results.songs
    if (songs.isEmpty()) return
    item(key = "songs") {
        TvSection(stringResource(R.string.library_songs)) {
            val covers = LocalCoverUrls.current
            TvCoverRow {
                itemsIndexed(songs, key = { _, track -> track.id }) { index, track ->
                    TvCoverCard(
                        title = track.displayTitle,
                        subtitle = track.displayArtist,
                        coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_LARGE),
                        onClick = { onPlaySong(index) },
                        placeholderIcon = DowntifyIcons.Song,
                        current = track.id == state.currentTrackId,
                        playing = state.isPlaying,
                        modifier = Modifier.width(CardWidth),
                    )
                }
            }
        }
    }
}

/** What the server found that isn't in the library: songs play in full from it, albums open. */
private fun LazyListScope.serverRows(
    query: String,
    state: SearchUiState,
    navigation: SearchNavigation,
    onPlayRemote: (RemoteSong, List<RemoteSong>) -> Unit,
    onRetry: () -> Unit,
) {
    val server = state.server
    if (server == ServerResults.Idle) return
    when (server) {
        ServerResults.Idle -> Unit

        ServerResults.Loading -> item(key = "server-loading") { ServerLoading(state.isLink) }

        ServerResults.Unreachable -> item(key = "server-unreachable") {
            Status(stringResource(R.string.search_server_unreachable), onRetry)
        }

        is ServerResults.Failed -> item(key = "server-failed") { ServerFailed(server.status, state.isLink, onRetry) }

        is ServerResults.Found -> {
            if (server.songs.isEmpty() && server.albums.isEmpty()) {
                item(key = "server-nothing") { Status(stringResource(R.string.search_server_nothing, query)) }
            }
            if (state.isLink || state.filter != SearchFilter.Albums) {
                remoteSongs("server-songs", server.songs, state, onPlayRemote, fromServer = true)
            }
            if (state.isLink || state.filter != SearchFilter.Songs) {
                remoteAlbums("server-albums", server.albums, navigation, fromServer = true)
            }
        }

        is ServerResults.Link -> {
            remoteSongs("link-songs", server.link.tracks, state, onPlayRemote)
            remoteAlbums("link-albums", server.link.albums, navigation)
        }
    }
}

private fun LazyListScope.remoteSongs(
    key: String,
    songs: List<RemoteSong>,
    state: SearchUiState,
    onPlayRemote: (RemoteSong, List<RemoteSong>) -> Unit,
    fromServer: Boolean = false,
) {
    if (songs.isEmpty()) return
    item(key = key) {
        TvSection(rowTitle(R.string.library_songs, fromServer)) {
            TvCoverRow {
                items(songs, key = { it.id }) { song ->
                    TvCoverCard(
                        title = song.title,
                        subtitle = song.artist,
                        coverUrl = song.coverUrl.ifBlank { null },
                        onClick = { onPlayRemote(song, songs) },
                        placeholderIcon = DowntifyIcons.Song,
                        current = state.playingStreamId != null && StreamVideo.videoIdOf(song) == state.playingStreamId,
                        playing = state.isPlaying,
                        modifier = Modifier.width(CardWidth),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.remoteAlbums(
    key: String,
    albums: List<RemoteAlbum>,
    navigation: SearchNavigation,
    fromServer: Boolean = false,
) {
    if (albums.isEmpty()) return
    item(key = key) {
        TvSection(rowTitle(R.string.library_albums, fromServer)) {
            TvCoverRow {
                items(albums, key = { it.id }) { album ->
                    TvCoverCard(
                        title = album.title,
                        subtitle = listOf(album.artist, album.year).filter { it.isNotBlank() }.joinToString(" · "),
                        coverUrl = album.coverUrl.ifBlank { null },
                        onClick = { if (album.url.isNotBlank()) navigation.onOpenLink(album.url) },
                        modifier = Modifier.width(CardWidth),
                    )
                }
            }
        }
    }
}

/**
 * A row's title; the server's rows say they aren't in the library ("Songs · Not in your library
 * yet"), since a row's title is all that shows of it once it scrolls up under the filters.
 */
@Composable
private fun rowTitle(kind: Int, fromServer: Boolean): String {
    val title = stringResource(kind)
    return if (fromServer) "$title · ${stringResource(R.string.search_not_in_library)}" else title
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerLoading(isLink: Boolean) {
    Row(
        Modifier.padding(horizontal = TvMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        LoadingIndicator(Modifier.size(32.dp))
        Text(
            stringResource(if (isLink) R.string.search_link_loading else R.string.search_server_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The server couldn't answer: a bad or empty link for good, anything else worth trying again. */
@Composable
private fun ServerFailed(status: Int?, isLink: Boolean, onRetry: () -> Unit) {
    val permanent = status == HTTP_BAD_REQUEST || status == HTTP_NOT_FOUND
    Status(
        stringResource(
            when {
                isLink && status == HTTP_BAD_REQUEST -> R.string.search_link_unsupported
                isLink && status == HTTP_NOT_FOUND -> R.string.search_link_empty
                else -> R.string.search_server_failed
            },
        ),
        onRetry.takeUnless { permanent },
    )
}

/** A line about the server's side, with a way to try again when it may work later. */
@Composable
private fun Status(text: String, onRetry: (() -> Unit)? = null) {
    Row(
        Modifier.padding(start = TvMargin, end = TvMargin - TvRingSpace),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onRetry != null) TvPill(stringResource(R.string.search_retry), selected = false, onClick = onRetry)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = TvMargin, vertical = Spacing.xxl),
    )
}

/** Where the focused row stops when the results scroll: its title shows above it. */
private val SectionTop = 44.dp
private val CardWidth = 136.dp
private val LastRowRoom = 240.dp
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvSearchPreview() {
    val artist = PreviewData.artists.first()
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvSearchScreen(
                query = "avr",
                state = SearchUiState(
                    query = "avr",
                    results = SearchResults(
                        albums = artist.albums,
                        artists = listOf(artist),
                        songs = artist.tracks,
                    ),
                ),
                navigation = SearchNavigation(),
                onQueryChange = {},
                onFilterChange = {},
                onPlaySong = {},
            )
        }
    }
}
