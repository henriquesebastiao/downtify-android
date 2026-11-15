package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailureKind
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjIds
import com.henriquesebastiao.downtify.core.model.DjReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A failed command never changes the selected transport or local play/pause intent. */
@UnstableApi
internal class DjNextControl(
    private val music: MusicAiRepository,
    private val scope: CoroutineScope,
    private val player: () -> Player?,
    private val report: (SessionError) -> Unit,
) {
    private val commands = Mutex()
    private var generation = 0L
    private var lastReference = DjIds.referenceOf(player()?.currentMediaItem?.mediaId)

    init {
        player()?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val next = DjIds.referenceOf(mediaItem?.mediaId)
                if (next != lastReference || reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    generation++
                }
                lastReference = next
            }
        })
    }

    fun request(reference: DjReference?): ListenableFuture<SessionResult> {
        val owner = music.session.value
        val mine = generation
        return scope.future {
            commands.withLock {
                fun current() = reference != null && owner == music.session.value &&
                    mine == generation && DjIds.referenceOf(player()?.currentMediaItem?.mediaId) == reference
                if (reference?.mode != "interactive" || !current()) {
                    return@withLock SessionResult(SessionError.ERROR_BAD_VALUE)
                }
                try {
                    val selected = checkNotNull(player())
                    val dj = checkNotNull(selected.currentMediaItem?.let(DjMediaItems::of))
                    val position = DjTimeline.position(selected, dj.origin) / 1000.0
                    val result = music.djCommand(reference.id, "next", navigationPayload(position))
                    if (!current()) return@withLock SessionResult(SessionError.ERROR_BAD_VALUE)
                    if (result.accepted) {
                        val target = result.seekToSeconds
                        if (target != null) {
                            val active = checkNotNull(player())
                            val absolute = DjTimeline.position(active, dj.origin)
                            val relative = (target * 1000).toLong() - absolute + active.currentPosition
                            if (!target.isFinite() || relative < 0) {
                                return@withLock failure(
                                    reference.id,
                                    "Le repère demandé est sorti de la fenêtre du direct.",
                                )
                            }
                            active.seekTo(relative)
                        }
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    } else {
                        failure(
                            reference.id,
                            result.reason.ifBlank { "Le serveur n’a pas accepté le changement de titre." },
                        )
                    }
                } catch (error: MusicAiFailure) {
                    if (!current()) return@withLock SessionResult(SessionError.ERROR_BAD_VALUE)
                    failure(reference.id, message(error.kind))
                }
            }
        }
    }

    fun unavailable(message: String) {
        val reference = DjIds.referenceOf(player()?.currentMediaItem?.mediaId) ?: return
        failure(reference.id, message)
    }

    private fun navigationPayload(position: Double) = if (music.capabilities.value.djNavigation) {
        mapOf("position" to position.toString())
    } else {
        emptyMap()
    }

    private fun failure(id: String, message: String): SessionResult {
        val error = SessionError(
            SessionError.ERROR_IO,
            message,
            Bundle().apply { putString(EXTRA_DJ_ID, id) },
        )
        report(error)
        return SessionResult(error)
    }

    private fun message(kind: MusicAiFailureKind): String = when (kind) {
        MusicAiFailureKind.Authentication -> "Connexion expirée. Reconnectez-vous pour commander le mix."

        MusicAiFailureKind.Forbidden -> "Le serveur refuse cette commande DJ pour ce compte."

        MusicAiFailureKind.Disabled -> "Le mix n’est plus actif ou le DJ est désactivé sur le serveur."

        MusicAiFailureKind.Unsupported -> "Cette version du serveur ne propose pas cette commande DJ."

        MusicAiFailureKind.Busy -> "Le serveur DJ est occupé. Réessayez dans un instant."

        MusicAiFailureKind.Timeout, MusicAiFailureKind.Network ->
            "La commande DJ n’a pas pu être confirmée. Vérifiez le réseau."

        MusicAiFailureKind.NotReady -> "Le serveur DJ n’est pas encore prêt. Réessayez dans un instant."

        MusicAiFailureKind.InvalidResponse -> "La réponse du serveur DJ est incompatible."

        MusicAiFailureKind.Other -> "Le changement de titre DJ a échoué. Réessayez."
    }

    companion object {
        const val EXTRA_DJ_ID = "djCommandId"
    }
}
