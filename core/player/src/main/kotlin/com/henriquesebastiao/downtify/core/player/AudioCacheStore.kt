package com.henriquesebastiao.downtify.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Stable per-association and per-quality keys let cached audio survive URL/network changes. */
@UnstableApi
@Singleton
class AudioCacheStore @Inject constructor(
    @ApplicationContext context: Context,
    private val sessions: SessionStore,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val quota = QuotaCacheEvictor(ReadAheadPolicy().cacheBytes)
    val cache = SimpleCache(File(context.cacheDir, "media"), quota, StandaloneDatabaseProvider(context))

    @Volatile private var limit = ReadAheadPolicy().cacheBytes

    @Volatile private var protected = emptyList<String>()

    init {
        scope.launch(Dispatchers.IO) {
            settings.settings.map { it.readAhead.cacheBytes }.distinctUntilChanged().collect {
                limit = it
                quota.setLimit(cache, it)
            }
        }
    }

    fun key(mediaId: String, variant: String): String = prefix(mediaId) + variant
    fun uriKey(uri: String): String = key("url:$uri", "original")

    fun complete(key: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0 && cache.isCached(key, 0, length)
    }

    fun completedQuality(mediaId: String): StreamQuality? {
        val prefix = prefix(mediaId)
        return cache.keys.asSequence().filter { it.startsWith(prefix) && complete(it) }
            .mapNotNull { StreamQuality.decode(it.removePrefix(prefix)) }.firstOrNull()
    }

    fun prioritize(keys: List<String>) {
        protected = keys.distinct()
        quota.prioritize(cache, keys)
    }

    /** A preload must fit alongside higher-priority cached data, not evict the playing track. */
    fun fits(key: String, length: Long): Boolean {
        if (length < 0) return true
        val earlier = protected.takeWhile { it != key }.sumOf { cache.getCachedSpans(it).sumOf { span -> span.length } }
        return length <= limit && earlier <= limit - length
    }

    /** Do not wait behind a foreground stream's write lock: prepare its following songs first. */
    fun canWrite(key: String): Boolean {
        var position = 0L
        while (true) {
            val available = cache.getCachedLength(key, position, Long.MAX_VALUE - position)
            if (available > 0) {
                position += available
                if (position == Long.MAX_VALUE) return false
            } else {
                val span = cache.startReadWriteNonBlocking(key, position, C.LENGTH_UNSET.toLong()) ?: return false
                if (span.isHoleSpan) cache.releaseHoleSpan(span)
                return span.isHoleSpan
            }
        }
    }

    private fun prefix(mediaId: String): String {
        val paired = sessions.current
        val owner = paired?.let { "${it.serverId.ifBlank { it.baseUrl }}\u0000${it.deviceId}" }.orEmpty()
        return "audio-v1:${digest(owner)}:${digest(mediaId)}:"
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
