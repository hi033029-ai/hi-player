package com.example.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.HiPlayerTheme
import com.example.viewmodel.LibraryNavMode
import com.example.viewmodel.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class VideoLibraryHeaderControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun categoriesAndActionsAreAvailableWithNoVideos() {
        val selected = mutableStateOf(LibraryNavMode.FOLDERS)
        compose.setContent { HiPlayerTheme {
            VideoLibraryHeaderControls(
                modes = listOf(LibraryNavMode.FOLDERS, LibraryNavMode.ALL_VIDEOS, LibraryNavMode.RECENT),
                selected = selected.value, folderCount = 0, videoCount = 0, recentCount = 0, favoriteCount = 0,
                query = "", onQueryChange = {}, viewMode = LibraryViewMode.LAYER_LIST,
                onToggleView = {}, onSort = {}, onSelect = { selected.value = it },
            )
        } }
        compose.onNodeWithTag("video_library_stable_controls").assertIsDisplayed()
        compose.onNodeWithTag("video_category_FOLDERS").assertExists()
        compose.onNodeWithTag("video_category_ALL_VIDEOS").performClick()
        compose.runOnIdle { assertEquals(LibraryNavMode.ALL_VIDEOS, selected.value) }
        compose.onNodeWithTag("view_mode_toggle").assertIsDisplayed()
        compose.onNodeWithTag("sort_by_button").assertIsDisplayed()
    }
}
