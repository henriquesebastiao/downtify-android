package com.henriquesebastiao.downtify.core.network.live

import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.network.CatalogJson
import com.henriquesebastiao.downtify.core.network.NetworkJson
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.di.DefaultClient
import com.henriquesebastiao.downtify.core.network.session.Session
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What the server says on `WS /api/ws` that the app acts on. */
sealed interface LiveEvent {
    data object Connected : LiveEvent
    data object MusicPolicyChanged : LiveEvent

    /** Tracks were added, removed or moved: sync. */
    data object LibraryChanged : LiveEvent

    /** Likes changed somewhere: refetch them. */
    data object LikesChanged : LiveEvent

    /** A server download moved on (no `type`; the job's own fields). */
    data class DownloadProgress(val job: ServerJob) : LiveEvent

    /** Many downloads were queued at once: refetch `GET /api/queue`. */
    data object QueueReload : LiveEvent

    /**
     * The socket was closed with 4401 (the device was unpaired while connected)
     * or the handshake was refused (the server refuses before accepting with an
     * HTTP 403, not a 4401 close frame). Check `GET /api/auth/status` to tell a
     * revoked token apart from a proxy that blocks WebSockets.
     */
    data class Refused(val closeCode: Int?, val httpStatus: Int?) : LiveEvent
}

class LiveUpdatesClosed(cause: Throwable?) : IOException("WebSocket closed", cause)

/** The server's WebSocket. The token goes on the handshake, via the auth interceptor. */
@Singleton
class LiveUpdatesClient @Inject constructor(@DefaultClient client: OkHttpClient) {
    private val client = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Events from [session]'s server until the socket closes. Completes normally
     * after [LiveEvent.Refused]; fails with [LiveUpdatesClosed] on any other
     * close so the caller can reconnect with a backoff.
     */
    fun events(session: Session, clientId: String): Flow<LiveEvent> = callbackFlow {
        val request = Request.Builder().url(ServerUrls.webSocket(session.baseUrl, clientId)).build()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    trySend(LiveEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parse(text)?.let { trySend(it) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    if (code == CLOSE_UNPAIRED) {
                        trySend(LiveEvent.Refused(closeCode = code, httpStatus = null))
                        close()
                    } else {
                        close(LiveUpdatesClosed(null))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close(LiveUpdatesClosed(null))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val status = response?.code
                    if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
                        trySend(LiveEvent.Refused(closeCode = null, httpStatus = status))
                        close()
                    } else {
                        close(LiveUpdatesClosed(t))
                    }
                }
            },
        )
        awaitClose { socket.close(NORMAL_CLOSURE, null) }
    }

    companion object {
        const val CLOSE_UNPAIRED = 4401
        private const val NORMAL_CLOSURE = 1000
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val PING_SECONDS = 30L

        /** The events the app acts on; everything else (podcasts, …) is ignored. */
        fun parse(text: String): LiveEvent? {
            val json = runCatching { NetworkJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            return when (json["type"]?.jsonPrimitive?.content) {
                "music_policy_changed" -> LiveEvent.MusicPolicyChanged
                "library_changed" -> LiveEvent.LibraryChanged
                "likes" -> LiveEvent.LikesChanged
                "queue_reload" -> LiveEvent.QueueReload
                null -> CatalogJson.job(json)?.let(LiveEvent::DownloadProgress)
                else -> null
            }
        }
    }
}
