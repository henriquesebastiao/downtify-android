package com.henriquesebastiao.downtify

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.player.PlaybackService
import io.mockk.verify
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** Exercises the app's merged declaration and real service rather than invoking callback mocks. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CarServiceIntegrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val fixture = CarServiceFixture()
    private lateinit var service: ServiceController<PlaybackService>
    private var browser: MediaBrowser? = null

    @Before fun startServiceWithoutActivity() {
        service = Robolectric.buildService(PlaybackService::class.java)
        fixture.inject(service.get())
        service.create()
        val component = ComponentName(context, PlaybackService::class.java)
        val binder = service.get().onBind(Intent(MediaLibraryService.SERVICE_INTERFACE))
        assertNotNull(binder)
        shadowOf(context).setComponentNameAndServiceForBindService(component, binder)
    }

    @After fun stopService() {
        browser?.release()
        shadowOf(Looper.getMainLooper()).idle()
        service.destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `installed app declares one discoverable car library with its own identity`() {
        val manager = context.packageManager
        val matches = manager.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName),
            PackageManager.GET_META_DATA,
        )
        assertEquals(listOf(PlaybackService::class.java.name), matches.map { it.serviceInfo.name })
        assertTrue(matches.single().serviceInfo.exported)
        assertTrue(matches.single().loadLabel(manager).toString().startsWith(context.getString(R.string.app_name)))
        val application = manager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertTrue(application.icon != 0)
        val descriptor = application.metaData.getInt("com.google.android.gms.car.application")
        assertTrue(descriptor != 0)
        context.resources.getXml(descriptor).use { xml ->
            var media = false
            while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "uses") {
                    media = media || xml.getAttributeValue(null, "name") == "media"
                }
            }
            assertTrue(media)
        }
        assertNotNull(service.get().onBind(Intent("android.media.browse.MediaBrowserService")))
    }

    @Test fun `cold browser connection restores queue and browses favorites silently`() {
        val controller = connect()
        val root = await(controller.getLibraryRoot(null)).value!!
        val categories = await(controller.getChildren(root.mediaId, 0, 20, null)).value!!
        assertEquals(6, categories.size)
        val favorites = categories.single {
            it.mediaMetadata.title.toString() ==
                context.getString(com.henriquesebastiao.downtify.core.player.R.string.car_favorites)
        }
        val tracks = await(controller.getChildren(favorites.mediaId, 0, 20, null)).value!!
        assertEquals(listOf("One"), tracks.map { it.mediaMetadata.title.toString() })
        assertTrue(tracks.single().mediaMetadata.isPlayable == true)
        assertEquals(
            listOf("a", "b"),
            (0 until controller.mediaItemCount).map {
                controller.getMediaItemAt(it).mediaId
            },
        )
        assertEquals(1, controller.currentMediaItemIndex)
        assertEquals(12_345L, controller.currentPosition)
        assertTrue(controller.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode)
        assertEquals(1.25f, controller.playbackParameters.speed)
        assertFalse(controller.playWhenReady)
        assertEquals(Player.STATE_IDLE, controller.playbackState)
        verify(exactly = 0) { fixture.source.createDataSource() }
    }

    @Test fun `association revocation clears the live queue`() {
        val controller = connect()
        fixture.association.value = null
        pumpUntil { controller.mediaItemCount == 0 }
        assertFalse(controller.playWhenReady)
        assertTrue(await(controller.getLibraryRoot(null)).resultCode < 0)
    }

    private fun connect(): MediaBrowser = await(
        MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync(),
    ).also { browser = it }

    private fun <T> await(future: ListenableFuture<T>): T {
        pumpUntil { future.isDone }
        return future.get(1, TimeUnit.SECONDS)
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Media3 connection or command did not finish", done())
        shadowOf(Looper.getMainLooper()).idle()
    }
}
