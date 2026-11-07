package com.henriquesebastiao.downtify.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MusicAiStatusDto(
    val ready: Boolean = false,
    val state: String = "",
    @SerialName("prepared_ready") val preparedReady: Boolean = false,
    @SerialName("interactive_ready") val interactiveReady: Boolean = false,
    @SerialName("queue_supported") val queueSupported: Boolean = false,
    @SerialName("continuous_supported") val continuousSupported: Boolean = false,
    @SerialName("interactive_trial_ready") val interactiveTrialReady: Boolean = false,
)

@Serializable data class MusicAiCapabilitiesDto(
    val version: Int = 0,
    val administrator: Boolean = false,
    @SerialName("profile_revision") val profileRevision: Int = 0,
    val features: Map<String, Boolean> = emptyMap(),
    val model: MusicAiStatusDto = MusicAiStatusDto(),
    val dj: MusicAiStatusDto = MusicAiStatusDto(),
    val moods: List<String> = emptyList(),
    @SerialName("home_cards") val homeCards: Boolean = false,
    @SerialName("dj_request_cancel") val djRequestCancel: Boolean = false,
    @SerialName("dj_light_status") val djLightStatus: Boolean = false,
    @SerialName("dj_seed_mix") val djSeedMix: Boolean = false,
) {
    fun enabled(feature: String) = version == 1 && features[feature] == true
    fun interactiveAvailable() =
        enabled("dj_interactive") && (dj.interactiveReady || (enabled("dj_trial") && dj.interactiveTrialReady))
}

@Serializable data class MusicAiPreferencesDto(
    val preferences: Map<String, Boolean> = emptyMap(),
    val effective: Map<String, Boolean> = emptyMap(),
)

@Serializable data class MusicAiEffectiveDto(val effective: Map<String, Boolean> = emptyMap())

@Serializable data class MusicAiTrackDto(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val reason: String = "",
    val file: String = "",
    val duration: Double = 0.0,
)

@Serializable data class MusicAiTracksDto(val tracks: List<MusicAiTrackDto> = emptyList(), val source: String = "local")

@Serializable data class MusicAiPlaylistDto(
    val cadence: String,
    val mood: String,
    val period: String,
    val count: Int,
    val created: Double = 0.0,
    @SerialName("cover_track_ids") val coverTrackIds: List<String> = emptyList(),
    val state: String = "ready",
    val title: String = "",
)

@Serializable data class MusicAiHomeDto(val cards: List<MusicAiPlaylistDto> = emptyList())

@Serializable data class MusicAiPlaylistsDto(val playlists: List<MusicAiPlaylistDto> = emptyList())

@Serializable data class MusicAiSelectionDto(val name: String, val ids: List<String>, val source: String)

@Serializable data class MusicAiSavedDto(val name: String, val count: Int = 0)

@Serializable data class MusicAiListenDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("track_id") val trackId: String,
    val seconds: Double,
    val outcome: String,
    val sequence: Int = 0,
    val generation: Int = 0,
)
