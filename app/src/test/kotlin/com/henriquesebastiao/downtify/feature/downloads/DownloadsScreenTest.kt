package com.henriquesebastiao.downtify.feature.downloads

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.data.offline.DownloadActivity
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.OfflineProgress
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.ServerQueueSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A phone-sized screen: the lazy list only composes what fits.
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class DownloadsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val album = DownloadItem(
        PlaybackContextType.Album,
        "alb",
        "Glass Harbor",
        null,
        OfflineProgress(total = 10, downloaded = 10, bytes = 412_000_000, overLimit = 0),
    )
    private val playlist = DownloadItem(
        PlaybackContextType.Playlist,
        "Deep Focus",
        "Deep Focus",
        null,
        OfflineProgress(total = 6, downloaded = 4, bytes = 0, overLimit = 0),
    )

    private fun state(keepLiked: Boolean = false, items: List<DownloadItem> = listOf(playlist, album)) =
        DownloadsUiState(
            loading = false,
            usedBytes = 2_400_000_000,
            limitBytes = 8_000_000_000,
            freeBytes = 5_600_000_000,
            songCount = 14,
            keepLiked = keepLiked,
            activity = DownloadActivity.WaitingForNetwork,
            items = items,
        )

    @Test
    fun showsStorageAndEachCollectionsStatus() {
        compose.setContent {
            DowntifyTheme { DownloadsScreen(state(), onKeepLiked = {}, onOpen = {}, onRemove = {}) }
        }
        compose.onNodeWithText("2.4\u00A0GB used").assertExists()
        compose.onNodeWithText("of 8.0\u00A0GB limit").assertExists()
        compose.onNodeWithText("10 songs · 412\u00A0MB").assertExists()
        compose.onNodeWithText("Downloading 4 of 6 · waiting for Wi-Fi").assertExists()
    }

    @Test
    fun togglesLikedSongsAndRemovesACollection() {
        var keep: Boolean? = null
        var removed: DownloadItem? = null
        compose.setContent {
            DowntifyTheme {
                DownloadsScreen(
                    state(keepLiked = false, items = listOf(album)),
                    onKeepLiked = { keep = it },
                    onOpen = {},
                    onRemove = { removed = it },
                )
            }
        }
        compose.onNodeWithText("Keep Liked songs offline").assertIsOff().performClick()
        assertEquals(true, keep)

        compose.onNodeWithContentDescription("Options for Glass Harbor").performClick()
        compose.onNodeWithText("Remove from this phone").performClick()
        assertEquals(album, removed)
    }

    @Test
    fun anEmptyPhoneExplainsHowToDownload() {
        compose.setContent {
            DowntifyTheme {
                DownloadsScreen(state(keepLiked = true, items = emptyList()), onKeepLiked = {
                }, onOpen = {}, onRemove = {})
            }
        }
        compose.onNodeWithText("Nothing on this phone yet").assertExists()
        compose.onNodeWithText("Keep Liked songs offline").assertIsOn()
    }

    @Test
    fun theServerTabListsTheServersQueue() {
        var tab: DownloadsTab? = null
        val jobs = listOf(
            ServerJob("1", "Harbor Song", "Nora Vale", "Harbor Nights", "", ServerJobStatus.Downloading, 42f, "", 1),
            ServerJob("2", "Low Lights", "Nora Vale", "Harbor Nights", "", ServerJobStatus.Queued, 0f, "", 2),
        )
        compose.setContent {
            DowntifyTheme {
                DownloadsScreen(
                    state(items = emptyList()).copy(
                        tab = DownloadsTab.Server,
                        serverJobs = jobs,
                        serverSummary = ServerQueueSummary.of(jobs),
                    ),
                    onKeepLiked = {},
                    onOpen = {},
                    onRemove = {},
                    onTab = { tab = it },
                )
            }
        }
        compose.onNodeWithText("The server is downloading 2 songs").assertExists()
        compose.onNodeWithText("Nora Vale · Downloading · 42%").assertExists()
        compose.onNodeWithText("#1").assertExists()
        compose.onNodeWithText("#2").assertExists()
        // The two halves read as tabs, the open one selected.
        compose.onNodeWithText("On the server").assertIsSelected()
        compose.onNodeWithText("On this phone").assertIsNotSelected().performClick()
        assertEquals(DownloadsTab.Phone, tab)
    }

    @Test
    fun itOpensAboveHomeAndBackLeavesIt() {
        var back = 0
        compose.setContent {
            DowntifyTheme {
                DownloadsScreen(state(), onKeepLiked = {}, onOpen = {}, onRemove = {}, onBack = { back++ })
            }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }
}
