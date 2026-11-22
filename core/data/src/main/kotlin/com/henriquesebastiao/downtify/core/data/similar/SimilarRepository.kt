package com.henriquesebastiao.downtify.core.data.similar

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.callServer
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.CatalogJson
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks that sound like one track (`GET /api/similar/tracks`), from
 * YouTube Music's radio mix. Needs no key; only when both are blank it
 * asks nothing and answers nothing (one field is enough for a seed).
 */
@Singleton
class SimilarRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    private val musicAi: MusicAiRepository,
) {
    val local: Boolean get() = musicAi.capabilities.value.version == 1
    val capabilities = musicAi.capabilities

    suspend fun similar(
        artist: String,
        track: String,
        seed: String = "",
        external: Boolean = false,
    ): ServerResult<List<RemoteSong>> {
        if (artist.isBlank() && track.isBlank() && seed.isBlank()) return ServerResult.Ok(emptyList())
        val refreshed = musicAi.refresh()
        if (refreshed !is ServerResult.Ok) {
            val legacy = refreshed is ServerResult.Failed && refreshed.status in setOf(404, 405)
            if (!legacy) {
                return when (refreshed) {
                    is ServerResult.Failed -> refreshed
                    else -> ServerResult.Unreachable
                }
            }
        } else if (!local) {
            return ServerResult.Failed(503)
        }
        if (local) {
            if (!capabilities.value.enabled(if (external) "external_beta" else "similarity")) {
                return ServerResult.Failed(409)
            }
            return try {
                ServerResult.Ok(CatalogJson.similarTracks(musicAi.similar(artist.trim(), track.trim(), external, seed)))
            } catch (error: MusicAiFailure) {
                ServerResult.Failed(error.status)
            }
        }
        return callServer(sessions, apis, patient = true) { api ->
            CatalogJson.similarTracks(api.similarTracks(artist.trim(), track.trim(), SIMILAR_LIMIT))
        }
    }

    private companion object {
        const val SIMILAR_LIMIT = 20
    }
}
