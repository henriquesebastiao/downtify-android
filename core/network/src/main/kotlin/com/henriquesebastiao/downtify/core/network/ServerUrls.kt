package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.StreamPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality
import java.net.URLEncoder

/** Absolute URLs on a server for things loaded outside Retrofit (images, audio). */
object ServerUrls {
    fun djFile(baseUrl: String, id: String, mode: String = "prepared"): String {
        require(com.henriquesebastiao.downtify.core.model.DjIds.valid(id))
        require(mode in setOf("prepared", "interactive"))
        val resource = if (mode == "prepared") "media" else "hls/index.m3u8"
        return baseUrl.trimEnd('/') + "/api/v1/music-ai/dj/sessions/$id/$resource"
    }

    fun isDjHlsResource(baseUrl: String, url: String): Boolean = try {
        val base = java.net.URI(baseUrl)
        val target = java.net.URI(url)
        val path = base.rawPath.orEmpty().trimEnd('/') + "/api/v1/music-ai/dj/sessions/"
        val resource = target.rawPath.orEmpty().removePrefix(path)
        if (base.scheme != target.scheme || base.authority != target.authority) {
            false
        } else {
            val plain = target.rawQuery == null && target.rawFragment == null
            plain && target.rawPath.orEmpty().startsWith(path) &&
                Regex("[a-f0-9]{32}/hls/(index\\.m3u8|segment-[0-9]{8}\\.ts)").matches(resource)
        }
    } catch (_: java.net.URISyntaxException) {
        false
    }
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
