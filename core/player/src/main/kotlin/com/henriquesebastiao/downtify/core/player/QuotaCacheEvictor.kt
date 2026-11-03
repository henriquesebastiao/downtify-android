package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/** Dynamic quota under Cache's lock; prefer evicting history before the current/look-ahead entries. */
@UnstableApi
internal class QuotaCacheEvictor(private var limit: Long) : CacheEvictor {
    private val spans = TreeSet(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position })
    private var bytes = 0L
    private var priority = emptyList<String>()

    fun setLimit(cache: Cache, value: Long) = synchronized(cache) {
        limit = value
        evict(cache, 0)
    }

    fun prioritize(cache: Cache, keys: List<String>) = synchronized(cache) {
        priority = keys.distinct()
    }

    override fun requiresCacheSpanTouches(): Boolean = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (spans.add(span)) bytes += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) bytes -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, required: Long) {
        while (bytes + required > limit && spans.isNotEmpty()) {
            val cold = spans.firstOrNull { it.key !in priority }
            val candidate =
                cold
                    ?: spans.maxWithOrNull(
                        compareBy<CacheSpan> {
                            priority.indexOf(it.key)
                        }.thenByDescending { it.lastTouchTimestamp },
                    )!!
            cache.removeSpan(candidate)
        }
    }
}
