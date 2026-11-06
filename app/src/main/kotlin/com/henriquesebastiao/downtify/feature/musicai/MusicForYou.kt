package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto

internal val moodNames = mapOf(
    "calme" to "Calme",
    "concentration" to "Concentration",
    "energie" to "Motivation",
    "fete" to "Fête",
    "melancolie" to "Mélancolie",
    "detente" to "Détente",
)
internal val moodColors = mapOf(
    "calme" to Color(0xFF4F46E5),
    "concentration" to Color(0xFF334155),
    "energie" to Color(0xFFC2410C),
    "fete" to Color(0xFFA21CAF),
    "melancolie" to Color(0xFF1D4ED8),
    "detente" to Color(0xFF0F766E),
)
internal fun mixTitle(cadence: String, mood: String) =
    "${if (cadence == "daily") "Mix du jour" else "Mix de la semaine"} · ${moodNames[mood].orEmpty()}"
private fun selectionTitle(state: MusicAiUiState) =
    if (state.selectedMood.isBlank()) "À écouter maintenant" else mixTitle(state.source, state.selectedMood)

data class MusicForYouActions(
    val refresh: () -> Unit = {},
    val forYou: () -> Unit = {},
    val cadence: (String) -> Unit = {},
    val mood: (String, String) -> Unit = { _, _ -> },
    val play: (MusicAiTrackDto, Boolean) -> Unit = { _, _ -> },
    val save: (String) -> Unit = {},
    val settings: () -> Unit = {},
    val clearSelection: () -> Unit = {},
)

fun LazyListScope.musicDjThemeItems(
    caps: MusicAiCapabilitiesDto,
    state: MusicDjUiState,
    onStart: (String, String) -> Unit,
) {
    if (!caps.enabled("dj_interactive") || !caps.dj.continuousSupported) return
    item("dj-themes") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vos mixes DJ", style = MaterialTheme.typography.titleLarge)
            Text(
                "Une sélection locale qui se prolonge avec des transitions adaptées.",
                style = MaterialTheme.typography.bodySmall,
            )
            val recipes = listOf(
                Triple("Fête", "fete", "library"), Triple("Rap", "energie", "rap"), Triple("Rock", "energie", "rock"),
                Triple(
                    "Électro",
                    "fete",
                    "electro",
                ),
                Triple("Calme", "calme", "library"), Triple("Concentration", "concentration", "library"),
                Triple(
                    "Énergie",
                    "energie",
                    "library",
                ),
                Triple("Détente", "detente", "library"), Triple("Mélancolie", "melancolie", "library"),
            )
            recipes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, theme, style) ->
                        Card(Modifier.weight(1f)) {
                            TextButton(
                                onClick = { onStart(theme, style) },
                                enabled =
                                    !state.busy && caps.interactiveAvailable(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Text(label, maxLines = 1)
                                    Text("DJ continu", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            if (!caps.interactiveAvailable()) {
                Text(
                    "Activez un moteur qualifié ou l’essai bêta volontaire dans Paramètres → IA.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun LazyListScope.musicForYouItems(state: MusicAiUiState, caps: MusicAiCapabilitiesDto, actions: MusicForYouActions) {
    item("ai-title") { MusicForYouHeader(state, caps, actions) }
    if (state.tracks.isNotEmpty() && caps.enabled(state.source)) {
        item("ai-back") {
            TextButton(
                onClick = actions.clearSelection,
                enabled = !state.busy,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("← Tous mes mixes")
            }
        }
        musicSelectionItems(state, actions)
    } else {
        musicCatalogItems(state, caps, actions)
    }
}

private fun LazyListScope.musicCatalogItems(
    state: MusicAiUiState,
    caps: MusicAiCapabilitiesDto,
    actions: MusicForYouActions,
) {
    item("ai-cadence") {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("daily" to "Chaque jour", "weekly" to "Chaque semaine")) {
                if (caps.enabled(key)) {
                    FilterChip(selected = state.cadence == key, onClick = {
                        actions.cadence(key)
                    }, label = { Text(label) })
                }
            }
        }
    }
    val cards = state.playlists.filter {
        it.cadence == state.cadence && caps.enabled(it.cadence) && it.mood in moodNames
    }
    if (cards.isEmpty() && !state.busy) {
        item("ai-empty") {
            Text(
                "Vos mixes apparaîtront ici après leur génération. Ils se renouvellent automatiquement.",
                Modifier.padding(16.dp),
            )
        }
    }
    items(cards.chunked(2), key = { "ai-card-${it.first().cadence}-${it.first().mood}-${it.first().period}" }) { pair ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (card in pair) {
                MusicMixCard(card, !state.busy, {
                    actions.mood(card.cadence, card.mood)
                }, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (caps.enabled("personalization")) {
        item("ai-recommend") {
            TextButton(
                onClick = actions.forYou,
                enabled = !state.busy && caps.model.ready,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("À écouter maintenant")
            }
        }
    }
}

@Composable
private fun MusicForYouHeader(state: MusicAiUiState, caps: MusicAiCapabilitiesDto, actions: MusicForYouActions) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pour vous", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Vos mixes du jour et de la semaine, à partir de votre musique et de vos goûts.")
        Row {
            TextButton(onClick = actions.settings) { Text("Préférences IA") }
            TextButton(onClick = actions.refresh, enabled = !state.busy) { Text("Actualiser") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.message.isNotBlank()) Text(state.message)
        if (!caps.model.ready) Text("L’analyse locale se prépare. Votre bibliothèque reste disponible.")
    }
}

@Composable
private fun MusicMixCard(
    card: MusicAiPlaylistDto,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = moodColors[card.mood] ?: MaterialTheme.colorScheme.primary
    Card(onClick = onClick, enabled = enabled, modifier = modifier) {
        Box(
            Modifier.fillMaxWidth().height(165.dp).background(
                Brush.linearGradient(
                    listOf(
                        color,
                        Color(
                            color.red * .65f,
                            color.green * .65f,
                            color.blue * .65f,
                        ),
                    ),
                ),
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (card.cadence ==
                        "daily"
                    ) {
                        "MIX DU JOUR"
                    } else {
                        "MIX DE LA SEMAINE"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    moodNames[card.mood].orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "${card.count} titres · ${if (card.cadence == "daily") "Chaque jour" else "Chaque semaine"}",
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun LazyListScope.musicSelectionItems(state: MusicAiUiState, actions: MusicForYouActions) {
    item("ai-selection") {
        Column(Modifier.padding(16.dp)) {
            Text(selectionTitle(state), style = MaterialTheme.typography.headlineSmall)
            Text("${state.tracks.size} titres · bibliothèque locale")
            Button(onClick = {
                actions.play(state.tracks.first(), false)
            }, enabled = !state.busy) { Text("Tout écouter") }
        }
    }
    items(state.tracks, key = { "ai-track-${it.id}" }) { track ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium)
            Text(track.reason, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { actions.play(track, false) }) { Text("Écouter") }
                TextButton(onClick = { actions.play(track, true) }) { Text("Ajouter à la file") }
            }
        }
    }
    item("ai-save") { SaveMusicMix(state, actions.save) }
}

@Composable
private fun SaveMusicMix(state: MusicAiUiState, save: (String) -> Unit) {
    var name by rememberSaveable(state.source, state.selectedMood) { mutableStateOf(selectionTitle(state)) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = {
            name = it.take(160)
        }, label = { Text("Nom de la playlist") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {
            save(name)
        }, enabled = !state.busy && name.isNotBlank()) { Text("Enregistrer une copie stable") }
        Text(
            "La copie enregistrée ne sera pas remplacée au prochain renouvellement.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
