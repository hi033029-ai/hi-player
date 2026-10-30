package com.example.player

import android.net.Uri
import com.example.model.VideoItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderPlaybackQueueTest {
    private fun video(path: String) = VideoItem(uri = Uri.fromFile(java.io.File(path)), path = path)

    @Test fun nextStaysInExactFolderAndPreservesDisplayedOrder() {
        val a = video("/storage/Movies/a.mp4")
        val b = video("/storage/Movies/b.mp4")
        val other = video("/storage/Download/z.mp4")
        assertEquals(b, FolderPlaybackQueue.adjacent(a, listOf(a, other, b), 1))
        assertEquals(a, FolderPlaybackQueue.adjacent(b, listOf(a, other, b), -1))
    }
    @Test fun identicalFolderLabelsAreNotTheSameFolder() {
        val a = video("/storage/card1/Movies/a.mp4")
        val b = video("/storage/card2/Movies/b.mp4")
        assertNull(FolderPlaybackQueue.adjacent(a, listOf(a, b), 1))
    }
    @Test fun endsAndUnknownCurrentDoNotJumpToFirstFile() {
        val a = video("/storage/Movies/a.mp4")
        val b = video("/storage/Movies/b.mp4")
        assertNull(FolderPlaybackQueue.adjacent(b, listOf(a, b), 1))
        assertNull(FolderPlaybackQueue.adjacent(a, listOf(a, b), -1))
        assertNull(FolderPlaybackQueue.adjacent(a, listOf(b), 1))
    }
    @Test fun networkAndUnresolvedContentUrisNeverAutoJoinLocalQueue() {
        val remote = VideoItem(uri = Uri.parse("https://example.com/movie.mp4"), path = "/storage/Movies/a.mp4")
        val unresolved = VideoItem(uri = Uri.parse("content://media/external/video/media/3"))
        val local = video("/storage/Movies/b.mp4")
        assertNull(FolderPlaybackQueue.adjacent(remote, listOf(remote, local), 1))
        assertNull(FolderPlaybackQueue.adjacent(unresolved, listOf(unresolved, local), 1))
    }
    @Test fun normalizedParentsAndDuplicateEntriesKeepOnePosition() {
        val a = video("/storage/Movies/./a.mp4")
        val b = video("/storage/Movies/b.mp4")
        assertEquals(b, FolderPlaybackQueue.adjacent(a, listOf(a, a, b), 1))
    }
}
