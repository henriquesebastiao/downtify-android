package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySortSettingsTest {
    private val store = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private suspend fun settings() = SettingsRepository(store).settings.first()

    @Test
    fun `a fresh install lists the newest first`() = runTest {
        assertEquals(LibrarySort.RecentlyAdded, settings().librarySort)
        assertTrue(settings().librarySortDescending)
    }

    @Test
    fun `someone who sorted by name before the order existed still reads A to Z`() = runTest {
        store.data.value = store.data.value.toMutablePreferences().apply {
            this[stringPreferencesKey("library_sort")] = LibrarySort.Alphabetical.name
        }
        assertFalse(settings().librarySortDescending)
    }

    @Test
    fun `the flipped order survives repository recreation`() = runTest {
        SettingsRepository(store).setLibrarySortDescending(false)
        assertFalse(settings().librarySortDescending)
    }

    @Test
    fun `picking another sort starts it in its own order`() = runTest {
        val repository = SettingsRepository(store)
        repository.setLibrarySortDescending(false)
        repository.setLibrarySort(LibrarySort.Artist)
        assertFalse(settings().librarySortDescending)
        repository.setLibrarySortDescending(true)
        repository.setLibrarySort(LibrarySort.RecentlyAdded)
        assertTrue(settings().librarySortDescending)
    }

    @Test
    fun `picking the sort that's already on keeps the order`() = runTest {
        val repository = SettingsRepository(store)
        repository.setLibrarySortDescending(false)
        repository.setLibrarySort(LibrarySort.RecentlyAdded)
        assertFalse(settings().librarySortDescending)
    }
}
