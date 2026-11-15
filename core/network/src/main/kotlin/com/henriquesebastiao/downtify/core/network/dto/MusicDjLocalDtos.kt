package com.henriquesebastiao.downtify.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MusicDjLocalRequestDto(
    val ids: List<String>,
    @SerialName("transition_ms") val transitionMs: Long = 5_000,
)

@Serializable data class MusicDjLocalTrackDto(
    val index: Int,
    val id: String,
    val revision: String = "",
    val status: String,
    val validity: String = "",
    val bpm: Float? = null,
    val confidence: Float? = null,
    @SerialName("downbeats_ms") val downbeatsMs: List<Long> = emptyList(),
)

@Serializable data class MusicDjLocalPlanDto(
    @SerialName("from_index") val fromIndex: Int,
    @SerialName("to_index") val toIndex: Int,
    val state: String,
    val aligned: Boolean = false,
    @SerialName("outgoing_start_ms") val outgoingStartMs: Long = 0,
    @SerialName("incoming_cue_ms") val incomingCueMs: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val speed: Float = 1f,
    val reason: String = "",
    @SerialName("source_end_ms") val sourceEndMs: Long? = null,
)

@Serializable data class MusicDjLocalPlansDto(
    val version: Int = 0,
    val revision: String = "",
    @SerialName("analysis_model") val analysisModel: String = "",
    val tracks: List<MusicDjLocalTrackDto> = emptyList(),
    val plans: List<MusicDjLocalPlanDto> = emptyList(),
)
