package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.downloads.*
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.MonochromeLightPalette
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserDownloadUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun progressMenuContainsFunctionalVideoActionsAndFiltersRows() {
        val rows = listOf(
            BrowserDownload(1, "active.mkv", "https://example.test/movie", "video/x-matroska", downloadedBytes = 1000000, totalBytes = 10000000, status = BrowserDownloadStatus.RUNNING),
            BrowserDownload(2, "done.mp4", "https://example.test/done", "video/mp4", status = BrowserDownloadStatus.COMPLETED),
        )
        compose.setContent { CompositionLocalProvider(LocalHiPalette provides MonochromeLightPalette) { Box(Modifier.size(400.dp, 760.dp)) { BrowserDownloadsPanel(rows, "", {}) } } }
        compose.onNodeWithContentDescription("Pause download").assertExists()
        compose.onNodeWithContentDescription("Options for active.mkv").performClick()
        compose.onNodeWithText("Play while downloading").assertExists()
        compose.onNodeWithText("Rename").assertExists()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Saved to: Download").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Filter downloads: All").performClick()
        compose.onNodeWithText("Completed").performClick()
        compose.onNodeWithText("active.mkv").assertDoesNotExist()
        compose.onNodeWithText("done.mp4").assertExists()
    }
    @Test fun confirmationShowsEditableNameDefaultFolderAndWorkingCancel() {
        var cancelled = false
        val request = BrowserDownloadRequest("http://127.0.0.1:9/video.mp4", "Test", "", "video/mp4", "video.mp4", 1024)
        compose.setContent { CompositionLocalProvider(LocalHiPalette provides MonochromeLightPalette) { BrowserDownloadSheet(request, { cancelled = true }, { _, _, _, _, _ -> }, {}) } }
        compose.onNodeWithText("File name").assertExists()
        compose.onAllNodesWithText("Download")[0].assertExists()
        compose.onNodeWithText("Play now").assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }
}
