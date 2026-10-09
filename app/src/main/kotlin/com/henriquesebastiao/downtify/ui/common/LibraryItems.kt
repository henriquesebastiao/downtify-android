package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverArt
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.formatDuration

/** "Kenji Aoki · 2023" */
@Composable
fun albumSubtitle(album: Album): String {
    val artist = album.artist.ifBlank { stringResource(R.string.unknown_artist) }
    return if (album.year.isBlank()) artist else stringResource(R.string.album_subtitle, artist, album.year)
}

/** "Kenji Aoki · 3:41" */
@Composable
fun trackSubtitle(track: Track): String {
    val artist = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) }
    return stringResource(R.string.album_subtitle, artist, formatDuration(track.duration))
}

@Composable
fun artistSubtitle(artist: Artist): String {
    val albums = pluralStringResource(R.plurals.albums_count, artist.albums.size, artist.albums.size)
    val songs = pluralStringResource(R.plurals.songs_count, artist.tracks.size, artist.tracks.size)
    return stringResource(R.string.album_subtitle, albums, songs)
}

/** A cover over two lines of text: grids and carousels. */
@Composable
fun CoverCard(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    placeholderIcon: Int = DowntifyIcons.Album,
    titleColor: Color = Color.Unspecified,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClickLabel = title, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        CoverArt(
            url = coverUrl,
            contentDescription = null,
            shape = shape,
            placeholderIcon = placeholderIcon,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
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

/** A list row with a cover: albums, artists, playlists in list layout and in search. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverRow(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    placeholderIcon: Int = DowntifyIcons.Album,
    coverSize: Dp = 56.dp,
    subtitleLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        },
        leadingContent = {
            CoverArt(
                url = coverUrl,
                contentDescription = null,
                shape = if (round) CircleShape else MaterialTheme.shapes.small,
                placeholderIcon = placeholderIcon,
                modifier = Modifier.size(coverSize),
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** What a track row's menu can do. */
data class TrackActions(
    val onToggleLike: ((Track) -> Unit)? = null,
    val onGoToAlbum: ((Track) -> Unit)? = null,
    val onGoToArtist: ((Track) -> Unit)? = null,
)

/**
 * A song row. [leading] is its number (albums), its cover (lists) or, when it
 * is the current track, bouncing bars. Long titles scroll as a marquee so
 * the full text stays readable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    coverUrl: String? = null,
    showCover: Boolean = number == null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isLiked: Boolean = false,
    downloaded: Boolean = false,
    actions: TrackActions = TrackActions(),
    containerColor: Color = Color.Transparent,
) {
    val highlight = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
    ListItem(
        headlineContent = {
            Text(
                track.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = highlight,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                modifier = Modifier.basicMarquee(),
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(
                        painterResource(DowntifyIcons.CheckCircle),
                        contentDescription = stringResource(R.string.downloads_downloaded),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp).size(14.dp),
                    )
                }
                Text(
                    trackSubtitle(track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        },
        leadingContent = {
            when {
                showCover -> Box(contentAlignment = Alignment.Center) {
                    CoverArt(url = coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
                    if (isCurrent) PlayingBars(animate = isPlaying)
                }

                isCurrent -> Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                    PlayingBars(animate = isPlaying)
                }

                else -> Text(
                    text = number?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp),
                )
            }
        },
        trailingContent = { TrackMenu(track, isLiked, actions) },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TrackMenu(track: Track, isLiked: Boolean, actions: TrackActions) {
    if (actions == TrackActions()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(DowntifyIcons.MoreVert),
                contentDescription = stringResource(R.string.track_more, track.displayTitle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.onToggleLike?.let { toggle ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (isLiked) R.string.track_unlike else R.string.track_like)) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (isLiked) DowntifyIcons.FavoriteFilled else DowntifyIcons.Favorite),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        open = false
                        toggle(track)
                    },
                )
            }
            if (track.albumId.isNotEmpty()) {
                actions.onGoToAlbum?.let { go ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.track_go_to_album)) },
                        leadingIcon = { Icon(painterResource(DowntifyIcons.Album), contentDescription = null) },
                        onClick = {
                            open = false
                            go(track)
                        },
                    )
                }
            }
            if (track.artistId.isNotEmpty()) {
                actions.onGoToArtist?.let { go ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.track_go_to_artist)) },
                        leadingIcon = { Icon(painterResource(DowntifyIcons.Artist), contentDescription = null) },
                        onClick = {
                            open = false
                            go(track)
                        },
                    )
                }
            }
        }
    }
}

/** A round artist avatar with the name under it. */
@Composable
fun ArtistCard(name: String, coverUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    CoverCard(
        title = name,
        subtitle = null,
        coverUrl = coverUrl,
        onClick = onClick,
        shape = CircleShape,
        placeholderIcon = DowntifyIcons.Artist,
        centered = true,
        modifier = modifier.width(size),
    )
}

@Composable
fun songsCount(count: Int): String = pluralStringResource(R.plurals.songs_count, count, count)

val ScreenPadding = Spacing.screen
