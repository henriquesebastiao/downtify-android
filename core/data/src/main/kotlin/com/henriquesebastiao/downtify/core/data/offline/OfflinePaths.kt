package com.henriquesebastiao.downtify.core.data.offline

import com.henriquesebastiao.downtify.core.model.Track
import java.security.MessageDigest

object OfflinePaths {
    const val MUSIC = "Music"
    const val PLAYLISTS = "Playlists"
    const val PARTS = ".parts"

    fun audio(track: Track): String {
        val artist = component(track.albumArtist.ifBlank { track.displayArtist }, "Unknown Artist")
        val album = component(track.album, "Unknown Album")
        val number = track.trackNumber.takeIf { it > 0 }?.let { "%02d - ".format(it) }.orEmpty()
        val title = component(track.displayTitle, "Unknown Track")
        val extension = track.file.substringAfterLast('.', "").lowercase()
            .takeIf { it.isNotEmpty() && it.length <= MAX_EXTENSION && it.all(Char::isLetterOrDigit) }
            ?: "audio"
        return "$MUSIC/$artist/$album/$number$title [${shortHash(track.id)}].$extension"
    }

    fun playlist(title: String, key: String): String =
        "$PLAYLISTS/${component(title, "Playlist")} [${shortHash(key)}].m3u"

    fun part(trackId: String): String = "$PARTS/${shortHash(trackId)}.part"

    private fun component(value: String, fallback: String): String {
        val safe = value.replace(INVALID_CHARACTERS, "_")
            .replace(WHITESPACE, " ")
            .replace(DOT_RUNS, "_")
            .trim(' ', '.')
            .ifBlank { fallback }
        return if (safe.length <= MAX_COMPONENT) safe else safe.take(MAX_COMPONENT).trimEnd(' ', '.')
    }

    private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(HASH_LENGTH)

    private val INVALID_CHARACTERS = Regex("[^\\p{L}\\p{N} ._-]")
    private val WHITESPACE = Regex("\\s+")
    private val DOT_RUNS = Regex("\\.{2,}")
    private const val MAX_COMPONENT = 80
    private const val MAX_EXTENSION = 5
    private const val HASH_LENGTH = 12
}
