package com.henriquesebastiao.downtify.core.network.dto

import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.LibraryPage
import com.henriquesebastiao.downtify.core.model.LrcParser
import com.henriquesebastiao.downtify.core.model.Lyrics
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.ServerInfo
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.Transcoding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes of the mobile client contract (api_version 1). Unknown fields are
// ignored and missing ones take the defaults below, so additions on the server
// side never break the app.

@Serializable
data class ServerInfoDto(
    @SerialName("server_id") val serverId: String = "",
    val name: String = "",
    val product: String = "",
    val version: String = "",
    @SerialName("api_version") val apiVersion: Int = 0,
    @SerialName("require_sign_in") val requireSignIn: Boolean = false,
    val capabilities: CapabilitiesDto = CapabilitiesDto(),
) {
    fun toModel() = ServerInfo(serverId, name, product, version, apiVersion, requireSignIn, capabilities.toModel())

    companion object {
        fun from(info: ServerInfo) = ServerInfoDto(
            serverId = info.serverId,
            name = info.name,
            product = info.product,
            version = info.version,
            apiVersion = info.apiVersion,
            requireSignIn = info.requireSignIn,
            capabilities = info.capabilities.let { c ->
                CapabilitiesDto(
                    transcoding = TranscodingDto(
                        c.transcoding.available,
                        c.transcoding.formats,
                        c.transcoding.bitrates,
                    ),
                    signedUrls = c.signedUrls,
                    pairing = c.pairing,
                    podcasts = c.podcasts,
                    discover = c.discover,
                    lyrics = c.lyrics,
                    librarySync = c.librarySync,
                )
            },
        )
    }
}

@Serializable
data class CapabilitiesDto(
    val transcoding: TranscodingDto = TranscodingDto(),
    @SerialName("signed_urls") val signedUrls: Boolean = false,
    val pairing: Boolean = false,
    val podcasts: Boolean = false,
    val discover: Boolean = false,
    val lyrics: Boolean = false,
    @SerialName("library_sync") val librarySync: Boolean = false,
) {
    fun toModel() = Capabilities(transcoding.toModel(), signedUrls, pairing, podcasts, discover, lyrics, librarySync)
}

@Serializable
data class TranscodingDto(
    val available: Boolean = false,
    val formats: List<String> = emptyList(),
    val bitrates: List<Int> = emptyList(),
) {
    fun toModel() = Transcoding(available, formats, bitrates)
}

@Serializable
data class PairRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
)

@Serializable
data class PairResponse(
    val token: String,
    val device: DeviceDto,
    val server: PairedServerDto = PairedServerDto(),
    /** Who showed the code (servers with accounts, 3.2+); the device belongs to them. */
    val user: UserDto? = null,
)

@Serializable
data class UserDto(val username: String = "", val role: String = "")

/** `POST /api/stream/prefetch`: warm the server's stream cache for a video. */
@Serializable
data class PrefetchRequestDto(@SerialName("video_id") val videoId: String)

/** `GET /api/me`. */
@Serializable
data class MeResponse(val user: UserDto? = null)

/** `POST /api/activity/playback`: what this player is doing, for the admins' Activity page. */
@Serializable
data class PlaybackActivityRequest(
    val player: String,
    val state: String,
    val track: ActivityTrackDto = ActivityTrackDto(),
    val position: Int = 0,
)

/** Empty (`{}`) with `stopped`. */
@Serializable
data class ActivityTrackDto(
    @SerialName("track_id") val trackId: String? = null,
    /** A podcast episode's library path, for what has no track id. */
    val file: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int? = null,
)

@Serializable
data class DeviceDto(val id: String = "", val name: String = "")

@Serializable
data class PairedServerDto(@SerialName("server_id") val serverId: String = "", val name: String = "")

@Serializable
data class AuthStatusDto(
    @SerialName("require_sign_in") val requireSignIn: Boolean = false,
    @SerialName("signed_in") val signedIn: Boolean = false,
    val via: String? = null,
    val device: DeviceDto? = null,
)

@Serializable
data class LibraryPageDto(
    val cursor: Long = 0,
    val full: Boolean = false,
    val tracks: List<TrackDto> = emptyList(),
    val deleted: List<String> = emptyList(),
) {
    fun toModel() = LibraryPage(cursor, full, tracks.map { it.toModel() }, deleted)
}

@Serializable
data class TrackDto(
    val id: String,
    val file: String = "",
    val title: String = "",
    val artist: String = "",
    val artists: List<String> = emptyList(),
    val album: String = "",
    @SerialName("album_artist") val albumArtist: String = "",
    @SerialName("album_id") val albumId: String = "",
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("track_number") val trackNumber: Int = 0,
    val year: String = "",
    val duration: Double = 0.0,
    val codec: String = "",
    val bitrate: Int = 0,
    @SerialName("sample_rate") val sampleRate: Int = 0,
    val channels: Int = 0,
    val size: Long = 0,
    val added: Long = 0,
    @SerialName("has_cover") val hasCover: Boolean = false,
    val playlists: List<String> = emptyList(),
) {
    fun toModel() = Track(
        id = id,
        file = file,
        title = title,
        artist = artist,
        artists = artists,
        album = album,
        albumArtist = albumArtist,
        albumId = albumId,
        artistId = artistId,
        trackNumber = trackNumber,
        year = year,
        duration = duration,
        codec = codec,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        size = size,
        added = added,
        hasCover = hasCover,
        playlists = playlists,
    )
}

@Serializable
data class PlaylistDto(
    val name: String,
    val liked: Boolean = false,
    val count: Int = 0,
    val cover: String = "",
    @SerialName("track_ids") val trackIds: List<String> = emptyList(),
) {
    fun toModel() = Playlist(name, liked, cover, trackIds)
}

@Serializable
data class LikesDto(@SerialName("track_ids") val trackIds: List<String> = emptyList())

@Serializable
data class LikeRequest(@SerialName("track_id") val trackId: String, val liked: Boolean)

@Serializable
data class LikeResponse(@SerialName("track_id") val trackId: String = "", val liked: Boolean = false)

@Serializable
data class LyricsDto(val synced: String = "", val plain: String = "") {
    fun toModel() = Lyrics(if (synced.isBlank()) emptyList() else LrcParser.parse(synced), plain)
}

@Serializable
data class ListenRequest(
    @SerialName("track_id") val trackId: String,
    @SerialName("play_id") val playId: String,
    /** ISO 8601, UTC. */
    @SerialName("played_at") val playedAt: String,
)
