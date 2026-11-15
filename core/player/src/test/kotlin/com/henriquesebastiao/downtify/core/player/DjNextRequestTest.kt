package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionError
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjReference
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjCommandResultDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjNextRequestTest {
    private val id = "0123456789abcdef0123456789abcdef"
    private val reference = DjReference(id, "interactive")

    private class Fixture(scope: TestScope, id: String) {
        val music = mockk<MusicAiRepository>()
        val owner = MutableStateFlow<Session?>(Session("https://fixture.test", "server", "Test", "device", "fixture"))
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), scope)
        val errors = mutableListOf<SessionError>()
        val capabilities = MutableStateFlow(MusicAiCapabilitiesDto(djNavigation = true))
        val control: DjNextControl

        init {
            every { music.session } returns owner
            every { music.capabilities } returns capabilities
            player.setMediaItems(listOf(DjMediaItems.from(id, "Mix", 600_000, "interactive"), localDjItem("classic")))
            player.prepare()
            control = DjNextControl(music, scope, { player }, errors::add)
        }
    }

    @Test fun `server failures retain the mix and do not report success`() {
        listOf(401, 403, 404, 405, 409, 429, 503, 504).forEach { status ->
            runTest {
                val f = Fixture(this, id)
                coEvery { f.music.djCommand(id, "next", any()) } throws MusicAiFailure(status)
                f.player.play()
                val result = f.control.request(reference)
                advanceUntilIdle()
                assertEquals(SessionError.ERROR_IO, result.get().resultCode)
                assertEquals(0, f.player.currentMediaItemIndex)
                assertTrue(f.player.playWhenReady)
                assertEquals(id, f.errors.single().extras.getString(DjNextControl.EXTRA_DJ_ID))
                f.player.release()
            }
        }
    }

    @Test fun `legacy servers receive the original command without a navigation payload`() = runTest {
        val f = Fixture(this, id)
        f.capabilities.value = MusicAiCapabilitiesDto()
        coEvery { f.music.djCommand(id, "next", emptyMap()) } returns MusicDjCommandResultDto(true)
        val result = f.control.request(reference)
        advanceUntilIdle()
        assertEquals(0, result.get().resultCode)
        coVerify(exactly = 1) { f.music.djCommand(id, "next", emptyMap()) }
        assertEquals(0, f.player.currentMediaItemIndex)
        f.player.release()
    }

    @Test fun `pause during a delayed success survives the confirmed seek`() = runTest {
        val f = Fixture(this, id)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.music.djCommand(id, "next", any()) } coAnswers {
            gate.await()
            MusicDjCommandResultDto(true, seekToSeconds = 180.0)
        }
        f.player.play()
        val result = f.control.request(reference)
        runCurrent()
        f.player.pause()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, result.get().resultCode)
        assertEquals(180_000L, f.player.currentPosition)
        assertFalse(f.player.playWhenReady)
        assertTrue(f.errors.isEmpty())
        f.player.release()
    }

    @Test fun `a late response from a retired selection cannot seek the reselected mix`() = runTest {
        val f = Fixture(this, id)
        val gate = CompletableDeferred<Unit>()
        coEvery { f.music.djCommand(id, "next", any()) } coAnswers {
            gate.await()
            MusicDjCommandResultDto(true, seekToSeconds = 180.0)
        }
        val result = f.control.request(reference)
        runCurrent()
        f.player.setMediaItem(localDjItem("other"))
        f.player.setMediaItem(DjMediaItems.from(id, "Mix", 600_000, "interactive"))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SessionError.ERROR_BAD_VALUE, result.get().resultCode)
        assertEquals(0L, f.player.currentPosition)
        assertFalse(f.player.playWhenReady)
        assertTrue(f.errors.isEmpty())
        f.player.release()
    }

    @Test fun `an accepted false response is a visible error and retains pause`() = runTest {
        val f = Fixture(this, id)
        coEvery { f.music.djCommand(id, "next", any()) } returns
            MusicDjCommandResultDto(false, reason = "La transition est déjà engagée.")
        val result = f.control.request(reference)
        advanceUntilIdle()
        assertEquals(SessionError.ERROR_IO, result.get().resultCode)
        assertEquals(0, f.player.currentMediaItemIndex)
        assertFalse(f.player.playWhenReady)
        assertEquals("La transition est déjà engagée.", f.errors.single().message)
        f.player.release()
    }
}
