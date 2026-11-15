package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal fun MusicAiCapabilitiesDto.weeklySetsEnabled() =
    djWeeklySets && listOf("weekly", "dj_prepared", "dj_weekly").all(::enabled)

@Composable
fun WeeklyDjSection(
    featured: Boolean,
    onSeeAll: () -> Unit = {},
    viewModel: MusicDjViewModel = hiltViewModel(),
    onOpenSet: ((String, String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
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
    WeeklyDjShelf(
        state,
        caps,
        featured,
        onSeeAll,
        onPlay = { viewModel.play(it) },
        onPrepare = viewModel::prepareWeekly,
        onSave = viewModel::saveCopy,
        onOpenSet = onOpenSet,
    )
}

@Composable
internal fun WeeklyDjShelf(
    state: MusicDjUiState,
    caps: MusicAiCapabilitiesDto,
    featured: Boolean,
    onSeeAll: () -> Unit = {},
    onPlay: (MusicDjSessionDto) -> Unit = {},
    onPrepare: (String) -> Unit = {},
    onSave: (MusicDjSessionDto, String) -> Unit = { _, _ -> },
    onOpenSet: ((String, String) -> Unit)? = null,
) {
    if (!caps.weeklySetsEnabled()) return
    val weekly = state.weekly
    var selectedTheme by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCopy by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Pour vous DJ", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (featured) TextButton(onClick = onSeeAll) { Text("Tous les sets") }
        }
        Text("Des sets audio de la semaine, adaptés à vos sélections. Aucune sauvegarde automatique.")
        if (weekly == null) {
            Text("Chargement des sets…")
        } else {
            val cards = if (featured) {
                weekly.featured.mapNotNull { theme -> weekly.cards.firstOrNull { it.theme == theme } }.take(4)
            } else {
                weekly.cards
            }
            WeeklyDjTiles(cards.map { it.theme to it.session }) { theme, _ ->
                if (onOpenSet != null) {
                    onOpenSet(theme, "")
                } else {
                    selectedTheme = theme
                    selectedCopy = null
                }
            }
            if (!featured && weekly.copies.isNotEmpty()) {
                Text("Mes sets enregistrés", style = MaterialTheme.typography.titleMedium)
                WeeklyDjTiles(weekly.copies.map { it.weeklyTheme.ifBlank { it.theme } to it }) { theme, copy ->
                    if (onOpenSet != null && copy != null) {
                        onOpenSet(theme, copy.id)
                    } else {
                        selectedCopy = copy?.id
                        selectedTheme = null
                    }
                }
            }
        }
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
    }
    val copy = weekly?.copies?.firstOrNull { it.id == selectedCopy }
    val card = weekly?.cards?.firstOrNull { it.theme == selectedTheme }
    if (copy != null || card != null) {
        WeeklyDjDetails(
            theme = copy?.let { it.weeklyTheme.ifBlank { it.theme } } ?: card!!.theme,
            session = copy ?: card?.session,
            savedCopy = copy != null,
            busy = state.busy,
            caps = caps,
            onDismiss = {
                selectedTheme = null
                selectedCopy = null
            },
            onPlay = onPlay,
            onPrepare = onPrepare,
            onSave = onSave,
        )
    }
}

@Composable
private fun WeeklyDjTiles(
    items: List<Pair<String, MusicDjSessionDto?>>,
    onOpen: (String, MusicDjSessionDto?) -> Unit,
) {
    for (row in items.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((theme, session) in row) {
                WeeklyDjCard(theme, session, Modifier.weight(1f)) { onOpen(theme, session) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

internal fun weeklyTitle(theme: String, session: MusicDjSessionDto?) =
    session?.title?.ifBlank { null } ?: "Set DJ · ${moodNames[theme] ?: theme}"

@Composable
private fun WeeklyDjCard(
    theme: String,
    session: MusicDjSessionDto?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val title = weeklyTitle(theme, session)
    Card(onClick = onOpen, modifier = modifier.semantics { contentDescription = "Détails de $title" }) {
        MusicMoodArt(
            theme,
            "weekly",
            Modifier.fillMaxWidth().height(144.dp),
            subtitle = "DJ : ${moodNames[theme] ?: theme}",
            compact = true,
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (session != null) WeeklyDjMetadata(session)
            Text(
                weeklyStatus(session),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WeeklyDjMetadata(session: MusicDjSessionDto) {
    val minutes = (session.duration.coerceAtLeast(0.0) / 60).toInt()
    Text(
        "${session.trackCount ?: session.tracks.size} titres · $minutes min",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun WeeklyDjDetails(
    theme: String,
    session: MusicDjSessionDto?,
    savedCopy: Boolean,
    busy: Boolean,
    caps: MusicAiCapabilitiesDto,
    onDismiss: () -> Unit,
    onPlay: (MusicDjSessionDto) -> Unit,
    onPrepare: (String) -> Unit,
    onSave: (MusicDjSessionDto, String) -> Unit,
) {
    val ready = session?.state == "ready"
    val preparing = session?.state in setOf("queued", "preparing", "rendering")
    val title = weeklyTitle(theme, session)
    var copying by rememberSaveable(session?.id) { mutableStateOf(false) }
    var copyName by rememberSaveable(session?.id) { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Let Material's content bounds size the dialog rather than a second window constraint.
        // This also avoids Robolectric #8460 at phone-width qualifiers when the editor opens.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (session != null) WeeklyDjMetadata(session)
                Text(weeklyStatus(session))
                if (preparing) {
                    LinearProgressIndicator(
                        progress = { (session?.progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (ready) {
                    TextButton(onClick = { onPlay(session) }, enabled = !busy) { Text("Écouter $title") }
                    if (caps.djAssetCopies && !savedCopy && !session.immutableCopy) {
                        CopySetForm(
                            session = session,
                            name = copyName,
                            expanded = copying,
                            busy = busy,
                            onExpand = { copying = it },
                            onName = { copyName = it },
                            onSave = onSave,
                        )
                    }
                } else if (!preparing && !savedCopy) {
                    TextButton(onClick = { onPrepare(theme) }, enabled = !busy && caps.dj.preparedReady) {
                        Text("Préparer ${moodNames[theme] ?: theme}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

internal fun weeklyStatus(session: MusicDjSessionDto?) = when (session?.state) {
    "ready" -> if (session.immutableCopy) "Copie enregistrée · audio conservé" else "Prêt à écouter"
    "queued" -> "En attente · priorité à votre bibliothèque et à la lecture"
    "preparing", "rendering" -> "En préparation · ${(session.progress.coerceIn(0.0, 1.0) * 100).toInt()} %"
    "expired" -> "À préparer à nouveau"
    "failed" -> "La préparation a échoué. Vous pouvez réessayer."
    else -> "Pas encore préparé"
}

@Composable
internal fun CopySetForm(
    session: MusicDjSessionDto,
    name: String,
    expanded: Boolean,
    busy: Boolean,
    onExpand: (Boolean) -> Unit,
    onName: (String) -> Unit,
    onSave: (MusicDjSessionDto, String) -> Unit,
) {
    if (!expanded) {
        TextButton(onClick = { onExpand(true) }, enabled = !busy) { Text("Enregistrer une copie") }
    } else {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 160) onName(it) },
            label = { Text("Nom de la copie") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = {
                onSave(session, name)
            }, enabled = !busy && name.isNotBlank()) { Text("Conserver l’audio") }
            TextButton(onClick = { onExpand(false) }, enabled = !busy) { Text("Annuler") }
        }
    }
}
