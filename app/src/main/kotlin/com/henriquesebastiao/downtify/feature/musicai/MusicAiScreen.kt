package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone

private val features = linkedMapOf(
    "personalization" to "Personnalisation et nouvelles écoutes",
    "similarity" to "Musiques basées sur un titre",
    "daily" to "Mixes du jour",
    "weekly" to "Mixes de la semaine",
    "external_beta" to "Découverte externe — bêta",
    "dj_prepared" to "Sets DJ préparés",
    "dj_interactive" to "DJ en temps réel",
    "dj_trial" to "DJ non qualifié — essai bêta volontaire",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAiRoute(onBack: () -> Unit, viewModel: MusicAiViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("playlists") }
    var serverExpanded by rememberSaveable { mutableStateOf(false) }
    val shown = features.filterKeys { it.startsWith("dj_") == (mode == "dj") }
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(topBar = {
        TopAppBar(title = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("IA")
                Tag("Expérimental", tone = TagTone.Secondary)
            }
        }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Vos préférences sont partagées entre le web et Android. " +
                        "Chaque fonction peut être désactivée séparément.",
                )
                if (state.message.isNotBlank()) Text(state.message)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (caps.version != 1) {
                item { Text("Ces fonctions ne sont pas disponibles sur ce serveur.") }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == "playlists", onClick = {
                            mode = "playlists"
                        }, label = { Text("Playlists personnalisées") })
                        FilterChip(selected = mode == "dj", onClick = { mode = "dj" }, label = { Text("DJ") })
                    }
                }
                item { Text("Vos préférences", style = MaterialTheme.typography.titleLarge) }
                items(shown.keys.toList(), key = { "pref-$it" }) { feature ->
                    MusicAiSwitch(
                        shown.getValue(feature),
                        state.preferences[feature] == true,
                        !state.busy && state.preferences.isNotEmpty(),
                    ) { viewModel.toggle(feature, it) }
                }
                if (caps.administrator) {
                    item {
                        TextButton(onClick = { serverExpanded = !serverExpanded }) { Text("Activation sur le serveur") }
                    }
                    if (serverExpanded) {
                        item {
                            Text(
                                "Ces choix s’appliquent à tous les utilisateurs, " +
                                    "en plus de leurs préférences personnelles.",
                            )
                        }
                        items(shown.keys.toList(), key = { "global-$it" }) { feature ->
                            MusicAiSwitch(
                                "Serveur : ${shown.getValue(feature)}",
                                state.global[feature] == true,
                                !state.busy && state.global.isNotEmpty(),
                            ) {
                                viewModel.toggle(feature, it, global = true)
                            }
                        }
                    }
                }
                if (mode == "dj") {
                    item {
                        Text(
                            "Préparez un set à l’avance ou mixez en temps réel sur le serveur. " +
                                "Le prochain titre et le thème changent à la prochaine transition disponible.",
                        )
                    }
                    if (caps.enabled("dj_prepared") || caps.enabled("dj_interactive")) {
                        item { MusicDjPanel() }
                    } else {
                        item {
                            Text(
                                "Activez un mode DJ pour ouvrir le studio. " +
                                    "L’administrateur doit aussi autoriser ce mode.",
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            "Retrouvez vos playlists dans Accueil → Pour vous. " +
                                "Mixes du jour à 04 h ; de la semaine, le lundi à 04 h 15, heure de Paris.",
                        )
                        Text(
                            "Les favoris et les copies de playlists enregistrées sont conservés " +
                                "quand une fonction est désactivée.",
                        )
                    }
                    item {
                        Text("Confidentialité", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Effacer le profil retire les écoutes utilisées pour personnaliser les propositions, " +
                                "sans toucher aux favoris ni aux playlists enregistrées.",
                        )
                        TextButton(onClick = viewModel::forget, enabled = !state.busy) {
                            Text("Effacer mon profil d’écoute")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicAiSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = change,
            modifier = Modifier.semantics {
                contentDescription =
                    label
            },
        )
    }
}
