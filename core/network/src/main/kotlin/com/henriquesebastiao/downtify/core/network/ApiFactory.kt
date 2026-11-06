package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.di.DefaultClient
import com.henriquesebastiao.downtify.core.network.session.AuthInterceptor
import com.henriquesebastiao.downtify.core.network.session.Session
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds (and keeps) a [DowntifyApi] for a server base URL. */
@Singleton
class ApiFactory @Inject constructor(@DefaultClient private val client: OkHttpClient) {
    private val converter = NetworkJson.asConverterFactory("application/json".toMediaType())

    // Some calls wait on the server: it downloads a podcast episode, or asks Deezer and
    // Spotify about the whole library, before it answers. They get a longer read timeout.
    private val patientClient by lazy {
        client.newBuilder().dispatcher(
            Dispatcher().apply {
                maxRequestsPerHost = 2
            },
        ).readTimeout(PATIENT_SECONDS, TimeUnit.SECONDS).build()
    }

    @Volatile
    private var cached: Pair<String, Retrofit>? = null

    @Volatile
    private var cachedPatient: Pair<String, Retrofit>? = null

    @Volatile private var cachedMusic: Pair<String, Retrofit>? = null

    @Volatile private var cachedOwnedMusic: Pair<Session, Retrofit>? = null

    @Volatile private var cachedOwnedCommands: Pair<Session, Retrofit>? = null
    private val musicClient by lazy {
        client.newBuilder().dispatcher(Dispatcher().apply { maxRequestsPerHost = 4 })
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }
    private val commandClient by lazy {
        musicClient.newBuilder().dispatcher(Dispatcher().apply { maxRequestsPerHost = 2 })
            .readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    }

    /** The mobile client contract's routes (`/api/v1/…`, pairing, sign-in). */
    fun create(baseUrl: String): DowntifyApi = retrofit(baseUrl).create(DowntifyApi::class.java)

    /** The web app's own routes a device may call: search, downloads, podcasts, Discover. */
    fun createWeb(baseUrl: String): WebApi = retrofit(baseUrl).create(WebApi::class.java)

    /** Web routes the server answers only when it's done: [createWeb] would time out first. */
    fun createWebPatient(baseUrl: String): WebApi = retrofit(baseUrl, patient = true).create(WebApi::class.java)

    /** The music trash endpoints ([TrashApi]). */
    fun createTrash(baseUrl: String): TrashApi = retrofit(baseUrl).create(TrashApi::class.java)

    fun createMusicAi(baseUrl: String): MusicAiApi {
        val retrofit = cachedMusic?.takeIf { it.first == baseUrl }?.second ?: Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/").client(musicClient).addConverterFactory(converter).build()
            .also { cachedMusic = baseUrl to it }
        return retrofit.create(MusicAiApi::class.java)
    }

    fun createMusicAi(owner: Session, commands: Boolean = false, current: () -> Session?): MusicAiApi {
        val slot = if (commands) cachedOwnedCommands else cachedOwnedMusic
        val existing = slot?.takeIf { it.first.token == owner.token && it.first.baseUrl == owner.baseUrl }?.second
        if (existing != null) return existing.create(MusicAiApi::class.java)
        val builder = (if (commands) commandClient else musicClient).newBuilder()
        builder.interceptors().add(
            0,
            okhttp3.Interceptor { chain ->
                val active = current()
                if (active == null || active.token != owner.token || active.baseUrl != owner.baseUrl ||
                    !AuthInterceptor.belongsTo(chain.request().url, owner.baseUrl)
                ) {
                    throw IOException("Playback owner changed")
                }
                chain.proceed(
                    chain.request().newBuilder().header(AuthInterceptor.AUTHORIZATION, "Bearer ${owner.token}").build(),
                )
            },
        )
        val retrofit = Retrofit.Builder().baseUrl(
            owner.baseUrl.trimEnd('/') + "/",
        ).client(builder.build()).addConverterFactory(converter).build()
        if (commands) cachedOwnedCommands = owner to retrofit else cachedOwnedMusic = owner to retrofit
        return retrofit.create(MusicAiApi::class.java)
    }

    private fun retrofit(baseUrl: String, patient: Boolean = false): Retrofit {
        val slot = if (patient) cachedPatient else cached
        slot?.let { (url, retrofit) -> if (url == baseUrl) return retrofit }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(if (patient) patientClient else client)
            .addConverterFactory(converter)
            .build()
        if (patient) cachedPatient = baseUrl to retrofit else cached = baseUrl to retrofit
        return retrofit
    }

    private companion object {
        /** A podcast episode can be a few hundred MB from a slow feed. */
        const val PATIENT_SECONDS = 600L
    }
}
