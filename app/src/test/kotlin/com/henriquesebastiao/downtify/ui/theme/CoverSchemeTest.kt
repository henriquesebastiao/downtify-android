package com.henriquesebastiao.downtify.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.henriquesebastiao.downtify.core.designsystem.component.coverColorScheme
import com.henriquesebastiao.downtify.core.designsystem.component.lerp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyDarkColors
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSchemeTest {
    // A deep red cover and a pale blue one.
    private val red = 0xFFB3261E.toInt()
    private val blue = 0xFF8AB4F8.toInt()

    @Test
    fun aDarkCoverSchemeHasADarkPlayerBackgroundWithReadableText() {
        val scheme = coverColorScheme(red, dark = true)
        assertTrue(scheme.primaryContainer.luminance() < 0.2f)
        assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
    }

    @Test
    fun aLightCoverSchemeHasALightPlayerBackgroundWithReadableText() {
        val scheme = coverColorScheme(red, dark = false)
        assertTrue(scheme.primaryContainer.luminance() > 0.5f)
        assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
    }

    @Test
    fun differentCoversGiveDifferentSchemes() {
        assertNotEquals(coverColorScheme(red, dark = true).primary, coverColorScheme(blue, dark = true).primary)
    }

    @Test
    fun theSameCoverAlwaysGivesTheSameScheme() {
        assertEquals(coverColorScheme(blue, dark = false).primary, coverColorScheme(blue, dark = false).primary)
    }

    @Test
    fun theAnimationStartsAtOneSchemeAndEndsAtTheOther() {
        val app = DowntifyDarkColors
        val cover = coverColorScheme(red, dark = true)
        assertEquals(app.primaryContainer, lerp(app, cover, 0f).primaryContainer)
        assertEquals(cover.primaryContainer, lerp(app, cover, 1f).primaryContainer)
        assertEquals(cover.surfaceContainerHighest, lerp(app, cover, 1f).surfaceContainerHighest)
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = max(a.luminance(), b.luminance()) to min(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }
}
