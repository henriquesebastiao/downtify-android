package com.henriquesebastiao.downtify.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.designsystem.theme.morphShape
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
    shape: Shape = DowntifyShape.Section,
    placeholderIcon: Int = DowntifyIcons.Album,
    titleColor: Color = Color.Unspecified,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClickLabel = title, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Artwork(
            url = coverUrl,
            contentDescription = null,
            shape = shape,
            placeholderIcon = placeholderIcon,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A song as a card of a separated list (v3): its 64 dp cover (or its number, in an album), the
 * title over the artist and the duration in tabular figures, the bars while it plays and a round "more"
 * button with its menu. What's playing looks as in PixelPlayer: the row rounds into a pill in
 * primaryContainer and its cover into a circle (spatial spring), its text, bars and "more" turn
 * onPrimaryContainer (effects spring). [compact] is for the library's long list: a 56 dp cover,
 * tighter padding and no duration line.
 * Swipes right to queue when [actions] can. [shape] comes from the list's [ListStyle].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackItem(
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
    shape: Shape = ListStyle.Separated.shapeOf(0, 1),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val playing by animateFloatAsState(if (isCurrent) 1f else 0f, motion.defaultSpatialSpec(), label = "playingShape")

    @Composable
    fun color(target: Color, label: String) = animateColorAsState(target, motion.defaultEffectsSpec(), label = label)
    val container by color(if (isCurrent) scheme.primaryContainer else containerColor, "playingContainer")
    val content by color(if (isCurrent) scheme.onPrimaryContainer else scheme.onSurface, "playingContent")
    val supporting by color(
        if (isCurrent) scheme.onPrimaryContainer.copy(alpha = PLAYING_SUBTITLE_ALPHA) else scheme.onSurfaceVariant,
        "playingSupporting",
    )
    val more = TrackMenuColors(
        container = color(
            if (isCurrent) scheme.onPrimaryContainer else scheme.surfaceContainerHigh,
            "moreContainer",
        ).value,
        content = color(if (isCurrent) scheme.primaryContainer else scheme.onSurface, "moreContent").value,
    )
    val rowShape = morphShape(shape, DowntifyShape.PlayingRow, playing)
    val item: @Composable (Modifier) -> Unit = { itemModifier ->
        MediaListItem(
            title = track.displayTitle,
            subtitle = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
            // The duration is a technical value: its own line, in tabular figures.
            meta = if (compact) null else formatDuration(track.duration),
            onClick = onClick,
            active = isCurrent,
            shape = rowShape,
            containerColor = container,
            compact = compact,
            titleColor = content,
            subtitleColor = supporting,
            modifier = itemModifier,
            leading = {
                if (showCover) {
                    Artwork(
                        url = coverUrl,
                        contentDescription = null,
                        shape = morphShape(DowntifyShape.ArtworkThumb, CircleShape, playing),
                        modifier = Modifier.size(if (compact) COMPACT_COVER else ITEM_COVER),
                    )
                } else {
                    Text(
                        text = number?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = supporting,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(32.dp),
                    )
                }
            },
            supportingLeading = if (downloaded) {
                {
                    Icon(
                        painterResource(DowntifyIcons.CheckCircle),
                        contentDescription = stringResource(R.string.downloads_downloaded),
                        tint = if (isCurrent) content else scheme.primary,
                        modifier = Modifier.padding(end = 6.dp).size(16.dp),
                    )
                }
            } else {
                null
            },
            trailing = {
                if (isCurrent) PlayingBars(animate = isPlaying, color = content)
                TrackMenu(track, isLiked, actions, more)
            },
        )
    }
    val addToQueue = actions.onAddToQueue
    if (addToQueue != null) {
        SwipeToQueue(onAddToQueue = { addToQueue(track) }, shape = rowShape, modifier = modifier) { item(Modifier) }
    } else {
        item(modifier)
    }
}

/**
 * An album, artist, playlist or show as a card of a separated list (v3): its 64 dp cover (round
 * for an artist), title, one line of [subtitle] and optional [trailing] controls.
 */
@Composable
fun CoverItem(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    placeholderIcon: Int = DowntifyIcons.Album,
    active: Boolean = false,
    meta: String? = null,
    subtitleLines: Int = 1,
    shape: Shape = ListStyle.Separated.shapeOf(0, 1),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    MediaListItem(
        title = title,
        subtitle = subtitle,
        subtitleLines = subtitleLines,
        meta = meta,
        onClick = onClick,
        active = active,
        shape = shape,
        containerColor = containerColor,
        modifier = modifier,
        leading = {
            Artwork(
                url = coverUrl,
                contentDescription = null,
                shape = if (round) CircleShape else DowntifyShape.ArtworkThumb,
                placeholderIcon = placeholderIcon,
                modifier = Modifier.size(ITEM_COVER),
            )
        },
        trailing = trailing,
    )
}

private val ITEM_COVER = 64.dp
private val COMPACT_COVER = 56.dp
private const val PLAYING_SUBTITLE_ALPHA = 0.8f

/** What a track row's menu can do. */
data class TrackActions(
    val onToggleLike: ((Track) -> Unit)? = null,
    /** Also turns on swipe-right-to-queue on the row. */
    val onAddToQueue: ((Track) -> Unit)? = null,
    val onGoToAlbum: ((Track) -> Unit)? = null,
    val onGoToArtist: ((Track) -> Unit)? = null,
    val onFindSimilar: ((artist: String, title: String) -> Unit)? = null,
    /** Only for an admin's pairing while the server has the music trash on (see `LocalMoveToTrash`). */
    val onMoveToTrash: ((Track) -> Unit)? = null,
)

/** The colors of a track row's "more" button: light on the row that's playing. */
private data class TrackMenuColors(val container: Color, val content: Color)

/** The track's "more" menu, behind the v3 rows' 48 dp tonal circle. */
@Composable
private fun TrackMenu(track: Track, isLiked: Boolean, actions: TrackActions, colors: TrackMenuColors) {
    if (actions == TrackActions()) return
    var open by remember { mutableStateOf(false) }
    Box {
        CircleButton(
            DowntifyIcons.MoreVert,
            stringResource(R.string.track_more, track.displayTitle),
            { open = true },
            containerColor = colors.container,
            contentColor = colors.content,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.onAddToQueue?.let { add ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.track_add_to_queue)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.AddToQueue), contentDescription = null) },
                    onClick = {
                        open = false
                        add(track)
                    },
                )
            }
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
            if (track.displayArtist.isNotBlank() && track.displayTitle.isNotBlank()) {
                actions.onFindSimilar?.let { find ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.similar_find)) },
                        leadingIcon = { Icon(painterResource(DowntifyIcons.Explore), contentDescription = null) },
                        onClick = {
                            open = false
                            find(track.displayArtist, track.displayTitle)
                        },
                    )
                }
            }
            actions.onMoveToTrash?.let { move ->
                val danger = MaterialTheme.colorScheme.error
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trash_move)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Delete), contentDescription = null) },
                    colors = MenuDefaults.itemColors(textColor = danger, leadingIconColor = danger),
                    onClick = {
                        open = false
                        move(track)
                    },
                )
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
