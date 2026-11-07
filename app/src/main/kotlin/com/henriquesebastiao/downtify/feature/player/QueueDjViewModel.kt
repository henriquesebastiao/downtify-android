package com.henriquesebastiao.downtify.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator
import com.henriquesebastiao.downtify.core.player.LocalDjSettings
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class QueueDjAvailability(val ready: Boolean = false, val reason: String = "")

internal fun queueDjAvailability(
    caps: MusicAiCapabilitiesDto,
    device: Boolean,
    state: PlayerState,
    signedIn: Boolean,
): QueueDjAvailability {
    val reason = when {
        !signedIn -> "Connectez-vous à un serveur pour activer le DJ."
        caps.version != 1 -> "Vérifiez la disponibilité du DJ sur ce serveur."
        !caps.enabled("dj_interactive") -> "Activez le DJ direct dans Paramètres → IA."
        !device && !caps.interactiveAvailable() -> "Le moteur DJ serveur n’est pas prêt ou autorisé en essai bêta."
        !device && !caps.dj.queueSupported -> "Ce serveur ne prend pas encore en charge le DJ depuis la file."
        device && state.shuffle -> "Désactivez la lecture aléatoire pour le DJ sur cet appareil."
        device && state.repeat == RepeatMode.One -> "Désactivez la répétition du titre pour le DJ sur cet appareil."
        device && state.track == null -> "Lancez un titre de la bibliothèque pour le DJ sur cet appareil."
        device && state.upNext.isEmpty() -> "Ajoutez au moins un titre local à suivre pour le fondu sur cet appareil."
        !device && state.upNext.size < 2 -> "Ajoutez au moins deux titres locaux à suivre pour le DJ serveur."
        else -> ""
    }
    return QueueDjAvailability(reason.isEmpty(), reason)
}

/** Navigation owns the UI; the application-scoped coordinator owns preparation. */
@HiltViewModel
class QueueDjViewModel @Inject constructor(
    private val coordinator: DjPlaybackCoordinator,
    private val local: LocalDjSettings,
    private val player: PlayerController,
    private val music: MusicAiRepository,
) : ViewModel() {
    val capabilities = coordinator.capabilities
    val localDevice = local.device
    val localTransition = local.transition
    val phase = coordinator.phase
    private val feedback = MutableStateFlow("")
    private val mutableChecking = MutableStateFlow(false)
    val checking = mutableChecking.asStateFlow()
    val message = combine(coordinator.message, feedback) { server, device ->
        device.ifBlank { server }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val busy = coordinator.busy
    val armed = combine(coordinator.armed, local.armed) { server, device -> server || device }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val availability = combine(capabilities, local.device, player.state, music.session) { caps, device, state, owner ->
        queueDjAvailability(caps, device, state, owner != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, currentAvailability())
    private var operation: Job? = null
    private var generation = 0L

    fun onQueueShown() {
        if (!checking.value) check(activate = false)
    }

    fun chooseDevice(value: Boolean) {
        cancelCheck("Moteur DJ changé. Vérifiez la disponibilité avant de l’activer.")
        coordinator.stop()
        local.chooseDevice(value)
    }

    fun toggle() {
        when {
            local.armed.value -> {
                cancelCheck("")
                local.setArmed(false)
                feedback.value = "DJ sur cet appareil désactivé."
            }

            !local.device.value && serverActive() -> {
                cancelCheck("")
                coordinator.stop()
            }

            checking.value -> cancelCheck("Vérification DJ annulée.")

            else -> check(activate = true)
        }
    }

    private fun serverActive() = coordinator.busy.value || coordinator.armed.value || player.state.value.dj != null

    private fun currentAvailability() = queueDjAvailability(
        capabilities.value,
        local.device.value,
        player.state.value,
        music.session.value != null,
    )

    private fun cancelCheck(reason: String) {
        generation++
        operation?.cancel()
        operation = null
        mutableChecking.value = false
        feedback.value = reason
    }

    private fun check(activate: Boolean) {
        val token = Check(++generation, music.session.value, player.selectionVersion.value, local.device.value)
        mutableChecking.value = true
        feedback.value = "Vérification de la disponibilité du DJ…"
        operation = viewModelScope.launch {
            try {
                if (!valid(token)) return@launch
                val refreshed = withTimeoutOrNull(10_000) { music.refresh(force = true) }
                if (!valid(token)) return@launch
                val failure = refreshFailure(refreshed)
                if (failure != null) {
                    feedback.value = failure
                } else if (activate) {
                    activate(token)
                } else {
                    feedback.value = currentAvailability().reason
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (valid(token)) {
                    feedback.value =
                        "Impossible de vérifier le DJ. Réessayez lorsque le serveur est joignable."
                }
            } finally {
                if (token.generation == generation) mutableChecking.value = false
            }
        }
    }

    private suspend fun activate(token: Check) {
        if (token.device && serverActive()) {
            feedback.value = "Retour au lecteur classique avant activation du DJ sur cet appareil…"
            coordinator.stop()
            val classic = withTimeoutOrNull(2000) { player.state.first { it.dj == null } }
            if (!valid(token)) return
            if (classic == null) {
                feedback.value = "Le retour au lecteur classique tarde. Réessayez après la fermeture du mix serveur."
                return
            }
        }
        val available = currentAvailability()
        feedback.value = available.reason
        if (!available.ready) return
        if (token.device) {
            local.setArmed(true)
            feedback.value = "DJ sur cet appareil activé · fondu à la fin du titre en cours."
        } else {
            coordinator.toggleQueue()
        }
    }

    private fun valid(token: Check): Boolean {
        if (token.generation != generation) return false
        val reason = when {
            token.owner == null -> "Connectez-vous à un serveur pour activer le DJ."
            token.owner != music.session.value -> "Compte ou serveur changé : activation DJ annulée."
            token.device != local.device.value -> "Moteur DJ changé : activation annulée."
            token.selection != player.selectionVersion.value -> "Sélection musicale changée : activation DJ annulée."
            else -> null
        }
        if (reason != null) feedback.value = reason
        return reason == null
    }

    private fun refreshFailure(result: ServerResult<Unit>?): String? = when (result) {
        is ServerResult.Ok -> null

        is ServerResult.Failed -> when (result.status) {
            401 -> "Session expirée. Reconnectez-vous pour vérifier le DJ."
            403 -> "Ce compte n’est pas autorisé à utiliser le DJ."
            503 -> "Le serveur DJ est temporairement indisponible. Réessayez."
            else -> "Le serveur a refusé la vérification du DJ. Réessayez."
        }

        ServerResult.Unreachable -> "Serveur injoignable. Vérifiez la connexion puis réessayez."

        null -> "La vérification du DJ a dépassé le délai. Réessayez."
    }

    private data class Check(val generation: Long, val owner: Session?, val selection: Long, val device: Boolean)
}
