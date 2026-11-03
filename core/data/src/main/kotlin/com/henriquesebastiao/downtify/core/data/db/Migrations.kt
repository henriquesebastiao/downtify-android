package com.henriquesebastiao.downtify.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Playlist favorites. Version 3 was built twice before the branches met: with the playback state
 * (main) and with the favorite columns (the favorites branch), and test devices have either. So
 * this adds only what the database at hand lacks instead of replaying one branch's changes.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columns = db.query("PRAGMA table_info(`playlists`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }
        val added = mapOf(
            "favorite" to "INTEGER NOT NULL DEFAULT 0",
            "displayName" to "TEXT NOT NULL DEFAULT ''",
            "favoriteSupported" to "INTEGER NOT NULL DEFAULT 0",
        )
        for ((column, definition) in added) {
            if (column !in columns) db.execSQL("ALTER TABLE `playlists` ADD COLUMN `$column` $definition")
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playback_state` (`id` INTEGER NOT NULL, `owner` TEXT NOT NULL, " +
                "`items` TEXT NOT NULL, `currentIndex` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, " +
                "`shuffle` INTEGER NOT NULL, `shuffleOrder` TEXT NOT NULL, `repeatMode` INTEGER NOT NULL, " +
                "`speed` REAL NOT NULL, `contextType` TEXT, `contextId` TEXT, `contextTitle` TEXT, PRIMARY KEY(`id`))",
        )
    }
}
