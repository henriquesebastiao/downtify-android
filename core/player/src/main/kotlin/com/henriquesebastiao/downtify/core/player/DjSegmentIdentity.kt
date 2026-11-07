package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import java.net.URI
import java.time.OffsetDateTime

/** Reused segment names are cacheable only with identities from a complete fresh manifest. */
internal object DjSegmentIdentity {
    fun parse(manifest: Uri, text: String): Map<String, String> {
        if (!text.startsWith("#EXTM3U") || text.length > 262144 || text.contains("#EXT-X-BYTERANGE")) return emptyMap()
        val cursor = Cursor(URI(manifest.toString()))
        text.lineSequence().map(String::trim).forEach(cursor::line)
        return cursor.identities.takeIf { it.size <= 512 }.orEmpty()
    }

    private class Cursor(private val base: URI) {
        val identities = mutableMapOf<String, String>()
        private var sequence = 0L
        private var time: Long? = null
        private var duration = 0L
        fun line(value: String) {
            when {
                value.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = value.substringAfter(':').toLongOrNull() ?: 0

                value.startsWith("#EXT-X-PROGRAM-DATE-TIME:") -> time = runCatching {
                    OffsetDateTime.parse(value.substringAfter(':')).toInstant().toEpochMilli()
                }.getOrNull()

                value.startsWith("#EXTINF:") ->
                    duration =
                        value.substringAfter(':').substringBefore(',').toDoubleOrNull()?.times(1000)?.toLong() ?: 0

                value.isNotEmpty() && !value.startsWith('#') -> segment(value)
            }
        }
        private fun segment(value: String) {
            val point = time
            val uri = runCatching { base.resolve(value) }.getOrNull()
            val clean = uri?.takeIf { it.rawQuery == null && it.fragment == null && it.userInfo == null }
            if (point != null && duration > 0 && clean != null) identities[clean.toString()] = "$point:$sequence"
            time = point?.plus(duration)
            sequence++
        }
    }
}
