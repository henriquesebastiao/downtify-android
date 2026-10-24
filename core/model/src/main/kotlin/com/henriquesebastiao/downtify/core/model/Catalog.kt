package com.henriquesebastiao.downtify.core.model

import java.net.URI
import java.net.URISyntaxException

/** Where a song found through the server's search comes from (the badge in the results). */
enum class CatalogSource {
    Spotify,
    YouTube,

    /** The query itself, offered for Soulseek when YouTube Music has nothing (`text_search`). */
    TextSearch,
    Other,
    ;

    companion object {
        fun of(wire: String): CatalogSource = when (wire) {
            "spotify" -> Spotify
            "youtube", "youtube-music" -> YouTube
            "text_search" -> TextSearch
            else -> Other
        }
    }
}

/**
 * A song the server can download (a search or link result), not in the
 * library yet. [raw] is the server's own object: it goes back unchanged when
 * asking for the download, so fields the app doesn't know about survive.
 */
data class RemoteSong(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val coverUrl: String,
    val durationSeconds: Int,
    val url: String,
    val source: CatalogSource,
    /** A 30-second clip of its own (Spotify), else blank: ask the server's `/api/preview`. */
    val previewUrl: String,
    val year: String,
    val raw: String,
    /** Local file identity when the server returned a library-only result. */
    val libraryFile: String = "",
) {
    val artist: String get() = artists.joinToString(", ")
}

/** An album the server can download, from its album search or an artist link. */
data class RemoteAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val year: String,
    /** The link the server resolves to get its tracks. */
    val url: String,
    /** "Album", "Single", "EP" — as YouTube Music calls it; may be blank. */
    val releaseType: String,
)

enum class LinkKind { Track, Album, Playlist, Artist }

/** What a pasted Spotify or YouTube Music link points at (`GET /api/url/resolve`). */
data class ResolvedLink(
    val url: String,
    val kind: LinkKind,
    val name: String,
    val subtitle: String,
    val coverUrl: String,
    val year: String,
    val tracks: List<RemoteSong>,
    val albums: List<RemoteAlbum>,
)

/** Links the server can resolve: Spotify and YouTube (Music) tracks, albums, playlists and artists. */
object CatalogLinks {
    private val SPOTIFY_HOSTS = setOf("open.spotify.com", "play.spotify.com")
    private val YOUTUBE_HOSTS =
        setOf("music.youtube.com", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    /** Whether [text] (trimmed) is one link the server can resolve, rather than words to search. */
    fun isLink(text: String): Boolean {
        val uri = parse(text) ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        return when (host) {
            in SPOTIFY_HOSTS -> SPOTIFY_KINDS.any { path.contains("/$it/") }
            in YOUTUBE_HOSTS -> host == "youtu.be" || YOUTUBE_PATHS.any { path.startsWith(it) }
            else -> false
        }
    }

    /** A playlist link: its download is kept together as a playlist on the server. */
    fun isPlaylist(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.path.orEmpty().contains("/playlist")
    }

    private fun parse(text: String): URI? {
        val trimmed = text.trim()
        if (trimmed.contains(' ') || !(trimmed.startsWith("https://") || trimmed.startsWith("http://"))) return null
        return try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            null
        }
    }

    private val SPOTIFY_KINDS = listOf("track", "album", "playlist", "artist")
    private val YOUTUBE_PATHS = listOf("/watch", "/playlist", "/browse/", "/channel/", "/@")
}

enum class ServerJobStatus {
    Queued,
    Downloading,
    Done,
    Error,
    ;

    val isActive: Boolean get() = this == Queued || this == Downloading

    companion object {
        fun of(wire: String): ServerJobStatus = when (wire) {
            "downloading" -> Downloading
            "done" -> Done
            "error" -> Error
            else -> Queued
        }
    }
}

/** A song in the server's download queue (`GET /api/queue`), keyed by the song's id. */
data class ServerJob(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val status: ServerJobStatus,
    /** 0–100. */
    val progress: Float,
    val message: String,
    /** 1-based place among the waiting jobs, null once finished or when the server didn't send one. */
    val position: Int? = null,
)

/** "The server is downloading 13 songs · Harbor Nights · 4 of 9", from the queue. */
data class ServerQueueSummary(
    /** Queued or downloading. */
    val active: Int,
    /** The album (or single song) being downloaded now, if any. */
    val currentAlbum: String,
    val currentAlbumDone: Int,
    val currentAlbumTotal: Int,
) {
    companion object {
        fun of(jobs: List<ServerJob>): ServerQueueSummary {
            val active = jobs.count { it.status.isActive }
            val current = jobs.firstOrNull { it.status == ServerJobStatus.Downloading }
                ?: jobs.firstOrNull { it.status == ServerJobStatus.Queued }
            val album = current?.album.orEmpty()
            val sameAlbum = if (album.isBlank()) emptyList() else jobs.filter { it.album == album }
            return ServerQueueSummary(
                active = active,
                currentAlbum = album.ifBlank { current?.title.orEmpty() },
                currentAlbumDone = sameAlbum.count { it.status == ServerJobStatus.Done },
                currentAlbumTotal = sameAlbum.size,
            )
        }
    }
}

/** How far the server got with a set of songs (an album from the search). */
data class ServerDownloadProgress(val done: Int, val total: Int, val failed: Int) {
    val isActive: Boolean get() = total > 0 && done + failed < total

    companion object {
        /** Null when none of [songIds] was queued. */
        fun of(songIds: Collection<String>, jobs: Map<String, ServerJob>): ServerDownloadProgress? {
            val mine = songIds.mapNotNull { jobs[it] }
            if (mine.isEmpty()) return null
            return ServerDownloadProgress(
                done = mine.count { it.status == ServerJobStatus.Done },
                total = songIds.size,
                failed = mine.count { it.status == ServerJobStatus.Error },
            )
        }
    }
}
