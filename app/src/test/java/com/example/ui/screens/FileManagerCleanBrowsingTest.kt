package com.example.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.model.FileItem
import com.example.ui.theme.HiPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class FileManagerCleanBrowsingTest {
    @get:Rule val compose = createComposeRule()
    private val file = File("/storage/emulated/0/Download/example.bin")
    private val item = FileItem(file, file.name, file.path, false)

    @Test fun longPressSupportDoesNotShowEmptySelectionCirclesWhileBrowsing() {
        val selecting = mutableStateOf(false)
        compose.setContent {
            HiPlayerTheme {
                GenericFileItemCard(item, onLongClick = {}, showSelectionControl = selecting.value)
            }
        }
        compose.onNodeWithTag("file_selection_circle", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { selecting.value = true }
        compose.onNodeWithTag("file_selection_circle", useUnmergedTree = true).assertExists()
    }

    @Test fun rootHasNoNumericBreadcrumbOrDuplicateSectionPanels() {
        compose.setContent {
            HiPlayerTheme {
                MergedFilesView(
                    archives = emptyList(), documents = emptyList(), folderItems = listOf(item),
                    currentDir = File("/storage/emulated/0"),
                    onInspectArchive = {}, onExtractArchive = {}, onOpenDocument = {},
                    onNavigateFolder = {}, onNavigateUp = {},
                )
            }
        }
        compose.onNodeWithTag("file_folder_header").assertDoesNotExist()
        compose.onNodeWithContentDescription("Up").assertDoesNotExist()
        compose.onNodeWithText("Extractable Archives").assertDoesNotExist()
        compose.onNodeWithText("Documents & Text Files").assertDoesNotExist()
        compose.onNodeWithText("Android Folders & Storage Explorer").assertDoesNotExist()
        compose.onNodeWithText("example.bin").assertExists()
    }
}
