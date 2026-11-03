package com.henriquesebastiao.downtify.feature.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SectionHeader
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.trash.LocalMoveToTrash
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackItem
import com.henriquesebastiao.downtify.ui.common.artistSubtitle
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

@Composable
fun ArtistRoute(
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistScreen(
        state = state,
        onBack = onBack,
        onAlbum = onAlbum,
        onPlay = viewModel::play,
        onPlayOrPause = viewModel::playOrPause,
        onShuffle = viewModel::shuffle,
        trackActions = TrackActions(
            onToggleLike = viewModel::toggleLike,
            onAddToQueue = viewModel::addToQueue,
            onGoToAlbum = { onAlbum(it.albumId) },
            onMoveToTrash = LocalMoveToTrash.current,
        ),
        modifier = modifier,
    )
}

/**
 * An artist (v3): their picture in a Material shape (one of the few places a shape carries real
 * content), the name, play and shuffle, their albums as a carousel and their songs as cards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    trackActions: TrackActions = TrackActions(),
) {
    val covers = LocalCoverUrls.current
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    CircleButton(
                        DowntifyIcons.Back,
                        stringResource(R.string.action_back),
                        onBack,
                        Modifier.padding(start = Spacing.xs),
                    )
                },
            )
        },
    ) { padding ->
        val artist = state.artist
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            artist == null -> EmptyState(
                icon = DowntifyIcons.Artist,
                title = stringResource(R.string.collection_not_found),
                hero = true,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
                verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
            ) {
                item(key = "header") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Artwork(
                            url = covers.track(artist.coverTrackId, ServerUrls.COVER_LARGE),
                            contentDescription = null,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            placeholderIcon = DowntifyIcons.Artist,
                            tone = ArtworkTone.Secondary,
                            modifier = Modifier.size(AVATAR_SIZE),
                        )
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            artistSubtitle(artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "actions") { Actions(state, artist.name, onPlayOrPause, onShuffle) }
                if (artist.albums.isNotEmpty()) {
                    item(key = "albums-header") { SectionHeader(stringResource(R.string.artist_albums)) }
                    item(key = "albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.screen),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(artist.albums, key = { it.id }) { album ->
                                CoverCard(
                                    title = album.title,
                                    subtitle = album.year.ifBlank { null },
                                    coverUrl = covers.track(album.coverTrackId),
                                    onClick = { onAlbum(album.id) },
                                    modifier = Modifier.width(148.dp),
                                )
                            }
                        }
                    }
                }
                item(key = "songs-header") {
                    SectionHeader(stringResource(R.string.artist_songs), modifier = Modifier.padding(top = Spacing.lg))
                }
                itemsIndexed(state.songs, key = { i, t -> "$i:${t.id}" }) { index, track ->
                    TrackItem(
                        track = track,
                        onClick = { onPlay(index) },
                        coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                        isCurrent = track.id == state.currentTrackId,
                        isPlaying = state.isPlaying,
                        isLiked = track.id in state.likedIds,
                        actions = trackActions,
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                    )
                }
                // No navigation bar here: the list clears the system's, unless the mini player under it does.
                item(key = "system-bar") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Actions(state: ArtistUiState, name: String, onPlayOrPause: () -> Unit, onShuffle: () -> Unit) {
    val playing = state.isCurrentContext && state.isPlaying
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
    ) {
        CircleButton(DowntifyIcons.Shuffle, stringResource(R.string.collection_shuffle), onShuffle)
        FilledIconButton(
            onClick = onPlayOrPause,
            shapes = IconButtonShapes(shape = DowntifyShape.Fab, pressedShape = RoundedCornerShape(16.dp)),
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
                contentDescription = stringResource(
                    if (playing) R.string.collection_pause else R.string.collection_play,
                    name,
                ),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private val AVATAR_SIZE = 184.dp

@PhonePreviews
@Composable
private fun ArtistPreview() {
    val artist = PreviewData.artists.first()
    DowntifyTheme {
        ArtistScreen(
            state = ArtistUiState(loading = false, artist = artist, songs = artist.tracks),
            onBack = {},
            onAlbum = {},
            onPlay = {},
            onPlayOrPause = {},
            onShuffle = {},
        )
    }
}
