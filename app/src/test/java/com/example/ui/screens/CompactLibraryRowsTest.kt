package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.model.VideoItem
import com.example.ui.components.FolderItemRow
import com.example.ui.components.VideoItemCard
import com.example.ui.theme.HiPlayerTheme
import com.example.viewmodel.VideoFolder
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class CompactLibraryRowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun folderAndVideoRowsFitTheirContentAndGrowWithText() {
        val scale = mutableFloatStateOf(com.example.data.AppTextSize.scale(com.example.data.AppTextSize.DEFAULT))
        compose.setContent {
            HiPlayerTheme(uiTextScale = scale.floatValue) {
                Column(Modifier.width(336.dp)) {
                    Box(Modifier.testTag("folder_row")) {
                        FolderItemRow(VideoFolder("Camera", 1, null), onClick = {})
                    }
                    Box(Modifier.testTag("video_row")) {
                        VideoItemCard(VideoItem(title = "My video", folderName = "Camera"), onClick = {},
                            onFavoriteToggle = {}, onPlayInBackground = {}, onShowDetails = {})
                    }
                }
            }
        }
        fun height(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val folderHeight = height("folder_row")
        val videoHeight = height("video_row")
        val favorite = compose.onNodeWithContentDescription("Add to Favorites").getUnclippedBoundsInRoot()
        val options = compose.onNodeWithContentDescription("Options").getUnclippedBoundsInRoot()
        assertTrue("Actions must share one row", favorite.top == options.top && favorite.right <= options.left)
        compose.onNodeWithContentDescription("Options").assertHasClickAction()
        compose.onNodeWithContentDescription("Add to Favorites").assertHasClickAction()
        compose.runOnIdle { scale.floatValue = 1.4f }
        assertTrue(height("folder_row") >= folderHeight)
        assertTrue(height("video_row") >= videoHeight)
        compose.onNodeWithText("My video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Properties").assertIsDisplayed()
    }
}
