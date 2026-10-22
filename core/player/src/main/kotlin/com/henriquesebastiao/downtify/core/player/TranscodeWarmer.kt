package com.henriquesebastiao.downtify.core.player

import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.StreamIds
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Has the server transcode the next track while this one plays. A transcoded stream
 * starts only once the server has the whole copy (seconds of ffmpeg for a song), so a
 * `HEAD` on the next track's address — which the server answers, like a `GET`, once
 * the copy is ready, and then keeps it — makes playing on or skipping to it start at
 * once. Waits for the current track to be ready, so its own transcode goes first; does
 * nothing for offline copies or the original quality. Silent on failure: that track
 * then waits as it always did.
 */
@OptIn(UnstableApi::class)
internal class TranscodeWarmer(
    private val player: Player,
    private val resolver: StreamResolver,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean = { true },
) : Player.Listener {
    /** Addresses already warmed (or being warmed) this session. */
    private val warmed = LruCache<String, Boolean>(WARMED_CACHE_SIZE)
    private val lock = Any()
    private var inFlight: Call? = null

    fun start() {
        player.addListener(this)
    }

    fun stop() {
        player.removeListener(this)
        synchronized(lock) { inFlight?.cancel() }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            warmNext()
        }
    }

    private fun warmNext() {
        if (!enabled()) {
            synchronized(lock) { inFlight?.cancel() }
            return
        }
        if (player.playbackState != Player.STATE_READY) return
        // The track that plays next in the real order: shuffle and repeat included.
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val trackId = player.getMediaItemAt(next).mediaId
        if (trackId.isEmpty() || EpisodeIds.isEpisode(trackId) || StreamIds.isStream(trackId)) return
        scope.launch(Dispatchers.IO) { warm(trackId) }
    }

    private fun warm(trackId: String) {
        val url = resolver.transcodedUrl(trackId) ?: return
        val call = synchronized(lock) {
            if (warmed[url] != null) return
            warmed.put(url, true)
            // Only the next track matters: one that is no longer next can wait.
            inFlight?.cancel()
            client.newCall(Request.Builder().url(url).head().build()).also { inFlight = it }
        }
        val started = System.nanoTime()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) warmed.remove(url)
                Log.d(TAG, "Warmed $trackId: ${response.code} in ${(System.nanoTime() - started) / NANOS_PER_MS} ms")
            }
        } catch (e: IOException) {
            warmed.remove(url)
            Log.d(TAG, "Couldn't warm $trackId", e)
        }
    }

    private companion object {
        const val TAG = "TranscodeWarmer"
        const val WARMED_CACHE_SIZE = 64
        const val NANOS_PER_MS = 1_000_000L
    }
}
