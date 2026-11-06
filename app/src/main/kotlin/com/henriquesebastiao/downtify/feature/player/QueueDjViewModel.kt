package com.henriquesebastiao.downtify.feature.player

import androidx.lifecycle.ViewModel
import com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Navigation owns the UI; the application-scoped coordinator owns preparation. */
@HiltViewModel
class QueueDjViewModel @Inject constructor(private val coordinator: DjPlaybackCoordinator) : ViewModel() {
    val capabilities = coordinator.capabilities
    val message = coordinator.message
    val busy = coordinator.busy
    val armed = coordinator.armed
    fun toggle() = coordinator.toggleQueue()
}
