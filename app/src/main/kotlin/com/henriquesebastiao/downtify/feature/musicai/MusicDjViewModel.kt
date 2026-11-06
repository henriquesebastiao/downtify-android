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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MusicDjUiState(
    val sessions: List<MusicDjSessionDto> = emptyList(),
    val busy: Boolean = false,
    val message: String = "",
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
    private val mutex = Mutex()
    private var ownBusy = false
    private var pending: Pair<Triple<String, String, Int>, String>? = null

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
                mutable.value = MusicDjUiState()
                pending = null
            }
        }
        viewModelScope.launch {
            music.capabilities.collectLatest {
                mutable.update { state ->
                    state.copy(sessions = state.sessions.filter { row -> it.enabled("dj_" + row.mode) })
                }
            }
        }
    }

    suspend fun refresh() = mutex.withLock {
        if (!anyEnabled()) return@withLock
        try {
            val found = music.djSessions().filter {
                music.capabilities.value.enabled("dj_" + it.mode) &&
                    DjIds.valid(it.id)
            }
            if (anyEnabled()) mutable.update { it.copy(sessions = found) }
        } catch (_: MusicAiFailure) {
            mutable.update { it.copy(message = "Les sets sont temporairement indisponibles.") }
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
        ownBusy = true
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                mutex.withLock { work() }
                refresh()
            } catch (error: MusicAiFailure) {
                mutable.update {
                    it.copy(
                        message = musicAiFailureMessage(error),
                    )
                }
            } finally {
                ownBusy = false
                mutable.update { it.copy(busy = coordinator.busy.value) }
            }
        }
    }

    fun play(session: MusicDjSessionDto) {
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
                    coordinator.listen(full, "Set DJ · ${full.theme}")
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
