package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.ArtworkTone
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.FocusRingWidth
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.artist.ArtistUiState
import com.henriquesebastiao.downtify.feature.artist.ArtistViewModel
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.artistSubtitle

/** An artist on a TV: the phone's [ArtistViewModel], drawn by [TvArtistScreen]. */
@Composable
fun TvArtistRoute(
    onAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvArtistScreen(
        state = state,
        onAlbum = onAlbum,
        onPlay = { viewModel.play(it) },
        onPlayOrPause = viewModel::playOrPause,
        onShuffle = viewModel::shuffle,
        onAddToQueue = viewModel::addToQueue,
        modifier = modifier,
    )
}

/** What an artist's page shows on its right: their songs or their albums. */
enum class TvArtistTab { Songs, Albums }

/**
 * An artist on a TV, after the TV music apps: on the left their photo (the one the server has
 * for them, else the cover of their newest album) in a large circle, their name, how many albums
 * and songs, shuffle and a large play/pause. On the right, pills for their songs and their albums (OK switches, as
 * the TV music apps do), then the songs as rows (OK plays the artist from that one, the album
 * under each title) or the albums as covers (OK opens one). Opens with focus on the first song.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvArtistScreen(
    state: ArtistUiState,
    onAlbum: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueue: (Track) -> Unit = {},
) {
    val artist = state.artist
    when {
        state.loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

        artist == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.collection_not_found),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Row(modifier.fillMaxSize().padding(start = TvMargin)) {
            Header(state, artist, onPlayOrPause, onShuffle, Modifier.width(HeaderWidth).fillMaxHeight())
            Content(state, artist, onAlbum, onPlay, onAddToQueue, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun Header(
    state: ArtistUiState,
    artist: Artist,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Spacer(Modifier.weight(1f))
        Artwork(
            url = LocalCoverUrls.current.artist(artist.name),
            fallbackUrl = LocalCoverUrls.current.track(artist.coverTrackId, ServerUrls.COVER_LARGE),
            contentDescription = null,
            shape = CircleShape,
            placeholderIcon = DowntifyIcons.Artist,
            tone = ArtworkTone.Secondary,
            modifier = Modifier.size(AvatarSize),
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            artist.name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            artistSubtitle(artist),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.songs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedPill(DowntifyIcons.Shuffle, stringResource(R.string.collection_shuffle), onShuffle)
                Spacer(Modifier.weight(1f))
                TvPlayDisc(playing = state.isCurrentContext && state.isPlaying, onClick = onPlayOrPause)
            }
        }
    }
}

/** The pills and, under them, the songs or the albums. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Content(
    state: ArtistUiState,
    artist: Artist,
    onAlbum: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onAddToQueue: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(TvArtistTab.Songs) }
    // Opened, the page takes the remote's focus on its first song, as the TV music apps do.
    val firstSong = remember { FocusRequester() }
    LaunchedEffect(state.songs.isNotEmpty()) {
        if (state.songs.isNotEmpty() && tab == TvArtistTab.Songs) firstSong.requestFocusWhenPlaced()
    }
    val songsPill = remember { FocusRequester() }
    val albumsPill = remember { FocusRequester() }
    val hasAlbums = artist.albums.isNotEmpty()
    Column(modifier.padding(start = Spacing.xxl - TvRingSpace)) {
        Row(
            Modifier
                // Up from the list lands on the selected pill, as the TV music apps do.
                .focusProperties {
                    onEnter = { (if (tab == TvArtistTab.Albums) albumsPill else songsPill).requestFocus() }
                }
                .focusGroup()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TvPill(
                label = stringResource(R.string.artist_songs),
                selected = tab == TvArtistTab.Songs,
                onClick = { tab = TvArtistTab.Songs },
                modifier = Modifier
                    .focusRequester(songsPill)
                    .focusProperties { if (!hasAlbums) right = FocusRequester.Cancel },
            )
            if (hasAlbums) {
                TvPill(
                    label = stringResource(R.string.artist_albums),
                    selected = tab == TvArtistTab.Albums,
                    onClick = { tab = TvArtistTab.Albums },
                    // The last pill: Right stays here instead of jumping to the top bar.
                    modifier = Modifier.focusRequester(albumsPill).focusProperties { right = FocusRequester.Cancel },
                )
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(ListTop)) {
            when (tab) {
                TvArtistTab.Songs -> LazyColumn(
                    contentPadding = PaddingValues(end = TvMargin - TvRingSpace, top = ListTop, bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.songs, key = { i, track -> "$i:${track.id}" }) { index, track ->
                        TvTrackRow(
                            track = track,
                            current = track.id == state.currentTrackId,
                            playing = state.isPlaying,
                            onClick = { onPlay(index) },
                            subtitle = track.album.ifBlank { track.displayArtist },
                            modifier = if (index == 0) Modifier.focusRequester(firstSong) else Modifier,
                            onAddToQueue = { onAddToQueue(track) },
                        )
                    }
                }

                TvArtistTab.Albums -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(AlbumMinWidth),
                    contentPadding = PaddingValues(end = TvMargin - TvRingSpace, top = ListTop, bottom = Spacing.xxl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(artist.albums, key = { it.id }) { album ->
                        TvCoverCard(
                            title = album.title,
                            subtitle = album.year.ifBlank { null },
                            coverUrl = LocalCoverUrls.current.track(album.coverTrackId, ServerUrls.COVER_LARGE),
                            onClick = { onAlbum(album.id) },
                            current = album.tracks.any { it.id == state.currentTrackId },
                            playing = state.isPlaying,
                        )
                    }
                }
            }
        }
    }
}

/** A secondary action as an outlined pill with its icon, ringed when focused. */
@Composable
private fun OutlinedPill(icon: Int, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .tvFocusFrame(focused, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .border(FocusRingWidth, scheme.outline, CircleShape)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Where the focused song or row of albums stops when the list scrolls: just under the pills. */
private val ListTop = Spacing.md
private val HeaderWidth = 300.dp
private val AlbumMinWidth = 128.dp
private val AvatarSize = 180.dp

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvArtistPreview() {
    val artist = PreviewData.artists.first()
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvArtistScreen(
                state = ArtistUiState(
                    loading = false,
                    artist = artist,
                    songs = artist.tracks,
                    currentTrackId = artist.tracks.firstOrNull()?.id,
                    isPlaying = true,
                ),
                onAlbum = {},
                onPlay = {},
                onPlayOrPause = {},
                onShuffle = {},
            )
        }
    }
}
