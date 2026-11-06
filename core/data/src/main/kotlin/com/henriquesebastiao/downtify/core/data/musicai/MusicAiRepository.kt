package com.henriquesebastiao.downtify.core.data.musicai

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.MusicAiApi
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiListenDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiSelectionDto
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

enum class MusicAiFailureKind {
    Network,
    Timeout,
    Authentication,
    Forbidden,
    Disabled,
    Busy,
    NotReady,
    Unsupported,
    InvalidResponse,
    Other,
}

class MusicAiFailure(val status: Int? = null, cause: Exception? = null) :
    Exception("Music service unavailable", cause) {
    val kind: MusicAiFailureKind get() = when {
        cause is java.net.SocketTimeoutException || status in setOf(408, 504) -> MusicAiFailureKind.Timeout
        cause is SerializationException -> MusicAiFailureKind.InvalidResponse
        status == 401 -> MusicAiFailureKind.Authentication
        status == 403 -> MusicAiFailureKind.Forbidden
        status == 409 -> MusicAiFailureKind.Disabled
        status == 429 -> MusicAiFailureKind.Busy
        status == 503 -> MusicAiFailureKind.NotReady
        status in setOf(404, 405) -> MusicAiFailureKind.Unsupported
        status != null && status in 300..399 -> MusicAiFailureKind.InvalidResponse
        cause is IOException -> MusicAiFailureKind.Network
        else -> MusicAiFailureKind.Other
    }
}

