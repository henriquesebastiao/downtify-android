package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.NameIndex
import com.henriquesebastiao.downtify.core.model.NameSection
import com.henriquesebastiao.downtify.core.model.Track
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
        onPlaySong = viewModel::playSongByName,
        modifier = modifier,
        onZtoA = viewModel::setZtoA,
    )
}

/**
 * The library on a TV, after the TV music apps: the sections as pills, then each one by name,
 * A to Z or Z to A (a pill at the end, each section its own), in rows of covers with their initial
 * in a column on the left ([ByLetter]). OK opens an album, an artist or a playlist, or plays the
 * songs from that one. "Go to", beside the order, lists the initials to jump to one, and Back from
 * the covers comes back to it. Other sorting and the list layout stay on the phone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvLibraryScreen(
    state: LibraryUiState,
    navigation: LibraryNavigation,
    onSelectTab: (LibraryTab) -> Unit,
    onPlaySong: (String) -> Unit,
    modifier: Modifier = Modifier,
    onZtoA: (LibraryTab, Boolean) -> Unit = { _, _ -> },
) {
    // Each section keeps its own place.
    val lists = LibraryTab.entries.associateWith { rememberLazyListState() }
    val jump = remember { LetterJump() }
    var listFocused by remember { mutableStateOf(false) }
    var goToOpen by rememberSaveable { mutableStateOf(false) }
    val goToFocus = remember { FocusRequester() }
    val zToA = state.tab in state.zToATabs
    // Flipping A–Z / Z–A starts that section from the top: the list would otherwise follow the
    // first visible row to its new place, the far end. Only on a flip, not on coming back.
    var shownZtoA by remember { mutableStateOf<Set<LibraryTab>?>(null) }
    LaunchedEffect(state.loading, state.zToATabs) {
        if (state.loading) return@LaunchedEffect
        shownZtoA?.let { shown ->
            LibraryTab.entries
                .filter { (it in shown) != (it in state.zToATabs) }
                .forEach { lists.getValue(it).scrollToItem(0) }
        }
        shownZtoA = state.zToATabs
    }
    val initials = when (state.tab) {
        LibraryTab.Songs -> state.songSections
        LibraryTab.Albums -> state.albumSections
        LibraryTab.Artists -> state.artistSections
        LibraryTab.Playlists -> state.playlistSections
    }.map { it.initial }
    val hasNames = !state.loading && initials.isNotEmpty()
    BackHandler(enabled = hasNames && listFocused) { goToFocus.requestFocus() }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TvMargin - TvRingSpace),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                LibraryTab.entries.forEach { tab ->
                    TvPill(
                        label = stringResource(tab.label),
                        selected = tab == state.tab,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (hasNames) {
                TvPill(
                    label = stringResource(R.string.library_go_to),
                    selected = false,
                    onClick = { goToOpen = true },
                    modifier = Modifier.focusRequester(goToFocus),
                )
                TvPill(
                    label = stringResource(if (zToA) R.string.library_sort_z_a else R.string.library_sort_a_z),
                    selected = false,
                    onClick = { onZtoA(state.tab, !zToA) },
                )
            }
        }
        val list = lists.getValue(state.tab)
        val listModifier = Modifier.onFocusChanged { listFocused = it.hasFocus }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

            state.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(state.tab.emptyTitle),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.tab == LibraryTab.Songs -> ByLetter(state.songSections, list, jump, {
                it.id
            }, listModifier) { track, m ->
                SongCard(track, state, onPlaySong, m)
            }

            state.tab == LibraryTab.Albums -> ByLetter(state.albumSections, list, jump, {
                it.id
            }, listModifier) { album, m ->
                AlbumCard(album, navigation, m)
            }

            state.tab == LibraryTab.Artists -> ByLetter(state.artistSections, list, jump, {
                it.id
            }, listModifier) { artist, m ->
                ArtistCard(artist, navigation, m)
            }

            else -> ByLetter(state.playlistSections, list, jump, {
                it.playlist?.name.orEmpty()
            }, listModifier) { item, m ->
                PlaylistCard(item, navigation, m)
            }
        }
    }
    if (goToOpen && hasNames) {
        TvGoToDialog(
            initials = initials,
            current = jump.current,
            onPick = {
                goToOpen = false
                jump.target = it
            },
            onDismiss = { goToOpen = false },
        )
    }
}

/** Between "Go to" and the list on screen: the initial to jump to, and the one in view. */
@Stable
private class LetterJump {
    var target by mutableStateOf<String?>(null)
    var current by mutableStateOf<String?>(null)
}

