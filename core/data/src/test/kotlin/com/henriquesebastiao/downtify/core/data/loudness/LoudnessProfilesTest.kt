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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        every { username } returns "owner"
        every { baseUrl } returns "https://example.org/"
    }
    private val sessions = mockk<SessionStore> { every { current } answers { this@LoudnessProfilesTest.session } }
    private val apis = mockk<ApiFactory> { every { create(any()) } returns api }
    private val settings = mockk<SettingsRepository> {
        every { settings } returns MutableStateFlow(UserSettings(normalizeVolume = true))
    }

    @Test
    fun matchedDownloadWorksOfflineAndChangedCopyInvalidatesItsMeasurement() = runTest {
        val context = mockk<Context> { every { filesDir } returns temporary.newFolder("private") }
        val file = temporary.newFile("audio.flac").apply { writeText("original audio") }
        val profile = LoudnessDto("ready", 1, -24.0, -10.0, 6.0, "source-one", "etag-one", file.length())
        coEvery { api.loudness("track") } returns profile
        val profiles = LoudnessProfiles(context, sessions, apis, settings, backgroundScope)
        runCurrent()
        profiles.bindDownloaded("track", file, "etag-one", file.length())
        profiles.prepareBlocking("track", file)
        assertEquals(6.0, profiles.gainDb("track"), 0.001)
        file.writeText("a replaced offline audio file")
        profiles.prepareBlocking("track", file, refresh = false)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
        coVerify(exactly = 1) { api.loudness("track") }
    }

    @Test
    fun oldOfflineCopiesAndResumedDownloadsNeverUseLatestServerMeasurement() = runTest {
        val context = mockk<Context> { every { filesDir } returns temporary.newFolder("private") }
        val file = temporary.newFile("old.flac").apply { writeText("old audio") }
        val profiles = LoudnessProfiles(context, sessions, apis, settings, backgroundScope)
        runCurrent()
        profiles.bindDownloaded("track", file, null, file.length())
        profiles.prepareBlocking("track", file)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
        coVerify(exactly = 0) { api.loudness(any()) }
    }

    @Test
    fun mismatchedDownloadEtagDoesNotBindAProfile() = runTest {
        val context = mockk<Context> { every { filesDir } returns temporary.newFolder("private") }
        val file = temporary.newFile("audio.flac").apply { writeText("audio") }
        coEvery { api.loudness("track") } returns
            LoudnessDto("ready", 1, -24.0, -10.0, 6.0, "new-source", "different-etag", file.length())
        val profiles = LoudnessProfiles(context, sessions, apis, settings, backgroundScope)
        runCurrent()
        profiles.bindDownloaded("track", file, "old-etag", file.length())
        profiles.prepareBlocking("track", file)
        assertEquals(0.0, profiles.gainDb("track"), 0.0)
    }
}
