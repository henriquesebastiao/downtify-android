package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Publishes ready local images without delaying audio or accepting a late image for another item. */
internal class SessionArtworkTracker(
    private val player: Player,
    private val cache: SessionArtworkCache,
    private val sessions: SessionStore,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
) : Player.Listener {
    private data class Target(val index: Int, val id: String, val source: Uri, val owner: String)
    private var targets = emptyList<Target>()
    private var work: Job? = null
    private var association: Job? = null

    fun start() {
        player.addListener(this)
        association = scope.launch {
            combine(
                sessions.session.map {
                    it?.artworkOwner
                },
                network.status,
            ) { owner, status -> owner to status.online }
                .distinctUntilChanged().collect {
                    targets = emptyList()
                    update()
                }
        }
        update()
    }

    fun stop() {
        player.removeListener(this)
        work?.cancel()
        association?.cancel()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            update()
        }
    }

    private fun update() {
        val paired = sessions.current
        val next = if (paired == null) {
            emptyList()
        } else {
            PlayOrder.ahead(player, 4).mapNotNull { index ->
                val item = player.getMediaItemAt(index)
                val original = MediaItems.originalArtwork(item) ?: return@mapNotNull null
                val source = if (!StreamIds.isStream(item.mediaId) && !EpisodeIds.isEpisode(item.mediaId)) {
                    Uri.parse(ServerUrls.cover(paired.baseUrl, item.mediaId, ServerUrls.COVER_LARGE))
                } else {
                    original
                }
                Target(index, item.mediaId, source, paired.artworkOwner)
            }
        }
        if (next == targets) return
        targets = next
        work?.cancel()
        work = scope.launch {
            next.forEach { publish(it) }
        }
    }

    private suspend fun publish(target: Target) {
        val image = cache.prepare(target.id, target.source, network.status.value.online) ?: return
        coroutineContext.ensureActive()
        if (sessions.current?.artworkOwner != target.owner || target.index >= player.mediaItemCount) return
        val current = player.getMediaItemAt(target.index)
        if (current.mediaId == target.id && current.mediaMetadata.artworkUri != image) {
            player.replaceMediaItem(target.index, MediaItems.withArtwork(current, image))
        }
    }
}
