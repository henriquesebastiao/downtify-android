package com.henriquesebastiao.downtify.core.data.db

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritesMigrationTest {
    @Test
    fun `main's version three gains the favorite columns`() = migrates(schema = 3)

    @Test
    fun `the favorites branch's version three gains the playback state`() =
        migrates(schema = 4, without = setOf("playback_state"))

    @Test
    fun `a version three that already has both keeps them`() = migrates(schema = 4)

    /** Opens a version 3 database built from [schema] (but [without]) and checks nothing was lost. */
    private fun migrates(schema: Int, without: Set<String> = emptySet()) = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "favorites-migration.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        createFromSchema(file, schema, version = 3, without = without) { old ->
            old.execSQL("INSERT INTO playlists (name, liked, cover, position, trackIds) VALUES ('Mix', 0, '', 0, 'a')")
            old.execSQL("INSERT INTO offline_collections VALUES ('kept', 'Album', 'album', 42)")
        }
        val db = Room.databaseBuilder(context, DowntifyDatabase::class.java, name)
            .addMigrations(MIGRATION_3_4)
            .build()
        try {
            db.openHelper.writableDatabase
                .query("SELECT name, favorite, displayName, favoriteSupported FROM playlists")
                .use {
                    check(it.moveToFirst())
                    assertEquals("Mix", it.getString(0))
                    assertEquals(0, it.getInt(1))
                    assertEquals("", it.getString(2))
                    assertEquals(0, it.getInt(3))
                }
            db.openHelper.writableDatabase.query("SELECT refId FROM offline_collections").use {
                check(it.moveToFirst())
                assertEquals("album", it.getString(0))
            }
            assertNull(db.playback().read())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
