package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontTest {
    @Test
    fun savedNamesComeBack() {
        AppFont.entries.forEach { assertEquals(it, AppFont.of(it.name)) }
    }

    @Test
    fun anUnknownOrMissingNameFallsBackToGoogleSansFlex() {
        assertEquals(AppFont.GoogleSansFlex, AppFont.of(null))
        assertEquals(AppFont.GoogleSansFlex, AppFont.of("Comic Sans"))
        assertEquals(AppFont.GoogleSansFlex, AppFont.Default)
    }

    @Test
    fun aSavedClassicChoiceStaysClassic() {
        // The classic look kept its saved name, so whoever picked it before keeps it.
        assertEquals(AppFont.Downtify, AppFont.of("Downtify"))
    }

    @Test
    fun offersGoogleSansFlexTheClassicLookAndTheSystemFont() {
        assertEquals(listOf("GoogleSansFlex", "Downtify", "System"), AppFont.entries.map { it.name })
    }
}
