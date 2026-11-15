package com.henriquesebastiao.downtify.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.feature.collection.CollectionKind
import com.henriquesebastiao.downtify.feature.collection.CollectionUiState
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvCollectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val album = PreviewData.albums.first()
    private val loaded = CollectionUiState(
        loading = false,
        title = album.title,
        artist = album.artist,
        artistId = "artist-1",
        year = "1996",
        tracks = album.tracks,
    )

    private fun show(
        state: CollectionUiState,
        onArtist: (String) -> Unit = {},
        onPlay: (Int) -> Unit = {},
        onPlayOrPause: () -> Unit = {},
        onShuffle: () -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) { TvCollectionScreen(state, onArtist, onPlay, onPlayOrPause, onShuffle) }
    }

    @Test
    fun theHeaderSaysWhatItIsAndItsSongsAreListed() {
        show(loaded)

        compose.onNodeWithText(album.title).assertExists()
        // The header's link comes first; each song repeats the artist under its title.
        compose.onAllNodesWithText(album.artist).onFirst().assertExists()
        compose.onNodeWithText(album.tracks.first().displayTitle).assertExists()
    }

    @Test
    fun okOnASongPlaysFromIt() {
        val played = mutableListOf<Int>()
        show(loaded, onPlay = { played += it })

        compose.onNodeWithText(album.tracks[1].displayTitle).performClick()

        assertEquals(listOf(1), played)
    }

    @Test
    fun theArtistOpensTheirPageAndTheButtonsPlayOrShuffle() {
        val artists = mutableListOf<String>()
        var plays = 0
        var shuffles = 0
        show(loaded, onArtist = { artists += it }, onPlayOrPause = { plays++ }, onShuffle = { shuffles++ })

        compose.onAllNodesWithText(album.artist).onFirst().performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Shuffle").performClick()

        assertEquals(listOf("artist-1"), artists)
        assertEquals(1, plays)
        assertEquals(1, shuffles)
    }

    @Test
    fun likedSongsIsNamedAsSuch() {
        show(loaded.copy(kind = CollectionKind.Liked, title = "", artist = ""))

        compose.onNodeWithText("Liked songs").assertExists()
    }

    @Test
    fun aCollectionThatIsGoneSaysSo() {
        show(CollectionUiState(loading = false, notFound = true))

        compose.onNodeWithText("This isn't in your library anymore.").assertExists()
    }
}
