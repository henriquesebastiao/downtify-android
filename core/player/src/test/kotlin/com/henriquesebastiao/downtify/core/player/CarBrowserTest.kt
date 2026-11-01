package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.offline.OfflineRepository
import com.henriquesebastiao.downtify.core.data.offline.OfflineState
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarBrowserTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val session = mockk<MediaLibrarySession>(relaxed = true)
    private val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
    private val paired =
        MutableStateFlow<Session?>(Session("https://fixture.test", "server", "Test", "device", "fixture"))
    private val snapshot = MutableStateFlow<LibrarySnapshot?>(null)
    private lateinit var browser: CarBrowser

    @Before fun setUp() {
        val repository = mockk<LibraryRepository>()
        every { repository.library } returns snapshot
        every { repository.playlists } returns MutableStateFlow(listOf(Playlist("Test", false, "", listOf("b", "a"))))
        every { repository.likedIds } returns MutableStateFlow(listOf("a"))
        val offline = mockk<OfflineRepository>()
        every { offline.state } returns MutableStateFlow<OfflineState?>(null)
        val sessions = mockk<SessionStore>()
        every { sessions.current } answers { paired.value }
        every { sessions.session } returns paired
        coEvery { sessions.ensureLoaded() } returns Unit
        val artwork = mockk<SessionArtworkCache>()
        coEvery { artwork.cached(any(), any()) } returns null
        coEvery { artwork.prepare(any(), any(), any()) } returns null
        browser = CarBrowser(RuntimeEnvironment.getApplication(), repository, offline, sessions, artwork, scope)
        browser.observe(session)
        snapshot.value = LibrarySnapshot(listOf(track("a"), track("b")))
    }

    @After fun tearDown() {
        scope.cancel()
    }

    @Test fun `root is browsable and never starts audio`() {
        val root = browser.onGetLibraryRoot(session, controller, null).get(5, TimeUnit.SECONDS)
        assertEquals(CarLibrary.ROOT, root.value!!.mediaId)
        assertFalse(root.value!!.mediaMetadata.isPlayable!!)
        assertEquals(
            6,
            browser.onGetChildren(session, controller, CarLibrary.ROOT, 0, 20, null)
                .get(5, TimeUnit.SECONDS).value!!.size,
        )
    }

    @Test fun `root announces search and keeps all categories accessible on limited receivers`() {
        val params = LibraryParams.Builder().setExtras(
            Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 4)
            },
        ).build()
        val root = browser.onGetLibraryRoot(session, controller, params).get(5, TimeUnit.SECONDS)
        assertTrue(root.params!!.extras.getBoolean("android.media.browse.SEARCH_SUPPORTED"))
        val children = browser.onGetChildren(session, controller, CarLibrary.ROOT, 0, 20, null)
            .get(5, TimeUnit.SECONDS).value!!
        assertEquals(1, children.size)
        val categories = browser.onGetChildren(session, controller, children.single().mediaId, 0, 20, null)
            .get(5, TimeUnit.SECONDS).value!!
        assertEquals(6, categories.size)
    }

    @Test fun `explicit multi item queue preserves its own order and selected index`() {
        val items =
            mutableListOf(MediaItem.Builder().setMediaId("b").build(), MediaItem.Builder().setMediaId("a").build())
        val result = browser.onSetMediaItems(session, controller, items, 1, 9000).get(5, TimeUnit.SECONDS)
        assertEquals(listOf("b", "a"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
        assertEquals(9000L, result.startPositionMs)
    }

    @Test fun `untrusted controller cannot connect and trusted car cannot impersonate the app`() {
        every { controller.uid } returns 200
        every { controller.packageName } returns "untrusted.example"
        every { controller.isTrusted } returns false
        assertFalse(ControllerAccess.canConnect(controller, "app.example", 100))
        every { controller.isTrusted } returns true
        assertTrue(ControllerAccess.canConnect(controller, "app.example", 100))
        assertFalse(ControllerAccess.isApp(controller, "app.example", 100))
        every { controller.uid } returns 100
        every { controller.packageName } returns "app.example"
        assertTrue(ControllerAccess.isApp(controller, "app.example", 100))
    }

    @Test fun `known playlist row expands to the same ordered player queue`() {
        val folder = browser.onGetChildren(session, controller, CarLibrary.PLAYLISTS, 0, 20, null)
            .get(5, TimeUnit.SECONDS).value!!.single()
        val row = browser.onGetChildren(session, controller, folder.mediaId, 1, 1, null)
            .get(5, TimeUnit.SECONDS).value!!.single()
        val result = browser.onSetMediaItems(session, controller, mutableListOf(row), 0, 5000)
            .get(5, TimeUnit.SECONDS)
        assertEquals(listOf("b", "a"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
        assertEquals(5000L, result.startPositionMs)
    }

    @Test fun `external URI never overrides the catalog audio source`() {
        val forged = MediaItem.Builder().setMediaId("a").setUri("https://untrusted.test/audio").build()
        val result = browser.onSetMediaItems(session, controller, mutableListOf(forged), 0, C.TIME_UNSET)
            .get(5, TimeUnit.SECONDS)
        assertEquals(MediaItems.uriFor("a"), result.mediaItems.single().localConfiguration!!.uri)
        assertNotEquals(forged.localConfiguration!!.uri, result.mediaItems.single().localConfiguration!!.uri)
    }

    @Test(expected = ExecutionException::class)
    fun `unknown external ID cannot become an arbitrary library request`() {
        browser.onSetMediaItems(
            session,
            controller,
            mutableListOf(MediaItem.Builder().setMediaId("missing").build()),
            0,
            C.TIME_UNSET,
        ).get(5, TimeUnit.SECONDS)
    }

    @Test fun `unpaired controller receives an authentication error`() {
        paired.value = null
        val root = browser.onGetLibraryRoot(session, controller, null).get(5, TimeUnit.SECONDS)
        assertEquals(SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED, root.resultCode)
        assertNull(root.value)
    }

    @Test fun `voice search resolves one library track without an online search`() {
        val request = MediaItem.RequestMetadata.Builder().setSearchQuery("needle").build()
        val item = MediaItem.Builder().setRequestMetadata(request).build()
        val result = browser.onSetMediaItems(session, controller, mutableListOf(item), 0, C.TIME_UNSET)
            .get(5, TimeUnit.SECONDS)
        assertEquals("a", result.mediaItems[result.startIndex].mediaId)
    }

    @Test(expected = ExecutionException::class)
    fun `ambiguous voice search does not choose an arbitrary track`() {
        val request = MediaItem.RequestMetadata.Builder().setSearchQuery("Artist").build()
        browser.onSetMediaItems(
            session,
            controller,
            mutableListOf(MediaItem.Builder().setRequestMetadata(request).build()),
            0,
            C.TIME_UNSET,
        )
            .get(5, TimeUnit.SECONDS)
    }

    @Test fun `invalid parent and invalid page return explicit errors`() {
        assertEquals(
            SessionError.ERROR_BAD_VALUE,
            browser.onGetChildren(session, controller, "unknown", 0, 20, null).get(5, TimeUnit.SECONDS).resultCode,
        )
        assertEquals(
            SessionError.ERROR_BAD_VALUE,
            browser.onGetChildren(
                session,
                controller,
                CarLibrary.TRACKS,
                -1,
                20,
                null,
            ).get(5, TimeUnit.SECONDS).resultCode,
        )
    }

    @Test fun `search results use library IDs and include no authenticated image URL`() {
        val result = browser.onGetSearchResult(session, controller, "Artist", 0, 20, null).get(5, TimeUnit.SECONDS)
        assertEquals(2, result.value!!.count { it.mediaMetadata.isPlayable == true })
        result.value!!.forEach { assertNull(it.mediaMetadata.artworkUri) }
    }

    private fun track(id: String) = Track(
        id, "$id.flac",
        if (id ==
            "a"
        ) {
            "Unique needle"
        } else {
            "Other title"
        },
        "Artist", listOf("Artist"), "Album", "Artist",
        "album", "artist", if (id == "a") 1 else 2, "2026", 120.0, "flac", 0, 44100, 2, 100, 0, false, emptyList(),
    )
}
