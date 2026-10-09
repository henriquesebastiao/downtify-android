package com.henriquesebastiao.downtify.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.RemoteAlbum
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ResolvedLink
import com.henriquesebastiao.downtify.core.model.ServerDownloadProgress
import com.henriquesebastiao.downtify.core.model.StreamVideo
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.player.LyricsSheet
import com.henriquesebastiao.downtify.ui.common.CoverRow
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackRow
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.artistSubtitle
import com.henriquesebastiao.downtify.ui.common.songsCount

data class SearchNavigation(
    val onAlbum: (String) -> Unit = {},
    val onArtist: (String) -> Unit = {},
    val onPlaylist: (String) -> Unit = {},
    val onSimilar: (artist: String, title: String) -> Unit = { _, _ -> },
    /** Open a server link (a release from an artist page) as a search. */
    val onOpenLink: (String) -> Unit = {},
)

@Composable
fun SearchRoute(
    navigation: SearchNavigation,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is SearchMessage.Queued -> if (message.count > 1) {
                        resources.getQuantityString(
                            R.plurals.search_queued_many,
                            message.count,
                            message.title,
                            message.count,
                        )
                    } else {
                        resources.getString(R.string.search_queued_one, message.title)
                    }

                    SearchMessage.NoStream -> resources.getString(R.string.search_no_stream)

                    SearchMessage.RequestFailed -> resources.getString(R.string.search_request_failed)

                    SearchMessage.Unreachable -> resources.getString(R.string.search_unreachable)
                },
            )
        }
    }
    SearchScreen(
        query = query,
        state = state,
        navigation = navigation,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onPlaySong = viewModel::playSong,
        remote = RemoteActions(
            onRetry = viewModel::retry,
            onPlay = viewModel::playRemote,
            onDownload = viewModel::download,
            onDownloadAlbum = viewModel::downloadAlbum,
            onDownloadLink = viewModel::downloadLink,
            onPlayLink = viewModel::playLink,
            onToggleLike = viewModel::toggleLike,
            onShowLyrics = viewModel::showLyrics,
        ),
        snackbarHostState = snackbar,
        modifier = modifier,
    )
    val lyrics by viewModel.lyricsFor.collectAsStateWithLifecycle()
    lyrics?.let {
        LyricsSheet(lyrics = it, positionMs = 0, onSeek = {}, onDismiss = viewModel::hideLyrics)
    }
}

/** What the server's results can do. */
data class RemoteActions(
    val onRetry: () -> Unit = {},
    val onPlay: (RemoteSong, List<RemoteSong>) -> Unit = { _, _ -> },
    val onDownload: (RemoteSong) -> Unit = {},
    val onDownloadAlbum: (RemoteAlbum) -> Unit = {},
    val onDownloadLink: (ResolvedLink) -> Unit = {},
    val onPlayLink: (ResolvedLink) -> Unit = {},
    val onToggleLike: (Track) -> Unit = {},
    val onShowLyrics: (Track) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    navigation: SearchNavigation,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onPlaySong: (Int) -> Unit,
    modifier: Modifier = Modifier,
    remote: RemoteActions = RemoteActions(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
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
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            ) {}
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(SearchFilter.entries) { filter ->
                    val selected = filter == state.filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filter) },
                        label = { Text(stringResource(filter.label)) },
                        leadingIcon = if (selected) {
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
            Results(query, state, navigation, onPlaySong, remote)
        }
    }
}

