package com.henriquesebastiao.downtify.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MusicDjSelectionDto(
    @SerialName("request_id") val requestId: String,
    val theme: String,
    val count: Int,
    val mode: String = "prepared",
    val ids: List<String> = emptyList(),
    @SerialName("queue_order") val queueOrder: Boolean = false,
    val continuous: Boolean = false,
    val style: String = "library",
    @SerialName("seed_id") val seedId: String = "",
    val trial: Boolean = false,
    @SerialName("skip_seed") val skipSeed: Boolean = false,
)

@Serializable data class MusicDjTransitionDto(val reason: String = "")

@Serializable data class MusicDjMarkerDto(
    val id: String,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val transition: MusicDjTransitionDto? = null,
    val cue: Double = 0.0,
    val speed: Double = 1.0,
    val occurrence: Int = -1,
)

@Serializable data class MusicDjSessionDto(
    val id: String,
    val mode: String = "prepared",
    val state: String,
    val theme: String,
    val progress: Double = 0.0,
    val phase: String = "",
    val error: String = "",
    val duration: Double = 0.0,
    val position: Double = 0.0,
    val rendered: Double = 0.0,
    @SerialName("next_mutable_at") val nextMutableAt: Double = 0.0,
    val commands: List<MusicDjCommandResultDto> = emptyList(),
    val tracks: List<MusicAiTrackDto> = emptyList(),
    val timeline: List<MusicDjMarkerDto> = emptyList(),
    @SerialName("media_origin") val mediaOrigin: Double? = null,
    val continuous: Boolean = false,
    @SerialName("queue_order") val queueOrder: Boolean = false,
    @SerialName("track_count") val trackCount: Int? = null,
)

@Serializable data class MusicDjSessionsDto(val sessions: List<MusicDjSessionDto> = emptyList())

@Serializable data class MusicDjCommandDto(
    @SerialName("request_id") val requestId: String,
    val action: String,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable data class MusicDjCommandResultDto(
    val accepted: Boolean,
    @SerialName("effective_after_seconds") val effectiveAfterSeconds: Double = 0.0,
    @SerialName("effective_at") val effectiveAt: Double = 0.0,
    val reason: String = "",
)
