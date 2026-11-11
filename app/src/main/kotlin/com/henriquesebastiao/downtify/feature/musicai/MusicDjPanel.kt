package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MusicDjPanel(viewModel: MusicDjViewModel = hiltViewModel()) {
    val openPlayer = com.henriquesebastiao.downtify.ui.common.LocalOpenPlayer.current
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    var theme by rememberSaveable { mutableStateOf("fete") }
    var count by rememberSaveable { mutableStateOf("12") }
    var mode by rememberSaveable { mutableStateOf("prepared") }
    val ready = if (mode == "prepared") caps.dj.preparedReady else caps.interactiveAvailable()
    LaunchedEffect(caps.features) {
        if (!caps.enabled("dj_" + mode)) mode = if (caps.enabled("dj_prepared")) "prepared" else "interactive"
    }
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.refresh()
                delay(5000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Votre set DJ", style = MaterialTheme.typography.titleLarge)
        if (caps.enabled("dj_prepared")) {
            FilterChip(selected = mode == "prepared", onClick = {
                mode = "prepared"
            }, label = { Text("Set préparé") })
        }
        if (caps.enabled("dj_interactive")) {
            FilterChip(selected = mode == "interactive", onClick = {
                mode =
                    "interactive"
            }, label = { Text("Direct interactif") })
        }
        Text("Un mix de vos fichiers locaux, avec transitions calculées sur le serveur. Les originaux sont conservés.")
        if (!ready) {
            Text(
                if (mode == "interactive") {
                    "Le direct nécessite une validation continue d’une heure sur ce serveur."
                } else {
                    "Le moteur DJ se prépare ou est indisponible."
                },
            )
        }
        listOf(
            "calme" to "Calme",
            "concentration" to "Concentration",
            "energie" to "Énergie",
            "fete" to "Fête",
            "melancolie" to "Mélancolie",
            "detente" to "Détente",
        ).forEach { (id, title) ->
            FilterChip(selected = theme == id, onClick = { theme = id }, label = { Text(title) })
        }
        OutlinedTextField(
            value = count,
            onValueChange = { count = it.filter(Char::isDigit).take(2) },
            label = { Text("Nombre de titres, de 2 à 40") },
            singleLine = true,
        )
        Button(
            onClick = { viewModel.create(mode, theme, count.toIntOrNull() ?: 12) },
            enabled = ready && !state.busy && count.toIntOrNull() in 2..40,
        ) { Text(if (mode == "prepared") "Préparer le set" else "Démarrer le direct") }
        Text(
            "Un seul rendu à la fois. La lecture utilise le lecteur habituel et conserve votre file.",
        )
        if (state.message.isNotEmpty()) Text(state.message)
        state.sessions.forEach { session ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "${session.theme} · ${session.trackCount ?: session.tracks.size} titres",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when (session.state) {
                        "queued" -> "En attente"
                        "preparing" -> "Analyse des morceaux"
                        "rendering" -> "Préparation du mix"
                        "ready" -> if (session.mode == "interactive") "Terminé" else "Prêt à écouter"
                        "playing" -> "Direct en cours"
                        "paused" -> "Direct en pause"
                        "failed" -> "Le rendu a échoué. Essayez une autre sélection."
                        else -> "Rendu arrêté"
                    },
                )
                if (session.state in setOf("queued", "preparing", "rendering", "playing", "paused")) {
                    if (session.state in setOf("queued", "preparing", "rendering")) {
                        LinearProgressIndicator(progress = { session.progress.toFloat().coerceIn(0f, 1f) })
                    }
                    TextButton(onClick = {
                        viewModel.stop(session.id)
                    }, enabled = !state.busy) { Text("Arrêter le rendu") }
                }
                if (listening == session.id && session.mode == "interactive" &&
                    session.state in setOf("playing", "paused")
                ) {
                    TextButton(onClick = {
                        viewModel.command(
                            session,
                            if (session.state ==
                                "paused"
                            ) {
                                "resume"
                            } else {
                                "pause"
                            },
                        )
                    }) {
                        Text(if (session.state == "paused") "Reprendre le direct" else "Mettre le direct en pause")
                    }
                    TextButton(onClick = { viewModel.command(session, "next") }) { Text("Prochain titre") }
                    Text(
                        "Prochaine transition modifiable dans environ ${kotlin.math.ceil(
                            (session.nextMutableAt - session.position).coerceAtLeast(0.0),
                        ).toInt()} s.",
                    )
                    TextButton(onClick = {
                        viewModel.command(session, "theme", mapOf("theme" to theme))
                    }) { Text("Appliquer le thème choisi") }
                    session.tracks.forEach { track ->
                        TextButton(onClick = {
                            viewModel.command(session, "replace", mapOf("id" to track.id))
                        }) { Text("Suivant : ${track.title}") }
                    }
                    session.commands.firstOrNull()?.takeIf { !it.accepted }?.let { Text(it.reason) }
                }
                val canPlay = when (session.mode) {
                    "prepared" -> session.state == "ready"
                    "interactive" -> session.state in setOf("playing", "paused")
                    else -> false
                }
                if (canPlay) {
                    Button(onClick = { viewModel.play(session, openPlayer) }) { Text("Écouter le set") }
                    if (session.mode ==
                        "prepared"
                    ) {
                        Text("Repères : lancez le set, puis touchez un titre pour vous y déplacer.")
                    }
                    session.timeline.filter { session.mode == "prepared" }.forEach { marker ->
                        val track = session.tracks.find { it.id == marker.id }
                        TextButton(onClick = { viewModel.seek(session, marker.start) }) {
                            Text(
                                "${(marker.start / 60).toInt()}:${(marker.start % 60).toInt().toString().padStart(
                                    2,
                                    '0',
                                )} · ${track?.title.orEmpty()}",
                            )
                        }
                        marker.transition?.reason?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
