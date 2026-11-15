package com.henriquesebastiao.downtify.core.data.offline

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyingOutputStreamTest {
    @Test fun `audio copy keeps bulk writes and notifies only after closing`() {
        var singleWrites = 0
        var blockWrites = 0
        var closed = false
        var notified = false
        val destination = object : ByteArrayOutputStream() {
            override fun write(value: Int) {
                singleWrites++
                super.write(value)
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                blockWrites++
                super.write(bytes, offset, length)
            }
            override fun close() {
                closed = true
                super.close()
            }
        }
        val bytes = ByteArray(32 * 1024) { (it % 251).toByte() }
        NotifyingOutputStream(destination) {
            assertTrue(closed)
            notified = true
        }.use { output ->
            ByteArrayInputStream(bytes).use { it.copyTo(output) }
            assertFalse(notified)
        }
        assertArrayEquals(bytes, destination.toByteArray())
        assertEquals(0, singleWrites)
        assertTrue(blockWrites > 0)
        assertTrue(notified)
    }

    @Test fun `failed close never publishes a successful change`() {
        var notified = false
        val destination = object : ByteArrayOutputStream() {
            override fun close() = throw IOException("synthetic close failure")
        }
        val stream = NotifyingOutputStream(destination) { notified = true }
        org.junit.Assert.assertThrows(IOException::class.java) { stream.close() }
        assertFalse(notified)
    }
}
