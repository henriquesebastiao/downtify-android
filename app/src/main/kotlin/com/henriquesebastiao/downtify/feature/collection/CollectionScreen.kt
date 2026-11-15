package com.henriquesebastiao.downtify.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.totalMinutes
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.feature.trash.LocalMoveToTrash
import com.henriquesebastiao.downtify.ui.common.AddSongsSheet
import com.henriquesebastiao.downtify.ui.common.LocalBottomBarsInset
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PlaylistNameDialog
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.SwipeToRemove
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackItem
import com.henriquesebastiao.downtify.ui.common.playlistErrorMessage
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
        editActions = CollectionEditActions(
            onToggleEditing = viewModel::toggleEditing,
            onOpenRename = viewModel::openRename,
            onDismissRename = viewModel::dismissRename,
            onRename = viewModel::rename,
            onOpenAddSongs = viewModel::openAddSongs,
            onDismissAddSongs = viewModel::dismissAddSongs,
            onAddSongs = viewModel::addSongs,
            onRemoveAt = viewModel::removeAt,
            onMove = viewModel::move,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** What a playlist page does to its own songs: rename, add, drag, swipe away. */
data class CollectionEditActions(
    val onToggleEditing: () -> Unit = {},
    val onOpenRename: () -> Unit = {},
    val onDismissRename: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onOpenAddSongs: () -> Unit = {},
    val onDismissAddSongs: () -> Unit = {},
    val onAddSongs: (List<String>) -> Unit = {},
    val onRemoveAt: (Int) -> Unit = {},
    val onMove: (Int, Int) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

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
    editActions: CollectionEditActions = CollectionEditActions(),
) {
    val covers = LocalCoverUrls.current
    val coverUrl = state.playlist?.let { covers.playlist(it, state.coverTrackId, ServerUrls.COVER_LARGE) }
        ?: covers.track(state.coverTrackId, ServerUrls.COVER_LARGE)
    val seedUrl = covers.track(state.coverTrackId, ServerUrls.COVER_SMALL)
    val title = if (state.kind == CollectionKind.Liked) stringResource(R.string.liked_songs) else state.title
    // Captured outside the cover's theme: the header fades into the app's surface, not the cover's.
    val appSurface = MaterialTheme.colorScheme.surface
    val list = rememberLazyListState()
    // One key per row that survives a move: the song and which copy of it this is.
    val keys = remember(state.tracks) { rowKeys(state.tracks) }
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
                        if (state.editing) {
                            TextButton(onClick = editActions.onToggleEditing) {
                                Text(
                                    stringResource(R.string.pl_done),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onPrimaryContainer,
                                )
                            }
                        } else {
                            state.playlist?.takeIf { it.favoriteSupported }?.let { playlist ->
                                FavoriteButton(
                                    playlist.favorite,
                                    enabled = !state.favoriteBusy,
                                    onClick = onToggleFavorite,
                                )
                            }
                            if (state.canRename || state.canEditTracks) {
                                PlaylistMenu(state, editActions)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.primaryContainer),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
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
                    state.editError?.let { error ->
                        item(key = "edit-error") {
                            Text(
                                playlistErrorMessage(error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(horizontal = Spacing.screen)
                                    .clickable(onClick = editActions.onDismissError),
                            )
                        }
                    }
                    if (state.editing) {
                        item(key = "edit-hint") { EditHint() }
                    }
                    itemsIndexed(
                        state.tracks,
                        key = { i, _ -> "${state.revision}:${keys.getOrElse(i) { i.toString() }}" },
                    ) { index, track ->
                        val row = Modifier.padding(horizontal = Spacing.screen)
                        val number = if (state.kind == CollectionKind.Album) {
                            track.trackNumber.takeIf { it > 0 } ?: (index + 1)
                        } else {
                            null
                        }
                        if (state.editing) {
                            EditableTrackRow(
                                track = track,
                                index = index,
                                count = state.tracks.size,
                                number = number,
                                coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                                isCurrent = track.id == state.currentTrackId && state.isCurrentContext,
                                isPlaying = state.isPlaying,
                                isLiked = track.id in state.likedIds,
                                downloaded = track.id in state.offline.downloadedIds,
                                onRemove = { editActions.onRemoveAt(index) },
                                onMove = editActions.onMove,
                                modifier = row,
                            )
                        } else {
                            TrackItem(
                                track = track,
                                onClick = { onPlay(index) },
                                number = number,
                                coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                                isCurrent = track.id == state.currentTrackId && state.isCurrentContext,
                                isPlaying = state.isPlaying,
                                isLiked = track.id in state.likedIds,
                                downloaded = track.id in state.offline.downloadedIds,
                                actions = trackActions,
                                modifier = row,
                            )
                        }
                    }
                    // No navigation bar here: the list clears the system's, unless the mini player under it does.
                    item(key = "system-bar") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
                Scrollbar(list)
            }
        }
    }
    PlaylistEditingSheets(state, editActions)
}

