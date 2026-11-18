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

    @Test fun flatFolderMatchesOnlyItsDirectFilesEvenWhenNamesRepeat() {
        val key = com.example.model.videoFolderKey("/one/Download/a.mp4", "Download")
        assertTrue(videoBelongsToSelectedFolder(key, "/one/Download/b.mp4", "Download"))
        assertFalse(videoBelongsToSelectedFolder(key, "/two/Download/a.mp4", "Download"))
        assertFalse(videoBelongsToSelectedFolder(key, "/one/Download/child/a.mp4", "child"))
        assertFalse(videoBelongsToSelectedFolder(key, "", "Download"))
        assertTrue(videoBelongsToSelectedFolder("name:Download", "", "Download"))
    }
}
