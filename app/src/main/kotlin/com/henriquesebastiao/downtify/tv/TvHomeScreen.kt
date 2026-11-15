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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.home.HomeUiState
import com.henriquesebastiao.downtify.feature.home.HomeViewModel
import com.henriquesebastiao.downtify.feature.home.ServerStatus
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.albumSubtitle
import com.henriquesebastiao.downtify.ui.common.songsCount

/** The TV's Home: the phone's [HomeViewModel], drawn for a TV by [TvHomeScreen]. */
@Composable
fun TvHomeRoute(
    onOpenRecent: (RecentContext) -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvHomeScreen(state, onOpenRecent, onOpenAlbum, modifier)
}

/**
 * Home on a TV, after the TV music apps: what to jump back into as wide tiles, then the favourite
 * playlists and the albums added lately as rows of covers. No header (the top bar has settings),
 * no "streaming from" line (only a problem with the server shows) and none of the phone's
 * shortcuts (Discover, similar songs, podcasts). The focused row scrolls to the top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    state: HomeUiState,
    onOpenRecent: (RecentContext) -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites = remember(state.favoritePlaylists) {
        state.favoritePlaylists.map {
            RecentContext(PlaybackContextType.Playlist, it.name, it.title, it.trackIds.firstOrNull(), 0)
        }
    }
    val playlists = remember(state.favoritePlaylists, state.playlistsByName) { state.knownPlaylists }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(SectionTop)) {
        LazyColumn(
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier = modifier.fillMaxSize(),
        ) {
            if (state.status == ServerStatus.Unreachable || state.status == ServerStatus.DifferentServer) {
                item(key = "status") { ServerProblem(state) }
            }
            when {
                state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                state.libraryEmpty && favorites.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(R.string.home_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = TvMargin, vertical = Spacing.xxl),
                    )
                }

                else -> {
                    if (state.jumpBackIn.isNotEmpty()) {
                        item(key = "jump") {
                            TvSection(stringResource(R.string.home_jump_back_in)) {
                                JumpBackIn(state.jumpBackIn.take(JUMP_BACK_IN_TILES), playlists, onOpenRecent)
                            }
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "favorites") {
                            TvSection(stringResource(R.string.favorite_playlists)) {
                                FavoriteRow(favorites, playlists, onOpenRecent)
                            }
                        }
                    }
                    if (state.recentlyAdded.isNotEmpty()) {
                        item(key = "recent") {
                            TvSection(stringResource(R.string.home_recently_added)) {
                                AlbumRow(state.recentlyAdded, onOpenAlbum)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = TvMargin).semantics { heading() },
        )
        content()
    }
}

/** Only what needs doing: the server can't be reached, or another one answers at its address. */
@Composable
private fun ServerProblem(state: HomeUiState) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, text) = when (state.status) {
        ServerStatus.DifferentServer ->
            Triple(DowntifyIcons.Warning, scheme.error, stringResource(R.string.home_different_server))

        else -> Triple(
            DowntifyIcons.Offline,
            scheme.outline,
            stringResource(R.string.home_unreachable, state.serverName),
        )
    }
    Row(Modifier.padding(horizontal = TvMargin), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    }
}