/** One row of covers: [items] under [initial], and whether it's the first ([first]) of it. */
private class LetterRow<T>(val initial: String, val first: Boolean, val items: List<T>)

private fun <T> letterRows(sections: List<NameSection<T>>, columns: Int): List<LetterRow<T>> =
    sections.flatMap { section ->
        section.items.chunked(columns).mapIndexed { index, row -> LetterRow(section.initial, index == 0, row) }
    }

/**
 * [sections] in rows of covers, as many to a row as fit, with their initial in a column on the left
 * beside the first row of it: no line of its own for a letter, so more covers show. A letter's
 * covers start a new row. The focused row scrolls to the top, as the TV's grids do. A letter picked
 * in "Go to" ([jump]) comes to the top with the focus on its first cover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> ByLetter(
    sections: List<NameSection<T>>,
    state: LazyListState,
    jump: LetterJump,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    card: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val room = maxWidth - (TvMargin - TvRingSpace) * 2 - LetterColumnWidth
        val columns = ((room + Spacing.sm) / (TvCardMinWidth + Spacing.sm)).toInt().coerceAtLeast(1)
        val rows = remember(sections, columns) { letterRows(sections, columns) }
        val current by remember(rows) { derivedStateOf { rows.getOrNull(state.firstVisibleItemIndex)?.initial } }
        LaunchedEffect(current) { jump.current = current }
        val jumpFocus = remember { FocusRequester() }
        val target = jump.target
        LaunchedEffect(target, rows) {
            if (target == null) return@LaunchedEffect
            val row = rows.indexOfFirst { it.first && it.initial == target }
            if (row >= 0) {
                state.scrollToItem(row)
                jumpFocus.requestFocusWhenPlaced()
            }
            jump.target = null
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvGridBringIntoViewSpec(GridTop)) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(
                    start = TvMargin - TvRingSpace,
                    end = TvMargin - TvRingSpace,
                    top = GridTop,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rows, key = { "${it.initial}:${key(it.items.first())}" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(Modifier.width(LetterColumnWidth - Spacing.sm)) {
                            if (row.first) LetterLabel(row.initial)
                        }
                        row.items.forEachIndexed { index, item ->
                            val focusHere = row.first && index == 0 && row.initial == target
                            card(
                                item,
                                Modifier
                                    .weight(1f)
                                    .then(if (focusHere) Modifier.focusRequester(jumpFocus) else Modifier),
                            )
                        }
                        repeat(columns - row.items.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A letter beside the first row of its covers; not focusable, the covers are. */
@Composable
private fun LetterLabel(initial: String) {
    Text(
        initial,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = TvRingSpace, top = TvRingSpace)
            .semantics { heading() },
    )
}

