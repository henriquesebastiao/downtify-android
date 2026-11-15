package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** The platform session is shared with Bluetooth. It must never broadcast the full catalog. */
@UnstableApi
internal class LegacyQueuePolicy {
    private val configured = CompletableDeferred<Unit>()

    fun commands(session: MediaSession, controller: MediaSession.ControllerInfo): Player.Commands {
        val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        if (session.isMediaNotificationController(controller)) {
            commands.remove(Player.COMMAND_GET_TIMELINE).remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        }
        return commands.build()
    }

    fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (session.isMediaNotificationController(controller)) configured.complete(Unit)
    }

    // Restoration may remain suspended until configuration, and is cancelled with the service.
    suspend fun beforeRestore() = configured.await()

    // A controller request is bounded and fails closed if platform configuration never succeeds.
    suspend fun awaitReady() = withTimeout(5_000) { configured.await() }
}
