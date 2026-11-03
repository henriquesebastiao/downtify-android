package com.henriquesebastiao.downtify.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackEntity::class,
        SyncStateEntity::class,
        PlaylistEntity::class,
        LikeEntity::class,
        RecentContextEntity::class,
        PendingListenEntity::class,
        OfflineCollectionEntity::class,
        OfflineFileEntity::class,
        PlaybackStateEntity::class,
        LyricsOffsetEntity::class,
    ],
    version = 5,
    exportSchema = true,
    // Offline collections and lyrics offsets are the user's choices, not a copy of the server's:
    // migrate, never drop. 3 to 4 is MIGRATION_3_4, written by hand.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class DowntifyDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
    abstract fun likes(): LikeDao
    abstract fun recents(): RecentContextDao
    abstract fun pendingListens(): PendingListenDao
    abstract fun offline(): OfflineDao
    abstract fun playback(): PlaybackStateDao
    abstract fun lyricsOffsets(): LyricsOffsetDao
}