/** Up to eight recent albums, artists and playlists as wide tiles, four to a row. */
@Composable
private fun JumpBackIn(
    entries: List<RecentContext>,
    playlists: Map<String, Playlist>,
    onOpen: (RecentContext) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = TvMargin - TvRingSpace),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        entries.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { entry -> RecentTile(entry, playlists, onOpen, Modifier.weight(1f)) }
                repeat(TILES_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** A recent context as a wide tile: its cover on the left, its name beside it. */
@Composable
private fun RecentTile(
    entry: RecentContext,
    playlists: Map<String, Playlist>,
    onOpen: (RecentContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val title = recentTitle(entry)
    Row(
        modifier
            .tvFocusFrame(focused, TileRingShape)
            .clickable(interaction, indication = null, onClickLabel = title, role = Role.Button) { onOpen(entry) }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, TileShape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Artwork(
            url = recentCover(entry, playlists, ServerUrls.COVER_SMALL),
            fallbackUrl = if (entry.type == PlaybackContextType.Artist) {
                LocalCoverUrls.current.track(entry.coverTrackId, ServerUrls.COVER_SMALL)
            } else {
                null
            },
            contentDescription = null,
            shape = if (entry.type == PlaybackContextType.Artist) CircleShape else TileShape,
            placeholderIcon = entry.placeholder,
            tone = if (entry.type == PlaybackContextType.Liked) ArtworkTone.Primary else ArtworkTone.Neutral,
            modifier = Modifier.size(TileCover),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = Spacing.md),
        )
    }
}

@Composable
private fun FavoriteRow(
    entries: List<RecentContext>,
    playlists: Map<String, Playlist>,
    onOpen: (RecentContext) -> Unit,
) {
    TvCoverRow {
        items(entries, key = { it.refId }) { entry ->
            TvCoverCard(
                title = recentTitle(entry),
                subtitle = playlists[entry.refId]?.let { songsCount(it.trackIds.size) },
                coverUrl = recentCover(entry, playlists, ServerUrls.COVER_LARGE),
                onClick = { onOpen(entry) },
                placeholderIcon = DowntifyIcons.Playlist,
                modifier = Modifier.width(RowCardWidth),
            )
        }
    }
}

@Composable
private fun AlbumRow(albums: List<Album>, onOpenAlbum: (String) -> Unit) {
    TvCoverRow {
        items(albums, key = { it.id }) { album ->
            TvCoverCard(
                title = album.title,
                subtitle = albumSubtitle(album),
                coverUrl = LocalCoverUrls.current.track(album.coverTrackId, ServerUrls.COVER_LARGE),
                onClick = { onOpenAlbum(album.id) },
                modifier = Modifier.width(RowCardWidth),
            )
        }
    }
}

/** A TV row of covers that scrolls the focused one to its start, where the row's first sits. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvCoverRow(content: LazyListScope.() -> Unit) {
    val start = TvMargin - TvRingSpace
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(start)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = start),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

@Composable
private fun recentTitle(entry: RecentContext): String = when (entry.type) {
    PlaybackContextType.Liked -> stringResource(R.string.liked_songs)
    PlaybackContextType.Songs -> stringResource(R.string.library_songs)
    else -> entry.title.ifEmpty { stringResource(R.string.library_songs) }
}

@Composable
private fun recentCover(entry: RecentContext, playlists: Map<String, Playlist>, size: Int): String? {
    val covers = LocalCoverUrls.current
    val playlist = if (entry.type == PlaybackContextType.Playlist) playlists[entry.refId] else null
    return when {
        playlist != null -> covers.playlist(playlist, playlist.trackIds.firstOrNull(), size)

        // An artist's photo; the tile falls back to their cover when the server has none.
        entry.type == PlaybackContextType.Artist -> covers.artist(entry.title)

        else -> covers.track(entry.coverTrackId, size)
    }
}

private val RecentContext.placeholder: Int
    get() = when (type) {
        PlaybackContextType.Liked -> DowntifyIcons.FavoriteFilled
        PlaybackContextType.Artist -> DowntifyIcons.Artist
        PlaybackContextType.Playlist -> DowntifyIcons.Playlist
        PlaybackContextType.Songs -> DowntifyIcons.Song
        else -> DowntifyIcons.Album
    }

/** Where the focused section's row stops: its title shows above it. */
private val SectionTop = 44.dp
private const val TILES_PER_ROW = 4
private const val JUMP_BACK_IN_TILES = 8
private val TileCover = 56.dp
private val TileShape = RoundedCornerShape(8.dp)
private val TileRingShape = RoundedCornerShape(8.dp + TvRingSpace)
private val RowCardWidth = 136.dp

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvHomePreview() {
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvHomeScreen(
                state = HomeUiState(
                    loading = false,
                    jumpBackIn = PreviewData.albums.map {
                        RecentContext(PlaybackContextType.Album, it.id, it.title, it.coverTrackId, 0)
                    },
                    recentlyAdded = PreviewData.albums,
                ),
                onOpenRecent = {},
                onOpenAlbum = {},
            )
        }
    }
}
