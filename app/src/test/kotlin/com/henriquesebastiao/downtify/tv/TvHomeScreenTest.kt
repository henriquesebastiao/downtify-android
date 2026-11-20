package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.feature.home.HomeUiState
import com.henriquesebastiao.downtify.feature.home.ServerStatus
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvHomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val album = PreviewData.albums.first()
    private val recent = RecentContext(PlaybackContextType.Album, album.id, album.title, album.coverTrackId, 0)

    private fun show(
        state: HomeUiState,
        onOpenRecent: (RecentContext) -> Unit = {},
        onOpenAlbum: (String) -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) { TvHomeScreen(state, onOpenRecent, onOpenAlbum) }
    }

    @Test
    fun noShortcutsAndNoStreamingLineOnATv() {
        show(
            HomeUiState(
                loading = false,
                serverName = "nas.local",
                jumpBackIn = listOf(recent),
                showDiscover = true,
                showPodcasts = true,
            ),
        )

        compose.onNodeWithText("Jump back in").assertExists()
        compose.onNodeWithText("Discover").assertDoesNotExist()
        compose.onNodeWithText("Similar tracks").assertDoesNotExist()
        compose.onNodeWithText("Podcasts").assertDoesNotExist()
        compose.onNodeWithText("Streaming from nas.local").assertDoesNotExist()
    }

    @Test
    fun aRecentTileOpensWhatItIs() {
        val opened = mutableListOf<RecentContext>()
        show(HomeUiState(loading = false, jumpBackIn = listOf(recent)), onOpenRecent = { opened += it })

        compose.onNodeWithText(album.title).performClick()

        assertEquals(listOf(recent), opened)
    }

    @Test
    fun aLooseSongShowsItselfNotTheSongsList() {
        val song = PreviewData.tracks.first()
        val entry = RecentContext(PlaybackContextType.Songs, song.id, song.title, song.id, 0)
        val opened = mutableListOf<RecentContext>()
        show(
            HomeUiState(loading = false, jumpBackIn = listOf(entry), recentSongs = mapOf(song.id to song)),
            onOpenRecent = { opened += it },
        )

        compose.onNodeWithText(song.displayArtist).assertExists()
        compose.onNodeWithText("Songs").assertDoesNotExist()
        compose.onNodeWithText(song.title).performClick()

        assertEquals(listOf(entry), opened)
    }

    @Test
    fun aRecentlyAddedAlbumOpensItsPage() {
        val opened = mutableListOf<String>()
        show(HomeUiState(loading = false, recentlyAdded = listOf(album)), onOpenAlbum = { opened += it })

        compose.onNodeWithText("Recently added").assertExists()
        compose.onNodeWithText(album.title).performClick()

        assertEquals(listOf(album.id), opened)
    }

    @Test
    fun anUnreachableServerSaysSo() {
        show(HomeUiState(loading = false, serverName = "nas.local", status = ServerStatus.Unreachable))

        compose.onNodeWithText("Can't reach nas.local · showing your synced library").assertExists()
    }
}
