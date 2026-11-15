package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.ui.common.plusBottomBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WeeklyDjPlaylistRoute(
    theme: String,
    copyId: String,
    onBack: () -> Unit,
    viewModel: MusicDjViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val summary = if (copyId.isNotBlank()) {
        state.weekly?.copies?.firstOrNull { it.id == copyId }
    } else {
        state.weekly?.cards?.firstOrNull { it.theme == theme }?.session
    }
    LaunchedEffect(lifecycle, caps.weeklySetsEnabled()) {
        if (!caps.weeklySetsEnabled()) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.refreshWeekly()
                val preparing = viewModel.state.value.weekly?.cards.orEmpty().any {
                    it.session?.state in setOf("queued", "preparing", "rendering")
                }
                delay(if (preparing) 10_000 else 60_000)
            }
        }
    }
    LaunchedEffect(summary?.id, summary?.state) {
        summary?.let { viewModel.loadWeeklyDetails(it.id) }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.clearWeeklyDetails() } }
    val details = state.details?.takeIf { it.id == summary?.id }
    val session = summary?.copy(
        tracks = details?.tracks ?: summary.tracks,
        timeline =
            details?.timeline ?: summary.timeline,
    )
    WeeklyDjPlaylistPage(
        theme, session, caps, state, copyId.isNotBlank(), onBack,
        onPlay = { viewModel.play(it) },
        onPrepare = viewModel::prepareWeekly,
        onSave = viewModel::saveCopy,
        onRetry = { summary?.let { viewModel.loadWeeklyDetails(it.id) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeeklyDjPlaylistPage(
    theme: String,
    session: MusicDjSessionDto?,
    caps: MusicAiCapabilitiesDto,
    state: MusicDjUiState,
    savedCopy: Boolean,
    onBack: () -> Unit,
    onPlay: (MusicDjSessionDto) -> Unit = {},
    onPrepare: (String) -> Unit = {},
    onSave: (MusicDjSessionDto, String) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    val title = weeklyTitle(theme, session)
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("weekly-dj-playlist"),
            contentPadding = PaddingValues(16.dp).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("cover") {
                MusicMoodArt(
                    theme,
                    "weekly",
                    Modifier.fillMaxWidth().height(200.dp),
                    subtitle = "DJ : ${moodNames[theme] ?: theme}",
                )
            }
            if (!caps.weeklySetsEnabled()) {
                item("disabled") { Text("Les sets DJ sont désactivés dans les paramètres IA.") }
            } else {
                item("actions") {
                    WeeklyDjPlaylistActions(theme, session, state, caps, savedCopy, onPlay, onPrepare, onSave)
                }
                if (state.detailsMessage.isNotBlank()) {
                    item("error") {
                        Text(state.detailsMessage)
                        TextButton(onClick = onRetry, enabled = !state.detailsLoading) { Text("Réessayer les titres") }
                    }
                }
                if (state.message.isNotBlank()) item("message") { Text(state.message) }
                if (state.detailsLoading) item("loading") { Text("Chargement des titres…") }
                itemsIndexed(session?.tracks.orEmpty(), key = { index, track ->
                    "$index:${track.id}"
                }) { index, track ->
                    ListItem(
                        headlineContent = { Text(track.title) },
                        supportingContent = { Text(track.artist) },
                        leadingContent = { Text("${index + 1}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyDjPlaylistActions(
    theme: String,
    session: MusicDjSessionDto?,
    state: MusicDjUiState,
    caps: MusicAiCapabilitiesDto,
    savedCopy: Boolean,
    onPlay: (MusicDjSessionDto) -> Unit,
    onPrepare: (String) -> Unit,
    onSave: (MusicDjSessionDto, String) -> Unit,
) {
    val preparing = session?.state in setOf("queued", "preparing", "rendering")
    var copying by rememberSaveable(session?.id) { mutableStateOf(false) }
    var name by rememberSaveable(session?.id) { mutableStateOf(weeklyTitle(theme, session)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (session != null) WeeklyDjMetadata(session)
        Text(
            when {
                state.weekly == null -> "Chargement du set…"
                savedCopy && session == null -> "Cette copie n’est plus disponible."
                else -> weeklyStatus(session)
            },
        )
        if (preparing) {
            LinearProgressIndicator(progress = {
                (session?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
        }
        if (session?.state == "ready") {
            TextButton(onClick = {
                onPlay(session)
            }, enabled = !state.busy) { Text("Écouter ${weeklyTitle(theme, session)}") }
            if (caps.djAssetCopies && !savedCopy && !session.immutableCopy) {
                CopySetForm(session, name, copying, state.busy, { copying = it }, { name = it }, onSave)
            }
        } else if (!savedCopy && !preparing && state.weekly != null) {
            TextButton(onClick = { onPrepare(theme) }, enabled = !state.busy && caps.dj.preparedReady) {
                Text("Préparer ${moodNames[theme] ?: theme}")
            }
        }
        if (session?.tracks?.isNotEmpty() == true) Text("Titres du mix", style = MaterialTheme.typography.titleMedium)
    }
}
