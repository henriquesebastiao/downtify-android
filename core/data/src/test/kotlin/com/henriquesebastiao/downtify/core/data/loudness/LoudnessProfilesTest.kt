package com.henriquesebastiao.downtify.core.data.loudness

import android.content.Context
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.DowntifyApi
import com.henriquesebastiao.downtify.core.network.dto.LoudnessDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoudnessProfilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private val api = mockk<DowntifyApi>()
    private val session = mockk<Session>(relaxed = true) {
        every { serverId } returns "server"
        every { deviceId } returns "device"
        every { baseUrl } returns "https://example.org/"
    }
    private val sessions = mockk<SessionStore> { every { current } answers { this@LoudnessProfilesTest.session } }
    private val apis = mockk<ApiFactory> { every { create(any()) } returns api }
    private val userSettings = MutableStateFlow(UserSettings(normalizeVolume = true))
    private val settings = mockk<SettingsRepository> { every { settings } returns userSettings }

    private fun ready(gain: Double, etag: String = "etag-one", size: Long = 0) =
        LoudnessDto("ready", 1, -18.0 - gain, -20.0, gain, "source-one", etag, size)

    private fun TestScope.profiles(): LoudnessProfiles {
        val context = mockk<Context> { every { filesDir } returns temporary.newFolder() }
        return LoudnessProfiles(context, sessions, apis, settings, backgroundScope).also { runCurrent() }
    }

    @Test
    fun matchedDownloadWorksOfflineAndChangedCopyInvalidatesItsMeasurement() = runTest {
        val file = temporary.newFile("audio.flac").apply { writeText("original audio") }
        coEvery { api.loudness("track") } returns ready(6.0, size = file.length())
        val profiles = profiles()
        profiles.bindDownloaded("track", file, "etag-one", file.length())
        profiles.prepareLocalBlocking("track", file)
        assertEquals(6.0, profiles.gainDb("track"), 0.001)
        file.writeText("a replaced offline audio file")
        profiles.prepareLocalBlocking("track", file)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
        coVerify(exactly = 1) { api.loudness("track") }
    }

    @Test
    fun oldOfflineCopiesAndResumedDownloadsNeverUseLatestServerMeasurement() = runTest {
        val file = temporary.newFile("old.flac").apply { writeText("old audio") }
        val profiles = profiles()
        profiles.bindDownloaded("track", file, null, file.length())
        profiles.prepareLocalBlocking("track", file)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
        coVerify(exactly = 0) { api.loudness(any()) }
    }

    @Test
    fun mismatchedDownloadEtagDoesNotBindAProfile() = runTest {
        val file = temporary.newFile("audio.flac").apply { writeText("audio") }
        coEvery { api.loudness("track") } returns ready(6.0, etag = "different-etag", size = file.length())
        val profiles = profiles()
        profiles.bindDownloaded("track", file, "old-etag", file.length())
        profiles.prepareLocalBlocking("track", file)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
    }

    @Test
    fun aCopyDownloadedWithTheSettingOffIsLeveledOnceItIsOn() = runTest {
        userSettings.value = UserSettings(normalizeVolume = false)
        val file = temporary.newFile("audio.flac").apply { writeText("original audio") }
        coEvery { api.loudness("track") } returns ready(-3.0, size = file.length())
        val profiles = profiles()
        profiles.bindDownloaded("track", file, "etag-one", file.length())
        profiles.prepareLocalBlocking("track", file)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
        userSettings.value = UserSettings(normalizeVolume = true)
        runCurrent()
        profiles.prepareLocalBlocking("track", file)
        assertEquals(-3.0, profiles.gainDb("track"), 0.001)
    }

    @Test
    fun aStreamedTrackIsAskedForOnceAndKeepsItsMeasurementWhenTheServerIsAway() = runTest {
        coEvery { api.loudness("track") } returns ready(4.0)
        val profiles = profiles()
        profiles.prepareStreamBlocking("track", ask = true)
        profiles.prepareStreamBlocking("track", ask = true)
        assertEquals(4.0, profiles.gainDb("track"), 0.001)
        coVerify(exactly = 1) { api.loudness("track") }

        coEvery { api.loudness("other") } throws IOException("offline")
        profiles.prepareStreamBlocking("other", ask = true)
        assertEquals(0.0, profiles.gainDb("other"), 0.0)
        // Mid-track reopen or offline: no request.
        profiles.prepareStreamBlocking("third", ask = false)
        coVerify(exactly = 0) { api.loudness("third") }
    }
}
