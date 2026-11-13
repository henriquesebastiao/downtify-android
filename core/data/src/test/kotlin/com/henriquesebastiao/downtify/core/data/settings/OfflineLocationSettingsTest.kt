package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineLocationSettingsTest {
    private val store = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun `a fresh install uses the default media directory`() = runTest {
        assertNull(SettingsRepository(store).settings.first().offlineDirectoryUri)
    }

    @Test
    fun `selected tree persists and reset restores the default`() = runTest {
        val repository = SettingsRepository(store)
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"

        repository.setOfflineDirectory(uri)
        assertEquals(uri, SettingsRepository(store).settings.first().offlineDirectoryUri)

        repository.setOfflineDirectory(null)
        assertNull(SettingsRepository(store).settings.first().offlineDirectoryUri)
    }
}
