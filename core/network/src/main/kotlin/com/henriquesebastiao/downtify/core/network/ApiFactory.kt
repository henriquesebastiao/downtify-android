package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.di.DefaultClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
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
    private val patientClient by lazy { client.newBuilder().readTimeout(PATIENT_SECONDS, TimeUnit.SECONDS).build() }

    @Volatile
    private var cached: Pair<String, Retrofit>? = null

    @Volatile
    private var cachedPatient: Pair<String, Retrofit>? = null

    /** The mobile client contract's routes (`/api/v1/…`, pairing, sign-in). */
    fun create(baseUrl: String): DowntifyApi = retrofit(baseUrl).create(DowntifyApi::class.java)

    /** The web app's own routes a device may call: search, downloads, podcasts, Discover. */
    fun createWeb(baseUrl: String): WebApi = retrofit(baseUrl).create(WebApi::class.java)

    /** Web routes the server answers only when it's done: [createWeb] would time out first. */
    fun createWebPatient(baseUrl: String): WebApi = retrofit(baseUrl, patient = true).create(WebApi::class.java)

    /** The music trash endpoints ([TrashApi]). */
    fun createTrash(baseUrl: String): TrashApi = retrofit(baseUrl).create(TrashApi::class.java)

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