/**
 * "Go to": the initials as round keys over the screen, the focus on the current one. OK on one
 * closes it and goes there; Back closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvGoToDialog(initials: List<String>, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val focus = remember(initials) { initials.associateWith { FocusRequester() } }
        LaunchedEffect(Unit) { (current?.let(focus::get) ?: focus.values.first()).requestFocusWhenPlaced() }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = GoToWidth),
        ) {
            Column(
                Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(R.string.library_go_to),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = TvRingSpace)
                        .semantics { heading() },
                )
                FlowRow {
                    initials.forEach { initial ->
                        LetterKey(
                            initial = initial,
                            current = initial == current,
                            onClick = { onPick(initial) },
                            modifier = Modifier.focusRequester(focus.getValue(initial)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterKey(initial: String, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .tvFocusFrame(focused, CircleShape)
            .clickable(
                interaction,
                indication = null,
                onClickLabel = stringResource(R.string.library_jump_to_letter, initial),
                role = Role.Button,
                onClick = onClick,
            )
            .size(LetterKeySize)
            .background(if (current) scheme.onSurface else scheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
            color = if (current) scheme.surface else scheme.onSurface,
        )
    }
}

@Composable
private fun PlaylistCard(item: PlaylistItem, navigation: LibraryNavigation, modifier: Modifier = Modifier) {
    val covers = LocalCoverUrls.current
    val playlist = item.playlist
    TvCoverCard(
        modifier = modifier,
        title = playlist?.title ?: stringResource(R.string.liked_songs),
        subtitle = songsCount(item.count),
        coverUrl = playlist?.let { covers.playlist(it, item.coverTrackId, ServerUrls.COVER_LARGE) }
            ?: covers.track(item.coverTrackId, ServerUrls.COVER_LARGE),
        placeholderIcon = if (playlist == null) DowntifyIcons.FavoriteFilled else DowntifyIcons.Playlist,
        onClick = { playlist?.let { navigation.onPlaylist(it.name) } ?: navigation.onLiked() },
    )
}

@Composable
private fun AlbumCard(album: Album, navigation: LibraryNavigation, modifier: Modifier = Modifier) {
    TvCoverCard(
        modifier = modifier,
        title = album.title,
        subtitle = albumSubtitle(album),
        coverUrl = LocalCoverUrls.current.track(album.coverTrackId, ServerUrls.COVER_LARGE),
        onClick = { navigation.onAlbum(album.id) },
    )
}

@Composable
private fun ArtistCard(artist: Artist, navigation: LibraryNavigation, modifier: Modifier = Modifier) {
    TvCoverCard(
        modifier = modifier,
        title = artist.name,
        subtitle = null,
        coverUrl = LocalCoverUrls.current.artist(artist.name),
        fallbackUrl = LocalCoverUrls.current.track(artist.coverTrackId, ServerUrls.COVER_LARGE),
        onClick = { navigation.onArtist(artist.id) },
        shape = CircleShape,
        ringShape = CircleShape,
        placeholderIcon = DowntifyIcons.Artist,
        centered = true,
    )
}

@Composable
private fun SongCard(track: Track, state: LibraryUiState, onPlay: (String) -> Unit, modifier: Modifier = Modifier) {
    TvCoverCard(
        modifier = modifier,
        title = track.displayTitle,
        subtitle = track.displayArtist,
        coverUrl = LocalCoverUrls.current.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_LARGE),
        onClick = { onPlay(track.id) },
        placeholderIcon = DowntifyIcons.Song,
        current = track.id == state.currentTrackId,
        playing = state.isPlaying,
    )
}

/**
 * A cover with its title and a line under it. Focus rings the cover along [ringShape], a small gap
 * out, and leaves the text alone; there's no tint over the picture. [current] marks what's playing
 * by its title's color and, before it, the bars ([playing] animates them).
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
    playing: Boolean = false,
    fallbackUrl: String? = null,
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
            fallbackUrl = fallbackUrl,
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusFrame(focused, ringShape)
                .aspectRatio(1f),
        )
        Column(Modifier.padding(horizontal = TvRingSpace), horizontalAlignment = align) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (current) PlayingBars(Modifier.size(14.dp), animate = playing)
                CardTitle(title, current, centered)
            }
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

@Composable
private fun CardTitle(title: String, current: Boolean, centered: Boolean) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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

/** The column the initials take on the left. */
private val LetterColumnWidth = 36.dp

private val LetterKeySize = 40.dp

/** The "Go to" dialog: about nine letters to a line. */
private val GoToWidth = 560.dp

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
                    albumSections = NameIndex.sections(PreviewData.albums) { it.title },
                ),
                navigation = LibraryNavigation(),
                onSelectTab = {},
                onPlaySong = {},
            )
        }
    }
}
