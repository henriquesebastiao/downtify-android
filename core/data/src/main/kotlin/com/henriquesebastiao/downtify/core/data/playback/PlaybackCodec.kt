package com.henriquesebastiao.downtify.core.data.playback

import com.henriquesebastiao.downtify.core.data.db.PlaybackStateEntity
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal object PlaybackCodec {
    fun encode(owner: String, state: PlaybackSnapshot): PlaybackStateEntity = PlaybackStateEntity(
        owner = owner,
        items = JsonArray(state.items.map(::encodeItem)).toString(),
        currentIndex = state.index,
        positionMs = state.positionMs,
        shuffle = state.shuffle,
        shuffleOrder = state.shuffleOrder.map(Int::toString),
        repeatMode = state.repeatMode,
        speed = state.speed,
        contextType = state.context?.type?.name,
        contextId = state.context?.refId,
        contextTitle = state.context?.title,
    )

    fun decode(row: PlaybackStateEntity): PlaybackSnapshot? = try {
        val items = (Json.parseToJsonElement(row.items) as? JsonArray)?.map { decodeItem(it.jsonObject) }
        items?.let {
            PlaybackSnapshot(
                items = it,
                index = row.currentIndex,
                positionMs = row.positionMs,
                shuffle = row.shuffle,
                shuffleOrder = row.shuffleOrder.mapNotNull(String::toIntOrNull),
                repeatMode = row.repeatMode,
                speed = row.speed,
                context = PlaybackContextType.entries.firstOrNull { type ->
                    type.name == row.contextType
                }?.let { type ->
                    PlaybackContext(type, row.contextId.orEmpty(), row.contextTitle.orEmpty())
                },
            ).validated()
        }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun encodeItem(item: SavedPlaybackItem): JsonObject = buildJsonObject {
        put("id", item.mediaId)
        put("title", item.title)
        put("artist", item.artist)
        put("album", item.album)
        put("duration", item.durationMs)
        put("cover", item.hasCover)
        put("queued", item.queued)
        item.videoId?.let { put("video", it) }
        item.rawSong?.let { put("raw", it) }
        item.episodeId?.let { put("episode", it) }
        item.showId?.let { put("show", it) }
        item.episodeFile?.let { put("file", it) }
        item.artworkUrl?.let { put("art", it) }
    }

    private fun decodeItem(json: JsonObject): SavedPlaybackItem = SavedPlaybackItem(
        mediaId = json.text("id").orEmpty(),
        title = json.text("title").orEmpty(),
        artist = json.text("artist").orEmpty(),
        album = json.text("album").orEmpty(),
        durationMs = json["duration"]?.jsonPrimitive?.longOrNull ?: 0,
        hasCover = json["cover"]?.jsonPrimitive?.booleanOrNull ?: false,
        queued = json["queued"]?.jsonPrimitive?.booleanOrNull ?: false,
        videoId = json.text("video"),
        rawSong = json.text("raw"),
        episodeId = json["episode"]?.jsonPrimitive?.longOrNull,
        showId = json["show"]?.jsonPrimitive?.longOrNull,
        episodeFile = json.text("file"),
        artworkUrl = json.text("art"),
    )

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
