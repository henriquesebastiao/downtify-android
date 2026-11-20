package com.henriquesebastiao.downtify.ui.common

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.network.ServerUrls

/** Builds cover URLs on the paired server. Previews get one that returns null (placeholders). */
@Stable
class CoverUrls(private val baseUrl: String?) {
    /** `/api/v1/tracks/{id}/cover?size=` — 150 or 300 in lists, 600 or full (null) in Now Playing. */
    fun track(trackId: String?, size: Int? = ServerUrls.COVER_MEDIUM): String? =
        if (baseUrl == null || trackId == null) null else ServerUrls.cover(baseUrl, trackId, size)

    /** A playlist's own cover when the server has one, else [fallbackTrackId]'s. */
    fun playlist(playlist: Playlist, fallbackTrackId: String?, size: Int = ServerUrls.COVER_MEDIUM): String? = when {
        baseUrl == null -> null
        playlist.cover.isNotBlank() -> ServerUrls.playlistCover(baseUrl, playlist.cover)
        else -> track(fallbackTrackId, size)
    }

    /**
     * An artist's photo: the one saved for them on the server, else their Deezer picture. When
     * there's neither the request fails, so draw it with one of their covers as `fallbackUrl`.
     */
    fun artist(name: String?): String? =
        if (baseUrl == null || name.isNullOrBlank()) null else ServerUrls.artistPhoto(baseUrl, name)

    companion object {
        val None = CoverUrls(null)
    }
}

val LocalCoverUrls = staticCompositionLocalOf { CoverUrls.None }
