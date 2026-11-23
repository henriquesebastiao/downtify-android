package com.henriquesebastiao.downtify.core.data.playlists

import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.ServerInfo
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.DowntifyApi
import com.henriquesebastiao.downtify.core.network.dto.PlaylistDto
import com.henriquesebastiao.downtify.core.network.dto.PlaylistOrderRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistTracksRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistWriteDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The write routes against a fake [DowntifyApi]: which request goes out, what each status means,
 * and the one retry a stale order gets.
 */
class PlaylistEditorRepositoryTest {
    private val api = mockk<DowntifyApi>()
    private val apiFactory = mockk<ApiFactory>()
    private val sessions = mockk<SessionStore>()
    private val server = mockk<ServerRepository>()
    private val library = mockk<LibraryRepository>(relaxed = true)
    private val info = MutableStateFlow<ServerInfo?>(serverInfo(playlistEditing = true))

    private fun serverInfo(playlistEditing: Boolean) = ServerInfo(
        serverId = "s1",
        name = "nas",
        product = ServerInfo.PRODUCT,
        version = "3.3.0",
        apiVersion = 1,
        requireSignIn = true,
        capabilities = Capabilities(librarySync = true, playlistEditing = playlistEditing),
    )

    // [backgroundScope]: the eager `stateIn` of the capability flag must not keep runTest waiting.
    private fun TestScope.repository() =
        PlaylistEditorRepository(sessions, apiFactory, server, library, backgroundScope)

    private val session = Session("http://nas.local:8000", "s1", "nas", "d1", "token")

    private fun setUpApi() {
        every { sessions.current } returns session
        every { apiFactory.create(any()) } returns api
        every { server.serverInfo } returns info
        coEvery { api.playlists() } returns listOf(
            PlaylistDto(
                name = "Mix",
                manual = true,
                count = 2,
                trackIds = listOf("t1", "t2"),
                mine = true,
                canEditTracks = true,
            ),
        )
    }

    @Test
    fun `creating a playlist trims the name and refetches the lists`() = runTest {
        setUpApi()
        coEvery { api.createPlaylist(any()) } returns PlaylistWriteDto(name = "Mix", manual = true)

        val outcome = repository().create("  Mix  ")

        assertEquals("Mix", (outcome as PlaylistWriteResult.Ok).result.name)
        coVerify { api.createPlaylist(match { it.name == "Mix" }) }
        coVerify { library.refreshPlaylists() }
    }

    @Test
    fun `an older server never sends a write`() = runTest {
        setUpApi()
        info.value = serverInfo(playlistEditing = false)

        val outcome = repository().addTracks("Mix", listOf("t1"))

        assertEquals(PlaylistEditError.NotSupported, (outcome as PlaylistWriteResult.Failed).error)
        coVerify(exactly = 0) { api.editPlaylistTracks(any()) }
    }

    @Test
    fun `a refused write says why, never crashes`() = runTest {
        setUpApi()
        coEvery { api.editPlaylistTracks(any()) } throws httpError(403)

        val outcome = repository().addTracks("Mix", listOf("t1"))

        assertEquals(PlaylistEditError.Forbidden, (outcome as PlaylistWriteResult.Failed).error)
    }

    @Test
    fun `a body over the reference limit is the 10,000 message`() = runTest {
        setUpApi()
        coEvery { api.editPlaylistTracks(any()) } throws httpError(400)

        val outcome = repository().addTracks("Mix", listOf("t1"))

        assertEquals(PlaylistEditError.TooManyTracks, (outcome as PlaylistWriteResult.Failed).error)
    }

    @Test
    fun `a warming-up sync index is its own message`() = runTest {
        setUpApi()
        coEvery { api.editPlaylistTracks(any()) } throws httpError(503)

        val outcome = repository().addTracks("Mix", listOf("t1"))

        assertEquals(PlaylistEditError.IndexNotReady, (outcome as PlaylistWriteResult.Failed).error)
    }

    @Test
    fun `episodes are dropped before the write, and an empty pick is not sent`() = runTest {
        setUpApi()
        coEvery { api.editPlaylistTracks(any()) } returns PlaylistWriteDto(name = "Mix")
        val repository = repository()

        repository.addTracks("Mix", listOf("t1", "episode:7", ""))
        val empty = repository.addTracks("Mix", listOf("episode:7"))

        coVerify { api.editPlaylistTracks(match<PlaylistTracksRequest> { it.add == listOf("t1") }) }
        assertEquals(PlaylistEditError.NoTracks, (empty as PlaylistWriteResult.Failed).error)
    }

    @Test
    fun `a dragged order goes out whole, repeats included`() = runTest {
        setUpApi()
        coEvery { api.reorderPlaylist(any()) } returns
            PlaylistWriteDto(name = "Mix", trackIds = listOf("t2", "t1", "t2"))

        repository().reorder("Mix", listOf("t2", "t1", "t2"))

        coVerify { api.reorderPlaylist(match<PlaylistOrderRequest> { it.trackIds == listOf("t2", "t1", "t2") }) }
    }

    @Test
    fun `a stale order reloads and retries once`() = runTest {
        setUpApi()
        var calls = 0
        coEvery { api.reorderPlaylist(any()) } answers {
            calls++
            if (calls == 1) throw httpError(409)
            PlaylistWriteDto(name = "Mix", trackIds = listOf("t2", "t1"))
        }

        val outcome = repository().reorder("Mix", listOf("t2", "t1"))

        assertTrue(outcome is PlaylistWriteResult.Ok)
        assertEquals(2, calls)
    }

    @Test
    fun `a stale order whose songs changed is not retried`() = runTest {
        setUpApi()
        coEvery { api.reorderPlaylist(any()) } throws httpError(409)

        val outcome = repository().reorder("Mix", listOf("t9", "t1"))

        assertEquals(PlaylistEditError.Stale, (outcome as PlaylistWriteResult.Failed).error)
        coVerify(exactly = 1) { api.reorderPlaylist(any()) }
    }

    @Test
    fun `the listed playlists carry what may be edited`() = runTest {
        setUpApi()
        val repository = repository()

        assertTrue(repository.refresh())

        val listed = repository.playlists.first()
        assertEquals("Mix", listed.single().name)
        assertTrue(listed.single().canEditTracks)
        assertTrue(listed.single().manual)
    }

    private fun httpError(code: Int) = HttpException(
        Response.error<Unit>(code, "{}".toResponseBody("application/json".toMediaType())),
    )
}
