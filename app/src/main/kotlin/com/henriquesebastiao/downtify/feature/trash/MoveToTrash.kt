package com.henriquesebastiao.downtify.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashPreview
import com.henriquesebastiao.downtify.core.model.TrashState

/**
 * "Move to trash" for a song's menu: null unless this is an admin's pairing and the server has the
 * music trash on. Screens hand it to their [com.henriquesebastiao.downtify.ui.common.TrackActions].
 */
val LocalMoveToTrash = compositionLocalOf<((Track) -> Unit)?> { null }

/**
 * Provides [LocalMoveToTrash] to [content] and shows the steps of a move over it: the server's
 * check, the confirmation listing exactly the files, and [onMessage] once it's on its way.
 */
@Composable
fun MoveToTrashHost(onMessage: (String) -> Unit, content: @Composable () -> Unit) {
    val model: MoveToTrashViewModel = hiltViewModel()
    val canManage by model.canManage.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val move = remember(canManage, model) { if (canManage) { track: Track -> model.move(listOf(track)) } else null }
    CompositionLocalProvider(LocalMoveToTrash provides move, content = content)
    MoveToTrashDialogs(state, onConfirm = model::confirm, onDismiss = model::dismiss)
    (state as? MoveToTrashState.Done)?.let { done ->
        val text = pluralStringResource(R.plurals.trash_moving, done.count, done.count)
        LaunchedEffect(done) {
            onMessage(text)
            model.dismiss()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoveToTrashDialogs(state: MoveToTrashState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        is MoveToTrashState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.trash_checking)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Text(stringResource(R.string.trash_checking_body))
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )

        is MoveToTrashState.Confirm -> ConfirmDialog(state, onConfirm, onDismiss)

        is MoveToTrashState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.trash_failed)) },
            text = { Text(trashMessage(state.code)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        )

        MoveToTrashState.Idle, is MoveToTrashState.Done -> Unit
    }
}

/** Exactly what will move, file by file, before anything does. */
@Composable
private fun ConfirmDialog(state: MoveToTrashState.Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val items = state.preview.items
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.trash_confirm_title, items.size, items.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    pluralStringResource(R.plurals.trash_confirm_body, state.retentionDays, state.retentionDays),
                )
                LazyColumn(Modifier.heightIn(max = PREVIEW_LIST_HEIGHT)) {
                    items(items, key = { it.id }) { item -> PreviewRow(item) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.confirming,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.trash_move)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.confirming) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PreviewRow(item: TrashItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            listOf(item.displayTitle, item.artist).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${item.format} · ${item.file}",
            style = DowntifyType.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

private val PREVIEW_LIST_HEIGHT = 280.dp

private val previewItems = listOf(
    TrashItem(
        "1",
        "Nora Vale/Harbor Nights/01 - Harbor Song.flac",
        title = "Harbor Song",
        artist = "Nora Vale",
        codec = "flac",
        state = TrashState.Unknown,
    ),
    TrashItem(
        "2",
        "Nora Vale/Harbor Nights/02 - Low Lights.mp3",
        title = "Low Lights",
        artist = "Nora Vale",
        codec = "mp3",
        state = TrashState.Unknown,
    ),
)

@PhonePreviews
@Composable
private fun ConfirmPreview() {
    DowntifyTheme {
        MoveToTrashDialogs(
            MoveToTrashState.Confirm(TrashPreview("token", previewItems), retentionDays = 30),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PhonePreviews
@Composable
private fun CheckingPreview() {
    DowntifyTheme { MoveToTrashDialogs(MoveToTrashState.Checking(2), onConfirm = {}, onDismiss = {}) }
}
