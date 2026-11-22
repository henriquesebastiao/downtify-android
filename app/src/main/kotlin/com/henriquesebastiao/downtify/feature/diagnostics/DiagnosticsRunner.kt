package com.henriquesebastiao.downtify.feature.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.henriquesebastiao.downtify.BuildConfig
import com.henriquesebastiao.downtify.core.network.ServerProbe
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.Reader
import java.time.Instant
import java.util.zip.CRC32
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class DiagnosticsRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionStore,
    private val probe: ServerProbe,
) {
    suspend fun run(): DiagnosticReport = withContext(Dispatchers.IO) {
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.elapsedRealtime()
        val errors = mutableListOf<String>()
        val checks = listOf(
            check(DiagnosticKind.UI, errors) { uiCheck() },
            check(DiagnosticKind.STORAGE, errors) { storageCheck() },
            check(DiagnosticKind.SERVER, errors) { serverCheck() },
        )
        val runtime = Runtime.getRuntime()
        DiagnosticReport(
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            Build.VERSION.SDK_INT,
            Instant.now().toString(),
            checks,
            mapOf(
                DiagnosticMetric.JAVA_USED_BYTES to runtime.totalMemory() - runtime.freeMemory(),
                DiagnosticMetric.JAVA_MAX_BYTES to runtime.maxMemory(),
                DiagnosticMetric.NATIVE_BYTES to Debug.getNativeHeapAllocatedSize(),
                DiagnosticMetric.PSS_KB to Debug.getPss(),
                DiagnosticMetric.CPU_MS to Process.getElapsedCpuTime(),
                DiagnosticMetric.CHECK_CPU_MS to Process.getElapsedCpuTime() - cpuStart,
                DiagnosticMetric.CHECK_WALL_MS to SystemClock.elapsedRealtime() - wallStart,
            ),
            errors + currentTraces() + exitTraces(),
        )
    }

    suspend fun export(uri: Uri, report: DiagnosticReport) = withContext(Dispatchers.IO) {
        val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
        output.bufferedWriter(Charsets.UTF_8).use { it.write(report.text()) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun check(
        kind: DiagnosticKind,
        errors: MutableList<String>,
        block: suspend () -> DiagnosticCheck,
    ): DiagnosticCheck {
        val start = SystemClock.elapsedRealtime()
        val result = try {
            withTimeout(CHECK_TIMEOUT_MS) { block() }
        } catch (_: TimeoutCancellationException) {
            DiagnosticCheck(kind, DiagnosticStatus.TIMEOUT)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errors += error.stackTrace.take(MAX_ERROR_FRAMES).mapNotNull { safeFrame("at $it") }.joinToString("\n")
            DiagnosticCheck(kind, DiagnosticStatus.ERROR)
        }
        return result.copy(elapsedMs = SystemClock.elapsedRealtime() - start)
    }

    private suspend fun uiCheck(): DiagnosticCheck {
        val delays = List(UI_SAMPLES) {
            val start = SystemClock.elapsedRealtime()
            val lag = withContext(Dispatchers.Main) { SystemClock.elapsedRealtime() - start }
            delay(UI_SAMPLE_INTERVAL_MS)
            lag
        }
        return DiagnosticCheck(
            DiagnosticKind.UI,
            if (delays.max() > UI_SLOW_MS) DiagnosticStatus.SLOW else DiagnosticStatus.OK,
            metrics = mapOf(
                DiagnosticMetric.MAX_UI_DELAY_MS to delays.max(),
                DiagnosticMetric.MEAN_UI_DELAY_MS to delays.sum() / delays.size,
            ),
        )
    }

    private fun storageCheck(): DiagnosticCheck {
        val file = File.createTempFile("diagnostic-", ".tmp", context.cacheDir)
        try {
            val expected = writeSample(file)
            val (size, checksum) = readSample(file)
            check(size == STORAGE_BYTES.toLong() && checksum == expected)
            return DiagnosticCheck(
                DiagnosticKind.STORAGE,
                DiagnosticStatus.OK,
                metrics = mapOf(DiagnosticMetric.BYTES to size),
            )
        } finally {
            file.delete()
        }
    }

    private fun writeSample(file: File): Long {
        val bytes = ByteArray(BLOCK_BYTES) { (it % BYTE_PATTERN).toByte() }
        val checksum = CRC32()
        file.outputStream().buffered().use { output ->
            repeat(STORAGE_BYTES / BLOCK_BYTES) {
                output.write(bytes)
                checksum.update(bytes)
            }
        }
        return checksum.value
    }

    private fun readSample(file: File): Pair<Long, Long> {
        val bytes = ByteArray(BLOCK_BYTES)
        val checksum = CRC32()
        var size = 0L
        file.inputStream().buffered().use { input ->
            while (true) {
                val count = input.read(bytes)
                if (count <= 0) break
                checksum.update(bytes, 0, count)
                size += count
            }
        }
        return size to checksum.value
    }

    private suspend fun serverCheck(): DiagnosticCheck {
        val session = sessions.current ?: return DiagnosticCheck(DiagnosticKind.SERVER, DiagnosticStatus.NOT_PAIRED)
        val status = when (val result = probe.check(session.baseUrl)) {
            is ServerProbe.Result.Found -> if (session.serverId.isNotEmpty() &&
                result.info.serverId != session.serverId
            ) {
                DiagnosticStatus.DIFFERENT_SERVER
            } else {
                DiagnosticStatus.OK
            }

            ServerProbe.Result.NotDowntify, is ServerProbe.Result.TooNew -> DiagnosticStatus.DIFFERENT_SERVER

            else -> DiagnosticStatus.UNREACHABLE
        }
        return DiagnosticCheck(DiagnosticKind.SERVER, status)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun currentTraces(): List<String> = try {
        Thread.getAllStackTraces().entries
            .filter {
                it.key.name == "main" || it.key.name.startsWith("ExoPlayer") ||
                    it.key.name.startsWith("DefaultDispatcher")
            }
            .sortedBy { if (it.key.name == "main") 0 else 1 }
            .take(MAX_CURRENT_THREADS)
            .map { (thread, frames) ->
                val role = if (thread.name == "main") "main" else "worker"
                val safe = frames.take(MAX_CURRENT_FRAMES).mapNotNull { safeFrame("at $it") }
                "current_thread=$role state=${thread.state}\n${safe.joinToString("\n")}"
            }
    } catch (_: Exception) {
        listOf("current_stacks=unavailable")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun exitTraces(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
            manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS).map { exit ->
                val trace = try {
                    exit.traceInputStream?.bufferedReader()?.use { safeExitTrace(readBounded(it), context.packageName) }
                } catch (_: java.io.IOException) {
                    null
                }
                "exit_reason=${exit.reason} utc=${Instant.ofEpochMilli(exit.timestamp)}\n${trace.orEmpty()}"
            }
        } catch (_: Exception) {
            listOf("exit_history=unavailable")
        }
    }

    companion object {
        private const val CHECK_TIMEOUT_MS = 5_000L
        private const val UI_SAMPLES = 10
        private const val UI_SAMPLE_INTERVAL_MS = 100L
        private const val UI_SLOW_MS = 250L
        private const val STORAGE_BYTES = 1024 * 1024
        private const val BLOCK_BYTES = 32 * 1024
        private const val BYTE_PATTERN = 251
        private const val MAX_ERROR_FRAMES = 24
        private const val MAX_EXITS = 5
        private const val MAX_CURRENT_THREADS = 8
        private const val MAX_CURRENT_FRAMES = 16
        private const val MAX_TRACE_CHARS = 64 * 1024

        internal fun readBounded(reader: Reader): String {
            val result = StringBuilder()
            val buffer = CharArray(BLOCK_BYTES)
            while (result.length < MAX_TRACE_CHARS) {
                val count = reader.read(buffer, 0, minOf(buffer.size, MAX_TRACE_CHARS - result.length))
                if (count <= 0) break
                result.append(buffer, 0, count)
            }
            return result.toString()
        }
    }
}
