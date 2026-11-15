package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.library.LibraryNavigation
import com.henriquesebastiao.downtify.feature.library.LibraryTab
import com.henriquesebastiao.downtify.feature.library.LibraryUiState
import com.henriquesebastiao.downtify.feature.library.LibraryViewModel
import com.henriquesebastiao.downtify.feature.library.PlaylistItem
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.songsCount

/** The TV's library: the phone's [LibraryViewModel], drawn for a TV by [TvLibraryScreen]. */
@Composable
fun TvLibraryRoute(
    navigation: LibraryNavigation,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvLibraryScreen(
        state = state,
        navigation = navigation,
        onSelectTab = viewModel::selectTab,
        onPlaySong = viewModel::playSong,
        modifier = modifier,
    )
}

/**
 * The library on a TV, after the TV music apps: the sections as pills, then a wide grid of
 * covers (round for artists) with the title and a line under each. OK opens an album, an artist or
 * a playlist, or plays a song. The focused row scrolls to the top of the grid
 * ([rememberTvGridBringIntoViewSpec]). Sorting and the list layout stay on the phone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvLibraryScreen(
    state: LibraryUiState,
    navigation: LibraryNavigation,
    onSelectTab: (LibraryTab) -> Unit,
    onPlaySong: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .padding(horizontal = TvMargin - TvRingSpace)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            LibraryTab.entries.forEach { tab ->
                TvPill(
                    label = stringResource(tab.label),
                    selected = tab == state.tab,
                    onClick = { onSelectTab(tab) },
                )
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

            state.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(state.tab.emptyTitle),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(GridTop)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TvCardMinWidth),
                    contentPadding = PaddingValues(
                        start = TvMargin - TvRingSpace,
                        end = TvMargin - TvRingSpace,
                        top = GridTop,
                        bottom = Spacing.xxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (state.tab) {
                        LibraryTab.Playlists -> playlists(state.playlists, navigation)
                        LibraryTab.Albums -> albums(state, navigation)
                        LibraryTab.Artists -> artists(state, navigation)
                        LibraryTab.Songs -> songs(state, onPlaySong)
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.playlists(playlists: List<PlaylistItem>, navigation: LibraryNavigation) {
    items(playlists, key = { it.playlist?.name ?: "" }) { item ->
        val covers = LocalCoverUrls.current
        val playlist = item.playlist
        TvCoverCard(
            title = playlist?.title ?: stringResource(R.string.liked_songs),
            subtitle = songsCount(item.count),
            coverUrl = playlist?.let { covers.playlist(it, item.coverTrackId, ServerUrls.COVER_LARGE) }
                ?: covers.track(item.coverTrackId, ServerUrls.COVER_LARGE),
            placeholderIcon = if (playlist == null) DowntifyIcons.FavoriteFilled else DowntifyIcons.Playlist,
            onClick = { playlist?.let { navigation.onPlaylist(it.name) } ?: navigation.onLiked() },
        )
    }
}

private fun LazyGridScope.albums(state: LibraryUiState, navigation: LibraryNavigation) {
    items(state.albums, key = { it.id }) { album ->
        TvCoverCard(
            title = album.title,
            subtitle = albumSubtitle(album),
            coverUrl = LocalCoverUrls.current.track(album.coverTrackId, ServerUrls.COVER_LARGE),
            onClick = { navigation.onAlbum(album.id) },
        )
    }
}

private fun LazyGridScope.artists(state: LibraryUiState, navigation: LibraryNavigation) {
    items(state.artists, key = { it.id }) { artist ->
        TvCoverCard(
            title = artist.name,
            subtitle = null,
            coverUrl = LocalCoverUrls.current.track(artist.coverTrackId, ServerUrls.COVER_LARGE),
            onClick = { navigation.onArtist(artist.id) },
            shape = CircleShape,
            ringShape = CircleShape,
            placeholderIcon = DowntifyIcons.Artist,
            centered = true,
        )
    }
}

private fun LazyGridScope.songs(state: LibraryUiState, onPlay: (Int) -> Unit) {
    itemsIndexed(state.songs, key = { _, track -> track.id }) { index, track ->
        TvCoverCard(
            title = track.displayTitle,
            subtitle = track.displayArtist,
            coverUrl = LocalCoverUrls.current.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_LARGE),
            onClick = { onPlay(index) },
            placeholderIcon = DowntifyIcons.Song,
            current = track.id == state.currentTrackId,
        )
    }
}

/**
 * A cover with its title and a line under it. Focus rings the cover along [ringShape], a small gap
 * out, and leaves the text alone; there's no tint over the picture. [current] marks what's playing
 * by its title's color.
 */
@Composable
internal fun TvCoverCard(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TvCoverShape,
    ringShape: Shape = TvCoverRingShape,
    placeholderIcon: Int = DowntifyIcons.Album,
    centered: Boolean = false,
    current: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(
        modifier.clickable(interaction, indication = null, onClickLabel = title, role = Role.Button, onClick = onClick),
        horizontalAlignment = align,
    ) {
        Artwork(
            url = coverUrl,
            contentDescription = null,
            shape = shape,
            placeholderIcon = placeholderIcon,
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusFrame(focused, ringShape)
                .aspectRatio(1f),
        )
        Column(Modifier.padding(horizontal = TvRingSpace), horizontalAlignment = align) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A section's pill: filled when selected, ringed when focused. */
@Composable
internal fun TvPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .tvFocusFrame(focused, CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .background(if (selected) scheme.onSurface else scheme.surfaceContainerHigh, CircleShape)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.surface else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Between the pills and the first row; where the focused row stops when the grid scrolls. */
private val GridTop = Spacing.md

/** The narrowest a card gets: six to a row on a 960 dp TV. */
private val TvCardMinWidth = 128.dp

private val LibraryUiState.isEmpty: Boolean
    get() = when (tab) {
        LibraryTab.Playlists -> false

        // Liked songs is always there.
        LibraryTab.Albums -> albums.isEmpty()

        LibraryTab.Artists -> artists.isEmpty()

        LibraryTab.Songs -> songs.isEmpty()
    }

private val LibraryTab.label: Int
    get() = when (this) {
        LibraryTab.Albums -> R.string.library_albums
        LibraryTab.Artists -> R.string.library_artists
        LibraryTab.Playlists -> R.string.library_playlists
        LibraryTab.Songs -> R.string.library_songs
    }

private val LibraryTab.emptyTitle: Int
    get() = when (this) {
        LibraryTab.Albums -> R.string.library_empty_albums
        LibraryTab.Artists -> R.string.library_empty_artists
        LibraryTab.Playlists -> R.string.library_empty_playlists
        LibraryTab.Songs -> R.string.library_empty_songs
    }

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvLibraryPreview() {
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvLibraryScreen(
                state = LibraryUiState(
                    loading = false,
                    tab = LibraryTab.Albums,
                    albums = PreviewData.albums,
                    artists = PreviewData.artists,
                ),
                navigation = LibraryNavigation(),
                onSelectTab = {},
                onPlaySong = {},
            )
        }
    }
}
