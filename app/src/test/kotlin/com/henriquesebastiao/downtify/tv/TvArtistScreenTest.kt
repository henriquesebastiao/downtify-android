package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.feature.artist.ArtistUiState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvArtistScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val artist = PreviewData.artists.first { it.albums.isNotEmpty() }
    private val loaded = ArtistUiState(loading = false, artist = artist, songs = artist.albums.flatMap { it.tracks })

    private fun show(
        state: ArtistUiState,
        onAlbum: (String) -> Unit = {},
        onPlay: (Int) -> Unit = {},
        onPlayOrPause: () -> Unit = {},
        onShuffle: () -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) { TvArtistScreen(state, onAlbum, onPlay, onPlayOrPause, onShuffle) }
    }

    @Test
    fun theNameAndTheSongsWithTheirAlbumShow() {
        show(loaded)

        compose.onNodeWithText(artist.name).assertExists()
        val first = loaded.songs.first()
        compose.onNodeWithText(first.displayTitle).assertExists()
        // Songs of one album repeat its title under theirs.
        compose.onAllNodesWithText(first.album).onFirst().assertExists()
    }

    @Test
    fun okOnASongPlaysTheArtistFromIt() {
        val played = mutableListOf<Int>()
        show(loaded, onPlay = { played += it })

        compose.onNodeWithText(loaded.songs[1].displayTitle).performClick()

        assertEquals(listOf(1), played)
    }

    @Test
    fun theAlbumsPillShowsTheAlbumsAndOkOpensOne() {
        val opened = mutableListOf<String>()
        show(loaded, onAlbum = { opened += it })
        val album = artist.albums.first()

        compose.onNodeWithText("Albums").performClick()
        compose.onNodeWithText(album.title).performClick()

        assertEquals(listOf(album.id), opened)
    }

    @Test
    fun theButtonsPlayAndShuffle() {
        var plays = 0
        var shuffles = 0
        show(loaded, onPlayOrPause = { plays++ }, onShuffle = { shuffles++ })

        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithText("Shuffle").performClick()

        assertEquals(1, plays)
        assertEquals(1, shuffles)
    }
}
