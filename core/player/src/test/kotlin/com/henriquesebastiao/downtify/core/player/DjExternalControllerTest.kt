package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjIds
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Controller/session integration simulation; physical Android Auto remains a separate check. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjExternalControllerTest {
    private val id = "0123456789abcdef0123456789abcdef"

    private fun mix(mode: String) = DjMediaItems.from(id, "Mix", 600_000, mode).let { item ->
        item.buildUpon().setMediaMetadata(
            item.mediaMetadata.buildUpon().setExtras(
                Bundle().apply {
                    putLong("djNextStart", 180_000)
                    putString(
                        "djResumePlan",
                        """[{"id":"one","start":0,"end":180},{"id":"two","start":180,"end":360}]""",
                    )
                },
            ).build(),
        ).build()
    }

    private fun connect(session: MediaSession, errors: MutableList<SessionError> = mutableListOf()): MediaController {
        val future = MediaController.Builder(RuntimeEnvironment.getApplication(), session.token)
            .setListener(object : MediaController.Listener {
                override fun onError(controller: MediaController, sessionError: SessionError) {
                    errors.add(sessionError)
                }
            }).buildAsync()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("controller connects through MediaSession", future.isDone)
        return future.get()
    }

    @Test fun `controller next and previous preserve prepared mix and pause across 25 cycles`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        val session = MediaSession.Builder(RuntimeEnvironment.getApplication(), DjSessionPlayer(player)).build()
        val controller = connect(session)
        try {
            repeat(25) {
                player.setMediaItems(listOf(mix("prepared"), localDjItem("classic")), 0, 0)
                player.prepare()
                shadowOf(Looper.getMainLooper()).idle()
                controller.seekToNext()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(0, player.currentMediaItemIndex)
                assertEquals(180_000L, player.currentPosition)
                assertFalse(player.playWhenReady)
                controller.seekToPrevious()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(0L, player.currentPosition)
                assertFalse(player.playWhenReady)
                player.setMediaItem(localDjItem("classic"))
                shadowOf(Looper.getMainLooper()).idle()
            }
        } finally {
            controller.release()
            session.release()
            player.release()
        }
    }

    @Test fun `real decoder controller next never selects the saved classic queue`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val player = LocalDjPlayer(
            androidx.media3.exoplayer.ExoPlayer.Builder(app).build(),
            androidx.media3.exoplayer.ExoPlayer.Builder(app).build(),
            this,
        )
        val session = MediaSession.Builder(app, DjSessionPlayer(player)).build()
        val controller = connect(session)
        try {
            for (shuffle in listOf(false, true)) {
                player.setMediaItems(listOf(localDjItem("before"), mix("prepared"), localDjItem("after")), 1, 0)
                player.shuffleModeEnabled = shuffle
                if (shuffle) player.restoreShuffleOrder(intArrayOf(1, 0, 2))
                shadowOf(Looper.getMainLooper()).idle()
                controller.seekToNext()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("next, shuffle=$shuffle", 1, player.currentMediaItemIndex)
                assertEquals("next marker", 180_000L, player.currentPosition)
                player.seekTo(1, 0)
                shadowOf(Looper.getMainLooper()).idle()
                assertFalse(player.playWhenReady)
                controller.seekToDefaultPosition(if (shuffle) 0 else 2)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("translated next-index command, shuffle=$shuffle", 1, player.currentMediaItemIndex)
                assertEquals("translated next marker", 180_000L, player.currentPosition)
            }
        } finally {
            controller.release()
            session.release()
            player.release()
        }
    }

    @Test fun `controller navigates prepared markers with no classic item after the mix`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val player = LocalDjPlayer(
            androidx.media3.exoplayer.ExoPlayer.Builder(app).build(),
            androidx.media3.exoplayer.ExoPlayer.Builder(app).build(),
            this,
        )
        player.setMediaItem(mix("prepared"))
        val session = MediaSession.Builder(app, DjSessionPlayer(player)).build()
        val controller = connect(session)
        try {
            assertTrue(controller.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
            controller.seekToNext()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(180_000L, player.currentPosition)
            assertEquals(0, player.currentMediaItemIndex)
            assertFalse(player.playWhenReady)
            controller.seekToPrevious()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0L, player.currentPosition)
        } finally {
            controller.release()
            session.release()
            player.release()
        }
    }

    @Test fun `controller receives a refusal without leaving the interactive mix`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        player.setMediaItems(listOf(mix("interactive"), localDjItem("classic")), 0, 0)
        player.prepare()
        val music = mockk<MusicAiRepository>()
        every { music.session } returns MutableStateFlow<Session?>(
            Session("https://fixture.test", "server", "Test", "device", "fixture"),
        )
        every { music.capabilities } returns MutableStateFlow(MusicAiCapabilitiesDto(djNavigation = true))
        coEvery { music.djCommand(id, "next", any()) } throws MusicAiFailure(409)
        val session = MediaSession.Builder(RuntimeEnvironment.getApplication(), DjSessionPlayer(player)).build()
        val next = DjNextControl(music, this, { player }, session::sendError)
        player.setDjNextHandler { next.request(DjIds.referenceOf(player.currentMediaItem?.mediaId)) }
        val errors = mutableListOf<SessionError>()
        val controller = connect(session, errors)
        try {
            controller.seekToNext()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, player.currentMediaItemIndex)
            assertFalse(player.playWhenReady)
            assertEquals(SessionError.ERROR_IO, errors.single().code)
        } finally {
            controller.release()
            session.release()
            player.release()
        }
    }

    private fun appSession(player: LocalDjPlayer) = MediaSession.Builder(
        RuntimeEnvironment.getApplication(),
        DjSessionPlayer(player),
    ).setCallback(object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(androidx.media3.session.SessionCommand(DjTransport.ACTION, Bundle.EMPTY)).build(),
                ).build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: androidx.media3.session.SessionCommand,
            args: Bundle,
        ) = if (command.customAction == DjTransport.ACTION) {
            DjTransport.handle(session.player, args)
        } else {
            super.onCustomCommand(session, controller, command, args)
        }
    }).build()

    @Test fun `app next never exposes a classic selection before the DJ command settles`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        val session = appSession(player)
        val controller = connect(session)
        val observed = mutableListOf<String?>()
        controller.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                observed.add(item?.mediaId)
            }
        })
        try {
            for (shuffle in listOf(false, true)) {
                player.setMediaItems(listOf(localDjItem("before"), mix("prepared"), localDjItem("after")), 1, 0)
                player.shuffleModeEnabled = shuffle
                if (shuffle) player.restoreShuffleOrder(intArrayOf(1, 0, 2))
                shadowOf(Looper.getMainLooper()).idle()
                observed.clear()
                val selected = controller.currentMediaItem?.mediaId
                DjTransport.navigate(controller, previous = false)
                assertEquals("No transient classic selection", selected, controller.currentMediaItem?.mediaId)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(selected, player.currentMediaItem?.mediaId)
                assertEquals(180_000L, player.currentPosition)
                assertFalse(player.playWhenReady)
                DjTransport.navigate(controller, previous = true)
                assertEquals(selected, controller.currentMediaItem?.mediaId)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(0L, player.currentPosition)
                assertTrue("Coordinator never observes a reason to retire the mix", observed.all { it == selected })
                assertEquals(3, player.mediaItemCount)
            }
        } finally {
            controller.release()
            session.release()
            player.release()
        }
    }

    @Test fun `app transport still navigates a classic queue`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        player.setMediaItems(listOf(localDjItem("one"), localDjItem("two")), 0, 0)
        val session = appSession(player)
        val controller = connect(session)
        try {
            DjTransport.navigate(controller, previous = false)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("two", player.currentMediaItem?.mediaId)
            DjTransport.navigate(controller, previous = true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("one", player.currentMediaItem?.mediaId)
        } finally {
            controller.release()
            session.release()
            player.release()
        }
    }
}
