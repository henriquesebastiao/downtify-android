package com.henriquesebastiao.downtify.core.data.offline

import com.henriquesebastiao.downtify.core.model.Track
import kotlin.math.roundToInt

object PlaylistExporter {
    fun render(title: String, tracks: List<Pair<Track, String>>): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#PLAYLIST:${oneLine(title)}")
        tracks.forEach { (track, path) ->
            val artist = oneLine(track.displayArtist).ifBlank { "Unknown Artist" }
            appendLine("#EXTINF:${track.duration.roundToInt()},$artist - ${oneLine(track.displayTitle)}")
            appendLine("../$path")
        }
    }

    private fun oneLine(value: String): String = value.replace(Regex("[\\r\\n]+"), " ")
}
