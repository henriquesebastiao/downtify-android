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
import kotlinx.coroutines.delay
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
) : ViewModel() {
    private val mutable = MutableStateFlow(MusicDjUiState())
    val state = mutable.asStateFlow()
    val capabilities = music.capabilities
    private val mutex = Mutex()
    private var pending: Pair<Triple<String, String, Int>, String>? = null

    init {
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
            music.capabilities.value.dj.interactiveReady
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

    fun stop(id: String) = action { music.djStop(id) }
    fun continuous(theme: String, style: String) = action {
        val owner = music.session.value
        val caps = music.capabilities.value
        if (!caps.enabled("dj_interactive") || !caps.dj.interactiveReady || !caps.dj.continuousSupported) return@action
        val created = music.djContinuous(theme, style, UUID.randomUUID().toString())
        mutable.update { it.copy(message = "Préparation du DJ continu ; votre écoute reste disponible.") }
        repeat(450) {
            delay(2000)
            if (owner != music.session.value) return@action
            if (!music.capabilities.value.enabled("dj_interactive")) return@action
            val session = music.djSession(created.id)
            if (session.state in setOf("failed", "stopped", "cancelled")) throw MusicAiFailure()
            if (session.state == "playing") {
                player.playDj(session.id, "DJ continu · $theme", 0, "interactive")
                mutable.update { it.copy(message = "DJ continu lancé dans le lecteur.") }
                return@action
            }
        }
        music.djStop(created.id)
        mutable.update { it.copy(message = "Préparation trop longue. Essayez une sélection plus petite.") }
    }

    fun command(session: MusicDjSessionDto, operation: String, payload: Map<String, String> = emptyMap()) = action {
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
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                mutex.withLock { work() }
                refresh()
            } catch (error: MusicAiFailure) {
                mutable.update {
                    it.copy(
                        message = when (error.status) {
                            409 -> "Le module DJ est désactivé."
                            429 -> "Deux sets sont déjà en attente."
                            503 -> "Le moteur DJ se prépare encore."
                            else -> "La demande n’a pas abouti. Vous pouvez réessayer."
                        },
                    )
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun play(session: MusicDjSessionDto) {
        if (session.state !in setOf("ready", "playing", "paused") ||
            !music.capabilities.value.enabled("dj_" + session.mode)
        ) {
            return
        }
        player.playDj(session.id, "Set DJ · ${session.theme}", (session.duration * 1000).toLong(), session.mode)
    }

    fun seek(session: MusicDjSessionDto, seconds: Double) {
        if (session.mode == "prepared" && player.state.value.dj?.id == session.id && seconds.isFinite()) {
            player.seekTo((seconds.coerceIn(0.0, session.duration) * 1000).toLong())
        }
    }
}
