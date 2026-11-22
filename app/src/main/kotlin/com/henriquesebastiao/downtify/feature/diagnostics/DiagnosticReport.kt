package com.henriquesebastiao.downtify.feature.diagnostics

internal enum class DiagnosticKind { UI, STORAGE, SERVER }
internal enum class DiagnosticStatus { OK, SLOW, ERROR, TIMEOUT, NOT_PAIRED, UNREACHABLE, DIFFERENT_SERVER }
internal enum class DiagnosticMetric {
    MAX_UI_DELAY_MS,
    MEAN_UI_DELAY_MS,
    BYTES,
    JAVA_USED_BYTES,
    JAVA_MAX_BYTES,
    NATIVE_BYTES,
    PSS_KB,
    CPU_MS,
    CHECK_CPU_MS,
    CHECK_WALL_MS,
}

internal data class DiagnosticCheck(
    val kind: DiagnosticKind,
    val status: DiagnosticStatus,
    val elapsedMs: Long = 0,
    val metrics: Map<DiagnosticMetric, Long> = emptyMap(),
)

internal data class DiagnosticReport(
    val version: String,
    val code: Int,
    val sdk: Int,
    val utc: String,
    val checks: List<DiagnosticCheck>,
    val metrics: Map<DiagnosticMetric, Long>,
    val traces: List<String>,
) {
    fun text(): String = buildString {
        appendLine("Downtify diagnostic v1")
        appendLine("version=$version code=$code sdk=$sdk utc=$utc")
        appendLine("scope=quick checks; audio quality and real-time playback NOT_QUALIFIED")
        metrics.forEach { (key, value) -> appendLine("${key.name}=$value") }
        checks.forEach { check ->
            appendLine("${check.kind}: ${check.status} elapsed_ms=${check.elapsedMs}")
            check.metrics.forEach { (key, value) -> appendLine("  ${key.name}=$value") }
        }
        appendLine("Own process exit traces (technical frames only):")
        traces.forEach(::appendLine)
    }.take(MAX_REPORT_CHARS)

    companion object {
        const val MAX_REPORT_CHARS = 64 * 1024
    }
}

/** Never export descriptions, URLs, thread names, account fields or another process's dump. */
internal fun safeExitTrace(text: String, packageName: String): String {
    var own = false
    val output = mutableListOf<String>()
    for (line in text.lineSequence().take(MAX_TRACE_LINES)) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Cmd line: ") ->
                own =
                    trimmed.removePrefix("Cmd line: ").substringBefore(':') == packageName

            trimmed.startsWith("----- end ") -> own = false

            !own -> Unit

            trimmed.startsWith("\"") -> output += when {
                trimmed.startsWith("\"main\"") -> "thread=main"
                trimmed.startsWith("\"DefaultDispatcher") -> "thread=worker"
                else -> "thread=other"
            }

            else -> safeFrame(trimmed)?.let(output::add)
        }
        if (output.size >= MAX_TRACE_FRAMES) break
    }
    return output.joinToString("\n")
}

internal fun safeFrame(line: String): String? {
    val match = FRAME.matchEntire(line) ?: return null
    val source = match.groupValues[2].takeIf { SOURCE.matches(it) } ?: "source omitted"
    return "at ${match.groupValues[1]}($source)"
}

private const val MAX_TRACE_LINES = 4096
private const val MAX_TRACE_FRAMES = 128
private val FRAME = Regex("""at ([A-Za-z0-9_.$<>-]+)\(([^)]*)\)""")
private val SOURCE =
    Regex(
        """(?:[A-Za-z0-9_.$-]+\.(?:kt|java)(?::\d+)?|""" +
            """r8-map-id-[0-9a-f]+:\d+|Native method|Native Method|Unknown Source)""",
    )
