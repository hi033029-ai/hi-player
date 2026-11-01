package com.example.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueTest {
    @Test fun nextAndPreviousFollowDisplayedNotAlphabeticalOrder() {
        val queue = PlaybackQueue.snapshot(listOf("c", "a", "b")) { it }
        assertEquals("a", PlaybackQueue.adjacent("c", queue, 1) { it })
        assertEquals("c", PlaybackQueue.adjacent("a", queue, -1) { it })
    }
    @Test fun boundariesAndUnknownItemsNeverWrapOrJump() {
        val queue = listOf("c", "a", "b")
        assertNull(PlaybackQueue.adjacent("b", queue, 1) { it })
        assertNull(PlaybackQueue.adjacent("c", queue, -1) { it })
        assertNull(PlaybackQueue.adjacent("missing", queue, 1) { it })
        assertNull(PlaybackQueue.adjacent("a", queue, 0) { it })
    }
    @Test fun duplicateUrisKeepTheirFirstDisplayedPosition() {
        assertEquals(listOf("b", "a"), PlaybackQueue.snapshot(listOf("b", "b", "a")) { it })
    }
    @Test fun refreshingTheSourceDoesNotReorderTheSession() {
        val source = mutableListOf("b", "a", "c")
        val queue = PlaybackQueue.snapshot(source) { it }
        source.reverse()
        assertEquals("a", PlaybackQueue.adjacent("b", queue, 1) { it })
    }
    @Test fun identityIsUriNotSharedMediaStoreIdOrTitle() {
        data class Item(val id: Long, val title: String, val uri: String)
        val first = Item(0, "Clip", "content://media/1")
        val second = Item(0, "Clip", "content://media/2")
        val queue = PlaybackQueue.snapshot(listOf(first, second)) { it.uri }
        assertEquals(second, PlaybackQueue.adjacent(first, queue, 1) { it.uri })
    }
    @Test fun aDisplayedAllFilesQueueCanCrossFolders() {
        val queue = listOf("/Movies/a", "/Download/b")
        assertEquals("/Download/b", PlaybackQueue.adjacent(queue[0], queue, 1) { it })
    }
}
