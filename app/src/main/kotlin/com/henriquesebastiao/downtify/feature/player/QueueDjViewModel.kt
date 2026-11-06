package com.henriquesebastiao.downtify.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator
import com.henriquesebastiao.downtify.core.player.LocalDjSettings
import com.henriquesebastiao.downtify.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val message = combine(coordinator.message, local.device) { text, device -> if (device) "" else text }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val busy = coordinator.busy
    val armed = combine(coordinator.armed, local.armed) { server, device -> server || device }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun chooseDevice(value: Boolean) {
        coordinator.stop()
        local.chooseDevice(value)
    }
    fun toggle() {
        if (!local.device.value) {
            coordinator.toggleQueue()
            return
        }
        if (local.armed.value) {
            local.setArmed(false)
            return
        }
        if (!capabilities.value.enabled("dj_interactive")) return
        val owner = music.session.value
        val selection = player.selectionVersion.value
        if (player.state.value.dj != null || coordinator.busy.value || coordinator.armed.value) coordinator.stop()
        viewModelScope.launch {
            val classic = withTimeoutOrNull(2000) { player.state.first { it.dj == null } } ?: return@launch
            if (owner != music.session.value || selection != player.selectionVersion.value) return@launch
            if (classic.track != null && classic.upNext.isNotEmpty()) local.setArmed(true)
        }
    }
}
