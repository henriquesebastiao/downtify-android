package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.legacy.MediaSessionCompat
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import org.robolectric.util.ReflectionHelpers

/** Observe the real compatibility publisher: framework getQueue isn't wired to setQueue in Robolectric. */
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyQueuePolicyTest {
    private fun observe(session: MediaSession): MutableList<Int> {
        // Version-pinned test instrumentation only; no production reflection or changed arguments.
        val impl = ReflectionHelpers.getField<Any>(session, "impl")
        val legacy = ReflectionHelpers.getField<Any>(impl, "sessionLegacyStub")
        val compatibility = spyk(ReflectionHelpers.getField<MediaSessionCompat>(legacy, "sessionCompat"))
        val sizes = mutableListOf<Int>()
        every { compatibility.setQueue(any()) } answers {
            sizes.add(firstArg<List<MediaSessionCompat.QueueItem>>().size)
            callOriginal()
        }
        every { compatibility.setQueue(null) } answers {
            sizes.add(0)
            callOriginal()
        }
        ReflectionHelpers.setField(legacy, "sessionCompat", compatibility)
        return sizes
    }

    private fun protectedSession(player: LocalDjPlayer, policy: LegacyQueuePolicy) = MediaSession.Builder(
        RuntimeEnvironment.getApplication(),
        DjSessionPlayer(player),
    ).setCallback(object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(policy.commands(session, controller)).build()

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            policy.onPostConnect(session, controller)
        }
    }).build()

    private fun connect(session: MediaSession, notification: Boolean = false): MediaController {
        val hints = Bundle().apply {
            putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, notification)
        }
        val result = MediaController.Builder(RuntimeEnvironment.getApplication(), session.token)
            .setConnectionHints(hints).buildAsync()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isDone)
        return result.get()
    }

    @Test fun `unprotected publisher really exposes every queue item as a positive control`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        val session = MediaSession.Builder(RuntimeEnvironment.getApplication(), DjSessionPlayer(player)).build()
        val published = observe(session)
        try {
            player.setMediaItems((0 until 1501).map { localDjItem("track-$it") })
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1501, published.maxOrNull())
        } finally {
            session.release()
            player.release()
        }
    }

    @Test fun `platform stays bounded while the app retains full queue and transport`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        val policy = LegacyQueuePolicy()
        val session = protectedSession(player, policy)
        val published = observe(session)
        val notification = connect(session, notification = true)
        val app = connect(session)
        try {
            policy.awaitReady()
            player.setMediaItems((0 until 500).map { localDjItem("track-$it") })
            player.addMediaItems((500 until 1501).map { localDjItem("track-$it") })
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1501, player.mediaItemCount)
            assertEquals(1501, app.mediaItemCount)
            assertTrue(app.isCommandAvailable(Player.COMMAND_GET_TIMELINE))
            assertFalse(notification.isCommandAvailable(Player.COMMAND_GET_TIMELINE))
            assertTrue(notification.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            assertTrue(published.isNotEmpty())
            assertEquals(0, published.maxOrNull())
            notification.seekToNext()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, player.currentMediaItemIndex)
            notification.seekToPrevious()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, player.currentMediaItemIndex)
            notification.shuffleModeEnabled = true
            notification.repeatMode = Player.REPEAT_MODE_ALL
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
            app.seekToDefaultPosition(1200)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1200, player.currentMediaItemIndex)
            assertEquals("track-1200", notification.currentMediaItem?.mediaId)
            assertFalse(player.playWhenReady)
            assertEquals(0, published.maxOrNull())
        } finally {
            app.release()
            notification.release()
            session.release()
            player.release()
        }
    }

    @Test fun `startup blocks restoration until platform permissions have been applied`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), this)
        val policy = LegacyQueuePolicy()
        val session = protectedSession(player, policy)
        val published = observe(session)
        val restore = backgroundScope.launch {
            policy.beforeRestore()
            player.setMediaItems((0 until 1501).map { localDjItem("track-$it") })
        }
        val app = connect(session)
        var notification: MediaController? = null
        try {
            runCurrent()
            assertEquals(0, player.mediaItemCount)
            assertFalse(restore.isCompleted)
            notification = connect(session, notification = true)
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(restore.isCompleted)
            assertEquals(1501, player.mediaItemCount)
            assertEquals(1501, app.mediaItemCount)
            assertTrue(published.isNotEmpty())
            assertEquals(0, published.maxOrNull())
        } finally {
            restore.cancel()
            app.release()
            notification?.release()
            session.release()
            player.release()
        }
    }
}
