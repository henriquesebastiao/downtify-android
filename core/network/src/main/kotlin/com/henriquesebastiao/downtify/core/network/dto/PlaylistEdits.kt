package com.henriquesebastiao.downtify.core.network.dto

import com.henriquesebastiao.downtify.core.model.PlaylistEditResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The playlist write routes of the mobile client contract: manual playlists only, by stable track
// ID. All four answer the playlist as the caller sees it now, `track_ids` in playlist order and
// once per occurrence ("Editing playlists").

/** `POST /api/v1/playlists`: an empty manual playlist. */
@Serializable
data class CreatePlaylistRequest(val name: String)

/** `POST /api/v1/playlists/rename`: its owner or an admin. */
@Serializable
data class RenamePlaylistRequest(val name: String, @SerialName("new_name") val newName: String)

/**
 * `POST /api/v1/playlists/tracks`: [add] appends (an id that is already there is skipped and comes
 * back in `already`, unless [allowDuplicates]); [remove] takes out every copy of an id;
 * [removeEntries] takes out one zero-based [TrackEntryDto.occurrence] of an id. At most 10,000
 * references across the three arrays.
 */
@Serializable
data class PlaylistTracksRequest(
    val name: String,
    val add: List<String>? = null,
    val remove: List<String>? = null,
    @SerialName("remove_entries") val removeEntries: List<TrackEntryDto>? = null,
    @SerialName("allow_duplicates") val allowDuplicates: Boolean? = null,
)

/** One occurrence (zero-based, as the playlist lists them) of a track. */
@Serializable
data class TrackEntryDto(@SerialName("track_id") val trackId: String, val occurrence: Int)

/** `PUT /api/v1/playlists/order`: the caller-visible ids as they should be, repeats included. */
@Serializable
data class PlaylistOrderRequest(val name: String, @SerialName("track_ids") val trackIds: List<String>)

/**
 * The answer to any of the four writes: `{name, manual, count, track_ids}`, plus `previous` for a
 * rename and `added`/`removed`/`already` for a track edit.
 */
@Serializable
data class PlaylistWriteDto(
    val name: String = "",
    val manual: Boolean = false,
    val count: Int = 0,
    @SerialName("track_ids") val trackIds: List<String> = emptyList(),
    val previous: String? = null,
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val already: List<String> = emptyList(),
) {
    fun toModel() = PlaylistEditResult(
        name = name,
        count = count,
        trackIds = trackIds,
        previousName = previous.orEmpty(),
        added = added,
        removed = removed,
        already = already,
    )
}
