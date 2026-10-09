package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import android.util.LruCache
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.offline.OfflineRepository
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.model.StreamPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.model.Transcoding
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Turns `downtify://track/{id}` into what the player reads at the moment it
 * opens it: the offline copy when the phone has one, else the server's stream
 * URL — the original on Wi-Fi, the mobile-data quality on a metered network
 * (when the server can transcode) — as set in Settings.
 *
 * The quality is chosen when a track starts from the beginning and kept for
 * that track's later range requests (seeks), so a network change mid-song
 * never mixes byte ranges of two different files.
 */
@UnstableApi
@Singleton
class StreamResolver @Inject constructor(
    private val sessions: SessionStore,
    private val server: ServerRepository,
    private val network: NetworkMonitor,
    private val offline: OfflineRepository,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : ResolvingDataSource.Resolver {

    private val settings = settings.settings.stateIn(scope, SharingStarted.Eagerly, UserSettings())
    private val chosen = LruCache<String, StreamQuality>(CHOSEN_CACHE_SIZE)
    private val local = LruCache<String, Boolean>(CHOSEN_CACHE_SIZE)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        // A song that isn't in the library: the server's stream cache has
        // the audio (or downloads it now), with seeking, under the token.
        MediaItems.streamVideoIdOf(dataSpec.uri)?.let { videoId ->
            val session = sessions.current ?: throw IOException("Not paired with a server")
            return dataSpec.withUri(Uri.parse(ServerUrls.streamFile(session.baseUrl, videoId)))
        }
        val trackId = MediaItems.trackIdOf(dataSpec.uri) ?: return dataSpec
        offline.localFile(trackId)?.let { file ->
            chosen.put(trackId, StreamQuality.Original)
            local.put(trackId, true)
            return dataSpec.withUri(Uri.fromFile(file))
        }
        local.remove(trackId)
        val session = sessions.current ?: throw IOException("Not paired with a server")
        val quality = if (dataSpec.position == 0L || chosen[trackId] == null) {
            qualityNow().also { chosen.put(trackId, it) }
        } else {
            chosen[trackId]!!
        }
        return dataSpec.withUri(Uri.parse(ServerUrls.stream(session.baseUrl, trackId, quality)))
    }

    /** What a stream started now would use (shown in Now Playing). */
    fun qualityNow(): StreamQuality {
        val s = settings.value
        val transcoding = server.serverInfo.value?.capabilities?.transcoding ?: Transcoding()
        return StreamPolicy.select(s.wifiQuality, s.mobileQuality, network.isMetered, transcoding)
    }

    /** The quality [trackId] is streaming in, when it has started. */
    fun qualityOf(trackId: String): StreamQuality? = chosen[trackId]

    /** Whether [trackId] is playing from its offline copy. */
    fun isLocal(trackId: String): Boolean = local[trackId] == true

    private companion object {
        const val CHOSEN_CACHE_SIZE = 64
    }
}