/** No local profile: the server owns preferences and derives recommendations for its account. */
@Singleton
class MusicAiRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    private val cache: MusicAiCache,
    @ApplicationScope scope: CoroutineScope,
) {
    private val mutableCapabilities = MutableStateFlow(MusicAiCapabilitiesDto())
    val capabilities = mutableCapabilities.asStateFlow()
    val session get() = sessions.session
    private val refreshMutex = Mutex()
    private var refreshedAt = 0L
    private var loadedOwner: String? = null
    private val homeMutex = Mutex()
    private var cachedHome: List<com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto>? = null
    private var cachedHomeOwner: String? = null
    private var homeAt = 0L
    var homeStale: Boolean = false
        private set

    init {
        scope.launch(Dispatchers.IO) {
            sessions.session.collectLatest { paired ->
                val owner = paired?.let(cache::scope)
                if (owner != loadedOwner) {
                    loadedOwner = owner
                    mutableCapabilities.value = MusicAiCapabilitiesDto()
                    refreshedAt = 0L
                    homeAt = 0L
                    cachedHome = null
                    cachedHomeOwner = null
                }
                if (paired != null && owner != null) {
                    try {
                        val saved = cache.capabilities(owner)
                        val cards = cache.home(owner)
                        if (sessions.current == paired) {
                            if (refreshedAt == 0L && saved != null) mutableCapabilities.value = saved
                            if (cachedHomeOwner != owner &&
                                cards != null
                            ) {
                                cachedHome = cards
                                cachedHomeOwner = owner
                            }
                        }
                    } catch (_: IOException) { /* The network can rebuild a derived cache. */ }
                    refresh()
                }
            }
        }
    }

    suspend fun refresh(force: Boolean = false): ServerResult<Unit> = refreshMutex.withLock {
        val paired = sessions.current ?: return@withLock ServerResult.Unreachable
        val now = System.nanoTime() / 1_000_000
        if (!force && refreshedAt != 0L && now - refreshedAt < 60_000) return@withLock ServerResult.Ok(Unit)
        val found = try {
            call { it.capabilities() }
        } catch (error: MusicAiFailure) {
            refreshedAt = now - 45_000 // Bounded retries, while retaining known capabilities.
            if (sessions.current == paired && error.status in setOf(404, 405)) {
                mutableCapabilities.value = MusicAiCapabilitiesDto()
            }
            return@withLock if (error.cause is IOException) {
                ServerResult.Unreachable
            } else {
                ServerResult.Failed(
                    error.status,
                )
            }
        }
        if (sessions.current == paired) {
            mutableCapabilities.value = found
            refreshedAt = now
            try {
                cache.putCapabilities(cache.scope(paired), found)
            } catch (_: IOException) { }
        }
        ServerResult.Ok(Unit)
    }

    suspend fun preferences() = call { it.preferences().preferences }
    suspend fun djSessions() = call { it.djSessions(capabilities.value.djLightStatus).sessions }
    suspend fun djCreate(theme: String, count: Int, requestId: String, mode: String = "prepared") = call {
        it.djCreate(
            com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto(
                requestId,
                theme,
                count,
                mode,
                trial =
                    mode == "interactive" && trial(),
            ),
        )
    }
    suspend fun djSession(id: String) = call { it.djSession(id) }
    private data class PlaybackCache(
        val owner: String,
        val id: String,
        val etag: String?,
        val value: com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto,
    )
    private var playbackCache: PlaybackCache? = null
    suspend fun djPlayback(
        id: String,
        position: Double,
    ): com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto {
        if (!capabilities.value.djLightStatus) return djSession(id)
        val paired = sessions.current ?: throw MusicAiFailure()
        val owner = cache.scope(paired)
        val previous = playbackCache?.takeIf { it.owner == owner && it.id == id }
        val response = call { it.djPlayback(id, position, previous?.etag) }
        val value = if (response.code() == 304) {
            previous?.value
        } else {
            if (!response.isSuccessful) throw MusicAiFailure(response.code())
            response.body()
        }
        if (value == null) throw MusicAiFailure(cause = SerializationException("Missing playback response"))
        if (sessions.current ==
            paired
        ) {
            playbackCache = PlaybackCache(owner, id, response.headers()["ETag"] ?: previous?.etag, value)
        }
        return value
    }
    suspend fun djQueue(ids: List<String>, requestId: String) = call {
        it.djCreate(
            com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto(
                requestId,
                "fete",
                ids.size,
                "interactive",
                ids = ids,
                queueOrder = true,
                trial = trial(),
            ),
        )
    }
    suspend fun djContinuous(theme: String, style: String, requestId: String) = call {
        it.djCreate(
            com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto(
                requestId,
                theme,
                24,
                "interactive",
                continuous = true,
                style = style,
                trial = trial(),
            ),
        )
    }
    suspend fun djCommand(id: String, action: String, payload: Map<String, String> = emptyMap()) =
        call(commands = true) {
            it.djCommand(
                id,
                com.henriquesebastiao.downtify.core.network.dto.MusicDjCommandDto(
                    java.util.UUID.randomUUID().toString(),
                    action,
                    payload,
                ),
            )
        }
    private fun trial() = !capabilities.value.dj.interactiveReady && capabilities.value.enabled("dj_trial")
    suspend fun djSeed(seed: String, requestId: String, skipSeed: Boolean = false) = call {
        it.djCreate(
            com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto(
                requestId,
                "fete",
                24,
                "interactive",
                continuous = true,
                seedId = seed,
                trial = trial(),
                skipSeed = skipSeed,
            ),
        )
    }
    suspend fun djStop(id: String) = call(commands = true) { it.djStop(id) }
    suspend fun djCancelRequest(nonce: String) {
        if (capabilities.value.djRequestCancel) call(commands = true) { it.djCancelRequest(nonce) }
    }
    suspend fun set(feature: String, enabled: Boolean) {
        call { it.preferences(mapOf(feature to enabled)) }
        refresh(force = true)
        invalidateHome()
    }
    suspend fun globalFeatures() = call { it.globalFeatures() }
    suspend fun setGlobal(feature: String, enabled: Boolean) {
        call { it.globalFeatures(mapOf(feature to enabled)) }
        refresh(force = true)
        invalidateHome()
    }
    suspend fun recommendations(seed: String = "") = call { it.recommendations(seed).tracks }
    suspend fun playlist(cadence: String, mood: String) = call { it.playlist(cadence, mood).tracks }
    suspend fun playlists() = call { it.playlists().playlists }
    suspend fun home() = homeMutex.withLock {
        val owner = sessions.current ?: throw MusicAiFailure()
        val ownerKey = cache.scope(owner)
        val now = System.nanoTime() / 1_000_000
        if (cachedHomeOwner == ownerKey && cachedHome != null && now - homeAt in 0..60_000 &&
            homeAt != 0L
        ) {
            return@withLock cachedHome.orEmpty()
        }
        try {
            val cards = call { if (capabilities.value.homeCards) it.home().cards else it.playlists().playlists }
            if (sessions.current == owner) {
                cachedHome = cards
                cachedHomeOwner = ownerKey
                homeAt = now
                homeStale = false
                try {
                    cache.putHome(cache.scope(owner), cards)
                } catch (_: IOException) { }
            }
            cards
        } catch (error: MusicAiFailure) {
            val old = cachedHome
            if (cachedHomeOwner == ownerKey && old != null && sessions.current == owner &&
                error.kind in setOf(MusicAiFailureKind.Network, MusicAiFailureKind.Timeout, MusicAiFailureKind.NotReady)
            ) {
                homeStale = true
                old
            } else {
                throw error
            }
        }
    }
    private suspend fun invalidateHome() {
        homeAt = 0L
        cachedHome = null
        cachedHomeOwner = null
        sessions.current?.let {
            try {
                cache.clearHome(cache.scope(it))
            } catch (_: IOException) { }
        }
    }
    suspend fun save(name: String, ids: List<String>, source: String) =
        call { it.save(MusicAiSelectionDto(name, ids, source)) }
    suspend fun forget() {
        call { it.forget() }
        invalidateHome()
        refresh(force = true)
    }
    suspend fun similar(artist: String, title: String, external: Boolean = false, seed: String = "") = call {
        if (external) {
            it.externalSimilar(artist, title)
        } else {
            it.similar(
                if (seed.isBlank()) artist else "",
                if (seed.isBlank()) title else "",
                seed,
            )
        }
    }
    suspend fun listen(event: MusicAiListenDto) {
        if (!capabilities.value.enabled("personalization")) return
        call { it.listen(event) }
    }

    private suspend fun <T> call(commands: Boolean = false, block: suspend (MusicAiApi) -> T): T =
        withContext(Dispatchers.IO) {
            val paired = sessions.current ?: throw MusicAiFailure()
            try {
                val result = block(apis.createMusicAi(paired, commands) { sessions.current })
                if (sessions.current != paired) throw CancellationException("Account changed")
                result
            } catch (error: HttpException) {
                if (error.code() == 401) sessions.onRevoked(paired.token)
                throw MusicAiFailure(error.code(), error)
            } catch (error: IOException) {
                throw MusicAiFailure(cause = error)
            } catch (error: SerializationException) {
                throw MusicAiFailure(cause = error)
            }
        }
}