@Composable
private fun Results(
    query: String,
    state: SearchUiState,
    navigation: SearchNavigation,
    onPlaySong: (Int) -> Unit,
    remote: RemoteActions,
) {
    val covers = LocalCoverUrls.current
    val results = state.results
    val showServer = state.isLink || state.filter == SearchFilter.All || state.filter == SearchFilter.Songs ||
        state.filter == SearchFilter.Albums
    when {
        query.isBlank() -> EmptyState(icon = DowntifyIcons.Search, title = stringResource(R.string.search_prompt))

        results.isEmpty && !showServer && state.query == query -> EmptyState(
            icon = DowntifyIcons.Search,
            title = stringResource(R.string.search_no_results, query),
        )

        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            if (!results.isEmpty) {
                item(key = "library") { SectionTitle(stringResource(R.string.search_in_library)) }
            }
            items(results.albums, key = { "album:${it.id}" }) { album ->
                CoverRow(
                    title = album.title,
                    subtitle = stringResource(
                        R.string.album_subtitle,
                        stringResource(R.string.search_kind_album),
                        albumSubtitle(album),
                    ),
                    coverUrl = covers.track(album.coverTrackId, ServerUrls.COVER_SMALL),
                    onClick = { navigation.onAlbum(album.id) },
                )
            }
            items(results.artists, key = { "artist:${it.id}" }) { artist ->
                CoverRow(
                    title = artist.name,
                    subtitle = stringResource(
                        R.string.album_subtitle,
                        stringResource(R.string.search_kind_artist),
                        artistSubtitle(artist),
                    ),
                    coverUrl = covers.track(artist.coverTrackId, ServerUrls.COVER_SMALL),
                    onClick = { navigation.onArtist(artist.id) },
                    round = true,
                    placeholderIcon = DowntifyIcons.Artist,
                )
            }
            items(results.playlists, key = { "playlist:${it.name}" }) { playlist ->
                CoverRow(
                    title = playlist.name,
                    subtitle = stringResource(
                        R.string.album_subtitle,
                        stringResource(R.string.search_kind_playlist),
                        songsCount(playlist.trackIds.size),
                    ),
                    coverUrl = covers.playlist(playlist, playlist.trackIds.firstOrNull(), ServerUrls.COVER_SMALL),
                    onClick = { navigation.onPlaylist(playlist.name) },
                    placeholderIcon = DowntifyIcons.Playlist,
                )
            }
            itemsIndexed(results.songs, key = { _, t -> "song:${t.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onPlaySong(index) },
                    coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                    isCurrent = track.id == state.currentTrackId,
                    isPlaying = state.isPlaying,
                )
            }
            if (showServer) serverSection(query, state, navigation, remote, divider = !results.isEmpty)
        }
    }
}

private fun LazyListScope.serverSection(
    query: String,
    state: SearchUiState,
    navigation: SearchNavigation,
    remote: RemoteActions,
    divider: Boolean,
) {
    val server = state.server
    if (server == ServerResults.Idle) return
    val showSongs = state.isLink || state.filter != SearchFilter.Albums
    val showAlbums = state.isLink || state.filter != SearchFilter.Songs
    if (!state.isLink) {
        item(key = "server") { ServerHeader(divider) }
    }
    when (server) {
        ServerResults.Idle -> Unit

        ServerResults.Loading -> item(key = "server-loading") {
            StatusLine(
                stringResource(if (state.isLink) R.string.search_link_loading else R.string.search_server_loading),
            )
        }

        ServerResults.Unreachable -> item(key = "server-unreachable") {
            StatusLine(stringResource(R.string.search_server_unreachable), onRetry = remote.onRetry)
        }

        is ServerResults.Failed -> item(key = "server-failed") {
            FailedLine(server.status, state.isLink, remote.onRetry)
        }

        is ServerResults.Found -> {
            if (server.songs.isEmpty() && server.albums.isEmpty()) {
                item(key = "server-nothing") { StatusLine(stringResource(R.string.search_server_nothing, query)) }
            }
            if (showSongs) remoteSongs(server.songs, state, navigation, remote)
            if (showAlbums) remoteAlbums(server.albums, state, navigation, remote)
        }

        is ServerResults.Link -> linkResults(server.link, state, navigation, remote)
    }
}

