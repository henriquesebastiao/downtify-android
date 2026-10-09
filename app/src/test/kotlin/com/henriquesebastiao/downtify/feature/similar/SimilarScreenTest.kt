package com.henriquesebastiao.downtify.feature.similar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.feature.search.RemoteActions
import com.henriquesebastiao.downtify.ui.common.LoadError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class SimilarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun song(id: String, title: String) = RemoteSong(
        id,
        title,
        listOf("Cher"),
        "Believe",
        "",
        224,
        "https://music.youtube.com/watch?v=$id",
        CatalogSource.YouTube,
        "",
        "1998",
        "{}",
    )

    private fun show(
        state: SimilarUiState,
        artist: String = "Cher",
        track: String = "Believe",
        onPlay: (RemoteSong, List<RemoteSong>) -> Unit = { _, _ -> },
        onDownload: (RemoteSong) -> Unit = {},
        onFind: () -> Unit = {},
        onPivot: (RemoteSong) -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme {
                SimilarScreen(
                    state = state,
                    artist = artist,
                    track = track,
                    onArtistChange = {},
                    onTrackChange = {},
                    onFind = onFind,
                    onRetry = {},
                    onBack = {},
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onPivot = onPivot,
                )
            }
        }
    }

    @Test
    fun emptyStateAsksForAnArtistOrATrack() {
        show(SimilarUiState(), artist = "", track = "")
        compose.onNodeWithText("Name an artist or a track to hear what sounds like it.").assertIsDisplayed()
    }

    @Test
    fun searchActionFindsWithOnlyOneField() {
        var found = false
        compose.setContent {
            DowntifyTheme {
                SimilarScreen(
                    state = SimilarUiState(),
                    artist = "Cher",
                    track = "",
                    onArtistChange = {},
                    onTrackChange = {},
                    onFind = { found = true },
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Cher").performImeAction()
        assertEquals(true, found)
    }

    @Test
    fun resultsPlayInFullAndDownload() {
        var played: RemoteSong? = null
        var downloaded: RemoteSong? = null
        val strong = song("v1", "Strong Enough")
        show(
            SimilarUiState(songs = listOf(strong), searched = true),
            onPlay = { song, _ -> played = song },
            onDownload = { downloaded = it },
        )
        compose.onNodeWithContentDescription("Play Strong Enough in full").performClick()
        assertEquals(strong, played)
        compose.onNodeWithText("Strong Enough").performClick()
        assertEquals(strong, played)
        compose.onNodeWithContentDescription("More options for Strong Enough").performClick()
        compose.onNodeWithText("Download Strong Enough to the server").performClick()
        assertEquals(strong, downloaded)
    }

    @Test
    fun resultsOfferSimilarFromTheRowMenu() {
        var pivoted: RemoteSong? = null
        val strong = song("v1", "Strong Enough")
        show(
            SimilarUiState(songs = listOf(strong), searched = true),
            onPivot = { pivoted = it },
        )
        compose.onNodeWithContentDescription("More options for Strong Enough").performClick()
        compose.onNodeWithText("Tracks like Strong Enough").performClick()
        assertEquals(strong, pivoted)
    }

    @Test
    fun aFailedSearchOffersToTryAgain() {
        var retried = false
        compose.setContent {
            DowntifyTheme {
                SimilarScreen(
                    state = SimilarUiState(searched = true, error = LoadError.Failed),
                    artist = "Cher",
                    track = "Believe",
                    onArtistChange = {},
                    onTrackChange = {},
                    onFind = {},
                    onRetry = { retried = true },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun typingAndFindingSearches() {
        var found = false
        val typed = androidx.compose.runtime.mutableStateOf("")
        compose.setContent {
            DowntifyTheme {
                SimilarScreen(
                    state = SimilarUiState(),
                    artist = typed.value,
                    track = "Believe",
                    onArtistChange = { typed.value = it },
                    onTrackChange = {},
                    onFind = { found = true },
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Artist").performTextInput("Cher")
        assertEquals("Cher", typed.value)
        compose.onNodeWithText("Cher").performImeAction()
        assertEquals(true, found)
    }
}
