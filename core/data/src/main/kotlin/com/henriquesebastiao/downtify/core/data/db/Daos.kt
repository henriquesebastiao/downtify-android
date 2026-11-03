package com.henriquesebastiao.downtify.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM tracks")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TrackEntity>

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun syncState(): SyncStateEntity?

    @Upsert
    suspend fun setSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clearSyncState()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY position")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsert(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
}

@Dao
interface LikeDao {
    @Query("SELECT trackId FROM likes ORDER BY position")
    fun observeIds(): Flow<List<String>>

    @Upsert
    suspend fun upsert(likes: List<LikeEntity>)

    @Query("DELETE FROM likes WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM likes")
    suspend fun deleteAll()

    @Query("SELECT MIN(position) FROM likes")
    suspend fun minPosition(): Int?
}

@Dao
interface LyricsOffsetDao {
    @Query("SELECT offsetMs FROM lyrics_offsets WHERE trackId = :trackId")
    fun observe(trackId: String): Flow<Long?>

    @Query("SELECT offsetMs FROM lyrics_offsets WHERE trackId = :trackId")
    suspend fun get(trackId: String): Long?

    @Upsert
    suspend fun upsert(entity: LyricsOffsetEntity)

    @Query("DELETE FROM lyrics_offsets WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

@Dao
interface RecentContextDao {
    @Query("SELECT * FROM recent_contexts ORDER BY playedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<RecentContextEntity>>

    @Upsert
    suspend fun upsert(entity: RecentContextEntity)

    @Query(
        "DELETE FROM recent_contexts WHERE `key` NOT IN " +
            "(SELECT `key` FROM recent_contexts ORDER BY playedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM recent_contexts")
    suspend fun deleteAll()
}

@Dao
interface PendingListenDao {
    @Upsert
    suspend fun insert(entity: PendingListenEntity)

    @Query("SELECT * FROM pending_listens ORDER BY createdAt LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingListenEntity>

    @Query("DELETE FROM pending_listens WHERE playId = :playId")
    suspend fun delete(playId: String)

    @Query("SELECT COUNT(*) FROM pending_listens")
    suspend fun count(): Int
}

@Dao
interface OfflineDao {
    @Query("SELECT * FROM offline_collections ORDER BY addedAt")
    fun observeCollections(): Flow<List<OfflineCollectionEntity>>

    @Upsert
    suspend fun upsertCollection(entity: OfflineCollectionEntity)

    @Query("DELETE FROM offline_collections WHERE `key` = :key")
    suspend fun deleteCollection(key: String)

    @Query("DELETE FROM offline_collections")
    suspend fun deleteAllCollections()

    @Query("SELECT * FROM offline_files")
    fun observeFiles(): Flow<List<OfflineFileEntity>>

    @Query("SELECT * FROM offline_files")
    suspend fun files(): List<OfflineFileEntity>

    @Upsert
    suspend fun upsertFile(entity: OfflineFileEntity)

    @Query("DELETE FROM offline_files WHERE trackId IN (:ids)")
    suspend fun deleteFiles(ids: List<String>)
}
