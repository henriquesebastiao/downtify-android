package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.MediaListItem
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SelectionCheckbox
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Selection
import com.henriquesebastiao.downtify.core.model.Track

/**
 * A playlist page's "Add songs": every song in the library, a tap to pick one, and a button that
 * sends the pick. A manual playlist may hold the same song twice, so nothing is left out here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddSongsSheet(
    songs: List<Track>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        AddSongsContent(songs = songs, onConfirm = { picked -> onConfirm(picked) })
    }
}

/**
 * The sheet's list, apart from the modal window that holds it: the library's songs with a check
 * each, and the button that sends them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddSongsContent(
    songs: List<Track>,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by remember { mutableStateOf(Selection()) }
    Column(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            stringResource(R.string.pl_add_songs),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = Spacing.sm).semantics { heading() },
        )
        LazyColumn(Modifier.heightIn(max = SONGS_MAX_HEIGHT)) {
            items(songs, key = { it.id }) { track ->
                MediaListItem(
                    title = track.displayTitle,
                    subtitle = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                    onClick = { picked = picked.toggle(track.id) },
                    leading = { SelectionCheckbox(picked.contains(track.id)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = { onConfirm(picked.ids) },
                enabled = picked.count > 0,
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
            ) {
                Icon(painterResource(DowntifyIcons.Add), contentDescription = null)
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    pluralStringResource(R.plurals.pl_add_n_to_playlist, picked.count, picked.count),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private val SONGS_MAX_HEIGHT = 400.dp

@PhonePreviews
@Composable
private fun AddSongsContentPreview() {
    // The sheet is a modal window: the preview draws the list it fills, not the scrim.
    DowntifyTheme {
        AddSongsContent(songs = PreviewData.tracks, onConfirm = {})
    }
}
