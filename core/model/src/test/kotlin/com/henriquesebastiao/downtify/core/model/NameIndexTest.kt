package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NameIndexTest {
    private val names = listOf("beatles", "Ávila", "Zé Ramalho", "2Pac", "Adele", "Белый", "Björk", "  Air")

    private fun sectioned(descending: Boolean = false) =
        NameIndex.sections(names, descending) { it }.map { it.initial to it.items }

    @Test
    fun `names are sectioned by initial, accents and case aside`() {
        assertEquals(
            listOf(
                "#" to listOf("2Pac"),
                "A" to listOf("Adele", "  Air", "Ávila"),
                "B" to listOf("beatles", "Björk"),
                "Z" to listOf("Zé Ramalho"),
                "Б" to listOf("Белый"),
            ),
            sectioned(),
        )
    }

    @Test
    fun `z to a reverses the sections and the names in them`() {
        assertEquals(
            listOf(
                "Б" to listOf("Белый"),
                "Z" to listOf("Zé Ramalho"),
                "B" to listOf("Björk", "beatles"),
                "A" to listOf("Ávila", "  Air", "Adele"),
                "#" to listOf("2Pac"),
            ),
            sectioned(descending = true),
        )
    }

    @Test
    fun `a name that does not start with a letter goes under other`() {
        assertEquals("#", NameIndex.initial("'39"))
        assertEquals("#", NameIndex.initial("12 Com Dezoito"))
        assertEquals("#", NameIndex.initial("..."))
        assertEquals("#", NameIndex.initial(""))
        assertEquals("E", NameIndex.initial("élan"))
    }

    @Test
    fun `punctuation before the first letter does not count`() {
        assertEquals("W", NameIndex.initial("(What's the Story) Morning Glory?"))
        assertEquals("E", NameIndex.initial("...É Isso"))
        assertEquals("D", NameIndex.initial("(Des) Concerto"))
        assertEquals(
            listOf("(Des) Concerto", "Diamond"),
            NameIndex.sections(listOf("Diamond", "(Des) Concerto")) { it }.single().items,
        )
    }
}
