package com.henriquesebastiao.downtify.core.network.di

import android.util.Log
import com.henriquesebastiao.downtify.core.network.session.AuthInterceptor
import com.henriquesebastiao.downtify.core.network.session.ClientCertificateManager
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** API calls, covers, the WebSocket. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultClient

/**
 * Audio. A transcoded copy is made on the first request and the request waits
 * for it (a few seconds for a song, longer on a slow server): a long read timeout.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @DefaultClient
    fun defaultClient(
        auth: AuthInterceptor,
        clientCerts: ClientCertificateManager,
        store: SessionStore,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .let(clientCerts::configureTls)
        .addInterceptor(auth)
        .build()
        .also { client ->
            store.onClientCertificateChanged = {
                clientCerts.clearCache()
                client.connectionPool.evictAll()
            }
        }

    @Provides
    @Singleton
    @StreamingClient
    fun streamingClient(@DefaultClient base: OkHttpClient): OkHttpClient = base.newBuilder()
        .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Which copy the server sent: "opus/160", or "no" when the original already fit.
            response.header("X-Downtify-Transcoded")?.let {
                Log.i(
                    "DowntifyStream",
                    "${chain.request().url.encodedPath} -> X-Downtify-Transcoded: $it (${response.code})",
                )
            }
            response
        }
        .build()

    /**
     * First play of an uncached track waits on the server downloading it,
     * which on a slow link takes minutes: reads may stall that long.
     */
    private const val STREAM_READ_TIMEOUT_SECONDS = 300L
}
