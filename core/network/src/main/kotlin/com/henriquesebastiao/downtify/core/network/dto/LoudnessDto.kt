package com.henriquesebastiao.downtify.core.network.dto

import com.henriquesebastiao.downtify.core.model.LoudnessProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/tracks/{id}/loudness`. */
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
    fun toModel() = LoudnessProfile(
        state,
        algorithmVersion,
        integratedLufs,
        truePeakDbtp,
        gainDb,
        fingerprint,
        sourceEtag,
        sourceSize,
    )
}