/**
 * A key per row that survives a move: a manual playlist may hold the same song twice, so the song's
 * id alone isn't enough — this says which copy of it the row is.
 */
internal fun rowKeys(tracks: List<Track>): List<String> {
    val seen = HashMap<String, Int>()
    return tracks.map { track ->
        val nth = seen.getOrDefault(track.id, 0)
        seen[track.id] = nth + 1
        "${track.id}#$nth"
    }
}

/** The page's menu: a rename for its owner, songs for anyone it lets edit them. */
@Composable
private fun PlaylistMenu(state: CollectionUiState, editActions: CollectionEditActions) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        CircleButton(
            DowntifyIcons.MoreVert,
            stringResource(R.string.pl_more),
            { open = true },
            containerColor = scheme.onPrimaryContainer.copy(alpha = TINT_ALPHA),
            contentColor = scheme.onPrimaryContainer,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.canRename) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pl_rename)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Edit), contentDescription = null) },
                    onClick = {
                        open = false
                        editActions.onOpenRename()
                    },
                )
            }
            if (state.canEditTracks) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pl_add_songs)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Add), contentDescription = null) },
                    onClick = {
                        open = false
                        editActions.onOpenAddSongs()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pl_edit_songs)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.DragHandle), contentDescription = null) },
                    onClick = {
                        open = false
                        editActions.onToggleEditing()
                    },
                )
            }
        }
    }
}

/** "Press and hold to move a song, swipe to take it out" — what edit mode is for. */
@Composable
private fun EditHint() {
    Text(
        stringResource(R.string.pl_edit_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screen),
    )
}

/**
 * A row in edit mode: swiped left it leaves the playlist, and its handle drags it up or down. The
 * dragged row follows the finger and swaps with its neighbour each time it is halfway across it;
 * the new order shows at once and the ViewModel sends it (`PUT /api/v1/playlists/order`).
 */
@Composable
private fun EditableTrackRow(
    track: Track,
    index: Int,
    count: Int,
    number: Int?,
    coverUrl: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    downloaded: Boolean,
    onRemove: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember { mutableFloatStateOf(0f) }
    var lift by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // The row keeps its identity while it moves, so the drag reads the position as it is now.
    val at by rememberUpdatedState(index)
    val rows by rememberUpdatedState(count)
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Box(modifier.onSizeChanged { heightPx = it.height.toFloat() }) {
        Box(Modifier.graphicsLayer { translationY = lift }) {
            SwipeToRemove(
                onRemove = onRemove,
                shape = DowntifyShape.Section,
                label = stringResource(R.string.pl_remove_from_playlist),
            ) {
                TrackItem(
                    track = track,
                    // Playing a row would fight the drag: in edit mode a tap does nothing.
                    onClick = {},
                    number = number,
                    coverUrl = coverUrl,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    downloaded = downloaded,
                    // No menu while editing: the handle and the swipe are the actions here.
                    actions = TrackActions(),
                    modifier = Modifier.padding(end = Spacing.xl),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(DRAG_TOUCH)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            lift = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            lift += amount.y
                            val half = heightPx / 2f
                            // Halfway across the next row, the two trade places and the drag goes on.
                            if (heightPx > 0f) {
                                when {
                                    lift > half && at < rows - 1 -> {
                                        onMove(at, at + 1)
                                        lift -= heightPx
                                    }

                                    lift < -half && at > 0 -> {
                                        onMove(at, at - 1)
                                        lift += heightPx
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            lift = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            lift = 0f
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(DowntifyIcons.DragHandle),
                contentDescription = stringResource(R.string.pl_reorder_handle),
                tint = if (dragging) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}

/** The rename dialog and the song picker, driven by the state so tests can reach them. */
@Composable
private fun PlaylistEditingSheets(state: CollectionUiState, editActions: CollectionEditActions) {
    if (state.renameOpen) {
        PlaylistNameDialog(
            title = stringResource(R.string.pl_rename),
            confirmLabel = stringResource(R.string.pl_rename),
            initialName = state.title,
            onDismiss = editActions.onDismissRename,
            onConfirm = editActions.onRename,
        )
    }
    if (state.addSongsOpen) {
        AddSongsSheet(
            songs = state.librarySongs,
            onDismiss = editActions.onDismissAddSongs,
            onConfirm = editActions.onAddSongs,
        )
    }
}

private val DRAG_TOUCH = 48.dp

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
