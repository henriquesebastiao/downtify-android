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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.collection.CollectionKind
import com.henriquesebastiao.downtify.feature.collection.CollectionUiState
import com.henriquesebastiao.downtify.feature.collection.CollectionViewModel
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.songsCount
import kotlin.math.roundToInt

/** An album, a playlist or Liked songs on a TV: the phone's [CollectionViewModel], drawn by [TvCollectionScreen]. */
@Composable
fun TvCollectionRoute(
    onArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvCollectionScreen(
        state = state,
        onArtist = onArtist,
        onPlay = { viewModel.play(it) },
        onPlayOrPause = viewModel::playOrPause,
        onShuffle = viewModel::shuffle,
        modifier = modifier,
    )
}

/**
 * An album, a playlist or Liked songs on a TV, after the TV music apps: on the left the cover, the
 * title, the artist (OK opens their page), the year, the songs and the length, with shuffle and a
 * large play/pause; on the right the songs, each with its cover, the one playing in the accent
 * color with the playing bars. OK on a song plays the collection from it. The focused song
 * scrolls to the top of the list; the page opens with focus on the first song. Keeping a
 * collection on the device and the songs' menus stay on the phone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvCollectionScreen(
    state: CollectionUiState,
    onArtist: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

        state.notFound -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.collection_not_found),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Row(modifier.fillMaxSize().padding(start = TvMargin)) {
            // Opened, the page takes the remote's focus on its first song, as the TV music apps do.
            val firstSong = remember { FocusRequester() }
            LaunchedEffect(state.tracks.isNotEmpty()) {
                if (state.tracks.isNotEmpty()) firstSong.requestFocusWhenPlaced()
            }
            Header(state, onArtist, onPlayOrPause, onShuffle, Modifier.width(HeaderWidth).fillMaxHeight())
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(ListTop)) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.xxl,
                        end = TvMargin - TvRingSpace,
                        top = ListTop,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                        TvTrackRow(
                            track = track,
                            current = track.id == state.currentTrackId,
                            playing = state.isPlaying,
                            onClick = { onPlay(index) },
                            modifier = if (index == 0) Modifier.focusRequester(firstSong) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: CollectionUiState,
    onArtist: (String) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val covers = LocalCoverUrls.current
    val scheme = MaterialTheme.colorScheme
    val liked = state.kind == CollectionKind.Liked
    Column(modifier.padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Spacer(Modifier.weight(1f))
        Artwork(
            url = state.playlist?.let { covers.playlist(it, state.coverTrackId, ServerUrls.COVER_LARGE) }
                ?: covers.track(state.coverTrackId, ServerUrls.COVER_LARGE),
            contentDescription = null,
            shape = CoverShape,
            placeholderIcon = if (liked) DowntifyIcons.FavoriteFilled else DowntifyIcons.Album,
            tone = if (liked) ArtworkTone.Primary else ArtworkTone.Neutral,
            modifier = Modifier.size(CoverSize),
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            if (liked) stringResource(R.string.liked_songs) else state.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (state.artist.isNotBlank()) {
            ArtistLink(state.artist, enabled = state.artistId.isNotEmpty()) {
                onArtist(state.artistId)
            }
        }
        Text(
            details(state),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.tracks.isNotEmpty()) {
                TvIconButton(DowntifyIcons.Shuffle, stringResource(R.string.collection_shuffle), onShuffle)
                Spacer(Modifier.weight(1f))
                TvPlayDisc(playing = state.isCurrentContext && state.isPlaying, onClick = onPlayOrPause)
            }
        }
    }
}

/** "2003 · 12 songs · 48 min": the year only when there is one. */
@Composable
private fun details(state: CollectionUiState): String {
    val parts = buildList {
        if (state.year.isNotBlank()) add(state.year)
        add(songsCount(state.tracks.size))
        add(stringResource(R.string.minutes_short, (state.durationSeconds / SECONDS_PER_MINUTE).roundToInt()))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ArtistLink(name: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            // The ring's room hangs into the margin, so the name lines up with the title.
            .offset(x = -TvRingSpace)
            .tvFocusFrame(focused, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(painterResource(DowntifyIcons.Artist), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A collection's or an artist's play/pause: a large disc in the accent color, ringed when focused. */
@Composable
internal fun TvPlayDisc(playing: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(
        Modifier
            .tvFocusFrame(focused, CircleShape)
            .clickable(interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .size(PlayDisc)
            .background(scheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
            contentDescription = label,
            tint = scheme.onPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * A song as a TV row: its cover, the title over [subtitle] (the artist, or the album on an artist's
 * page), the length at the end. Focused, the row lights up and rings; the one playing has its
 * title in the accent color, after the bars.
 */
@Composable
internal fun TvTrackRow(
    track: Track,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = track.displayArtist,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .tvFocusFrame(focused, RowRingShape)
            .clickable(
                interaction,
                indication = null,
                onClickLabel = track.displayTitle,
                role = Role.Button,
                onClick = onClick,
            )
            .background(if (focused) scheme.surfaceContainerHigh else scheme.surface.copy(alpha = 0f), RowShape)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Artwork(
            url = LocalCoverUrls.current.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
            contentDescription = null,
            shape = RowCoverShape,
            modifier = Modifier.size(RowCover),
        )
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (current) PlayingBars(Modifier.size(16.dp), animate = playing)
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (current) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(track.duration), style = DowntifyType.dataSmall, color = scheme.onSurfaceVariant)
    }
}

/** Where the focused song stops when the list scrolls: at the list's top padding. */
private val ListTop = Spacing.md
private val HeaderWidth = 280.dp
private val CoverSize = 160.dp
private val CoverShape = RoundedCornerShape(8.dp)
private val PlayDisc = 64.dp
private val RowCover = 56.dp
private val RowCoverShape = RoundedCornerShape(6.dp)
private val RowShape = RoundedCornerShape(8.dp)
private val RowRingShape = RoundedCornerShape(8.dp + TvRingSpace)
private const val SECONDS_PER_MINUTE = 60.0

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvCollectionPreview() {
    val album = PreviewData.albums.first()
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvCollectionScreen(
                state = CollectionUiState(
                    loading = false,
                    title = album.title,
                    artist = album.artist,
                    artistId = "art",
                    year = album.year,
                    tracks = album.tracks,
                    currentTrackId = album.tracks.firstOrNull()?.id,
                    isPlaying = true,
                ),
                onArtist = {},
                onPlay = {},
                onPlayOrPause = {},
                onShuffle = {},
            )
        }
    }
}
