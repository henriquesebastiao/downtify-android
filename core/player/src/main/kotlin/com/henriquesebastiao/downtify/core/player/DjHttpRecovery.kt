package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.network.session.Session
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Small, idempotent audio-load retries; OkHttp's whole-call timeout remains the outer budget. */
internal class DjHttpRecovery(private val current: () -> Session?, private val enabled: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().method !in setOf("GET", "HEAD")) return chain.proceed(chain.request())
        val owner = current() ?: throw IOException("DJ playback unavailable")
        val started = System.nanoTime()
        var retry = 0
        while (true) {
            if (current() != owner || !enabled()) throw IOException("DJ playback owner changed")
            try {
                val response = chain.proceed(chain.request())
                if (response.code !in setOf(429, 500, 502, 503, 504) ||
                    !canRetry(chain, retry, started)
                ) {
                    return response
                }
                response.close()
            } catch (error: IOException) {
                if (!canRetry(chain, retry, started)) throw error
            }
            try {
                Thread.sleep((250L shl retry).coerceAtMost(2000))
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("DJ retry interrupted", error)
            }
            retry++
        }
    }

    private fun canRetry(chain: Interceptor.Chain, retry: Int, started: Long) =
        retry < 3 && !chain.call().isCanceled() && (System.nanoTime() - started) < 9_000_000_000L
}
