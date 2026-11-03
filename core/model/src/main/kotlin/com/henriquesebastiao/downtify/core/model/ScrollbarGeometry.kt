package com.henriquesebastiao.downtify.core.model

/**
 * Where a lazy list or grid stands, in lines (a list's items, a grid's rows) and pixels, as a
 * scrollbar needs it. Lines of a lazy layout aren't measured until they show, so [lineSize] is the
 * size of the ones on screen (spacing included) taken for all of them.
 *
 * [firstLineOffset] is where the first visible line starts in the viewport: 0 at the top, negative
 * once it's partly scrolled off. [before] and [after] are the content padding.
 */
data class ScrollMetrics(
    val firstLine: Int,
    val firstLineOffset: Int,
    val lineSize: Float,
    val lineCount: Int,
    val spacing: Int,
    val before: Int,
    val after: Int,
    val viewport: Int,
) {
    private val content: Float get() = before + lineCount * lineSize - spacing + after

    /** How far the content can scroll, in pixels. */
    val maxScroll: Float get() = (content - viewport).coerceAtLeast(0f)

    /** How far it has scrolled, in pixels. */
    val scrolled: Float get() = firstLine * lineSize - firstLineOffset

    /** The share of the content in view, 0–1: the thumb's length against the track's. */
    val visibleShare: Float get() = if (content <= 0f) 1f else (viewport / content).coerceIn(0f, 1f)

    /** How far down the content is, 0–1: the thumb's position. */
    val progress: Float get() = if (maxScroll == 0f) 0f else (scrolled / maxScroll).coerceIn(0f, 1f)

    /** The line to scroll to, and how far into it, to stand at [progress]. */
    fun target(progress: Float): ScrollTarget {
        if (lineSize <= 0f) return ScrollTarget(0, 0)
        val px = progress.coerceIn(0f, 1f) * maxScroll
        val line = (px / lineSize).toInt().coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        return ScrollTarget(line, (px - line * lineSize).toInt())
    }
}

/** A line of a lazy layout and the pixels to scroll into it. */
data class ScrollTarget(val line: Int, val offset: Int)

/** A scrollbar's thumb on a track [trackLength] long. */
data class ScrollThumb(val top: Float, val length: Float) {
    companion object {
        /**
         * The thumb for content [visibleShare] of which shows, [progress] of the way down; never
         * shorter than [minLength].
         */
        fun of(trackLength: Float, visibleShare: Float, progress: Float, minLength: Float): ScrollThumb {
            val length = (trackLength * visibleShare).coerceIn(minLength.coerceAtMost(trackLength), trackLength)
            return ScrollThumb((trackLength - length) * progress.coerceIn(0f, 1f), length)
        }

        /**
         * The progress that puts the middle of a thumb [thumbLength] long under [y]: where a drag
         * takes the content.
         */
        fun progressAt(y: Float, trackLength: Float, thumbLength: Float): Float {
            val room = trackLength - thumbLength
            return if (room <= 0f) 0f else ((y - thumbLength / 2) / room).coerceIn(0f, 1f)
        }
    }
}
