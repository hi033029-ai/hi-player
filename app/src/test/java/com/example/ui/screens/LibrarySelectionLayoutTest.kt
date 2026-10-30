package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.model.FileItem
import com.example.ui.components.InlineSelectionToolbar
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class LibrarySelectionLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val directory = File("/storage/emulated/0/Download")
    private fun items(count: Int) = (0 until count).map {
        val file = File(directory, "item$it.bin")
        FileItem(file, file.name, file.path, false)
    }

    @Test fun folderTitleIsReplacedWithoutMovingFirstFile() {
        val selected = mutableStateOf(false)
        compose.setContent {
            HiPlayerTheme {
                Box(Modifier.size(360.dp, 600.dp)) {
                    FoldersBrowserView(
                        currentDir = directory, items = items(3), onNavigateFolder = {}, onNavigateUp = {},
                        onInspectArchive = {}, onExtractArchive = {}, onOpenDocument = {},
                        isSelectionMode = selected.value,
                        selectedPaths = if (selected.value) setOf(items(3).first().path) else emptySet(),
                        selectionActions = if (selected.value) ({
                            InlineSelectionToolbar(1, {}, {}, {}, {}, {})
                        }) else null,
                    )
                }
            }
        }
        val before = compose.onNodeWithText("item0.bin").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("file_folder_header").assertHeightIsEqualTo(64.dp)
        compose.runOnIdle { selected.value = true }
        compose.onNodeWithTag("inline_selection_toolbar").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
        val after = compose.onNodeWithText("item0.bin").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 0.5f)
        compose.runOnIdle { selected.value = false }
        assertEquals(before, compose.onNodeWithText("item0.bin").fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test fun storageOverviewScrollsAwayWithRootFiles() {
        compose.setContent {
            HiPlayerTheme {
                Box(Modifier.size(360.dp, 600.dp)) {
                    MergedFilesView(
                        archives = emptyList(), documents = emptyList(), folderItems = items(60), currentDir = directory,
                        onInspectArchive = {}, onExtractArchive = {}, onOpenDocument = {},
                        onNavigateFolder = {}, onNavigateUp = {},
                        headerContent = { Box(Modifier.fillMaxWidth().height(120.dp).testTag("test_storage_overview")) },
                    )
                }
            }
        }
        compose.onNodeWithTag("test_storage_overview").assertIsDisplayed()
        compose.onNodeWithText("item40.bin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("test_storage_overview").assertDoesNotExist()
    }

    @Test fun selectionActionsUseLabeledIconsAndInvokeCallbacks() {
        val called = mutableListOf<String>()
        compose.setContent {
            HiPlayerTheme {
                InlineSelectionToolbar(2, { called += "close" }, { called += "all" }, { called += "clear" },
                    { called += "info" }, { called += "delete" })
            }
        }
        listOf("Select all", "Deselect all", "Properties", "Delete", "Exit selection").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        compose.runOnIdle { assertEquals(listOf("all", "clear", "info", "delete", "close"), called) }
        compose.onNodeWithText("Select all").assertDoesNotExist()
        compose.onNodeWithTag("inline_selection_toolbar").assertHeightIsEqualTo(64.dp)
    }
}
