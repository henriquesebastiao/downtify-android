package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

private val dailyMoods = listOf(
    "detente" to "Détente",
    "concentration" to "Concentration",
    "energie" to "Motivation",
    "calme" to "Calme",
)

@Composable
fun DailyMusicShelf(
    state: MusicAiUiState,
    caps: MusicAiCapabilitiesDto,
    onOpen: (String, String) -> Unit,
    onSeeAll: () -> Unit,
) {
    val covers = LocalCoverUrls.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pour vous", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) { Text("Tout voir") }
        }
        dailyMoods.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (mood, label) ->
                    val card = state.playlists.firstOrNull { it.cadence == "daily" && it.mood == mood }
                    Surface(
                        onClick = { onOpen("daily", mood) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).height(112.dp),
                    ) {
                        MusicMoodArt(
                            mood,
                            "daily",
                            compact = true,
                            subtitle = if (card != null &&
                                card.count > 0
                            ) {
                                "${card.count} titres · du jour"
                            } else if (!caps.enabled("daily")) {
                                "À activer dans IA"
                            } else {
                                "En préparation"
                            },
                        )
                    }
                }
            }
        }
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlaylistsRoute(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    cadence: String = "daily",
    mood: String = "",
    viewModel: MusicAiViewModel = hiltViewModel(),
    onOpenPlaylist: (String, String) -> Unit = { c, m -> viewModel.mood(c, m) },
    onArtist: (String) -> Unit = {},
    onAlbum: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    LaunchedEffect(cadence, mood) {
        if (mood.isBlank()) viewModel.refreshPlaylists() else viewModel.openPlaylist(cadence, mood)
    }
    if (mood.isNotBlank() || state.tracks.isNotEmpty()) {
        TemporaryMusicPlaylist(
            viewModel,
            state,
            if (mood.isBlank()) viewModel::clearSelection else onBack,
            onArtist,
            onAlbum,
        )
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Pour vous") }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp).plusBottomBars(),
        ) {
            musicForYouItems(
                state,
                caps,
                MusicForYouActions(
                    viewModel::refreshPlaylists,
                    viewModel::forYou,
                    viewModel::selectCadence,
                    onOpenPlaylist,
                    viewModel::play,
                    viewModel::save,
                    onSettings,
                    viewModel::clearSelection,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStudioRoute(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    seed: String = "",
    viewModel: MusicDjViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val queue: com.henriquesebastiao.downtify.feature.player.QueueDjViewModel = hiltViewModel()
    val queueMessage by queue.message.collectAsStateWithLifecycle()
    LaunchedEffect(seed) {
        viewModel.refresh()
        if (seed.isNotBlank()) viewModel.seed(seed)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("DJ · Bêta") }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Créez un mix préparé ou continu à partir de votre musique. Les transitions et les titres restent dans votre lecteur habituel.",
                )
                TextButton(onClick = onSettings) { Text("Paramètres IA") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = true, onClick = {}, label = { Text("Serveur") })
                    androidx.compose.material3.FilterChip(selected = false, enabled = false, onClick = {
                    }, label = { Text("Cet appareil · Bêta") })
                }
                Text(
                    "Le moteur local attend la validation de l’export ONNX et des performances sur votre appareil. Aucun modèle n’est téléchargé. Le serveur reste disponible.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = queue::toggle, enabled = caps.interactiveAvailable()) {
                    Text("Mode DJ sur la file")
                }
                if (queueMessage.isNotBlank()) Text(queueMessage)
                if (!caps.enabled("dj_prepared") &&
                    !caps.enabled("dj_interactive")
                ) {
                    Text("Activez un mode DJ dans Paramètres → IA, puis autorisez-le sur le serveur.")
                }
            }
            musicDjThemeItems(caps, state, viewModel::continuous)
            item { MusicDjPanel() }
        }
    }
}
