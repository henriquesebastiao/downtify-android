package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal fun sleepRemainingLabel(remainingMs: Long): String {
    val seconds = ((remainingMs.coerceAtLeast(0) + 999) / 1000)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun SleepTimerStatus(deadline: Long?, onCancel: () -> Unit) {
    if (deadline == null) return
    var now by remember(deadline) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (now < deadline) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Minuterie", style = MaterialTheme.typography.labelMedium)
                Text("Arrêt dans ${sleepRemainingLabel(deadline - now)}", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onCancel) { Text("Annuler") }
        }
    }
}
