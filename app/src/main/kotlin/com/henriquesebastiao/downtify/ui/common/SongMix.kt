package com.henriquesebastiao.downtify.ui.common

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.CatalogJson
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.mediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

val LocalSongMix = staticCompositionLocalOf<(Track) -> Unit> { {} }

@HiltViewModel
class SongMixViewModel @Inject constructor(
    private val musicAi: MusicAiRepository,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val sessions: SessionStore,
) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    fun start(seed: Track) {
        if (busy) return
        val owner = sessions.current ?: return
        val current = player.state.value.mediaKey
        busy = true
        message = "Préparation du mix…"
        viewModelScope.launch {
            try {
                val refreshed = musicAi.refresh()
                if (refreshed !is ServerResult.Ok || !musicAi.capabilities.value.enabled("similarity")) {
                    message = "La similarité locale est désactivée ou indisponible."
                    return@launch
                }
                val result = CatalogJson.similarTracks(musicAi.similar("", "", seed = seed.id))
                if (sessions.current != owner || player.state.value.mediaKey != current) return@launch
                val snapshot = library.library.value ?: return@launch
                val tracks = result
                    .mapNotNull { snapshot.byId[it.id] }.filter { it.id != seed.id }.distinctBy { it.id }
                if (!musicAi.capabilities.value.enabled("similarity") || tracks.isEmpty()) {
                    message = "Mix indisponible : vérifie la similarité IA et l’analyse de ce titre."
                } else {
                    player.startMix(seed, tracks)
                    message = "Mix lancé · ${tracks.size + 1} titres"
                }
            } catch (_: MusicAiFailure) {
                if (sessions.current == owner) message = "Mix indisponible : ce titre doit être analysé."
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun songMixAction(viewModel: SongMixViewModel = hiltViewModel()): (Track) -> Unit {
    val context = LocalContext.current
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    return viewModel::start
}
