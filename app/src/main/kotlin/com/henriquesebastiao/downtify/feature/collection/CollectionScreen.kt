package com.henriquesebastiao.downtify.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.rememberCoverColors
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.totalMinutes
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackRow
import com.henriquesebastiao.downtify.ui.common.songsCount

@Composable
fun CollectionRoute(
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteBusy by viewModel.favoriteBusy.collectAsStateWithLifecycle()
    val favoriteError by viewModel.favoriteError.collectAsStateWithLifecycle()
    CollectionScreen(
        state = state.copy(favoriteBusy = favoriteBusy, favoriteError = favoriteError),
        onBack = onBack,
        onArtist = onArtist,
        onPlay = viewModel::play,
        onPlayOrPause = viewModel::playOrPause,
        onShuffle = viewModel::shuffle,
        onToggleOffline = viewModel::toggleOffline,
        trackActions = TrackActions(
            onToggleLike = viewModel::toggleLike,
            onAddToQueue = viewModel::addToQueue,
            onGoToAlbum = if (state.kind == CollectionKind.Album) null else { t -> onAlbum(t.albumId) },
            onGoToArtist = { onArtist(it.artistId) },
        ),
        modifier = modifier,
        onToggleFavorite = viewModel::toggleFavorite,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleOffline: () -> Unit = {},
    trackActions: TrackActions = TrackActions(),
    onToggleFavorite: () -> Unit = {},
) {
    val covers = LocalCoverUrls.current
    val coverUrl = state.playlist?.let { covers.playlist(it, state.coverTrackId, ServerUrls.COVER_LARGE) }
        ?: covers.track(state.coverTrackId, ServerUrls.COVER_LARGE)
    val colors = rememberCoverColors(covers.track(state.coverTrackId, ServerUrls.COVER_SMALL))
    val title = if (state.kind == CollectionKind.Liked) stringResource(R.string.liked_songs) else state.title

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    state.playlist?.takeIf { it.favoriteSupported }?.let { playlist ->
                        IconButton(onClick = onToggleFavorite, enabled = !state.favoriteBusy) {
                            Icon(
                                painterResource(DowntifyIcons.FavoriteFilled),
                                contentDescription = stringResource(
                                    if (playlist.favorite) {
                                        R.string.playlist_remove_favorite
                                    } else {
                                        R.string.playlist_add_favorite
                                    },
                                ),
                                tint = if (playlist.favorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(DowntifyIcons.Back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.notFound -> EmptyState(
                icon = DowntifyIcons.Album,
                title = stringResource(R.string.collection_not_found),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                item {
                    Header(state, title, coverUrl, colors.surface, colors.onSurfaceVariant, onArtist)
                }
                item { Actions(state, title, onPlayOrPause, onShuffle, onToggleOffline) }
                if (state.favoriteError) {
                    item {
                        Text(
                            stringResource(R.string.playlist_favorite_failed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(Spacing.screen),
                        )
                    }
                }
                itemsIndexed(state.tracks, key = { i, t -> "$i:${t.id}" }) { index, track ->
                    val current = track.id == state.currentTrackId && state.isCurrentContext
                    TrackRow(
                        track = track,
                        onClick = { onPlay(index) },
                        number = if (state.kind ==
                            CollectionKind.Album
                        ) {
                            track.trackNumber.takeIf { it > 0 } ?: (index + 1)
                        } else {
                            null
                        },
                        coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                        isCurrent = current,
                        isPlaying = state.isPlaying,
                        isLiked = track.id in state.likedIds,
                        downloaded = track.id in state.offline.downloadedIds,
                        actions = trackActions,
                        containerColor = if (current) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: CollectionUiState,
    title: String,
    coverUrl: String?,
    surface: Color,
    onVariant: Color,
    onArtist: (String) -> Unit,
) {
    val details = buildList {
        if (state.year.isNotBlank()) add(state.year)
        add(songsCount(state.tracks.size))
        add(stringResource(R.string.minutes_short, totalMinutes(state.durationSeconds)))
    }.joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(surface, MaterialTheme.colorScheme.surface)))
            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CoverArt(
            url = coverUrl,
            contentDescription = null,
            shape = MaterialTheme.shapes.extraLarge,
            placeholderIcon = if (state.kind ==
                CollectionKind.Liked
            ) {
                DowntifyIcons.FavoriteFilled
            } else {
                DowntifyIcons.Album
            },
            modifier = Modifier.size(232.dp).shadow(16.dp, MaterialTheme.shapes.extraLarge),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.artist.isNotBlank()) {
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.clickable(enabled = state.artistId.isNotEmpty()) {
                            onArtist(state.artistId)
                        },
                    )
                    Text(" · ", style = MaterialTheme.typography.bodyMedium, color = onVariant)
                }
                Text(details, style = MaterialTheme.typography.bodyMedium, color = onVariant)
            }
        }
    }
}

@Composable
private fun Actions(
    state: CollectionUiState,
    title: String,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    onToggleOffline: () -> Unit,
) {
    val playing = state.isCurrentContext && state.isPlaying
    var confirmRemove by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OfflineChip(
            offline = state.offline,
            enabled = state.tracks.isNotEmpty() || state.offline.kept,
            onClick = { if (state.offline.kept) confirmRemove = true else onToggleOffline() },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShuffle, enabled = state.tracks.isNotEmpty()) {
            Icon(
                painterResource(DowntifyIcons.Shuffle),
                contentDescription = stringResource(R.string.collection_shuffle),
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        FilledIconButton(
            onClick = onPlayOrPause,
            enabled = state.tracks.isNotEmpty(),
            shape = DowntifyShape.Fab,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
                contentDescription = stringResource(
                    if (playing) R.string.collection_pause else R.string.collection_play,
                    title,
                ),
                modifier = Modifier.size(32.dp),
            )
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.collection_remove_title)) },
            text = { Text(stringResource(R.string.collection_remove_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onToggleOffline()
                }) { Text(stringResource(R.string.collection_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** "Download", then "Downloading 3 of 10", then "On this phone" — the design's offline toggle. */
@Composable
private fun OfflineChip(offline: CollectionOffline, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = offline.kept,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                when {
                    !offline.kept -> stringResource(R.string.collection_download)
                    offline.isComplete -> stringResource(R.string.collection_on_phone)
                    else -> stringResource(R.string.collection_downloading, offline.downloaded, offline.total)
                },
            )
        },
        leadingIcon = {
            Icon(
                painterResource(if (offline.isComplete) DowntifyIcons.CheckCircle else DowntifyIcons.Downloads),
                contentDescription = null,
                tint = if (offline.kept) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

@PreviewLightDark
@Composable
private fun AlbumPreview() {
    val album = PreviewData.albums.first()
    DowntifyTheme {
        CollectionScreen(
            state = CollectionUiState(
                loading = false,
                title = album.title,
                artist = album.artist,
                artistId = album.artistId,
                year = album.year,
                tracks = album.tracks,
                currentTrackId = "t2",
                isPlaying = true,
                isCurrentContext = true,
            ),
            onBack = {},
            onArtist = {},
            onPlay = {},
            onPlayOrPause = {},
            onShuffle = {},
            modifier = Modifier.statusBarsPadding(),
        )
    }
}
