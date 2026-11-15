package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    @Test
    fun `picking keeps the order the user picked in`() {
        val selection = Selection().toggle("t3").toggle("t1").toggle("t9")

        assertEquals(listOf("t3", "t1", "t9"), selection.ids)
        assertEquals(3, selection.count)
    }

    @Test
    fun `toggling twice leaves nothing picked`() {
        val selection = Selection().toggle("t1").toggle("t1")

        assertTrue(selection.isEmpty)
    }

    @Test
    fun `selecting what is already picked keeps it where it is`() {
        val selection = Selection(listOf("t2", "t1")).select("t2").select("t4")

        assertEquals(listOf("t2", "t1", "t4"), selection.ids)
    }

    @Test
    fun `deselecting something that was never picked changes nothing`() {
        val selection = Selection(listOf("t1"))

        assertEquals(selection, selection.deselect("t2"))
    }

    @Test
    fun `select all adds what is in view after the pick, and covers it`() {
        val view = listOf("t1", "t2", "t3")
        val selection = Selection(listOf("t3")).selectAll(view)

        assertEquals(listOf("t3", "t1", "t2"), selection.ids)
        assertTrue(selection.covers(view))
        assertFalse(selection.covers(view + "t4"))
        // Nothing in view is not "all of it": select-all would have nothing to do.
        assertFalse(selection.covers(emptyList()))
    }

    @Test
    fun `retaining drops what left the list and keeps the pick order`() {
        val selection = Selection(listOf("t5", "t1", "t8")).retain(setOf("t1", "t8"))

        assertEquals(listOf("t1", "t8"), selection.ids)
    }

    @Test
    fun `clearing an empty selection is the same value`() {
        val empty = Selection()

        assertTrue(empty.clear() === empty)
    }

    @Test
    fun `the controller publishes every change it makes`() {
        val controller = SelectionController()

        assertEquals(listOf("t1"), controller.toggle("t1").ids)
        assertEquals(listOf("t1", "t2"), controller.select("t2").ids)
        assertTrue(controller.isSelected("t2"))
        assertEquals(listOf("t1"), controller.deselect("t2").ids)
        assertEquals(listOf("t1", "t2"), controller.selectAll(listOf("t1", "t2")).ids)
        assertEquals(2, controller.count)
        assertTrue(controller.clear().isEmpty)
        assertEquals(0, controller.count)
    }
}
