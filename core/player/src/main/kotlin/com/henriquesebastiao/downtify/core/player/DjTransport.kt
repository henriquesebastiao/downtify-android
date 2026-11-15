package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.model.DjIds

/** Keep the app controller from masking a DJ marker seek as a classic queue selection. */
@UnstableApi
internal object DjTransport {
    const val ACTION = "com.henriquesebastiao.downtify.DJ_TRANSPORT"
    private const val MEDIA_ID = "djMediaId"
    private const val DIRECTION = "djDirection"

    fun navigate(controller: MediaController, previous: Boolean) {
        val id = controller.currentMediaItem?.mediaId
        if (DjIds.referenceOf(id) == null) {
            if (previous) controller.seekToPrevious() else controller.seekToNext()
            return
        }
        controller.sendCustomCommand(
            SessionCommand(ACTION, Bundle.EMPTY),
            Bundle().apply {
                putString(MEDIA_ID, id)
                putString(DIRECTION, if (previous) "previous" else "next")
            },
        )
    }

    fun handle(player: Player, args: Bundle): ListenableFuture<SessionResult> {
        val id = player.currentMediaItem?.mediaId
        if (id != args.getString(MEDIA_ID) || DjIds.referenceOf(id) == null) return rejected()
        val deck = when (player) {
            is DjSessionPlayer -> player.deck
            is LocalDjPlayer -> player
            else -> return rejected()
        }
        val handled = when (args.getString(DIRECTION)) {
            "next" -> deck.nextInsideDj()
            "previous" -> deck.previousInsideDj()
            else -> return rejected()
        }
        return if (handled == null) rejected() else Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private fun rejected() = Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
}
