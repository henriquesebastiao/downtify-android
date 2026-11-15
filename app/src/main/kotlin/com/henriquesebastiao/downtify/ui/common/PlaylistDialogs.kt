package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme

/**
 * Names a new playlist, or renames one: a single text field and a confirm that stays off while
 * there is nothing to send. The typed name lives in this composable's snapshot state (never in a
 * flow that went through `combine`), so no keystroke is dropped; the caller only hears the finished
 * name. [initialName] is selected-for-replacement on first focus, as a rename should be.
 */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty()
    fun confirm() {
        if (valid) onConfirm(trimmed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.pl_name_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The library's "New playlist": a name, then the playlist opens empty. */
@PhonePreviews
@Composable
private fun NewPlaylistDialogPreview() {
    DowntifyTheme {
        PlaylistNameDialog(
            title = stringResource(R.string.pl_new),
            confirmLabel = stringResource(R.string.pl_create),
            onDismiss = {},
            onConfirm = {},
        )
    }
}
