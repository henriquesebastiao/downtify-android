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
import com.henriquesebastiao.downtify.core.player.LocalDjMode
import com.henriquesebastiao.downtify.feature.player.PlaybackMixMode
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

private val dailyMoods = listOf(
    "detente" to "Détente",
    "concentration" to "Concentration",
    "energie" to "Motivation",
    "calme" to "Calme",
)

private val genreMoods = listOf("rock", "rap", "pop", "electro")

@Composable
fun DailyMusicShelf(
    state: MusicAiUiState,
    caps: MusicAiCapabilitiesDto,
    onOpen: (String, String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pour vous", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) { Text("Tout voir") }
        }
        Text("Selon votre humeur", style = MaterialTheme.typography.titleSmall)
        HomeMixRows(dailyMoods.map { it.first }, "daily", state, caps, onOpen)
        Text("Vos genres", style = MaterialTheme.typography.titleSmall)
        Text(
            if (caps.enabled("personalization")) {
                "Vos écoutes affinent vos sélections. Aimez vos titres préférés pour préciser vos goûts."
            } else {
                "Activez la personnalisation dans les préférences IA pour adapter les mixes à vos goûts."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        HomeMixRows(genreMoods, "weekly", state, caps, onOpen)
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeMixRows(
    moods: List<String>,
    cadence: String,
    state: MusicAiUiState,
    caps: MusicAiCapabilitiesDto,
    onOpen: (String, String) -> Unit,
) {
    moods.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { mood ->
                val card = state.playlists.firstOrNull { it.cadence == cadence && it.mood == mood }
                val ready = caps.enabled(cadence) && card != null && card.count > 0
                Surface(
                    onClick = { onOpen(cadence, mood) },
                    enabled = caps.enabled(cadence),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).height(112.dp),
                ) {
                    MusicMoodArt(
                        mood,
                        cadence,
                        compact = true,
                        subtitle = when {
                            !caps.enabled(cadence) -> "À activer dans IA"
                            ready && card.state == "previous" -> "${card.count} titres · renouvellement en cours"
                            ready -> "${card.count} titres · à écouter"
                            else -> "En préparation"
                        },
                    )
                }
            }
        }
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
    onOpenWeeklyDj: (String, String) -> Unit = { _, _ -> },
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
            if (caps.djWeeklySets) {
                item("weekly-dj-sets") {
                    WeeklyDjSection(featured = false, onOpenSet = onOpenWeeklyDj)
                }
            }
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
                    viewModel::renewPlaylists,
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
    val localDevice by queue.localDevice.collectAsStateWithLifecycle()
    val mode by queue.mixMode.collectAsStateWithLifecycle()
    val localMode by queue.localMode.collectAsStateWithLifecycle()
    val localReadiness by queue.localReadiness.collectAsStateWithLifecycle()
    val queueChecking by queue.checking.collectAsStateWithLifecycle()
    val queueArmed by queue.armed.collectAsStateWithLifecycle()
    val queueBusy by queue.busy.collectAsStateWithLifecycle()
    val queueAvailability by queue.availability.collectAsStateWithLifecycle()
    val queuePhase by queue.phase.collectAsStateWithLifecycle()
    LaunchedEffect(queue) { queue.onQueueShown() }
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
                    "Créez un mix préparé ou continu à partir de votre musique. " +
                        "Les transitions et les titres restent dans votre lecteur habituel.",
                )
                TextButton(onClick = onSettings) { Text("Paramètres IA") }
                com.henriquesebastiao.downtify.feature.player.PlaybackMixSelector(mode, queue::selectMode)
                com.henriquesebastiao.downtify.feature.player.RhythmicDjEngine(localDevice, queue::chooseDevice)
                if (localDevice && mode == PlaybackMixMode.Dj) {
                    com.henriquesebastiao.downtify.feature.player.LocalDjFeedback(
                        localReadiness,
                        queue::retryLocalPlans,
                        queue::useServerFallback,
                    )
                }
                Text(
                    androidx.compose.ui.res.stringResource(
                        if (localDevice) {
                            com.henriquesebastiao.downtify.R.string.music_dj_local_description
                        } else {
                            com.henriquesebastiao.downtify.R.string.music_dj_server_description
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (localMode == LocalDjMode.Transitions || localDevice) {
                    val seconds by queue.transitionSeconds.collectAsStateWithLifecycle()
                    Column {
                        Text(
                            "Durée du fondu : $seconds s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = seconds.toFloat(),
                            onValueChange = { queue.setTransitionSeconds(it.toInt()) },
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Un fondu plus long adoucit l'enchaînement, un fondu court reste proche du " +
                                "titre suivant. Aucun effet si les deux titres ne sont pas dans votre " +
                                "bibliothèque locale.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(
                    onClick = queue::toggle,
                    enabled = !queueChecking || queueArmed || queueBusy,
                ) {
                    Text(
                        when {
                            queuePhase == com.henriquesebastiao.downtify.core.player.DjPhase.Preparing ->
                                "Annuler la préparation DJ"

                            queuePhase == com.henriquesebastiao.downtify.core.player.DjPhase.Ready ->
                                "Annuler le relais DJ"

                            mode == PlaybackMixMode.Transitions && queueArmed -> "Désactiver les transitions"

                            queueArmed || queueBusy ||
                                queuePhase == com.henriquesebastiao.downtify.core.player.DjPhase.Active ->
                                "Désactiver le mode DJ"

                            queueChecking && localMode == LocalDjMode.Transitions -> "Vérification des transitions…"

                            queueChecking -> "Vérification du DJ…"

                            queueAvailability.ready && localMode == LocalDjMode.Transitions ->
                                "Activer les transitions"

                            queueAvailability.ready -> "Activer le DJ sur la file"

                            else -> "Vérifier le DJ"
                        },
                    )
                }
                if (queueChecking || queueBusy) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                val queueDetail = queueMessage.ifBlank { queueAvailability.reason }
                if (queueDetail.isNotBlank()) Text(queueDetail)
                if (!caps.enabled("dj_prepared") &&
                    !caps.enabled("dj_interactive")
                ) {
                    Text("Activez un mode DJ dans Paramètres → IA, puis autorisez-le sur le serveur.")
                }
            }
            if (!localDevice) musicDjThemeItems(caps, state, viewModel::continuous)
            item { MusicDjPanel() }
        }
    }
}
