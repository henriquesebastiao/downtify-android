package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.player.PlaybackSleepTimer
import com.henriquesebastiao.downtify.ui.common.LocalMusicAiCapabilities
import com.henriquesebastiao.downtify.ui.common.LocalSongMix

@Composable
fun PlayerOptions(track: Track?, sleepActive: Boolean, setSleepTimer: (Int?) -> Unit) {
    val startMix = LocalSongMix.current
    val startDj = com.henriquesebastiao.downtify.ui.common.LocalSongDjMix.current
    var open by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    Box {
        CircleButton(DowntifyIcons.MoreVert, "Options de lecture", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (track != null && LocalMusicAiCapabilities.current.enabled("similarity")) {
                DropdownMenuItem(
                    text = { Text("Lancer un mix à partir de ce titre") },
                    onClick = {
                        open = false
                        startMix(track)
                    },
                )
                HorizontalDivider()
            }
            if (track != null && LocalMusicAiCapabilities.current.enabled("dj_interactive") &&
                LocalMusicAiCapabilities.current.enabled("similarity")
            ) {
                DropdownMenuItem(text = { Text("Lancer un mix DJ à partir de ce titre") }, onClick = {
                    open = false
                    startDj(track)
                })
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(if (sleepActive) "Modifier la minuterie" else "Minuterie d’arrêt") },
                onClick = {
                    open = false
                    timerOpen = true
                },
            )
        }
    }

    if (timerOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { timerOpen = false },
            title = { Text("Minuterie d’arrêt") },
            text = {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    PlaybackSleepTimer.PRESETS.forEach { minutes ->
                        androidx.compose.material3.FilterChip(
                            selected = false,
                            onClick = {
                                setSleepTimer(minutes)
                                timerOpen = false
                            },
                            label = { Text(if (minutes == 60) "1 h" else "$minutes min") },
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { timerOpen = false }) { Text("Fermer") }
            },
            dismissButton = {
                if (sleepActive) {
                    androidx.compose.material3.TextButton(onClick = {
                        setSleepTimer(null)
                        timerOpen = false
                    }) {
                        Text("Annuler la minuterie")
                    }
                }
            },
        )
    }
}
