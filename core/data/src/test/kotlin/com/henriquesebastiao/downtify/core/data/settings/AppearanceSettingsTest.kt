package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.henriquesebastiao.downtify.core.model.AppFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    private val store = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun `a fresh install gets Google Sans Flex and the floating navigation bar`() = runTest {
        val settings = SettingsRepository(store).settings.first()
        assertEquals(AppFont.GoogleSansFlex, settings.font)
        assertTrue(settings.floatingNavBar)
    }

    @Test
    fun `the full-width navigation bar survives repository recreation`() = runTest {
        SettingsRepository(store).setFloatingNavBar(false)
        assertFalse(SettingsRepository(store).settings.first().floatingNavBar)
    }

    @Test
    fun `someone who picked the Downtify font before keeps the classic look`() = runTest {
        store.data.value = store.data.value.toMutablePreferences().apply {
            this[stringPreferencesKey("font")] = "Downtify"
        }
        assertEquals(AppFont.Downtify, SettingsRepository(store).settings.first().font)
    }
}
