package com.henriquesebastiao.downtify.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.ArtworkTone
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone
import com.henriquesebastiao.downtify.core.designsystem.component.VerticalScrollbar
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.totalMinutes
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.trash.LocalMoveToTrash
import com.henriquesebastiao.downtify.ui.common.LocalBottomBarsInset
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackItem
import com.henriquesebastiao.downtify.ui.common.plusBottomBars
import com.henriquesebastiao.downtify.ui.common.songsCount

@Composable
private fun BoxScope.Scrollbar(state: LazyListState) {
    VerticalScrollbar(state, scrollbarPlacement())
}

@Composable
private fun BoxScope.scrollbarPlacement(): Modifier = Modifier
    .align(Alignment.TopEnd)
    .padding(top = Spacing.sm, bottom = Spacing.xl + LocalBottomBarsInset.current)


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
            onMoveToTrash = LocalMoveToTrash.current,
        ),
        modifier = modifier,
        onToggleFavorite = viewModel::toggleFavorite,
    )
}

/**
 * An album, a playlist or Liked songs (v3): the header and its top bar take the cover's colors
 * ([CoverTheme]), fading into the app's surface; the songs are cards of a separated list in the
 * app's own scheme (pure black included), the duration of each in tabular figures.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val seedUrl = covers.track(state.coverTrackId, ServerUrls.COVER_SMALL)
    val title = if (state.kind == CollectionKind.Liked) stringResource(R.string.liked_songs) else state.title
    // Captured outside the cover's theme: the header fades into the app's surface, not the cover's.
    val appSurface = MaterialTheme.colorScheme.surface
    val list = rememberLazyListState()
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CoverTheme(seedUrl) {
                val scheme = MaterialTheme.colorScheme
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        CircleButton(
                            DowntifyIcons.Back,
                            stringResource(R.string.action_back),
                            onBack,
                            Modifier.padding(start = Spacing.xs),
                            containerColor = scheme.onPrimaryContainer.copy(alpha = TINT_ALPHA),
                            contentColor = scheme.onPrimaryContainer,
                        )
                    },
                    actions = {
                        state.playlist?.takeIf { it.favoriteSupported }?.let { playlist ->
                            FavoriteButton(playlist.favorite, enabled = !state.favoriteBusy, onClick = onToggleFavorite)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.primaryContainer),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            state.notFound -> EmptyState(
                icon = DowntifyIcons.Album,
                title = stringResource(R.string.collection_not_found),
                hero = true,
                modifier = Modifier.padding(padding),
            )

            else -> Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
                    verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
                ) {
                    item(key = "header") {
                        CoverTheme(seedUrl) {
                            Header(
                                state,
                                title,
                                coverUrl,
                                appSurface,
                                onArtist,
                                onPlayOrPause,
                                onShuffle,
                                onToggleOffline,
                            )
                        }
                    }
                    if (state.favoriteError) {
                        item(key = "favorite-error") {
                            Text(
                                stringResource(R.string.playlist_favorite_failed),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(Spacing.screen),
                            )
                        }
                    }
                    itemsIndexed(state.tracks, key = { i, t -> "$i:${t.id}" }) { index, track ->
                        TrackItem(
                            track = track,
                            onClick = { onPlay(index) },
                            number = if (state.kind == CollectionKind.Album) {
                                track.trackNumber.takeIf { it > 0 } ?: (index + 1)
                            } else {
                                null
                            },
                            coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                            isCurrent = track.id == state.currentTrackId && state.isCurrentContext,
                            isPlaying = state.isPlaying,
                            isLiked = track.id in state.likedIds,
                            downloaded = track.id in state.offline.downloadedIds,
                            actions = trackActions,
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                        )
                    }
                    // No navigation bar here: the list clears the system's, unless the mini player under it does.
                    item(key = "system-bar") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
                Scrollbar(list)
            }
        }
    }
}

/** A playlist's favorite (the server's pin on the home screen), on the cover's colors. */
@Composable
private fun FavoriteButton(favorite: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    CircleButton(
        if (favorite) DowntifyIcons.FavoriteFilled else DowntifyIcons.Favorite,
        stringResource(if (favorite) R.string.playlist_remove_favorite else R.string.playlist_add_favorite),
        onClick,
        Modifier.padding(end = Spacing.sm),
        enabled = enabled,
        containerColor = if (favorite) scheme.primary else scheme.onPrimaryContainer.copy(alpha = TINT_ALPHA),
        contentColor = if (favorite) scheme.onPrimary else scheme.onPrimaryContainer,
    )
}

@Suppress("LongParameterList")
@Composable
private fun Header(
    state: CollectionUiState,
    title: String,
    coverUrl: String?,
    appSurface: Color,
    onArtist: (String) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    onToggleOffline: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, appSurface)))
            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Artwork(
            url = coverUrl,
            contentDescription = null,
            shape = DowntifyShape.Artwork,
            placeholderIcon = if (state.kind ==
                CollectionKind.Liked
            ) {
                DowntifyIcons.FavoriteFilled
            } else {
                DowntifyIcons.Album
            },
            tone = if (state.kind == CollectionKind.Liked) ArtworkTone.Primary else ArtworkTone.Neutral,
            modifier = Modifier.size(COVER_SIZE),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = scheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (state.artist.isNotBlank()) {
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(enabled = state.artistId.isNotEmpty()) { onArtist(state.artistId) },
                )
            }
            Details(state)
        }
        Actions(state, title, onPlayOrPause, onShuffle, onToggleOffline)
    }
}

/** Year, songs and length as small tags: they wrap whole, never mid-value. */
@Composable
private fun Details(state: CollectionUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.year.isNotBlank()) Tag(state.year, small = true, tone = TagTone.OnColor)
        Tag(songsCount(state.tracks.size), small = true, tone = TagTone.OnColor)
        Tag(
            stringResource(R.string.minutes_short, totalMinutes(state.durationSeconds)),
            small = true,
            tone = TagTone.OnColor,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Actions(
    state: CollectionUiState,
    title: String,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    onToggleOffline: () -> Unit,
) {
    val playing = state.isCurrentContext && state.isPlaying
    val scheme = MaterialTheme.colorScheme
    var confirmRemove by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OfflineChip(
            offline = state.offline,
            enabled = state.tracks.isNotEmpty() || state.offline.kept,
            onClick = { if (state.offline.kept) confirmRemove = true else onToggleOffline() },
        )
        Spacer(Modifier.weight(1f))
        CircleButton(
            DowntifyIcons.Shuffle,
            stringResource(R.string.collection_shuffle),
            onShuffle,
            enabled = state.tracks.isNotEmpty(),
            containerColor = scheme.onPrimaryContainer.copy(alpha = TINT_ALPHA),
            contentColor = scheme.onPrimaryContainer,
        )
        FilledIconButton(
            onClick = onPlayOrPause,
            enabled = state.tracks.isNotEmpty(),
            shapes = IconButtonShapes(shape = DowntifyShape.Fab, pressedShape = RoundedCornerShape(16.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
            ),
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
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onToggleOffline()
                    },
                ) { Text(stringResource(R.string.collection_remove_confirm)) }
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

private val COVER_SIZE = 232.dp
private const val TINT_ALPHA = 0.12f

@PhonePreviews
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
