package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.loudness.LoudnessProfiles
import com.henriquesebastiao.downtify.core.data.offline.OfflineAudio
import com.henriquesebastiao.downtify.core.data.offline.OfflineRepository
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamResolverTest {
    private val offline = mockk<OfflineRepository>()
    private val settings = mockk<SettingsRepository> {
        every { settings } returns MutableStateFlow(UserSettings())
    }

    @Test
    fun `offline SAF audio uri reaches Media3 unchanged`() = runTest {
        val audio = OfflineAudio(Uri.parse("content://music/tree/song.flac"), "Music/song.flac", 100, 1)
        every { offline.localAudio("track") } returns audio

        val resolved = resolver().resolveDataSpec(dataSpec())

        assertEquals(audio.uri, resolved.uri)
    }

    @Test
    fun `offline default audio remains a file uri`() = runTest {
        val audio = OfflineAudio(
            Uri.parse("file:///storage/emulated/0/Android/media/app/Music/song.flac"),
            "Music/song.flac",
            100,
            1,
        )
        every { offline.localAudio("track") } returns audio

        val resolved = resolver().resolveDataSpec(dataSpec())

        assertEquals("file", resolved.uri.scheme)
        assertEquals(audio.uri, resolved.uri)
    }

    @Test
    fun `preload uses the same offline uri`() = runTest {
        val audio = OfflineAudio(Uri.parse("content://music/tree/song.flac"), "Music/song.flac", 100, 1)
        every { offline.localAudio("track") } returns audio

        val resolved = resolver().preloadSpec(
            dataSpec().uri,
            com.henriquesebastiao.downtify.core.model.StreamQuality.Original,
        )

        assertEquals(audio.uri, resolved?.uri)
    }

    private fun TestScope.resolver() = StreamResolver(
        sessions = mockk<SessionStore>(relaxed = true),
        server = mockk<ServerRepository>(relaxed = true),
        network = mockk<NetworkMonitor>(relaxed = true),
        offline = offline,
        loudness = mockk<LoudnessProfiles>(relaxed = true),
        settings = settings,
        scope = backgroundScope,
        cache = mockk<AudioCacheStore>(relaxed = true),
    )

    private fun dataSpec() = DataSpec.Builder().setUri(MediaItems.uriFor("track")).build()
}
