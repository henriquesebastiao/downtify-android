package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.LinkKind
import com.henriquesebastiao.downtify.core.model.RemoteAlbum
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ResolvedLink
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The web API's song, album and queue objects. They come from several
 * providers (Spotify, YouTube Music, Soulseek) and aren't versioned like
 * `/api/v1`, so they're read field by field and leniently: a missing or
 * oddly-typed field becomes a default, never an error.
 */
object CatalogJson {

    fun songs(array: JsonArray): List<RemoteSong> = array.mapNotNull { (it as? JsonObject)?.let(::song) }

    fun song(obj: JsonObject): RemoteSong? {
        val url = obj.string("url")
        val id = obj.string("song_id").ifBlank { url }
        if (id.isBlank()) return null
        val artists = (obj["artists"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: obj.string("artist").split(", ").filter { it.isNotBlank() }
        return RemoteSong(
            id = id,
            title = obj.string("name").ifBlank { obj.string("title") },
            artists = artists,
            album = obj.string("album_name").ifBlank { obj.string("album") },
            coverUrl = obj.string("cover_url"),
            durationSeconds = obj.number("duration")?.toInt() ?: 0,
            url = url,
            source = CatalogSource.of(obj.string("source")),
            previewUrl = obj.string("preview_url"),
            year = obj.string("year"),
            raw = obj.toString(),
        )
    }

    fun albums(array: JsonArray): List<RemoteAlbum> = array.mapNotNull { (it as? JsonObject)?.let(::album) }

    fun album(obj: JsonObject): RemoteAlbum? {
        val url = obj.string("url")
        if (url.isBlank()) return null
        return RemoteAlbum(
            id = obj.string("album_id").ifBlank { obj.string("id") }.ifBlank { url },
            title = obj.string("name").ifBlank { obj.string("title") },
            artist = obj.string("artist").ifBlank {
                (obj["artists"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.joinToString(", ")
                    .orEmpty()
            },
            coverUrl = obj.string("cover_url"),
            year = obj.string("year"),
            url = url,
            releaseType = obj.string("release_type"),
        )
    }

    fun resolved(url: String, obj: JsonObject): ResolvedLink = ResolvedLink(
        url = url,
        kind = when (obj.string("kind")) {
            "album" -> LinkKind.Album
            "playlist" -> LinkKind.Playlist
            "artist" -> LinkKind.Artist
            else -> LinkKind.Track
        },
        name = obj.string("name"),
        subtitle = obj.string("subtitle"),
        coverUrl = obj.string("cover_url"),
        year = obj.string("year"),
        tracks = (obj["tracks"] as? JsonArray)?.let(::songs).orEmpty(),
        albums = (obj["albums"] as? JsonArray)?.let(::albums).orEmpty(),
    )

    /** `GET /api/similar/tracks`: downloadable rows under `tracks`. */
    fun similarTracks(obj: JsonObject): List<RemoteSong> = (obj["tracks"] as? JsonArray)?.let(::songs).orEmpty()

    fun jobs(array: JsonArray): List<ServerJob> = array.mapNotNull { (it as? JsonObject)?.let(::job) }

    /** A queue row, or a WebSocket progress message (the same fields, no `type`). */
    fun job(obj: JsonObject): ServerJob? {
        val song = obj["song"] as? JsonObject ?: return null
        val remote = song(song) ?: return null
        return ServerJob(
            songId = remote.id,
            title = remote.title,
            artist = remote.artist,
            album = remote.album,
            coverUrl = remote.coverUrl,
            status = ServerJobStatus.of(obj.string("status")),
            progress = (obj.number("progress") ?: 0.0).toFloat().coerceIn(0f, PERCENT),
            message = obj.string("message"),
        )
    }

    /** `POST /api/download/batch`: the songs as the server sent them. */
    fun batch(songs: List<RemoteSong>, playlistUrl: String?): JsonObject = buildJsonObject {
        put("songs", JsonArray(songs.map { NetworkJson.parseToJsonElement(it.raw) }))
        if (playlistUrl != null) put("playlist_url", playlistUrl)
        // A search result or an album isn't a playlist: no M3U for it.
        put("generate_m3u", playlistUrl != null)
    }

    private fun JsonObject.string(key: String): String = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull.orEmpty().let { if (it == "null") "" else it }
        else -> ""
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.let { p ->
        p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull()
    }

    private const val PERCENT = 100f
}
