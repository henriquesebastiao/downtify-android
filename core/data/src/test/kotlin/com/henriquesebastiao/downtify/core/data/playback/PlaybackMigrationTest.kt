package com.henriquesebastiao.downtify.core.data.playback

import androidx.room.Room
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.MIGRATION_3_4
import com.henriquesebastiao.downtify.core.data.db.createFromSchema
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
class PlaybackMigrationTest {
    @Test
    fun `version two migrates without losing offline choices or pending listens`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "playback-migration.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        createFromSchema(file, schema = 2) { old ->
            old.execSQL("INSERT INTO offline_collections VALUES ('kept', 'Album', 'album', 42)")
            old.execSQL("INSERT INTO pending_listens VALUES ('play', 'track', '2026-01-01T00:00:00Z', 42)")
        }
        val db = Room.databaseBuilder(context, DowntifyDatabase::class.java, name)
            .addMigrations(MIGRATION_3_4)
            .build()
        try {
            db.openHelper.writableDatabase.query("SELECT refId FROM offline_collections").use {
                check(it.moveToFirst())
                assertEquals("album", it.getString(0))
            }
            db.openHelper.writableDatabase.query("SELECT playId FROM pending_listens").use {
                check(it.moveToFirst())
                assertEquals("play", it.getString(0))
            }
            assertNull(db.playback().read())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
