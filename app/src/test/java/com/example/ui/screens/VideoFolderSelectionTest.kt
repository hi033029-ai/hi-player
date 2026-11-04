package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFolderSelectionTest {
    @Test fun treeFolderIncludesDescendantsButNotSiblingPrefixes() {
        assertTrue(videoBelongsToSelectedFolder("path:/storage/Movies", "/storage/Movies/a/b.mp4", "a"))
        assertFalse(videoBelongsToSelectedFolder("path:/storage/Movies", "/storage/Movies2/a.mp4", "Movies2"))
        assertFalse(videoBelongsToSelectedFolder("path:/storage/Movies", "", "Movies"))
    }

    @Test fun flatFolderUsesTheDisplayedFolderName() {
        assertTrue(videoBelongsToSelectedFolder("name:Download", "/one/Download/a.mp4", "Download"))
        assertTrue(videoBelongsToSelectedFolder("name:Download", "/two/Download/b.mp4", "Download"))
        assertFalse(videoBelongsToSelectedFolder("name:Download", "/one/Downloads/a.mp4", "Downloads"))
    }
}
