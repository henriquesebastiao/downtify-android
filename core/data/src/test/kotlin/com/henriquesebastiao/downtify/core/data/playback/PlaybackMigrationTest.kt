package com.henriquesebastiao.downtify.core.data.playback

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val resource =
            File(
                System.getProperty("downtify.schemas"),
                "com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase/2.json",
            )
        val schema = Json.parseToJsonElement(resource.readText())
            .jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            for (entry in schema.getValue("entities").jsonArray) {
                val entity = entry.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                for (index in entity["indices"]?.jsonArray.orEmpty()) {
                    old.execSQL(
                        index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table),
                    )
                }
            }
            for (query in schema.getValue("setupQueries").jsonArray) old.execSQL(query.jsonPrimitive.content)
            old.execSQL("INSERT INTO offline_collections VALUES ('kept', 'Album', 'album', 42)")
            old.execSQL("INSERT INTO pending_listens VALUES ('play', 'track', '2026-01-01T00:00:00Z', 42)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, DowntifyDatabase::class.java, name).build()
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
