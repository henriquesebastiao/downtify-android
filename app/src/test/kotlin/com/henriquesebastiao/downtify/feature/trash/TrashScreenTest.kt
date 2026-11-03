package com.henriquesebastiao.downtify.feature.trash

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashPreview
import com.henriquesebastiao.downtify.core.model.TrashState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A phone-sized screen: the lazy list only composes what fits.
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class TrashScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_790_000_000_000L
    private val kept = TrashItem(
        "1",
        "Nora Vale/Harbor Song.flac",
        title = "Harbor Song",
        artist = "Nora Vale",
        codec = "flac",
        state = TrashState.Trashed,
        expiresAt = now + 86_400_000,
    )
    private val stopped = TrashItem(
        "2",
        "Lumen Field/Tides.m4a",
        title = "Tides",
        state = TrashState.Error,
        error = "restore_collision",
    )

    private fun show(
        state: TrashUiState,
        onRestore: (TrashItem) -> Unit = {},
        onRetry: (TrashItem) -> Unit = {},
        onTab: (TrashTab) -> Unit = {},
    ) = compose.setContent {
        DowntifyTheme {
            TrashScreen(
                state = state,
                search = "",
                onSearch = {},
                onTab = onTab,
                onRestore = onRestore,
                onRetry = onRetry,
                onAllowDownload = {},
                onLoadMore = {},
                onDismissError = {},
                nowMillis = now,
            )
        }
    }

    @Test
    fun keptSongsRestoreAndStoppedOnesSayWhyAndTryAgain() {
        val restored = mutableListOf<TrashItem>()
        val retried = mutableListOf<TrashItem>()
        show(
            TrashUiState(loading = false, items = listOf(kept, stopped), total = 2, bytes = 1_000),
            onRestore = { restored += it },
            onRetry = { retried += it },
        )
        compose.onNodeWithText("Harbor Song").assertIsDisplayed()
        compose.onNodeWithText("Restore").performClick()
        assertEquals(listOf(kept), restored)
        compose.onNodeWithText(
            "Another file is already in its place. Nothing was overwritten; the song stays in the trash.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf(stopped), retried)
    }

    @Test
    fun anEmptyTrashSaysSo() {
        show(TrashUiState(loading = false))
        compose.onNodeWithText("The trash is empty").assertIsDisplayed()
    }

    @Test
    fun anAccountThatIsntAnAdminSeesNothingToManage() {
        show(TrashUiState(loading = false, canManage = false, items = listOf(kept), total = 1))
        compose.onNodeWithText("Only admins can see this").assertIsDisplayed()
        compose.onAllNodesWithText("Harbor Song").assertCountEquals(0)
    }

    @Test
    fun theOtherTabListsWhatDownloadsSkip() {
        val tabs = mutableListOf<TrashTab>()
        show(TrashUiState(loading = false), onTab = { tabs += it })
        compose.onNodeWithText("Kept out of downloads").performClick()
        assertEquals(listOf(TrashTab.Exclusions), tabs)
    }

    @Test
    fun theConfirmationListsEveryFile() {
        val preview = TrashPreview("token", listOf(kept.copy(state = TrashState.Unknown), stopped))
        var confirmed = 0
        compose.setContent {
            DowntifyTheme {
                MoveToTrashDialogs(
                    MoveToTrashState.Confirm(preview, retentionDays = 30),
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Move 2 songs to the trash?").assertIsDisplayed()
        compose.onNodeWithText("FLAC · Nora Vale/Harbor Song.flac").assertIsDisplayed()
        compose.onNodeWithText("M4A · Lumen Field/Tides.m4a").assertIsDisplayed()
        compose.onNodeWithText("Move to trash").performClick()
        assertEquals(1, confirmed)
    }
}
