package com.henriquesebastiao.downtify.core.network.dto

import kotlin.math.min
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoudnessDto(
    val state: String = "unanalysed",
    @SerialName("algorithm_version") val algorithmVersion: Int = 0,
    @SerialName("integrated_lufs") val integratedLufs: Double? = null,
    @SerialName("true_peak_dbtp") val truePeakDbtp: Double? = null,
    @SerialName("gain_db") val gainDb: Double = 0.0,
    val fingerprint: String = "",
    @SerialName("source_etag") val sourceEtag: String = "",
    @SerialName("source_size") val sourceSize: Long = 0,
) {
    fun safeGainDb(): Double {
        val integrated = integratedLufs ?: return 0.0
        val peak = truePeakDbtp ?: return 0.0
        if (state != "ready" || algorithmVersion != 1) return 0.0
        if (!integrated.isFinite() || !peak.isFinite() || !gainDb.isFinite()) {
            return 0.0
        }
        if (integrated !in -120.0..20.0 || peak !in -160.0..20.0 || gainDb < -60) return 0.0
        return min(min(12.0, -18.0 - integrated), min(-1.0 - peak, gainDb))
    }
}
