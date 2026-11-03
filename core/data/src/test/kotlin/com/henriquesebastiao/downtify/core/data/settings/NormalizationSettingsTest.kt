package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.henriquesebastiao.downtify.core.model.NormalizationLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizationSettingsTest {
    private val store = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun theExistingVolumeRemainsTheDefaultAndTheChoiceSurvivesRecreation() = runTest {
        val repository = SettingsRepository(store)
        assertEquals(NormalizationLevel.Normal, repository.settings.first().normalizationLevel)
        repository.setNormalizeVolume(true)
        repository.setNormalizationLevel(NormalizationLevel.Maximum)
        val restored = SettingsRepository(store).settings.first()
        assertEquals(NormalizationLevel.Maximum, restored.normalizationLevel)
        assertTrue(restored.normalizeVolume)
    }

    @Test
    fun anUnknownStoredLevelFallsBackToTheExistingVolume() = runTest {
        store.data.value = emptyPreferences().toMutablePreferences().apply {
            this[stringPreferencesKey("normalization_level")] = "unrecognised"
        }
        assertEquals(NormalizationLevel.Normal, SettingsRepository(store).settings.first().normalizationLevel)
    }
}
