package com.henriquesebastiao.downtify.feature.musicai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjIds
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class MusicDjUiState(
    val sessions: List<MusicDjSessionDto> = emptyList(),
    val busy: Boolean = false,
    val message: String = "",
    val weekly: com.henriquesebastiao.downtify.core.network.dto.MusicDjWeeklyDto? = null,
    val details: MusicDjSessionDto? = null,
    val detailsLoading: Boolean = false,
    val detailsMessage: String = "",
)

@HiltViewModel
class MusicDjViewModel @Inject constructor(
    private val music: MusicAiRepository,
    private val player: PlayerController,
    private val coordinator: com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(MusicDjUiState())
    val state = mutable.asStateFlow()
    val capabilities = music.capabilities
    val listening = player.state.map { it.dj?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val mutex = Mutex()
    private var ownBusy = false
    private var pending: Pair<Triple<String, String, Int>, String>? = null
    private var pendingAsset: Pair<List<String>, String>? = null
    private var detailsJob: Job? = null
    private var detailsRequest = 0L

    init {
        viewModelScope.launch {
            coordinator.message.collectLatest { message ->
                if (message.isNotBlank()) mutable.update { it.copy(message = message) }
            }
        }
        viewModelScope.launch {
            coordinator.busy.collectLatest { busy -> mutable.update { it.copy(busy = ownBusy || busy) } }
        }
        viewModelScope.launch {
            music.session.drop(1).collectLatest {
                detailsJob?.cancel()
                detailsRequest++
                mutable.value = MusicDjUiState()
                pending = null
                pendingAsset = null
            }
        }
        viewModelScope.launch {
            music.capabilities.collectLatest {
                if (!it.weeklySetsEnabled()) clearWeeklyDetails()
                mutable.update { state ->
                    state.copy(
                        sessions = state.sessions.filter { row -> it.enabled("dj_" + row.mode) },
                        weekly = state.weekly.takeIf { _ -> it.weeklySetsEnabled() },
                        details = state.details.takeIf { _ -> it.weeklySetsEnabled() },
                    )
                }
            }
        }
    }

    suspend fun refresh() = mutex.withLock {
        if (!anyEnabled()) return@withLock
        val owner = music.session.value
        try {
            val found = music.djSessions().filter {
                music.capabilities.value.enabled("dj_" + it.mode) &&
                    DjIds.valid(it.id)
            }
            if (owner == music.session.value && anyEnabled()) mutable.update { it.copy(sessions = found) }
        } catch (_: MusicAiFailure) {
            if (owner == music.session.value) {
                mutable.update { it.copy(message = "Les sets sont temporairement indisponibles.") }
            }
        }
    }

    suspend fun refreshWeekly() = mutex.withLock {
        if (!music.capabilities.value.weeklySetsEnabled()) return@withLock
        val owner = music.session.value
        try {
            val found = music.djWeekly()
            if (owner == music.session.value && music.capabilities.value.weeklySetsEnabled()) {
                mutable.update { it.copy(weekly = found) }
            }
        } catch (_: MusicAiFailure) {
            if (owner == music.session.value) {
                mutable.update { it.copy(message = "Les sets de la semaine sont temporairement indisponibles.") }
            }
        }
    }

    fun prepareWeekly(theme: String) = assetAction(listOf("prepare", theme)) { request ->
        music.djPrepareWeekly(theme, request)
    }

    fun loadWeeklyDetails(id: String) {
        if (!music.capabilities.value.weeklySetsEnabled() || !DjIds.valid(id)) return
        detailsJob?.cancel()
        val request = ++detailsRequest
        val owner = music.session.value
        mutable.update {
            it.copy(
                details = it.details?.takeIf { value ->
                    value.id == id
                },
                detailsLoading = true,
                detailsMessage = "",
            )
        }
        detailsJob = viewModelScope.launch {
            try {
                val found = music.djSession(id)
                if (request == detailsRequest && owner == music.session.value &&
                    music.capabilities.value.weeklySetsEnabled()
                ) {
                    mutable.update { it.copy(details = found) }
                }
            } catch (error: MusicAiFailure) {
                if (request == detailsRequest && owner == music.session.value) {
                    mutable.update { it.copy(detailsMessage = musicAiFailureMessage(error)) }
                }
            } finally {
                if (request == detailsRequest && owner == music.session.value) {
                    mutable.update { it.copy(detailsLoading = false) }
                }
            }
        }
    }

    fun clearWeeklyDetails() {
        detailsJob?.cancel()
        detailsRequest++
        mutable.update { it.copy(details = null, detailsLoading = false, detailsMessage = "") }
    }

    fun saveCopy(session: MusicDjSessionDto, name: String) {
        val clean = name.trim()
        if (!music.capabilities.value.djAssetCopies || clean.isBlank() || clean.length > 160) return
        assetAction(listOf("copy", session.id, clean)) { request -> music.djCopy(session.id, clean, request) }
    }

    private fun assetAction(intent: List<String>, work: suspend (String) -> Unit) {
        if (!music.capabilities.value.weeklySetsEnabled()) return
        action {
            val owner = music.session.value
            val request = pendingAsset?.takeIf { it.first == intent }?.second ?: UUID.randomUUID().toString()
            pendingAsset = intent to request
            try {
                work(request)
            } catch (error: MusicAiFailure) {
                if (owner == music.session.value) {
                    mutable.update {
                        it.copy(
                            message = when (error.status) {
                                409, 410 -> "Le set ou vos réglages ont changé. Actualisez avant de réessayer."
                                507 -> "Espace ou limite de copies atteint sur le serveur."
                                else -> musicAiFailureMessage(error)
                            },
                        )
                    }
                }
                if (error.status != null && error.status in 400..499) pendingAsset = null
                return@action
            }
            if (owner == music.session.value) {
                pendingAsset = null
                val found = music.djWeekly()
                if (owner == music.session.value && music.capabilities.value.weeklySetsEnabled()) {
                    mutable.update { it.copy(weekly = found, message = "Demande enregistrée.") }
                }
            }
        }
    }

    private fun anyEnabled() =
        music.capabilities.value.enabled("dj_prepared") || music.capabilities.value.enabled("dj_interactive")

    fun create(mode: String, theme: String, count: Int) = action {
        val ready = if (mode ==
            "prepared"
        ) {
            music.capabilities.value.dj.preparedReady
        } else {
            music.capabilities.value.interactiveAvailable()
        }
        if (!music.capabilities.value.enabled("dj_" + mode) || !ready) {
            return@action
        }
        val intent = Triple(mode, theme, count.coerceIn(2, 40))
        val request = pending?.takeIf { it.first == intent }?.second ?: UUID.randomUUID().toString()
        pending = intent to request
        music.djCreate(intent.second, intent.third, request, intent.first)
        pending = null
    }

    fun stop(id: String) {
        if (player.state.value.dj?.id == id) coordinator.stop() else action { music.djStop(id) }
    }
    fun continuous(theme: String, style: String) = coordinator.startTheme(theme, style)
    fun seed(id: String) = coordinator.startSeed(id)

    fun command(session: MusicDjSessionDto, operation: String, payload: Map<String, String> = emptyMap()) {
        if (operation == "resume" && player.state.value.dj?.id != session.id) {
            play(session)
            return
        }
        if (player.state.value.dj?.id == session.id && operation in setOf("pause", "resume")) {
            if (operation == "pause") player.pause() else player.resume()
            return
        }
        action { commandRemote(session, operation, payload) }
    }

    private suspend fun commandRemote(session: MusicDjSessionDto, operation: String, payload: Map<String, String>) {
        val result = music.djCommand(session.id, operation, payload)
        if (player.state.value.dj?.id == session.id) {
            if (operation == "pause") player.pause()
            if (operation == "resume") player.resume()
        }
        mutable.update {
            it.copy(
                message = if (result.accepted) {
                    "Commande reçue. Délai estimé : ${kotlin.math.ceil(result.effectiveAfterSeconds).toInt()} s."
                } else {
                    result.reason
                },
            )
        }
    }

    private fun action(work: suspend () -> Unit) {
        if (mutable.value.busy) return
        val owner = music.session.value
        ownBusy = true
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                mutex.withLock { if (owner == music.session.value) work() }
                if (owner == music.session.value) refresh()
            } catch (error: MusicAiFailure) {
                if (owner == music.session.value) {
                    mutable.update {
                        it.copy(
                            message = musicAiFailureMessage(error),
                        )
                    }
                }
            } finally {
                ownBusy = false
                mutable.update { it.copy(busy = coordinator.busy.value) }
            }
        }
    }

    fun play(session: MusicDjSessionDto, onReady: () -> Unit = {}) {
        if (session.state !in setOf("ready", "playing", "paused") ||
            !music.capabilities.value.enabled("dj_" + session.mode)
        ) {
            return
        }
        val selection = player.selectionVersion.value
        val owner = music.session.value
        viewModelScope.launch {
            try {
                val full = if (session.tracks.isEmpty()) music.djSession(session.id) else session
                if (selection == player.selectionVersion.value &&
                    owner == music.session.value
                ) {
                    coordinator.listen(
                        full,
                        full.title.ifBlank {
                            "Set DJ · ${full.weeklyTheme.ifBlank { full.theme }}"
                        },
                    )
                    val attached = withTimeoutOrNull(8000) { player.state.first { it.dj?.id == full.id } }
                    if (attached != null && owner == music.session.value) onReady()
                }
            } catch (error: MusicAiFailure) {
                mutable.update { it.copy(message = musicAiFailureMessage(error)) }
            }
        }
    }

    fun seek(session: MusicDjSessionDto, seconds: Double) {
        if (session.mode == "prepared" && player.state.value.dj?.id == session.id && seconds.isFinite()) {
            player.seekTo((seconds.coerceIn(0.0, session.duration) * 1000).toLong())
        }
    }
}
