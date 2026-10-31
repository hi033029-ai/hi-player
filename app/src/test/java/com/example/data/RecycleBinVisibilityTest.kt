package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleBinVisibilityTest {
    @Test fun hidesExactPathAndChildrenButNotPrefixSiblings() {
        val hidden = setOf("/storage/videos/movie")
        assertTrue(RecycleBinVisibility.isHidden("/storage/videos/movie", hidden))
        assertTrue(RecycleBinVisibility.isHidden("/storage/videos/movie/clip.mp4", hidden))
        assertFalse(RecycleBinVisibility.isHidden("/storage/videos/movie-extra/clip.mp4", hidden))
        assertFalse(RecycleBinVisibility.isHidden("", hidden))
    }

    @Test fun failedAndUnprocessedItemsReappearWhileSuccessfulMovesStayHidden() {
        val success = "/test-recycle/success.mp4"
        val failed = "/test-recycle/failed.mp3"
        val unprocessed = "/test-recycle/unprocessed.pdf"
        val requested = setOf(success, failed, unprocessed)
        try {
            RecycleBinVisibility.begin(requested)
            assertTrue(RecycleBinVisibility.hiddenPaths.value.containsAll(requested))
            RecycleBinVisibility.finish(requested, setOf(success))
            assertTrue(success in RecycleBinVisibility.hiddenPaths.value)
            assertFalse(failed in RecycleBinVisibility.hiddenPaths.value)
            assertFalse(unprocessed in RecycleBinVisibility.hiddenPaths.value)
        } finally {
            RecycleBinVisibility.finish(requested, emptySet())
            requested.forEach(RecycleBinVisibility::restored)
        }
    }

    @Test fun restoreReleasesCommittedVisibility() {
        val path = "/test-recycle/restored.flac"
        try {
            RecycleBinVisibility.loadCommitted(setOf(path))
            assertTrue(path in RecycleBinVisibility.hiddenPaths.value)
            RecycleBinVisibility.restored(path)
            assertFalse(path in RecycleBinVisibility.hiddenPaths.value)
        } finally { RecycleBinVisibility.restored(path) }
    }
}
