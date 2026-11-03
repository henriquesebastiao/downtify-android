package com.henriquesebastiao.downtify.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.henriquesebastiao.downtify.core.model.OfflineCollection
import com.henriquesebastiao.downtify.core.model.OfflineFile
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.model.Track

@Entity(
    tableName = "tracks",
    indices = [Index("albumId"), Index("artistId"), Index("added")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val file: String,
    val title: String,
    val artist: String,
    val artists: List<String>,
    val album: String,
    val albumArtist: String,
    val albumId: String,
    val artistId: String,
    val trackNumber: Int,
    val year: String,
    val duration: Double,
    val codec: String,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val size: Long,
    val added: Long,
    val hasCover: Boolean,
    val playlists: List<String>,
) {
    fun toModel() = Track(
        id, file, title, artist, artists, album, albumArtist, albumId, artistId, trackNumber, year, duration,
        codec, bitrate, sampleRate, channels, size, added, hasCover, playlists,
    )

    companion object {
        fun from(t: Track) = TrackEntity(
            t.id, t.file, t.title, t.artist, t.artists, t.album, t.albumArtist, t.albumId, t.artistId, t.trackNumber,
            t.year, t.duration, t.codec, t.bitrate, t.sampleRate, t.channels, t.size, t.added, t.hasCover, t.playlists,
        )
    }
}

/** Which server the local library came from, and the feed cursor to continue from. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val id: Int = 0, val serverId: String, val cursor: Long, val syncedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val name: String,
    val liked: Boolean,
    val cover: String,
    val position: Int,
    val trackIds: List<String>,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "''") val displayName: String = "",
    @ColumnInfo(defaultValue = "0") val favoriteSupported: Boolean = false,
) {
    fun toModel() = Playlist(name, liked, cover, trackIds, favorite, displayName, favoriteSupported)
}

@Entity(tableName = "likes")
data class LikeEntity(
    @PrimaryKey val trackId: String,
    /** 0 = most recently liked. */
    val position: Int,
)

/** How much later than its LRC a track's synced lyrics run, as set on this phone (ms, may be negative). */
@Entity(tableName = "lyrics_offsets")
data class LyricsOffsetEntity(@PrimaryKey val trackId: String, val offsetMs: Long)

@Entity(tableName = "recent_contexts")
data class RecentContextEntity(
    @PrimaryKey val key: String,
    val type: String,
    val refId: String,
    val title: String,
    val coverTrackId: String?,
    val playedAt: Long,
) {
    fun toModel(): RecentContext? {
        val type = PlaybackContextType.entries.firstOrNull { it.name == type } ?: return null
        return RecentContext(type, refId, title, coverTrackId, playedAt)
    }
}

/** A listen waiting to be reported (`POST /api/discover/listens`). */
@Entity(tableName = "pending_listens")
data class PendingListenEntity(
    @PrimaryKey val playId: String,
    val trackId: String,
    /** ISO 8601, UTC. */
    val playedAt: String,
    val createdAt: Long,
)

/** A collection kept on the phone (see [OfflineCollection]). */
@Entity(tableName = "offline_collections")
data class OfflineCollectionEntity(
    @PrimaryKey val key: String,
    val type: String,
    val refId: String,
    val addedAt: Long,
) {
    fun toModel(): OfflineCollection? {
        val type = PlaybackContextType.entries.firstOrNull { it.name == type } ?: return null
        return OfflineCollection(type, refId, addedAt)
    }
}

/** A complete offline copy in the app's `offline/` folder. Partial downloads aren't listed. */
@Entity(tableName = "offline_files")
data class OfflineFileEntity(
    @PrimaryKey val trackId: String,
    val fileName: String,
    val bytes: Long,
    val downloadedAt: Long,
) {
    fun toModel() = OfflineFile(trackId, fileName, bytes)
}
