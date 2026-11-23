package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.IconAvatar
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/** A playlist the "Add to playlist" sheet offers: its row's title and how many songs it holds. */
data class PlaylistTarget(val name: String, val title: String, val count: Int)

/**
 * The sheet behind "Add to playlist" (a song's menu, or the selection bar): the playlists that can
 * take the songs — manual ones the caller may edit — with "New playlist" at the top. A tap answers
 * with a name and the caller closes the sheet; the write happens in the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    targets: List<PlaylistTarget>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        AddToPlaylistContent(targets = targets, onPick = onPick, onCreateNew = onCreateNew)
    }
}

/**
 * The sheet's list, apart from the modal window that holds it: "New playlist" and the playlists
 * that can take the songs.
 */
@Composable
fun AddToPlaylistContent(
    targets: List<PlaylistTarget>,
    onPick: (String) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .heightIn(max = SHEET_MAX_HEIGHT)
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
    ) {
        Text(
            stringResource(R.string.pl_add_to_playlist),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.xs).semantics { heading() },
        )
        MediaListItem(
            title = stringResource(R.string.pl_new),
            onClick = onCreateNew,
            leading = { IconAvatar(DowntifyIcons.Add) },
        )
        targets.forEach { target ->
            MediaListItem(
                title = target.title,
                subtitle = songsCount(target.count),
                onClick = { onPick(target.name) },
                leading = { IconAvatar(DowntifyIcons.Playlist) },
            )
        }
    }
}

private val SHEET_MAX_HEIGHT = 480.dp

@PhonePreviews
@Composable
private fun AddToPlaylistContentPreview() {
    // The sheet itself is a modal window: the preview draws its content, not the scrim.
    DowntifyTheme {
        AddToPlaylistContent(
            targets = listOf(
                PlaylistTarget("Deep Focus", "Deep Focus", 3),
                PlaylistTarget("Late Night Drive", "Late Night Drive", 2),
            ),
            onPick = {},
            onCreateNew = {},
        )
    }
}
