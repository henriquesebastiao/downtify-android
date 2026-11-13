package com.henriquesebastiao.downtify

import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RussianLocalizationTest {
    @Test
    fun russianContextUsesLocalizedSettingsAndPluralForms() {
        val context = RuntimeEnvironment.getApplication()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru"))
        }
        val russian = context.createConfigurationContext(configuration)

        assertEquals("Настройки", russian.getString(R.string.settings_title))
        assertEquals("Папка загрузок", russian.getString(R.string.settings_offline_directory))
        assertEquals("1 песня", russian.resources.getQuantityString(R.plurals.songs_count, 1, 1))
        assertEquals("2 песни", russian.resources.getQuantityString(R.plurals.songs_count, 2, 2))
        assertEquals("5 песен", russian.resources.getQuantityString(R.plurals.songs_count, 5, 5))
    }
}
