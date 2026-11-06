package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object DjTimeline {
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
        val seconds = positionMs / 1000.0
        val index = session.timeline.indexOfFirst { it.start <= seconds && seconds < it.end }
        val row = session.timeline.getOrNull(index) ?: return item
        val track = session.tracks.firstOrNull { it.id == row.id } ?: return item
        val nextRow = session.timeline.getOrNull(index + 1)
        val next = session.tracks.firstOrNull { it.id == nextRow?.id }
        val extras = Bundle().apply {
            putString("djTrackId", track.id)
            putString("djNextTitle", next?.title)
            putString("djNextArtist", next?.artist)
            putString("djNextId", next?.id)
            putLong("djStart", (row.start * 1000).toLong())
            putLong("djEnd", (row.end * 1000).toLong())
            putLong("djNextStart", nextRow?.start?.times(1000)?.toLong() ?: Long.MAX_VALUE)
            putLong("djOrigin", session.mediaOrigin?.times(1000)?.toLong() ?: 0)
        }
        return item.buildUpon().setMediaMetadata(
            item.mediaMetadata.buildUpon()
                .setTitle(track.title).setArtist(track.artist)
                .setArtworkUri(Uri.parse(ServerUrls.cover(baseUrl, track.id, ServerUrls.COVER_LARGE)))
                .setExtras(extras).build(),
        ).build()
    }

    fun shown(dj: PlayingDj, absolute: Long): PlayingDj = dj.copy(
        transition = if (dj.end > dj.nextStart && absolute in dj.nextStart until dj.end) {
            ((absolute - dj.nextStart).toFloat() / (dj.end - dj.nextStart)).coerceIn(0f, 1f)
        } else {
            null
        },
    )
}
