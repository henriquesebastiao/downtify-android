package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import com.henriquesebastiao.downtify.core.network.NetworkJson
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjMarkerDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlinx.serialization.builtins.ListSerializer

internal data class DjResumePoint(val id: String, val sourceMs: Long, val occurrence: Int)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object DjTimeline {
    /** A millisecond seek must reach the boundary, never land just before a fractional end. */
    fun boundaryMs(seconds: Double): Long = ceil(seconds * 1000).toLong()

    fun markerIndex(rows: List<MusicDjMarkerDto>, positionMs: Long): Int =
        rows.indexOfFirst { boundaryMs(it.start) <= positionMs && positionMs < boundaryMs(it.end) }

    fun position(player: Player, origin: Long): Long {
        if (origin <= 0 || player.currentTimeline.isEmpty) return player.currentPosition.coerceAtLeast(0)
        val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        return if (window.windowStartTimeMs != C.TIME_UNSET) {
            (window.windowStartTimeMs - origin + player.currentPosition).coerceAtLeast(0)
        } else {
            player.currentPosition.coerceAtLeast(0)
        }
    }

    fun metadata(item: MediaItem, session: MusicDjSessionDto, positionMs: Long, baseUrl: String): MediaItem {
        val index = markerIndex(session.timeline, positionMs)
        val row = session.timeline.getOrNull(index) ?: return item
        val track = session.tracks.firstOrNull { it.id == row.id } ?: return item
        val nextRow = session.timeline.getOrNull(index + 1)
        val next = session.tracks.firstOrNull { it.id == nextRow?.id }
        val plan = NetworkJson.encodeToString(ListSerializer(MusicDjMarkerDto.serializer()), session.timeline)
        val tracks = NetworkJson.encodeToString(ListSerializer(MusicAiTrackDto.serializer()), session.tracks)
        val previous = item.mediaMetadata
        if (sameMetadata(previous, session, track, row, plan, tracks)) return item
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            putString("djResumePlan", plan)
            putString("djResumeTracks", tracks)
            putBoolean("djQueueOrder", session.queueOrder)
            putString("djTrackId", track.id)
            putString("djNextTitle", next?.title)
            putString("djNextArtist", next?.artist)
            putString("djNextId", next?.id)
            putLong("djStart", boundaryMs(row.start))
            putLong("djEnd", boundaryMs(row.end))
            putLong("djNextStart", nextRow?.start?.let(::boundaryMs) ?: Long.MAX_VALUE)
            putLong("djOrigin", session.mediaOrigin?.times(1000)?.toLong() ?: 0)
        }
        return item.buildUpon().setMediaMetadata(
            item.mediaMetadata.buildUpon()
                .setTitle(track.title).setArtist(track.artist)
                .setArtworkUri(Uri.parse(ServerUrls.cover(baseUrl, track.id, ServerUrls.COVER_LARGE)))
                .setExtras(extras).build(),
        ).build()
    }

    private fun sameMetadata(
        previous: androidx.media3.common.MediaMetadata,
        session: MusicDjSessionDto,
        track: MusicAiTrackDto,
        row: MusicDjMarkerDto,
        plan: String,
        tracks: String,
    ): Boolean {
        val samePlan = previous.extras?.getString("djResumePlan") == plan &&
            previous.extras?.getString("djResumeTracks") == tracks
        val sameTrack = previous.extras?.getString("djTrackId") == track.id &&
            previous.title?.toString() == track.title && previous.artist?.toString() == track.artist
        val sameOrigin = previous.extras?.getLong("djOrigin") == (session.mediaOrigin?.times(1000)?.toLong() ?: 0)
        val sameOccurrence = previous.extras?.getLong("djStart") == boundaryMs(row.start) &&
            previous.extras?.getLong("djEnd") == boundaryMs(row.end)
        return samePlan && sameTrack && sameOrigin && sameOccurrence
    }

    fun resumeRows(item: MediaItem): List<MusicDjMarkerDto> = runCatching {
        NetworkJson.decodeFromString(
            ListSerializer(MusicDjMarkerDto.serializer()),
            item.mediaMetadata.extras?.getString("djResumePlan") ?: "[]",
        )
    }.getOrDefault(emptyList())

    fun fallbackItems(item: MediaItem, baseUrl: String): Map<String, MediaItem> = runCatching {
        NetworkJson.decodeFromString(
            ListSerializer(MusicAiTrackDto.serializer()),
            item.mediaMetadata.extras?.getString("djResumeTracks") ?: "[]",
        )
            .associate { track ->
                track.id to MediaItems.restored(
                    SavedPlaybackItem(
                        track.id,
                        track.title,
                        track.artist,
                        album = track.album,
                        durationMs = (track.duration * 1000).toLong(),
                        hasCover = true,
                    ),
                    baseUrl,
                )
            }
    }.getOrDefault(emptyMap())

    fun resumePoint(item: MediaItem, positionMs: Long): DjResumePoint? {
        val rows = resumeRows(item)
        if (rows.isEmpty()) return null
        val seconds = positionMs / 1000.0
        var index = markerIndex(rows, positionMs)
        if (index < 0) index = if (positionMs < boundaryMs(rows.first().start)) 0 else rows.lastIndex
        val row = rows[index]
        val next = rows.getOrNull(index + 1)
        val selected = if (next != null && row.end > next.start &&
            positionMs >= boundaryMs((next.start + row.end) / 2)
        ) {
            index++
            next
        } else {
            row
        }
        val source = (selected.cue + (seconds - selected.start).coerceAtLeast(0.0) * selected.speed).coerceAtLeast(0.0)
        return DjResumePoint(
            selected.id,
            (source * 1000).roundToLong(),
            selected.occurrence.takeIf {
                it >= 0
            } ?: index,
        )
    }

    fun shown(dj: PlayingDj, absolute: Long): PlayingDj = dj.copy(
        transition = if (dj.end > dj.nextStart && absolute in dj.nextStart until dj.end) {
            ((absolute - dj.nextStart).toFloat() / (dj.end - dj.nextStart)).coerceIn(0f, 1f)
        } else {
            null
        },
    )
}
