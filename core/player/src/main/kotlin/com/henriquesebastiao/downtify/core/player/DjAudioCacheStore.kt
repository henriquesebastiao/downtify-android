package com.henriquesebastiao.downtify.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.di.StreamingClient
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Private, disposable DJ transport cache, separate from kept songs and the ordinary read-ahead cache. */
@UnstableApi
@Singleton
class DjAudioCacheStore @Inject constructor(
    @ApplicationContext context: Context,
    sessions: SessionStore,
    music: MusicAiRepository,
    @StreamingClient client: OkHttpClient,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cache = SimpleCache(
        File(context.cacheDir, "dj-segments-v1"),
        LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
        StandaloneDatabaseProvider(context),
    )

    // Keep the normal auth interceptor, but do not persist redirect destinations or wait five minutes
    // before the player's bounded recovery policy gets an opportunity to retry a DJ chunk.
    private val http = OkHttpDataSource.Factory(
        client.newBuilder().followRedirects(false).followSslRedirects(false)
            .addInterceptor(
                DjHttpRecovery({
                    sessions.current
                }, { music.capabilities.value.enabled("dj_interactive") }),
            )
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).build(),
    ).setUserAgent("Downtify-Android")
    private val segments = DjSegmentCache(
        cache,
        { sessions.current },
        { music.capabilities.value.enabled("dj_interactive") },
        http,
    )
    val factory: DataSource.Factory = segments

    init {
        scope.launch(Dispatchers.IO) {
            combine(sessions.session, music.capabilities) { owner, caps -> owner to caps.enabled("dj_interactive") }
                .distinctUntilChanged().collectLatest {
                    while (isActive && !segments.purgeRetired()) delay(1000)
                }
        }
    }

    private companion object {
        const val CACHE_BYTES = 64L * 1024 * 1024
    }
}
