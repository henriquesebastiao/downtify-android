package com.henriquesebastiao.downtify.core.data.session

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.sync.RoomLibraryStore
import com.henriquesebastiao.downtify.core.model.ServerInfo
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.NetworkJson
import com.henriquesebastiao.downtify.core.network.PairingClient
import com.henriquesebastiao.downtify.core.network.ServerProbe
import com.henriquesebastiao.downtify.core.network.dto.ServerInfoDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.network.session.SignOutReason
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** How the paired server is doing, for the Settings card and Home's status line. */
enum class ConnectionState {
    /** Not checked yet this launch. */
    Unknown,
    Connected,

    /** The address doesn't answer (off, another network): the app works from what it synced. */
    Unreachable,

    /** Another Downtify answers at the address: its `server_id` differs from the paired one. */
    DifferentServer,
}

/** The paired server: signing in or pairing, forgetting it, and what it currently says about itself. */
@Singleton
class ServerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionStore,
    private val probe: ServerProbe,
    private val pairing: PairingClient,
    private val apis: ApiFactory,
    private val library: RoomLibraryStore,
    private val prefs: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    val session: StateFlow<Session?> get() = sessions.session
    val signOutReason: StateFlow<SignOutReason?> get() = sessions.signOutReason

    private val connectionState = MutableStateFlow(ConnectionState.Unknown)
    val connection: StateFlow<ConnectionState> = connectionState.asStateFlow()

    /** The server's last known `/api/server/info`, kept across launches (capabilities matter offline too). */
    val serverInfo: StateFlow<ServerInfo?> = prefs.data
        .map { p ->
            p[SERVER_INFO]?.let {
                runCatching { NetworkJson.decodeFromString<ServerInfoDto>(it).toModel() }.getOrNull()
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) { sessions.ensureLoaded() }

    /** Checks what the user typed or picked. */
    suspend fun probe(input: String): ServerProbe.Result = probe.probe(input)

    suspend fun check(baseUrl: String): ServerProbe.Result = probe.check(baseUrl)

    /** Signs in to the server at [baseUrl] (already checked: [info]) with the account's password. */
    suspend fun signIn(baseUrl: String, info: ServerInfo, username: String, password: String): PairingClient.Result =
        adopt(info, pairing.signIn(baseUrl, username, password, deviceName()))

    /** Pairs with the server at [baseUrl] (already checked: [info]) with a code from its web page. */
    suspend fun pair(baseUrl: String, info: ServerInfo, code: String): PairingClient.Result =
        adopt(info, pairing.pair(baseUrl, code, deviceName()))

    /**
     * Keeps the token a sign-in or a pairing got. Starting over with a different
     * server than the local library came from clears the library first.
     */
    private suspend fun adopt(info: ServerInfo, result: PairingClient.Result): PairingClient.Result {
        if (result is PairingClient.Result.Paired) {
            val session = result.session.copy(
                serverId = result.session.serverId.ifEmpty { info.serverId },
                serverName = result.session.serverName.ifEmpty { info.name },
            )
            val previous = library.syncState()?.serverId
            if (previous != null && previous != session.serverId) library.clearServerData()
            saveInfo(info)
            withContext(Dispatchers.IO) { sessions.save(session) }
            connectionState.value = ConnectionState.Connected
        }
        return result
    }

    /** Forgets the token (the server keeps listing the phone until it's unpaired there). Keeps the library. */
    suspend fun unpair() = withContext(Dispatchers.IO) {
        sessions.clear(SignOutReason.User)
        connectionState.value = ConnectionState.Unknown
    }

    /** The account this device belongs to can be renamed on the web: follow it (`GET /api/me`). */
    private suspend fun refreshUser(baseUrl: String) {
        val username = try {
            apis.create(baseUrl).me().user?.username ?: return
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return
        } catch (_: HttpException) {
            return // Older servers have no /api/me.
        }
        if (username != sessions.current?.username) {
            withContext(Dispatchers.IO) { sessions.update { it.copy(username = username) } }
        }
    }

    /** Asks the paired server who it is now; updates the name, capabilities and [connection]. */
    suspend fun refresh(): ConnectionState {
        val current = sessions.current ?: return ConnectionState.Unknown
        val state = when (val result = probe.check(current.baseUrl)) {
            is ServerProbe.Result.Found -> {
                if (current.serverId.isNotEmpty() && result.info.serverId != current.serverId) {
                    ConnectionState.DifferentServer
                } else {
                    saveInfo(result.info)
                    if (result.info.name != current.serverName) {
                        withContext(Dispatchers.IO) { sessions.update { it.copy(serverName = result.info.name) } }
                    }
                    refreshUser(current.baseUrl)
                    ConnectionState.Connected
                }
            }

            ServerProbe.Result.NotDowntify, is ServerProbe.Result.TooNew -> ConnectionState.DifferentServer

            else -> ConnectionState.Unreachable
        }
        connectionState.value = state
        return state
    }

    /**
     * `GET /api/auth/status`: whether the server still knows this phone. Used when
     * the WebSocket is refused, which the server answers with 403 before accepting.
     * Drops the token when it doesn't.
     */
    suspend fun verifyStillPaired() {
        val current = sessions.current ?: return
        val status = try {
            apis.create(current.baseUrl).authStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        if (status.via != "device") sessions.onRevoked(current.token)
    }

    fun markUnreachable() {
        if (connectionState.value !=
            ConnectionState.DifferentServer
        ) {
            connectionState.value = ConnectionState.Unreachable
        }
    }

    fun markConnected() {
        if (connectionState.value != ConnectionState.DifferentServer) connectionState.value = ConnectionState.Connected
    }

    private suspend fun saveInfo(info: ServerInfo) {
        prefs.edit {
            it[SERVER_INFO] = NetworkJson.encodeToString(ServerInfoDto.serializer(), ServerInfoDto.from(info))
        }
    }

    /** What the web page's Paired apps list shows: the name the user gave the phone, else its model. */
    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
                .distinct()
                .joinToString(" ")

    private companion object {
        val SERVER_INFO = stringPreferencesKey("server_info")
    }
}
