package com.henriquesebastiao.downtify.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.network.di.StreamingClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Audio from the server: resolved to a quality, cached, fetched with the device token. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AudioDataSource

/** Plain HTTP with the device token: artwork for the notification and lock screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArtworkDataSource

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    /** The streaming cache: recently played audio, so replays and seeks back don't hit the network. */
    @Provides
    @Singleton
    fun cache(store: AudioCacheStore): SimpleCache = store.cache

    /**
     * OkHttp, whose auth interceptor sets `Authorization: Bearer <token>` on
     * every request to the paired server (the token can change while the app
     * runs, so it's read per request rather than fixed as a default header).
     */
    @Provides
    @Singleton
    fun httpDataSource(@StreamingClient client: OkHttpClient): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)

    @Provides
    @Singleton
    @AudioDataSource
    fun audioDataSource(
        @ApplicationContext context: Context,
        cache: SimpleCache,
        http: OkHttpDataSource.Factory,
        resolver: StreamResolver,
        store: AudioCacheStore,
    ): DataSource.Factory = ResolvingDataSource.Factory(
        // file:// (offline copies) is read straight from disk; http(s) goes through the streaming cache.
        DefaultDataSource.Factory(
            context,
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(http)
                .setCacheKeyFactory { it.key ?: store.uriKey(it.uri.toString()) }
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
        ),
        resolver,
    )

    @Provides
    @Singleton
    @ArtworkDataSource
    fun artworkDataSource(@ApplicationContext context: Context, http: OkHttpDataSource.Factory): DataSource.Factory =
        DefaultDataSource.Factory(context, http)

    private const val USER_AGENT = "Downtify-Android"
}
