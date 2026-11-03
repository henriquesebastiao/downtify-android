package com.henriquesebastiao.downtify.core.data.library

import android.util.Log
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.model.TrashCapability
import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashPage
import com.henriquesebastiao.downtify.core.model.TrashPreview
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.NetworkJson
import com.henriquesebastiao.downtify.core.network.TrashApi
import com.henriquesebastiao.downtify.core.network.dto.TrashConfirmationDto
import com.henriquesebastiao.downtify.core.network.dto.TrashIdsDto
import com.henriquesebastiao.downtify.core.network.dto.TrashSelectionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/** A trash request the server refused or couldn't take: [code] is its `detail.code` (or `unavailable`). */
class TrashFailure(val code: String, cause: Throwable? = null) : Exception(code, cause)

/**
 * The server's music trash, for an admin's pairing (contract: "Music trash (admins)"). The server
 * does the moves in the background and checks the account on every request; [capability] says
 * whether to offer any of it, and drops to off on a server without it or a 403.
 */
@Singleton
class LibraryTrashRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    @ApplicationScope scope: CoroutineScope,
) {
    private val capabilityState = MutableStateFlow(TrashCapability())
    val capability: StateFlow<TrashCapability> = capabilityState.asStateFlow()

    /** The pairing the trash belongs to: a change of it ends whatever was under way. */
    val session: StateFlow<Session?> get() = sessions.session

    init {
        scope.launch(Dispatchers.IO) {
            sessions.session.collectLatest { session ->
                capabilityState.value = TrashCapability()
                if (session != null) refreshCapability()
            }
        }
    }

    /** Ask the server again (the account may have stopped, or started, being an admin). */
    suspend fun refreshCapability() {
        val paired = sessions.current ?: return
        val found = try {
            call { it.trashCapabilities().toModel() }
        } catch (_: TrashFailure) {
            TrashCapability()
        }
        if (sessions.current == paired) capabilityState.value = found
    }

    /** Has the server check exactly what would move; resolves when its answer is ready. */
    suspend fun preview(trackIds: List<String>): TrashPreview {
        val started = call { it.trashPreview(TrashSelectionDto(trackIds)) }
        repeat(PREVIEW_POLLS) {
            val status = call { it.trashPreviewStatus(started.requestId) }
            status.preview?.takeIf { status.state == "ready" }?.let { return it.toModel() }
            delay(PREVIEW_POLL_MS)
        }
        throw TrashFailure("preview_unavailable")
    }

    /** Confirms a [preview]; the songs leave the library now and move soon after. Returns how many. */
    suspend fun confirm(confirmation: String): Int =
        call { it.trashConfirm(TrashConfirmationDto(confirmation)).ids.size }

    suspend fun page(offset: Int, search: String): TrashPage = call {
        it.trashList(offset, PAGE_SIZE, search).toModel()
    }

    suspend fun exclusions(): List<TrashItem> = call { api -> api.trashExclusions().items.map { it.toModel() } }

    suspend fun restore(ids: List<String>) {
        call { it.trashRestore(TrashIdsDto(ids)) }
    }

    suspend fun retry(ids: List<String>) {
        call { it.trashRetry(TrashIdsDto(ids)) }
    }

    suspend fun allowDownload(ids: List<String>) {
        call { it.trashAllowDownload(TrashIdsDto(ids)) }
    }

    private suspend fun <T> call(block: suspend (TrashApi) -> T): T = withContext(Dispatchers.IO) {
        val session = sessions.current ?: throw TrashFailure(UNAVAILABLE)
        try {
            block(apis.createTrash(session.baseUrl))
        } catch (e: HttpException) {
            val code = codeOf(e) ?: if (e.code() == HTTP_FORBIDDEN) ADMIN_REQUIRED else UNAVAILABLE
            Log.w(TAG, "Trash request refused: ${e.code()} $code")
            if (code == ADMIN_REQUIRED) capabilityState.update { it.copy(canManage = false) }
            throw TrashFailure(code, e)
        } catch (e: IOException) {
            throw TrashFailure(UNAVAILABLE, e)
        } catch (e: SerializationException) {
            throw TrashFailure(UNAVAILABLE, e)
        }
    }

    private fun codeOf(e: HttpException): String? = runCatching {
        val body = e.response()?.errorBody()?.string().orEmpty()
        NetworkJson.parseToJsonElement(body).jsonObject["detail"]?.jsonObject?.get("code")?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        const val PAGE_SIZE = 50
        const val UNAVAILABLE = "unavailable"
        const val ADMIN_REQUIRED = "admin_required"
        private const val TAG = "LibraryTrash"
        private const val HTTP_FORBIDDEN = 403
        private const val PREVIEW_POLL_MS = 1_000L

        // Half an hour of checking at most, as the web app allows.
        private const val PREVIEW_POLLS = 1_800
    }
}
