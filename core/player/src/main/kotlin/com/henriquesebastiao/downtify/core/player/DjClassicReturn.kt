package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.model.DjIds

/** Exit runs against the full service queue and never replaces retained classic occurrences. */
@UnstableApi
internal object DjClassicReturn {
    const val ACTION = "com.henriquesebastiao.downtify.DJ_EXIT"
    private const val MEDIA_ID = "djExitMediaId"

    fun arguments(mediaId: String) = Bundle().apply { putString(MEDIA_ID, mediaId) }

    fun handle(
        player: Player,
        args: Bundle,
        baseUrl: String,
        libraryItem: (String) -> MediaItem? = { null },
    ): ListenableFuture<SessionResult> {
        val current = player.currentMediaItem
        if (current == null || current.mediaId != args.getString(MEDIA_ID) ||
            DjIds.referenceOf(current.mediaId) == null
        ) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        }
        val extras = current.mediaMetadata.extras
        val position = DjTimeline.position(player, extras?.getLong("djOrigin") ?: 0)
        val point = DjTimeline.resumePoint(current, position)
        val classic = (0 until player.mediaItemCount).filter {
            DjIds.referenceOf(player.getMediaItemAt(it).mediaId) == null
        }
        val wantsPlay = player.playWhenReady
        if (classic.isNotEmpty()) {
            returnToRetained(player, classic, extras, point)
        } else {
            returnToRendered(player, current, point, baseUrl, libraryItem)
        }
        if (player.mediaItemCount > 0) {
            player.setPlaybackSpeed(1f)
            player.prepare()
            player.playWhenReady = wantsPlay
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private fun returnToRetained(player: Player, classic: List<Int>, extras: Bundle?, point: DjResumePoint?) {
        val anchor = extras?.getInt("djQueueAnchor") ?: 0
        val preferred = anchor + (point?.occurrence ?: 0)
        val matches = classic.indices.filter { player.getMediaItemAt(classic[it]).mediaId == point?.id }
        val selected = if (point != null && preferred in matches) preferred else matches.firstOrNull()
        val fallback = (extras?.getInt("djClassicIndex") ?: 0).coerceIn(classic.indices)
        val index = classic[selected ?: fallback]
        val position = if (selected != null) point?.sourceMs ?: 0 else extras?.getLong("djClassicPosition") ?: 0
        when (player) {
            is DjSessionPlayer -> player.deck.seekToClassic(index, position)
            is LocalDjPlayer -> player.seekToClassic(index, position)
            else -> player.seekTo(index, position)
        }
        // Seeking can notify a tracker that already retires a DJ entry. Inspect fresh indices.
        for (row in player.mediaItemCount - 1 downTo 0) {
            if (DjIds.referenceOf(player.getMediaItemAt(row).mediaId) != null) player.removeMediaItem(row)
        }
    }

    private fun returnToRendered(
        player: Player,
        current: MediaItem,
        point: DjResumePoint?,
        baseUrl: String,
        libraryItem: (String) -> MediaItem?,
    ) {
        val rows = DjTimeline.resumeRows(current)
        val fallback = DjTimeline.fallbackItems(current, baseUrl)
        val items = rows.withIndex().mapNotNull { (ordinal, row) ->
            (libraryItem(row.id) ?: fallback[row.id])?.let { ordinal to it }
        }
        val occurrence = rows.indexOfFirst { row -> row.occurrence >= 0 && row.occurrence == point?.occurrence }
            .takeIf { it >= 0 } ?: point?.occurrence
        val selected = items.indexOfFirst { it.first == occurrence }
        if (selected >= 0) {
            player.setMediaItems(items.map { it.second }, selected, point?.sourceMs ?: 0)
        } else {
            player.pause()
            player.clearMediaItems()
        }
    }
}
