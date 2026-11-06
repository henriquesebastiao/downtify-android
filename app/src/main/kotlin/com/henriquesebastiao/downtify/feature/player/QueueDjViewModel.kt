package com.henriquesebastiao.downtify.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.mediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QueueDjViewModel @Inject constructor(
    private val music: MusicAiRepository,
    private val player: PlayerController,
    private val library: LibraryRepository,
) : ViewModel() {
    val capabilities = music.capabilities
    private val messageState = MutableStateFlow("")
    val message = messageState.asStateFlow()
    private val busyState = MutableStateFlow(false)
    val busy = busyState.asStateFlow()
    private val armedState = MutableStateFlow(false)
    val armed = armedState.asStateFlow()
    private var pendingId: String? = null
        set(value) {
            field = value
            armedState.value = value != null
        }

    fun toggle() {
        val active = player.state.value.dj?.id ?: pendingId
        if (active != null) {
            viewModelScope.launch {
                try {
                    music.djStop(active)
                    player.exitDj(active)
                } catch (_: MusicAiFailure) {
                    messageState.value =
                        "Impossible d’arrêter le DJ pour le moment."
                }
                pendingId = null
                busyState.value = false
            }
            return
        }
        if (busyState.value) return
        val snapshot = player.state.value
        val defer = snapshot.isPlaying
        val ids = (if (defer) emptyList() else listOfNotNull(snapshot.track?.id)) + snapshot.upNext.map { it.trackId }
        if (ids.size !in 2..40 || ids.any { library.library.value?.byId?.containsKey(it) != true }) {
            messageState.value = "Choisissez de 2 à 40 titres locaux à suivre."
            return
        }
        prepare(ids, defer)
    }

    private fun prepare(ids: List<String>, defer: Boolean) {
        val owner = music.session.value
        val key = player.state.value.mediaKey
        busyState.value = true
        messageState.value = "Préparation des transitions ; votre écoute continue."
        viewModelScope.launch {
            try {
                val caps = music.capabilities.value
                if (!caps.enabled("dj_interactive") || !caps.dj.interactiveReady ||
                    !caps.dj.queueSupported
                ) {
                    return@launch
                }
                val created = music.djQueue(ids, UUID.randomUUID().toString())
                pendingId = created.id
                repeat(450) {
                    delay(2000)
                    if (pendingId != created.id) return@launch
                    if (music.session.value != owner || player.state.value.mediaKey != key) {
                        if (music.session.value == owner) music.djStop(created.id)
                        pendingId = null
                        messageState.value = "Le titre a changé avant le relais. La lecture classique continue."
                        return@launch
                    }
                    val session = music.djSession(created.id)
                    if (session.state in setOf("failed", "stopped", "cancelled")) throw MusicAiFailure()
                    if (session.state == "playing") music.djCommand(session.id, "pause")
                    if (session.state == "paused") {
                        if (defer) {
                            player.queueDj(session.id, "Mode DJ · votre file", (session.duration * 1000).toLong())
                            messageState.value = "Mode DJ prêt · relais au prochain titre."
                        } else {
                            music.djCommand(session.id, "resume")
                            player.playDj(
                                session.id,
                                "Mode DJ · votre file",
                                (session.duration * 1000).toLong(),
                                "interactive",
                            )
                            messageState.value = "Mode DJ actif."
                        }
                        return@launch
                    }
                }
                music.djStop(created.id)
                pendingId = null
                messageState.value = "La préparation a pris trop de temps. Votre file reste disponible."
            } catch (_: MusicAiFailure) {
                messageState.value = "Le mode DJ est indisponible. Votre file classique reste disponible."
                pendingId = null
            } finally {
                busyState.value = false
            }
        }
    }
}
