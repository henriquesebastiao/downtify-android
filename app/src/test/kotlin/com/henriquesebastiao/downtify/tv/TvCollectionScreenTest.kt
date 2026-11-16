package com.henriquesebastiao.downtify.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Track
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
        onAddToQueue: (Track) -> Unit = {},
        onAddAllToQueue: () -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme(darkTheme = true) {
            RemoteMode {
                TvCollectionScreen(
                    state,
                    onArtist,
                    onPlay,
                    onPlayOrPause,
                    onShuffle,
                    onAddToQueue = onAddToQueue,
                    onAddAllToQueue = onAddAllToQueue,
                )
            }
        }
    }

    /** As with a remote: rows and buttons can take focus. */
    @Composable
    private fun RemoteMode(content: @Composable () -> Unit) {
        val inputMode = LocalInputModeManager.current
        remember { inputMode.requestInputMode(InputMode.Keyboard) }
        content()
    }

    @Test
    fun theButtonBesideShuffleQueuesTheWholeAlbum() {
        var queuedAll = 0
        show(loaded, onAddAllToQueue = { queuedAll++ })
        compose.waitForIdle()

        // Two "Add to queue" buttons: the header's, then the focused song's.
        compose.onAllNodesWithContentDescription("Add to queue").onFirst().performClick()

        assertEquals(1, queuedAll)
    }

    @Test
    fun aFocusedSongShowsAddToQueueReachedWithRight() {
        val queued = mutableListOf<Track>()
        show(loaded, onAddToQueue = { queued += it })
        compose.waitForIdle()

        // The page opens on its first song: only that row shows the button, after the header's.
        compose.onAllNodesWithContentDescription("Add to queue").assertCountEquals(2)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Add to queue").onLast().assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf(album.tracks.first()), queued)
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
