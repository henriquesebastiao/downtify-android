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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class MusicAiFailure(val status: Int? = null, cause: Exception? = null) : Exception("Music service unavailable", cause)

/** No local profile: the server owns preferences and derives recommendations for its account. */
@Singleton
class MusicAiRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    @ApplicationScope scope: CoroutineScope,
) {
    private val mutableCapabilities = MutableStateFlow(MusicAiCapabilitiesDto())
    val capabilities = mutableCapabilities.asStateFlow()
    val session get() = sessions.session
    private val refreshMutex = Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            sessions.session.collectLatest { paired ->
                mutableCapabilities.value = MusicAiCapabilitiesDto()
                if (paired != null) {
                    while (isActive) {
                        refresh()
                        delay(30_000)
                    }
                }
            }
        }
    }

    suspend fun refresh(): ServerResult<Unit> = refreshMutex.withLock {
        val paired = sessions.current ?: return@withLock ServerResult.Unreachable
        val found = try {
            call { it.capabilities() }
        } catch (error: MusicAiFailure) {
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
        if (sessions.current == paired) mutableCapabilities.value = found
        ServerResult.Ok(Unit)
    }

    suspend fun preferences() = call { it.preferences().preferences }
    suspend fun djSessions() = call { it.djSessions().sessions }
    suspend fun djCreate(theme: String, count: Int, requestId: String, mode: String = "prepared") = call {
        it.djCreate(com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto(requestId, theme, count, mode))
    }
    suspend fun djSession(id: String) = call { it.djSession(id) }
    suspend fun djQueue(ids: List<String>, requestId: String) = call {
        it.djCreate(
            com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto(
                requestId,
                "fete",
                ids.size,
                "interactive",
                ids = ids,
                queueOrder = true,
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
            ),
        )
    }
    suspend fun djCommand(id: String, action: String, payload: Map<String, String> = emptyMap()) = call {
        it.djCommand(
            id,
            com.henriquesebastiao.downtify.core.network.dto.MusicDjCommandDto(
                java.util.UUID.randomUUID().toString(),
                action,
                payload,
            ),
        )
    }
    suspend fun djStop(id: String) = call { it.djStop(id) }
    suspend fun set(feature: String, enabled: Boolean) {
        call { it.preferences(mapOf(feature to enabled)) }
        refresh()
    }
    suspend fun globalFeatures() = call { it.globalFeatures() }
    suspend fun setGlobal(feature: String, enabled: Boolean) {
        call { it.globalFeatures(mapOf(feature to enabled)) }
        refresh()
    }
    suspend fun recommendations(seed: String = "") = call { it.recommendations(seed).tracks }
    suspend fun playlist(cadence: String, mood: String) = call { it.playlist(cadence, mood).tracks }
    suspend fun playlists() = call { it.playlists().playlists }
    suspend fun save(name: String, ids: List<String>, source: String) =
        call { it.save(MusicAiSelectionDto(name, ids, source)) }
    suspend fun forget() {
        call { it.forget() }
        refresh()
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

    private suspend fun <T> call(block: suspend (MusicAiApi) -> T): T = withContext(Dispatchers.IO) {
        val paired = sessions.current ?: throw MusicAiFailure()
        try {
            val result = block(apis.createMusicAi(paired.baseUrl))
            if (sessions.current != paired) throw CancellationException("Account changed")
            result
        } catch (error: HttpException) {
            throw MusicAiFailure(error.code(), error)
        } catch (error: IOException) {
            throw MusicAiFailure(cause = error)
        } catch (error: SerializationException) {
            throw MusicAiFailure(cause = error)
        }
    }
}
