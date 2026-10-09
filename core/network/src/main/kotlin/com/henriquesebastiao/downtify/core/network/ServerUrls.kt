package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.StreamPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality
import java.net.URLEncoder

/** Absolute URLs on a server for things loaded outside Retrofit (images, audio). */
object ServerUrls {
    const val COVER_SMALL = 150
    const val COVER_MEDIUM = 300
    const val COVER_LARGE = 600

    fun cover(baseUrl: String, trackId: String, size: Int?): String = baseUrl + StreamPolicy.coverPath(trackId, size)

    fun stream(baseUrl: String, trackId: String, quality: StreamQuality): String =
        baseUrl + StreamPolicy.streamPath(trackId, quality)

    /** `GET /api/stream/file`: audio the server cached for a video, with seeking. */
    fun streamFile(baseUrl: String, videoId: String): String =
        baseUrl.trimEnd('/') + "/api/stream/file?video_id=" + URLEncoder.encode(videoId, "UTF-8")

    fun playlistCover(baseUrl: String, path: String): String =
        "$baseUrl/playlist-cover?file=" + URLEncoder.encode(path, "UTF-8")

    fun webSocket(baseUrl: String, clientId: String): String =
        "$baseUrl/api/ws?client_id=" + URLEncoder.encode(clientId, "UTF-8")
}
