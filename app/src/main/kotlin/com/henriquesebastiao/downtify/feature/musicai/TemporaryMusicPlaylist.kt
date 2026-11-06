package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.feature.collection.CollectionScreen
import com.henriquesebastiao.downtify.feature.trash.LocalMoveToTrash
import com.henriquesebastiao.downtify.ui.common.TrackActions

/** The native playlist screen with an immutable-copy action instead of an offline subscription. */
@Composable
internal fun TemporaryMusicPlaylist(
    viewModel: MusicAiViewModel,
    state: MusicAiUiState,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
) {
    val collection by viewModel.collectionState.collectAsStateWithLifecycle()
    var showSave by rememberSaveable(state.selectionId) { mutableStateOf(false) }
    var name by rememberSaveable(state.selectionId) {
        mutableStateOf(if (state.selectedMood.isBlank()) "Pour vous" else mixTitle(state.source, state.selectedMood))
    }
    CollectionScreen(
        state = collection,
        onBack = onBack,
        onArtist = onArtist,
        onPlay = { viewModel.playSelection(it) },
        onPlayOrPause = viewModel::playOrPause,
        onShuffle = { viewModel.playSelection(0, shuffle = true) },
        showOffline = false,
        trackActions = TrackActions(
            onToggleLike = viewModel::toggleLike,
            onAddToQueue = viewModel::addToQueue,
            onGoToAlbum = { onAlbum(it.albumId) },
            onGoToArtist = { onArtist(it.artistId) },
            onMoveToTrash = LocalMoveToTrash.current,
        ),
        headerArtwork = {
            MusicMoodArt(
                state.selectedMood,
                state.source,
                Modifier.size(232.dp).clip(RoundedCornerShape(24.dp)),
                subtitle = "POUR VOUS",
            )
        },
        headerExtras = {
            Column {
                Button(onClick = {
                    showSave = true
                }, enabled = state.tracks.isNotEmpty() && !state.busy) { Text("Enregistrer en playlist") }
                if (state.message.isNotBlank()) {
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                } else if (state.tracks.isEmpty() &&
                    !state.busy
                ) {
                    Text("Cette playlist est en préparation.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Enregistrer en playlist") },
            text = {
                Column {
                    Text("Une copie de ces titres sera conservée. Le prochain renouvellement ne la modifiera pas.")
                    OutlinedTextField(value = name, onValueChange = {
                        name = it.take(120)
                    }, label = { Text("Nom de la playlist") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.save(name)
                        showSave = false
                    },
                    enabled =
                        name.isNotBlank() && !state.busy,
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Annuler") } },
        )
    }
}
