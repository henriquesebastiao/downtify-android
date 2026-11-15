package com.henriquesebastiao.downtify.feature.diagnostics

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test fun `exit traces exclude descriptions addresses thread names and other processes`() {
        val raw = """
            exception: account=private-user token=synthetic-token title=Private song
            Cmd line: com.example.app
            "main" prio=5 tid=1 Blocked
              at com.example.Storage.label(Storage.kt:42)
            "OkHttp https://private.example.test/path?token=synthetic-token" prio=5
              at a.b(r8-map-id-abcdef:12)
              at c.d(/private/user/private-song.flac)
            ----- end 7 -----
            Cmd line: com.example.other
              at com.example.Secret.method(Secret.java:4)
        """.trimIndent()
        val result = safeExitTrace(raw, "com.example.app")
        assertTrue(result.contains("thread=main"))
        assertTrue(result.contains("Storage.kt:42"))
        assertTrue(result.contains("r8-map-id-abcdef:12"))
        assertTrue(result.contains("source omitted"))
        listOf("private-user", "synthetic-token", "Private song", "private.example", "private-song", "Secret").forEach {
            assertFalse(result.contains(it))
        }
    }

    @Test fun `a trace without an own process header is not exported`() {
        assertEquals("", safeExitTrace("at com.example.Secret.method(Secret.java:4)", "com.example.app"))
    }

    @Test fun `trace reader and exported report have fixed limits`() {
        val input = "x".repeat(128 * 1024)
        assertEquals(64 * 1024, DiagnosticsRunner.readBounded(StringReader(input)).length)
        val report = DiagnosticReport("beta", 30, 34, "2026-11-15T00:00:00Z", emptyList(), emptyMap(), listOf(input))
        assertEquals(DiagnosticReport.MAX_REPORT_CHARS, report.text().length)
    }
}
