package com.henriquesebastiao.downtify.core.data.offline

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFileHealthTest {
    private val file: File = Files.createTempFile("offline-health", ".flac").toFile()
    private val health = OfflineFileHealth()

    @After fun cleanUp() {
        file.delete()
    }

    @Test fun rejectsAFullSizedZeroFilledCopy() {
        file.writeBytes(ByteArray(4096))
        assertFalse(health.usable(file, 4096))
    }

    @Test fun rejectsATruncatedOrMissingCopy() {
        file.writeText("fLaC")
        assertFalse(health.usable(file, 4096))
        file.delete()
        assertFalse(health.usable(file, 4))
    }

    @Test fun keepsAnEncodedHeaderWithSilentSamples() {
        file.writeBytes("fLaC".toByteArray() + ByteArray(4096))
        assertTrue(health.usable(file, 4100))
        assertTrue(health.usable(file, 4100))
    }

    @Test fun rechecksAReplacedCopy() {
        file.writeBytes(ByteArray(4096))
        assertFalse(health.usable(file, 4096))
        file.writeBytes("ID3".toByteArray() + ByteArray(8192))
        assertTrue(health.usable(file, 8195))
    }
}
