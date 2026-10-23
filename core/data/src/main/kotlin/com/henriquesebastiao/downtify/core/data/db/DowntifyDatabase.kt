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
    ],
    version = 3,
    exportSchema = true,
    // Offline collections are the user's choices, not a copy of the server's: migrate, never drop.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class DowntifyDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
    abstract fun likes(): LikeDao
    abstract fun recents(): RecentContextDao
    abstract fun pendingListens(): PendingListenDao
    abstract fun offline(): OfflineDao
}
