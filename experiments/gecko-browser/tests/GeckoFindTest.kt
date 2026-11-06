package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoFindTest {
    private data class Request(val id: Int, val query: String?, val backwards: Boolean, val done: (GeckoFindResult?) -> Unit)
    @Test fun findsAgainAndShowsTruthfulCounts() {
        val requests = mutableListOf<Request>()
        val cleared = mutableListOf<Int>()
        val find = GeckoFindController({ id, query, backwards, cb -> requests += Request(id, query, backwards, cb) }, { cleared += it })
        find.open(2, "https://example.org")
        find.changeQuery("word")
        assertEquals("word", requests.single().query)
        requests.last().done(GeckoFindResult(true, 2, 5)); assertEquals("2/5", find.label)
        find.next(true); assertNull(requests.last().query); assertTrue(requests.last().backwards)
        requests.last().done(GeckoFindResult(true, 1, -1)); assertEquals("1/…", find.label)
        find.next(false); assertFalse(requests.last().backwards)
        requests.last().done(GeckoFindResult(false, 0, 0)); assertEquals("0/0", find.label)
        find.dismiss(); assertEquals(listOf(2), cleared); assertFalse(find.visible)
    }
    @Test fun staleCallbacksCannotOverwriteNewQueryOrClosedPanel() {
        val requests = mutableListOf<Request>()
        val find = GeckoFindController({ id, query, backwards, cb -> requests += Request(id, query, backwards, cb) }, {})
        find.open(1, "https://one.test")
        find.changeQuery("old"); find.changeQuery("new")
        requests[0].done(GeckoFindResult(true, 1, 9)); assertEquals("", find.label)
        requests[1].done(GeckoFindResult(true, 1, 2)); assertEquals("1/2", find.label)
        find.dismiss(); requests[1].done(GeckoFindResult(true, 2, 2)); assertEquals("", find.label)
    }
    @Test fun blankQueryClearsAndPageSwitchDismissesOldSession() {
        val cleared = mutableListOf<Int>()
        val requests = mutableListOf<Request>()
        val find = GeckoFindController({ id, query, backwards, cb -> requests += Request(id, query, backwards, cb) }, { cleared += it })
        find.open(3, "https://one.test"); find.changeQuery("hello"); find.changeQuery("")
        assertEquals(listOf(3), cleared); requests.single().done(GeckoFindResult(true, 1, 1)); assertEquals("", find.label)
        find.pageChanged(4, "https://two.test", false)
        assertFalse(find.visible); assertEquals(listOf(3, 3), cleared)
        find.open(4, "https://two.test"); find.pageChanged(4, "https://two.test", true)
        assertFalse(find.visible)
    }
    @Test fun failureIsShownAndCloseRejectsLateResults() {
        var callback: ((GeckoFindResult?) -> Unit)? = null
        val find = GeckoFindController({ _, _, _, cb -> callback = cb }, {})
        find.open(1, "https://example.org"); find.changeQuery("hi")
        callback!!(null); assertEquals("Unavailable", find.label)
        find.changeQuery("next"); find.close(); callback!!(GeckoFindResult(true, 1, 1))
        assertFalse(find.visible); assertEquals("", find.label)
        find.open(1, "https://example.org"); assertFalse(find.visible)
    }
}
