package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoContextActionsTest {
    private var active = 1 to false
    private val opened = mutableListOf<Pair<Boolean, String>>()
    private val copied = mutableListOf<String>()
    private val shared = mutableListOf<String>()
    private var canOpen = true
    private val actions = GeckoContextActions({ active }, { privateTab, url -> if (canOpen) opened.add(privateTab to url); canOpen },
        { copied.add(it); true }, { shared.add(it); true })

    @Test fun explicitOpenUsesCurrentPrivacyMode() {
        active = 2 to true
        actions.offer(2, "https://example.org/a", null)
        assertTrue(opened.isEmpty())
        actions.open()
        assertEquals(listOf(true to "https://example.org/a"), opened)
        assertNull(actions.request)
    }
    @Test fun acceptsImageIfLinkInvalidAndRejectsUnsafeSchemes() {
        actions.offer(1, "javascript:alert(1)", "https://example.org/p.png")
        assertTrue(actions.request!!.image)
        actions.copy()
        assertEquals(listOf("https://example.org/p.png"), copied)
        actions.offer(1, "https://user:secret@example.org", "file:///data/secret")
        assertNull(actions.request)
        actions.offer(1, "http://example.org:99999/", null)
        assertNull(actions.request)
    }
    @Test fun prefersLinkOverImageAndSharesOnlyOnTap() {
        actions.offer(1, "https://example.org/", "https://example.org/p.png")
        assertFalse(actions.request!!.image)
        assertTrue(shared.isEmpty())
        actions.share()
        assertEquals(listOf("https://example.org/"), shared)
    }
    @Test fun staleAndBackgroundTabsCannotAct() {
        actions.offer(2, "https://example.org", null)
        assertNull(actions.request)
        actions.offer(1, "https://example.org", null)
        active = 2 to true
        actions.share()
        assertTrue(shared.isEmpty())
        assertNull(actions.request)
    }
    @Test fun limitFailureIsVisibleAndDoesNotInventNewTab() {
        actions.offer(1, "https://example.org", null)
        canOpen = false
        actions.open()
        assertTrue(actions.failed)
        assertTrue(opened.isEmpty())
        actions.dismissFailure()
        assertFalse(actions.failed)
    }
}
