package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SongDjStatusDialog(status: SongDjStatus, onDismiss: () -> Unit, onCancel: () -> Unit, onOpenPlayer: () -> Unit) {
    if (!status.visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(status.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(status.message)
                if (status.preparing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (status.canOpenPlayer) {
                TextButton(onClick = onOpenPlayer) { Text("Ouvrir le lecteur") }
            } else {
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
        dismissButton = {
            Row {
                if (status.canCancel) TextButton(onClick = onCancel) { Text("Annuler") }
                if (status.canOpenPlayer) TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}
