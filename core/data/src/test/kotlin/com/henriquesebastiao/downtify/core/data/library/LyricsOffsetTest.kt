package com.henriquesebastiao.downtify.core.data.library

import androidx.room.Room
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.createFromSchema
import com.henriquesebastiao.downtify.core.model.LyricsTiming
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsOffsetTest {
    @Test
    fun `presses add up per track, stop at the limit and reset`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DowntifyDatabase::class.java)
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = LibraryRepository(database, mockk(), mockk(), mockk(), scope)
            assertEquals(0L, repository.lyricsOffset("a").first())

            // Quick presses, all at once: none is lost.
            List(4) { async(Dispatchers.Default) { repository.shiftLyrics("a", LyricsTiming.STEP_MS) } }.awaitAll()
            repository.shiftLyrics("b", -LyricsTiming.STEP_MS)
            assertEquals(4 * LyricsTiming.STEP_MS, repository.lyricsOffset("a").first())
            assertEquals(-LyricsTiming.STEP_MS, repository.lyricsOffset("b").first())

            repository.shiftLyrics("a", 60_000)
            assertEquals(LyricsTiming.MAX_MS, repository.lyricsOffset("a").first())

            repository.resetLyricsOffset("a")
            assertEquals(0L, repository.lyricsOffset("a").first())
            // Back on the LRC's times, a track keeps no row.
            repository.shiftLyrics("b", LyricsTiming.STEP_MS)
            assertNull(database.lyricsOffsets().get("b"))
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `version four gains the offsets and keeps the offline choices`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "lyrics-offsets-migration.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        createFromSchema(file, schema = 4) { old ->
            old.execSQL("INSERT INTO offline_collections VALUES ('kept', 'Album', 'album', 42)")
        }
        val database = Room.databaseBuilder(context, DowntifyDatabase::class.java, name).build()
        try {
            database.openHelper.writableDatabase.query("SELECT refId FROM offline_collections").use {
                check(it.moveToFirst())
                assertEquals("album", it.getString(0))
            }
            assertNull(database.lyricsOffsets().get("a"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
