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
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.player.DjPhase
import com.henriquesebastiao.downtify.core.player.LocalDjSettings
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.mediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

val LocalSongMix = staticCompositionLocalOf<(Track) -> Unit> { {} }
val LocalSongDjMix = staticCompositionLocalOf<(Track) -> Unit> { {} }

data class SongDjStatus(
    val visible: Boolean = false,
    val title: String = "Mix DJ",
    val message: String = "",
    val preparing: Boolean = false,
    val canCancel: Boolean = false,
    val canOpenPlayer: Boolean = false,
)

@HiltViewModel
class SongMixViewModel @Inject constructor(
    private val musicAi: MusicAiRepository,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val sessions: SessionStore,
    private val dj: com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator,
    private val local: LocalDjSettings,
) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var djStatus by mutableStateOf(SongDjStatus())
        private set
    private var djGeneration = 0L
    private var djPreparation: Job? = null
    private var serverRequest = false
    private var localPrepared = false
    private var requestOwner: Session? = null

    init {
        viewModelScope.launch {
            combine(dj.busy, dj.phase, dj.message, dj.armed) { preparing, phase, detail, armed ->
                Triple(preparing, phase, detail to armed)
            }.collect { (preparing, phase, details) ->
                if (!serverRequest || requestOwner != sessions.current) return@collect
                val (detail, armed) = details
                val ready = phase == DjPhase.Ready || phase == DjPhase.Active || armed
                if (preparing || ready || detail.isNotBlank()) {
                    djStatus = djStatus.copy(
                        message = detail.ifBlank {
                            if (ready) "Mix DJ prêt dans le lecteur." else "Préparation des transitions…"
                        },
                        preparing = preparing,
                        canCancel = preparing || ready,
                        canOpenPlayer = ready,
                    )
                }
            }
        }
        viewModelScope.launch {
            musicAi.session.collect {
                if (requestOwner != null && it != requestOwner) {
                    // The coordinator/service owns account cleanup. Never stop
                    // playback belonging to the newly selected account here.
                    djGeneration++
                    djPreparation?.cancel()
                    djPreparation = null
                    serverRequest = false
                    localPrepared = false
                    requestOwner = null
                    djFailure("Compte ou serveur changé. Relancez le mix DJ pour ce compte.")
                }
            }
        }
    }

    fun clearMessage() {
        message = null
    }

    fun startDj(seed: Track) {
        val owner = sessions.current
        djPreparation?.cancel()
        if (requestOwner == owner) {
            if (serverRequest) dj.stop()
            if (localPrepared) local.setArmed(false)
        }
        localPrepared = false
        serverRequest = !local.device.value
        val mine = ++djGeneration
        requestOwner = owner
        val original = player.state.value
        val selection = player.selectionVersion.value
        djStatus = SongDjStatus(
            visible = true,
            message = "Vérification du mode DJ…",
            preparing = true,
            canCancel = true,
        )
        if (owner == null) {
            djFailure("Reconnectez-vous au serveur pour préparer ce mix.")
        } else if (serverRequest) {
            dj.startSeed(seed.id)
        } else {
            djPreparation = viewModelScope.launch { prepareLocalDj(seed, mine, owner, original, selection) }
        }
    }

    fun dismissDjStatus() {
        djStatus = djStatus.copy(visible = false)
    }

    fun cancelDjMix() {
        djGeneration++
        djPreparation?.cancel()
        djPreparation = null
        if (requestOwner == sessions.current) {
            if (serverRequest) dj.stop()
            if (localPrepared) local.setArmed(false)
        }
        serverRequest = false
        localPrepared = false
        djFailure("Préparation DJ annulée. Votre file reste disponible.")
    }

    private fun djFailure(reason: String) {
        djStatus = djStatus.copy(message = reason, preparing = false, canCancel = false, canOpenPlayer = false)
    }

    private suspend fun prepareLocalDj(
        seed: Track,
        mine: Long,
        owner: Session,
        original: PlayerState,
        selection: Long,
    ) {
        val request = LocalPreparation(seed, mine, owner, original, selection)
        try {
            val tracks = withTimeout(30_000) { loadLocalCandidates(request) }
            installLocalQueue(request, tracks)
        } catch (failure: LocalPreparationFailure) {
            reportLocalFailure(request, failure.reason)
        } catch (_: TimeoutCancellationException) {
            reportLocalFailure(request, "Préparation trop longue. Vérifiez la connexion puis réessayez.")
        } catch (error: CancellationException) {
            throw error
        } catch (_: MusicAiFailure) {
            reportLocalFailure(request, "Mix indisponible : vérifiez la connexion et l’analyse de ce titre.")
        } catch (_: Exception) {
            reportLocalFailure(request, "Le mix DJ n’a pas pu être préparé. Réessayez depuis ce titre.")
        }
    }

    private suspend fun loadLocalCandidates(request: LocalPreparation): List<Track> {
        val refreshed = musicAi.refresh(force = true)
        ensureLocalRequest(request)
        requireLocalRefresh(refreshed)
        requireLocalSeedSource(request.original)
        djStatus = djStatus.copy(message = "Recherche de titres de votre bibliothèque…")
        val result = CatalogJson.similarTracks(musicAi.similar("", "", seed = request.seed.id))
        ensureLocalRequest(request)
        val snapshot = library.library.value
        if (snapshot?.byId?.containsKey(request.seed.id) != true) {
            throw LocalPreparationFailure("Ce titre n’est plus disponible dans la bibliothèque synchronisée.")
        }
        val tracks = result.mapNotNull { snapshot.byId[it.id] }
            .filter { it.id != request.seed.id }.distinctBy { it.id }
        if (tracks.isEmpty()) {
            throw LocalPreparationFailure(
                "Aucun titre similaire disponible dans votre bibliothèque. Analysez ce titre puis réessayez.",
            )
        }
        return tracks
    }

    private fun requireLocalRefresh(result: ServerResult<Unit>) {
        if (result !is ServerResult.Ok) {
            throw LocalPreparationFailure(
                "Impossible de vérifier les réglages DJ. Vérifiez la connexion au serveur puis réessayez.",
            )
        }
    }

    private fun requireLocalSeedSource(original: PlayerState) {
        requireLocalCapabilities("Activez le DJ direct et la similarité dans Paramètres → IA.")
        if (original.dj != null) {
            throw LocalPreparationFailure("Désactivez le DJ serveur avant de préparer un mix sur cet appareil.")
        }
    }

    private suspend fun installLocalQueue(request: LocalPreparation, tracks: List<Track>) {
        ensureLocalRequest(request)
        requireLocalCapabilities()
        val seed = library.library.value?.byId?.get(request.seed.id)
            ?: throw LocalPreparationFailure("Ce titre n’est plus disponible dans la bibliothèque synchronisée.")
        val expectedSelection = request.selection + if (request.original.track?.id == seed.id) 0 else 1
        djStatus = djStatus.copy(message = "Mise en place de la file locale…")
        player.startMix(seed, tracks)
        val ready = withTimeoutOrNull(5_000) {
            awaitLocalQueue(request, expectedSelection, tracks.map { it.id })
        } == true
        ensureLocalRequest(request, unchanged = false)
        if (!ready) {
            throw LocalPreparationFailure(
                "La file n’a pas pu être confirmée ou la sélection a changé. Réessayez depuis le lecteur.",
            )
        }
        requireLocalCapabilities()
        local.setArmed(true)
        localPrepared = true
        djStatus = djStatus.copy(
            message = "Mix DJ local prêt · transitions à la fin du titre en cours.",
            preparing = false,
            canCancel = true,
            canOpenPlayer = true,
        )
    }

    private suspend fun awaitLocalQueue(
        request: LocalPreparation,
        expectedSelection: Long,
        queue: List<String>,
    ): Boolean {
        while (localRequestCurrent(request)) {
            val state = player.state.value
            val version = player.selectionVersion.value
            val validSelection = version == request.selection || version == expectedSelection
            val validMedia = state.mediaKey == request.original.mediaKey || state.mediaKey == "track:${request.seed.id}"
            if (!validSelection || !validMedia) break
            if (version == expectedSelection && state.track?.id == request.seed.id &&
                state.upNext.map { it.trackId } == queue
            ) {
                return true
            }
            delay(50)
        }
        return false
    }

    private fun localRequestCurrent(request: LocalPreparation) = request.generation == djGeneration &&
        sessions.current == request.owner && local.device.value

    private fun ensureLocalRequest(request: LocalPreparation, unchanged: Boolean = true) {
        val sameSelection = player.selectionVersion.value == request.selection &&
            player.state.value.mediaKey == request.original.mediaKey
        if (!localRequestCurrent(request) || (unchanged && !sameSelection)) {
            throw LocalPreparationFailure("Préparation annulée : le compte, le moteur ou le titre a changé.")
        }
    }

    private fun requireLocalCapabilities(reason: String = "Le DJ direct ou la similarité a été désactivé.") {
        val caps = musicAi.capabilities.value
        if (!caps.enabled("dj_interactive") || !caps.enabled("similarity")) throw LocalPreparationFailure(reason)
    }

    private fun reportLocalFailure(request: LocalPreparation, reason: String) {
        if (request.generation == djGeneration) djFailure(reason)
    }

    private data class LocalPreparation(
        val seed: Track,
        val generation: Long,
        val owner: Session,
        val original: PlayerState,
        val selection: Long,
    )

    private class LocalPreparationFailure(val reason: String) : Exception(reason)

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

@Composable
fun songDjMixAction(viewModel: SongMixViewModel = hiltViewModel()): (Track) -> Unit = viewModel::startDj
