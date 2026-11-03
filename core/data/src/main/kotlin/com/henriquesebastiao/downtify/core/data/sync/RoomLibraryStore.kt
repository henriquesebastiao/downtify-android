package com.henriquesebastiao.downtify.core.data.sync

import androidx.room.withTransaction
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.SyncStateEntity
import com.henriquesebastiao.downtify.core.data.db.TrackEntity
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.network.session.cacheOwner
import java.io.IOException
import javax.inject.Inject

class RoomLibraryStore @Inject constructor(
    private val db: DowntifyDatabase,
    private val deletedRegistry: DeletedTrackRegistry,
    private val sessions: SessionStore,
) : LibraryLocalStore {
    private val tracks get() = db.tracks()

    override suspend fun syncState(): SyncState? = tracks.syncState()?.let { SyncState(it.serverId, it.cursor) }

    override suspend fun replaceAll(serverId: String, tracks: List<Track>, cursor: Long) {
        val wanted = tracks.mapTo(hashSetOf()) { it.id }
        recordRemoved(serverId, this.tracks.allIds().filter { it !in wanted })
        db.withTransaction {
            this.tracks.deleteAll()
            tracks.chunked(CHUNK).forEach { chunk -> this.tracks.upsert(chunk.map(TrackEntity::from)) }
            this.tracks.setSyncState(
                SyncStateEntity(serverId = serverId, cursor = cursor, syncedAt = System.currentTimeMillis()),
            )
        }
    }

    override suspend fun applyChanges(serverId: String, tracks: List<Track>, deleted: List<String>, cursor: Long) {
        recordRemoved(serverId, deleted)
        db.withTransaction {
            tracks.chunked(CHUNK).forEach { chunk -> this.tracks.upsert(chunk.map(TrackEntity::from)) }
            deleted.chunked(CHUNK).forEach { this.tracks.delete(it) }
            this.tracks.setSyncState(
                SyncStateEntity(serverId = serverId, cursor = cursor, syncedAt = System.currentTimeMillis()),
            )
        }
    }

    /**
     * Tracks gone from the server (deleted, moved to its trash, hidden): their read-ahead copies go
     * too ([DeletedTrackRegistry]), even when the app is closed before the cache gets to them.
     */
    private suspend fun recordRemoved(serverId: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val session = sessions.current ?: throw IOException("session unavailable")
        if (session.serverId != serverId) throw IOException("session changed")
        deletedRegistry.record(session.cacheOwner, ids)
    }

    /** Everything that belongs to one server: before pairing with another. */
    suspend fun clearServerData() = db.withTransaction {
        tracks.deleteAll()
        tracks.clearSyncState()
        db.playlists().deleteAll()
        db.likes().deleteAll()
        db.recents().deleteAll()
        // Another server's ids: its offline copies go too (their files are swept once unlisted).
        db.offline().deleteAllCollections()
    }

    private companion object {
        /** SQLite's variable limit is 999 on older Androids. */
        const val CHUNK = 500
    }
}
