package com.example.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserAdvancedRulesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun prepare() { BrowserAdvancedRules.initialize(context); BrowserAdvancedRules.save(context, BrowserRuleSnapshot()) }
    @After fun clear() { BrowserAdvancedRules.save(context, BrowserRuleSnapshot()) }
    @Test fun acceptsHostnamesNotCredentialUrlsOrWildcardRules() {
        assertEquals("example.com", BrowserAdvancedRules.host("EXAMPLE.com."))
        assertEquals("xn--bcher-kva.example", BrowserAdvancedRules.host("bücher.example"))
        listOf("https://example.com", "user:pass@example.com", "*.example.com", "example.com/path", "a..com", "-a.com").forEach { assertNull(it, BrowserAdvancedRules.host(it)) }
    }
    @Test fun blocksWinWithExactSuffixBoundaries() {
        BrowserAdvancedRules.save(context, BrowserRuleSnapshot(blocked = setOf("blocked.example"), allowed = setOf("child.blocked.example")))
        assertEquals(true, BrowserAdvancedRules.decision("child.blocked.example", "site.example"))
        assertNull(BrowserAdvancedRules.decision("notblocked.example", "site.example"))
        assertNull(BrowserAdvancedRules.decision("blocked.example.evil.test", "site.example"))
    }
    @Test fun pauseIsPageScopedAndDoesNotOverrideExplicitBlocks() {
        BrowserAdvancedRules.save(context, BrowserRuleSnapshot(blocked = setOf("blocked.example"), sites = mapOf("site.example" to BrowserSiteTools(pauseBlocking = true))))
        assertEquals(false, BrowserAdvancedRules.decision("tracker.example", "site.example"))
        assertNull(BrowserAdvancedRules.decision("tracker.example", "other.example"))
        assertEquals(true, BrowserAdvancedRules.decision("blocked.example", "site.example"))
    }
    @Test fun skipperOptionsAndPauseWrapBothInjectionPaths() {
        BrowserAdvancedRules.save(context, BrowserRuleSnapshot(sites = mapOf("site.example" to BrowserSiteTools(true, 10, false))))
        val script = BrowserAdvancedRules.wrap("orbit_page_skipper.js", "/* payload */")
        assertTrue(script.contains("__hiSkipOptions"))
        assertTrue(script.contains("\"speed\":10"))
        assertTrue(script.contains("\"continue\":false"))
        assertTrue(BrowserAdvancedRules.wrap("orbit_youtube.js", "/* payload */").contains("if(p.pause)return"))
        assertEquals("unchanged", BrowserAdvancedRules.wrap("orbit_desktop_viewport.js", "unchanged"))
    }
}
