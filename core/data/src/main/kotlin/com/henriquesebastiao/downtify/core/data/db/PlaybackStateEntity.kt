package com.henriquesebastiao.downtify.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** One local session. The queue payload is only rewritten when its contents change. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    val owner: String,
    val items: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val shuffleOrder: List<String>,
    val repeatMode: Int,
    val speed: Float,
    val contextType: String?,
    val contextId: String?,
    val contextTitle: String?,
)

@Dao
interface PlaybackStateDao {
    // Never put the whole queue in one CursorWindow row. Existing v3 databases
    // may already contain several megabytes here; no destructive migration is needed.
    @Query(
        "SELECT id, owner, '' AS items, currentIndex, positionMs, shuffle, shuffleOrder, " +
            "repeatMode, speed, contextType, contextId, contextTitle FROM playback_state WHERE id = 0",
    )
    suspend fun readHeader(): PlaybackStateEntity?

    @Query("SELECT substr(items, :offset, 65536) FROM playback_state WHERE id = 0")
    suspend fun readItemsChunk(offset: Int): String?

    @Query("SELECT owner FROM playback_state WHERE id = 0")
    suspend fun readOwner(): String?

    @Transaction
    suspend fun read(): PlaybackStateEntity? {
        val header = readHeader() ?: return null
        val payload = StringBuilder()
        var offset = 1
        while (true) {
            val chunk = readItemsChunk(offset).orEmpty()
            if (chunk.isEmpty()) break
            payload.append(chunk)
            // SQLite substr counts Unicode code points; String.length counts UTF-16 units.
            offset += chunk.codePointCount(0, chunk.length)
        }
        return header.copy(items = payload.toString())
    }

    @Upsert
    suspend fun write(state: PlaybackStateEntity)

    @Query("UPDATE playback_state SET currentIndex = :index, positionMs = :positionMs WHERE id = 0 AND owner = :owner")
    suspend fun checkpoint(owner: String, index: Int, positionMs: Long)

    @Query("DELETE FROM playback_state")
    suspend fun clear()
}
