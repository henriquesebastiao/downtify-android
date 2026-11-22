package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.henriquesebastiao.downtify.core.model.DjIds
import com.henriquesebastiao.downtify.core.model.DjReference

/** Logical render addresses survive MediaController IPC and private queue persistence. */
object DjMediaItems {
    fun uri(id: String, mode: String = "prepared"): Uri {
        DjIds.mediaId(id, mode)
        return Uri.Builder().scheme(MediaItems.SCHEME).authority("dj").appendPath(mode).appendPath(id).build()
    }
    fun addressOf(uri: Uri): DjReference? {
        if (uri.scheme != MediaItems.SCHEME || uri.authority != "dj") return null
        val parts = uri.pathSegments
        return when (parts.size) {
            1 -> parts[0].takeIf(DjIds::valid)?.let { DjReference(it, "prepared") }
            2 -> DjIds.referenceOf("dj:${parts[0]}:${parts[1]}")
            else -> null
        }
    }
    fun idOf(uri: Uri) = addressOf(uri)?.id
    fun mimeType(mode: String) = if (mode == "interactive") MimeTypes.APPLICATION_M3U8 else MimeTypes.AUDIO_MP4
    fun from(id: String, title: String, durationMs: Long, mode: String = "prepared"): MediaItem = MediaItem.Builder()
        .setMediaId(DjIds.mediaId(id, mode)).setUri(uri(id, mode)).setMimeType(mimeType(mode))
        .apply {
            if (mode == "interactive") {
                setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(24000).setMaxPlaybackSpeed(1f).build(),
                )
            }
        }
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist("Mix DJ local")
                .setDurationMs(durationMs).setIsPlayable(true).setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build(),
        ).build()
    fun of(item: MediaItem): PlayingDj? = DjIds.referenceOf(item.mediaId)?.let {
        val extras = item.mediaMetadata.extras
        PlayingDj(
            it.id, item.mediaMetadata.title?.toString().orEmpty(), it.mode,
            artist = item.mediaMetadata.artist?.toString() ?: "Mix DJ local",
            trackId = extras?.getString("djTrackId"),
            nextTitle = extras?.getString("djNextTitle").orEmpty(),
            nextArtist = extras?.getString("djNextArtist").orEmpty(),
            nextTrackId = extras?.getString("djNextId"),
            start = extras?.getLong("djStart") ?: 0,
            end = extras?.getLong("djEnd") ?: 0,
            nextStart = extras?.getLong("djNextStart", Long.MAX_VALUE) ?: Long.MAX_VALUE,
            origin = extras?.getLong("djOrigin") ?: 0,
        )
    }
}
