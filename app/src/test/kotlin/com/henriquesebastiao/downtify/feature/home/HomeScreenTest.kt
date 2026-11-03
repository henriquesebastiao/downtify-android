package com.henriquesebastiao.downtify.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.ui.common.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h800dp")
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val liked = RecentContext(PlaybackContextType.Liked, "", "", null, 0)
    private val state = HomeUiState(
        loading = false,
        serverName = "nas.local",
        jumpBackIn = listOf(liked) +
            PreviewData.albums.map { RecentContext(PlaybackContextType.Album, it.id, it.title, null, 0) },
        recentlyAdded = PreviewData.albums,
        showDiscover = true,
    )

    private fun show(
        state: HomeUiState,
        onOpenRecent: (RecentContext) -> Unit = {},
        onOpenSimilar: () -> Unit = {},
        onOpenDiscover: () -> Unit = {},
        onOpenMenu: () -> Unit = {},
    ) {
        compose.setContent {
            DowntifyTheme {
                HomeScreen(
                    state = state,
                    onOpenSettings = {},
                    onOpenRecent = onOpenRecent,
                    onOpenAlbum = {},
                    onSeeAll = {},
                    onRefresh = {},
                    onOpenSimilar = onOpenSimilar,
                    onOpenDiscover = onOpenDiscover,
                    onOpenMenu = onOpenMenu,
                )
            }
        }
    }

    @Test
    fun thePageOpensOnTheServersStatusWithNoGreeting() {
        show(state)
        listOf("Good morning", "Good afternoon", "Good evening").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithText("Streaming from nas.local").assertExists()
        val section = compose.onNodeWithText("Jump back in").fetchSemanticsNode()
        assertTrue(SemanticsProperties.Heading in section.config)
    }

    @Test
    fun aRecentTileAndTheShortcutsOpenWhatTheySay() {
        var opened: RecentContext? = null
        var similar = 0
        var discover = 0
        show(state, onOpenRecent = { opened = it }, onOpenSimilar = { similar++ }, onOpenDiscover = { discover++ })
        fun tap(text: String) {
            // The page, not the carousel of recent albums inside it.
            val page =
                hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            compose.onNode(page).performScrollToNode(hasText(text))
            compose.onNodeWithText(text).performClick()
        }
        tap("Liked songs")
        assertEquals(liked, opened)
        tap("Similar tracks")
        tap("Discover")
        assertEquals(1, similar)
        assertEquals(1, discover)
    }

    @Test
    fun theMenuButtonOpensTheSideMenu() {
        var menu = 0
        show(state, onOpenMenu = { menu++ })
        compose.onNodeWithContentDescription("Open menu").performClick()
        assertEquals(1, menu)
    }

    @Test
    fun anEmptyLibrarySaysSoAndOffersNoDiscover() {
        show(HomeUiState(loading = false, serverName = "nas.local", libraryEmpty = true, showDiscover = true))
        compose.onNodeWithText("Your library is empty").assertExists()
        compose.onNodeWithText("Jump back in").assertDoesNotExist()
        compose.onNodeWithText("Discover").assertDoesNotExist()
    }

    @Test
    fun aServerThatIsntTheOnePairedSaysSoInsteadOfStreaming() {
        show(state.copy(status = ServerStatus.DifferentServer))
        // Whole, on a narrow phone too: it says what to do.
        compose.onNodeWithText("Another server answers at this address · sign in again in Settings").assertExists()
        compose.onNodeWithText("Streaming from nas.local").assertDoesNotExist()
    }
}
