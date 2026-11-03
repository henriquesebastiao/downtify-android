package com.henriquesebastiao.downtify.core.model

import java.util.Locale
import kotlin.math.abs

/** A track's lyrics: time-synced lines when an LRC is available, else plain text. */
data class Lyrics(val synced: List<LyricLine>, val plain: String) {
    val isEmpty: Boolean get() = synced.isEmpty() && plain.isBlank()
    val isSynced: Boolean get() = synced.isNotEmpty()

    /** The words without their times: the plain text, or the synced lines when there is none. */
    val text: String get() = plain.ifBlank { synced.joinToString("\n") { it.text } }.trim()

    companion object {
        val Empty = Lyrics(emptyList(), "")
    }
}

data class LyricLine(val timeMs: Long, val text: String)

/**
 * Moving synced lyrics against the audio, for an LRC timed to another cut of the song (a live
 * take, a video's longer intro): with a positive offset every line comes that much later.
 */
object LyricsTiming {
    /** One press of the earlier / later buttons. */
    const val STEP_MS = 250L

    /** As far as the lyrics move either way. */
    const val MAX_MS = 15_000L

    fun clamp(offsetMs: Long): Long = offsetMs.coerceIn(-MAX_MS, MAX_MS)

    /** [lines] [offsetMs] later (earlier when negative). */
    fun shift(lines: List<LyricLine>, offsetMs: Long): List<LyricLine> =
        if (offsetMs == 0L) lines else lines.map { it.copy(timeMs = it.timeMs + offsetMs) }

    /** The offset in seconds, signed, with two decimals written [locale]'s way: "+0,25", "-1,50", "0,00". */
    fun seconds(offsetMs: Long, locale: Locale): String {
        val sign = when {
            offsetMs > 0 -> "+"
            offsetMs < 0 -> "-"
            else -> ""
        }
        return sign + String.format(locale, "%.2f", abs(offsetMs) / MS_PER_SECOND)
    }

    private const val MS_PER_SECOND = 1000.0
}

/** Parses LRC text: `[mm:ss.xx] line`, several stamps per line, and an `[offset:±ms]` tag. */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        var offset = 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            offsetTag.find(line)?.let { offset = it.groupValues[1].toLong() }
            val stamps = mutableListOf<Long>()
            var rest = line
            while (true) {
                val match = stamp.matchAt(rest, 0) ?: break
                stamps += toMillis(match)
                rest = rest.substring(match.range.last + 1)
            }
            if (stamps.isEmpty()) continue
            val text = rest.trim()
            // The LRC offset shifts lyrics later when positive: subtract it from the stamp.
            stamps.forEach { lines += LyricLine((it - offset).coerceAtLeast(0), text) }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** The index of the line being sung at [positionMs], or -1 before the first. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    /** How long after [positionMs] the next line starts, or null when the one being sung is the last. */
    fun nextLineInMs(lines: List<LyricLine>, positionMs: Long): Long? =
        lines.getOrNull(activeIndex(lines, positionMs) + 1)?.let { it.timeMs - positionMs }

    private fun toMillis(match: MatchResult): Long {
        val (min, sec, frac) = match.destructured
        val fraction = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fraction
    }
}