private fun LazyListScope.linkResults(
    link: ResolvedLink,
    state: SearchUiState,
    navigation: SearchNavigation,
    remote: RemoteActions,
) {
    item(key = "link") {
        LinkHeader(
            link = link,
            requested = link.tracks.isNotEmpty() && link.tracks.all { it.id in state.jobs },
            onDownload = { remote.onDownloadLink(link) },
            onPlay = { remote.onPlayLink(link) },
        )
    }
    remoteSongs(link.tracks, state, navigation, remote)
    remoteAlbums(link.albums, state, navigation, remote)
}

@Composable
private fun FailedLine(status: Int?, isLink: Boolean, onRetry: () -> Unit) {
    val permanent = status == HTTP_BAD_REQUEST || status == HTTP_NOT_FOUND
    StatusLine(
        stringResource(
            when {
                isLink && status == HTTP_BAD_REQUEST -> R.string.search_link_unsupported
                isLink && status == HTTP_NOT_FOUND -> R.string.search_link_empty
                else -> R.string.search_server_failed
            },
        ),
        onRetry = onRetry.takeUnless { permanent },
    )
}

private fun LazyListScope.remoteSongs(
    songs: List<RemoteSong>,
    state: SearchUiState,
    navigation: SearchNavigation,
    remote: RemoteActions,
) {
    items(songs, key = { "remote:${it.id}" }) { song ->
        val libraryTrack = state.library?.findSong(song)
        RemoteSongRow(
            song = song,
            job = state.jobs[song.id],
            playing = state.playingStreamId?.let { StreamVideo.videoIdOf(song) == it } == true,
            resolving = state.resolvingStreamId == song.id,
            onPlay = { remote.onPlay(song, songs) },
            onSimilar = { navigation.onSimilar(song.artist, song.title) },
            onDownload = { remote.onDownload(song) },
            libraryTrack = libraryTrack,
            isLiked = libraryTrack != null && libraryTrack.id in state.likedIds,
            onToggleLike = remote.onToggleLike,
            onShowLyrics = remote.onShowLyrics,
        )
    }
}

private fun LazyListScope.remoteAlbums(
    albums: List<RemoteAlbum>,
    state: SearchUiState,
    navigation: SearchNavigation,
    remote: RemoteActions,
) {
    items(albums, key = { "remote-album:${it.id}" }) { album ->
        RemoteAlbumRow(
            album = album,
            progress = state.requestedAlbums[album.id]?.let { ServerDownloadProgress.of(it, state.jobs) },
            onDownload = { remote.onDownloadAlbum(album) },
            onOpen = { if (album.url.isNotBlank()) navigation.onOpenLink(album.url) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm).semantics { heading() },
    )
}

@Composable
private fun ServerHeader(divider: Boolean) {
    Column(Modifier.padding(top = if (divider) Spacing.md else 0.dp)) {
        if (divider) HorizontalDivider(Modifier.padding(horizontal = Spacing.screen))
        Row(
            Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                painterResource(DowntifyIcons.Cloud),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.search_not_in_library),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.search_server_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun StatusLine(text: String, onRetry: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.search_retry)) }
    }
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404

private val SearchFilter.label: Int
    get() = when (this) {
        SearchFilter.All -> R.string.search_all
        SearchFilter.Songs -> R.string.library_songs
        SearchFilter.Albums -> R.string.library_albums
        SearchFilter.Artists -> R.string.library_artists
        SearchFilter.Playlists -> R.string.library_playlists
    }

@PreviewLightDark
@Composable
private fun SearchPreview() {
    DowntifyTheme {
        SearchScreen(
            query = "harbor",
            state = SearchUiState(
                query = "harbor",
                results = SearchResults(
                    albums = PreviewData.albums.take(1),
                    artists = PreviewData.artists.take(1),
                    songs = PreviewData.tracks.take(3),
                ),
            ),
            navigation = SearchNavigation(),
            onQueryChange = {},
            onFilterChange = {},
            onPlaySong = {},
        )
    }
}
