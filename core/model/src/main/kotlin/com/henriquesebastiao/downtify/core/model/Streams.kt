package com.henriquesebastiao.downtify.core.model

/**
 * A song that isn't in the library, played in full from the server's
 * stream cache (`GET /api/stream/file`), never downloaded as a file.
 * Media ids look like `stream:{videoId}` — anything keyed by library
 * track (likes, lyrics, offline copies, listens) must skip them, the
 * way it skips podcast episodes ([EpisodeIds]).
 */
object StreamIds {
    private const val PREFIX = "stream:"

    fun mediaId(videoId: String): String = PREFIX + videoId

    fun isStream(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(PREFIX)

    fun videoIdOf(mediaId: String?): String? =
        if (isStream(mediaId)) mediaId!!.removePrefix(PREFIX).takeIf { it.isNotBlank() } else null
}

/** Resolving a song to the YouTube video the server streams. */
object StreamVideo {
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun isVideoId(text: String): Boolean = VIDEO_ID.matches(text)

    /**
     * The video id to stream without asking the server: the song's own
     * when it carries one (`song_id` of a YouTube Music row), or the `v`
     * of a `music.youtube.com/watch` link it stands for. Null when only
     * a search by artist and title can find it.
     */
    fun videoIdOf(song: RemoteSong): String? {
        if (isVideoId(song.id)) return song.id
        val watch = song.url.takeIf { it.contains("watch") } ?: return null
        val query = watch.substringAfter("?", "")
        val v = query.split("&").firstOrNull { it.startsWith("v=") }?.removePrefix("v=").orEmpty()
        return v.takeIf(::isVideoId)
    }

    /** `/api/stream/file?video_id=…` on [baseUrl]: cached audio served by the server. */
    fun fileUrl(baseUrl: String, videoId: String): String =
        baseUrl.trimEnd('/') + "/api/stream/file?video_id=" + encodeSegment(videoId)

    private fun encodeSegment(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
