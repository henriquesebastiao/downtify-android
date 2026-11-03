package com.henriquesebastiao.downtify.feature.podcasts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.PodcastEpisode
import com.henriquesebastiao.downtify.core.model.PodcastShow
import com.henriquesebastiao.downtify.ui.common.LoadError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class PodcastsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val show = PodcastShow(1, "Radiolab", "WNYC Studios", "Stories.", "", 671, 3)

    private fun episode(
        id: Long,
        title: String,
        file: String? = "Podcasts/R/$id.mp3",
        position: Double = 0.0,
        played: Boolean = false,
    ) = PodcastEpisode(id, 1, title, "", "2026-09-18T14:00:00+00:00", 1_862, null, null, file, position, played)

    @Test
    fun listsTheShowsAndOpensOne() {
        var opened: Long? = null
        compose.setContent {
            DowntifyTheme {
                PodcastsScreen(
                    PodcastsUiState(loading = false, shows = listOf(show)),
                    onBack = {},
                    onShow = { opened = it },
                    onRefresh = {},
                )
            }
        }
        compose.onNodeWithText("WNYC Studios · 671 episodes").assertIsDisplayed()
        compose.onNodeWithText("Radiolab").performClick()
        assertEquals(1L, opened)
    }

    @Test
    fun withoutShowsItSaysWhereToAddThem() {
        compose.setContent {
            DowntifyTheme {
                PodcastsScreen(PodcastsUiState(loading = false), onBack = {}, onShow = {}, onRefresh = {})
            }
        }
        compose.onNodeWithText("No podcasts yet").assertIsDisplayed()
    }

    @Test
    fun anUnreachableServerIsSaidSo() {
        compose.setContent {
            DowntifyTheme {
                PodcastsScreen(
                    PodcastsUiState(loading = false, error = LoadError.Unreachable),
                    onBack = {},
                    onShow = {},
                    onRefresh = {},
                )
            }
        }
        compose.onNodeWithText("The server can't be reached.").assertIsDisplayed()
    }

    @Test
    fun episodesShowWhereToResumeAndWhatWasPlayed() {
        compose.setContent {
            DowntifyTheme {
                ShowScreen(
                    ShowUiState(
                        loading = false,
                        show = show,
                        episodes = listOf(
                            episode(1, "The Sweetest Thing", position = 754.0),
                            episode(2, "Zoozve", played = true),
                        ),
                    ),
                    onBack = {},
                    onRefresh = {},
                    actions = EpisodeActions(),
                )
            }
        }
        compose.onNodeWithText("The Sweetest Thing").assertIsDisplayed()
        // In words on their own line; the date and length keep the data line to themselves.
        compose.onNodeWithText("Resume at 12:34").assertIsDisplayed()
        compose.onNodeWithText("Played").assertIsDisplayed()
    }

    @Test
    fun anEpisodePlaysAndOneTheServerLacksIsDownloaded() {
        val played = mutableListOf<Long>()
        val downloaded = mutableListOf<Long>()
        compose.setContent {
            DowntifyTheme {
                ShowScreen(
                    ShowUiState(
                        loading = false,
                        show = show,
                        episodes = listOf(episode(1, "Have it"), episode(2, "Lack it", file = null)),
                    ),
                    onBack = {},
                    onRefresh = {},
                    actions = EpisodeActions(
                        onPlay = { played += it.id },
                        onDownload = { downloaded += it.id },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Play Have it").performClick()
        compose.onNodeWithContentDescription("Download Lack it to the server").performClick()
        assertEquals(listOf(1L), played)
        assertEquals(listOf(2L), downloaded)
    }

    @Test
    fun anEpisodeCanBeMarkedPlayedFromItsMenu() {
        var marked: Pair<Long, Boolean>? = null
        compose.setContent {
            DowntifyTheme {
                ShowScreen(
                    ShowUiState(loading = false, show = show, episodes = listOf(episode(1, "One"))),
                    onBack = {},
                    onRefresh = {},
                    actions = EpisodeActions(onSetPlayed = { e, played -> marked = e.id to played }),
                )
            }
        }
        compose.onNodeWithContentDescription("Options for One").performClick()
        compose.onNodeWithText("Mark as played").performClick()
        assertEquals(1L to true, marked)
    }

    @Test
    fun theEpisodeBeingDownloadedShowsProgressInsteadOfAButton() {
        compose.setContent {
            DowntifyTheme {
                ShowScreen(
                    ShowUiState(
                        loading = false,
                        show = show,
                        episodes = listOf(episode(1, "Busy one", file = null)),
                        busy = setOf(1L),
                    ),
                    onBack = {},
                    onRefresh = {},
                    actions = EpisodeActions(),
                )
            }
        }
        compose.onNodeWithContentDescription("The server is downloading it").assertIsDisplayed()
    }
}
