package com.henriquesebastiao.downtify.core.model

import kotlin.math.min

/** Playback-only offsets; the measured peak always remains the upper bound. */
enum class NormalizationLevel(val offsetDb: Double) {
    Normal(0.0),
    Louder(6.0),
    Maximum(12.0),
}

/**
 * A track's playback loudness measurement from the server (`GET /api/v1/tracks/{id}/loudness`):
 * the constant gain that brings it towards [TARGET_LUFS], and what says whether it can be trusted.
 */
data class LoudnessProfile(
    val state: String = "unanalysed",
    val algorithmVersion: Int = 0,
    val integratedLufs: Double? = null,
    val truePeakDbtp: Double? = null,
    val gainDb: Double = 0.0,
    /** The measured file (size, modification time, inode, algorithm) on the server. */
    val fingerprint: String = "",
    /** The `ETag` and size of the original file's stream, to bind an offline copy to it. */
    val sourceEtag: String = "",
    val sourceSize: Long = 0,
) {
    /**
     * The gain to play at, in dB: 0 unless this is a measurement of a known algorithm with sane
     * values, and never more than [MAX_GAIN_DB], the target, or the peak's headroom — whatever
     * the server sent.
     */
    fun safeGainDb(level: NormalizationLevel = NormalizationLevel.Normal): Double {
        val integrated = integratedLufs ?: return 0.0
        val peak = truePeakDbtp ?: return 0.0
        if (state != READY || algorithmVersion != ALGORITHM_VERSION) return 0.0
        if (!integrated.isFinite() || !peak.isFinite() || !gainDb.isFinite()) return 0.0
        if (integrated !in -120.0..20.0 || peak !in -160.0..20.0 || gainDb < MIN_GAIN_DB) return 0.0
        return min(
            min(MAX_GAIN_DB, TARGET_LUFS + level.offsetDb - integrated),
            min(PEAK_CEILING_DBTP - peak, gainDb + level.offsetDb),
        )
    }

    /** Measured (a usable [safeGainDb]) and tied to the exact original file. */
    val bindable: Boolean get() = state == READY && algorithmVersion == ALGORITHM_VERSION && fingerprint.isNotBlank()

    companion object {
        const val READY = "ready"
        const val ALGORITHM_VERSION = 1
        const val TARGET_LUFS = -18.0
        const val MAX_GAIN_DB = 12.0
        const val MIN_GAIN_DB = -60.0
        const val PEAK_CEILING_DBTP = -1.0
    }
}
