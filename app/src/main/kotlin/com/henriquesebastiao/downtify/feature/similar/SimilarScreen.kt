package com.henriquesebastiao.downtify.feature.similar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.StreamVideo
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.feature.player.LyricsSheet
import com.henriquesebastiao.downtify.feature.search.RemoteSongRow
import com.henriquesebastiao.downtify.ui.common.LoadError

@Composable
fun SimilarRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SimilarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is SimilarMessage.Queued -> resources.getString(R.string.search_queued_one, message.title)
                    SimilarMessage.NoStream -> resources.getString(R.string.similar_no_stream)
                    SimilarMessage.RequestFailed -> resources.getString(R.string.search_request_failed)
                    SimilarMessage.Unreachable -> resources.getString(R.string.search_unreachable)
                },
            )
        }
    }
    SimilarScreen(
        state = state,
        artist = viewModel.artist,
        track = viewModel.track,
        onArtistChange = viewModel::onArtistChange,
        onTrackChange = viewModel::onTrackChange,
        onFind = viewModel::search,
        onRetry = viewModel::retry,
        onBack = onBack,
        onPlay = viewModel::playRemote,
        onPivot = viewModel::pivot,
        onDownload = viewModel::download,
        onLoadMore = viewModel::loadMore,
        onToggleLike = viewModel::toggleLike,
        onShowLyrics = viewModel::showLyrics,
        snackbarHostState = snackbar,
        modifier = modifier,
    )
    val lyrics by viewModel.lyricsFor.collectAsStateWithLifecycle()
    lyrics?.let {
        LyricsSheet(lyrics = it, positionMs = 0, onSeek = {}, onDismiss = viewModel::hideLyrics)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarScreen(
    state: SimilarUiState,
    artist: String,
    track: String,
    onArtistChange: (String) -> Unit,
    onTrackChange: (String) -> Unit,
    onFind: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (RemoteSong, List<RemoteSong>) -> Unit = { _, _ -> },
    onPivot: (RemoteSong) -> Unit = {},
    onDownload: (RemoteSong) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onToggleLike: (Track) -> Unit = {},
    onShowLyrics: (Track) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.similar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(DowntifyIcons.Back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = artist,
                            onQueryChange = onArtistChange,
                            onSearch = { onFind() },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text(stringResource(R.string.similar_artist)) },
                            leadingIcon = {
                                Icon(
                                    painterResource(DowntifyIcons.Search),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = if (artist.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onArtistChange("") }) {
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
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                ) {}
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = track,
                            onQueryChange = onTrackChange,
                            onSearch = { onFind() },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text(stringResource(R.string.similar_track)) },
                            leadingIcon = {
                                Icon(
                                    painterResource(DowntifyIcons.Search),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = if (track.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onTrackChange("") }) {
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
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                ) {}
            }
            when {
                state.loading && state.songs.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.error != null && state.songs.isEmpty() -> EmptyState(
                    icon = DowntifyIcons.Error,
                    title = stringResource(
                        if (state.error == LoadError.Unreachable) {
                            R.string.similar_unreachable
                        } else {
                            R.string.similar_failed
                        },
                    ),
                    action = {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.search_retry))
                        }
                    },
                )

                state.searched && state.songs.isEmpty() -> EmptyState(
                    icon = DowntifyIcons.Search,
                    title = stringResource(R.string.similar_no_results),
                )

                state.songs.isNotEmpty() -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(state.songs, key = { "similar:${it.id}" }) { song ->
                        val libraryTrack = state.library?.findSong(song)
                        RemoteSongRow(
                            song = song,
                            job = state.jobs[song.id],
                            playing = state.playingStreamId?.let {
                                StreamVideo.videoIdOf(song) == it
                            } == true,
                            resolving = state.resolvingStreamId == song.id,
                            onPlay = { onPlay(song, state.songs) },
                            onSimilar = { onPivot(song) },
                            onDownload = { onDownload(song) },
                            libraryTrack = libraryTrack,
                            isLiked = libraryTrack != null && libraryTrack.id in state.likedIds,
                            onToggleLike = onToggleLike,
                            onShowLyrics = onShowLyrics,
                        )
                    }
                    if (state.hasMore) {
                        item(key = "similar-more") {
                            Box(
                                Modifier.fillMaxWidth().padding(Spacing.lg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                            LaunchedEffect(state.songs.size) { onLoadMore() }
                        }
                    }
                }

                else -> EmptyState(
                    icon = DowntifyIcons.Explore,
                    title = stringResource(R.string.similar_empty),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SimilarScreenPreview() {
    DowntifyTheme {
        SimilarScreen(
            state = SimilarUiState(
                songs = listOf(
                    RemoteSong(
                        "dQw4w9WgXcQ",
                        "Believe",
                        listOf("Cher"),
                        "Believe",
                        "",
                        224,
                        "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                        CatalogSource.YouTube,
                        "",
                        "1998",
                        "{}",
                    ),
                ),
                searched = true,
            ),
            artist = "Cher",
            track = "Believe",
            onArtistChange = {},
            onTrackChange = {},
            onFind = {},
            onRetry = {},
            onBack = {},
        )
    }
}
