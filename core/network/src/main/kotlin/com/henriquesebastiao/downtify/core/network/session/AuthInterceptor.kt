package com.henriquesebastiao.downtify.core.network.session

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Sends `Authorization: Bearer <token>` on every request to the paired server
 * (API, covers, audio, the WebSocket handshake) — and only to it, so the token
 * never leaks to another host. A `401` to a request that carried the token
 * means the phone was unpaired: the session is dropped.
 */
@Singleton
class AuthInterceptor @Inject constructor(private val store: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val session = store.current
        if (session == null || request.header(AUTHORIZATION) != null || !belongsTo(request.url, session.baseUrl)) {
            return chain.proceed(request)
        }
        val response = chain.proceed(
            request.newBuilder().header(AUTHORIZATION, "Bearer ${session.token}").build(),
        )
        // A 401 to a pairing code or a password is about what was typed, not about the token.
        val signingIn = SIGN_IN_PATHS.any { request.url.encodedPath.endsWith(it) }
        if (response.code == HTTP_UNAUTHORIZED && !signingIn) {
            store.onRevoked(session.token)
        }
        return response
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        private const val HTTP_UNAUTHORIZED = 401
        private val SIGN_IN_PATHS = listOf("/api/auth/pair", "/api/auth/token")

        /** Whether [url] is on the server at [baseUrl]: same scheme, host and port, under its path. */
        fun belongsTo(url: HttpUrl, baseUrl: String): Boolean {
            val base = baseUrl.toHttpUrlOrNull() ?: return false
            if (url.scheme != base.scheme || url.host != base.host || url.port != base.port) return false
            val basePath = base.encodedPath.trimEnd('/')
            return basePath.isEmpty() || url.encodedPath == basePath || url.encodedPath.startsWith("$basePath/")
        }
    }
}
