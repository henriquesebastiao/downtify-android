package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAheadSettingsTest {
    @Test
    fun `preferences survive repository recreation and do not change offline downloads`() = runTest {
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val settings = SettingsRepository(store)
        val previousOffline = settings.settings.first().offlineLimitBytes
        for (count in 0..3) {
            settings.setReadAhead(count)
            settings.setStreamCacheMb(if (count % 2 == 0) 10 else 200)
            settings.setReadAheadMobile(true)
            val reread = SettingsRepository(store).settings.first()
            assertEquals(ReadAheadPolicy(count, if (count % 2 == 0) 10 else 200, true), reread.readAhead)
            assertEquals(previousOffline, reread.offlineLimitBytes)
        }
        settings.setReadAhead(0)
        settings.setStreamCacheMb(-3)
        assertEquals(ReadAheadPolicy(0, 10, true), settings.settings.first().readAhead)
    }
}
