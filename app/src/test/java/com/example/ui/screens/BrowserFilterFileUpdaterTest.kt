package com.example.ui.screens

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BrowserFilterFileUpdaterTest {
    @Test fun updateSourcesRequireApprovedHttpsHosts() {
        assertTrue(BrowserFilterFileUpdater.permittedSource("https://easylist.to/easylist/easylist.txt"))
        assertTrue(BrowserFilterFileUpdater.permittedSource("https://pgl.yoyo.org/adservers/serverlist.php?mimetype=plaintext"))
        listOf("http://easylist.to/list", "https://easylist.to.evil.invalid/list",
            "https://easylist.to@evil.invalid/list", "https://evil.invalid/list",
            "https://easylist.to:8443/list").forEach {
            assertFalse(it, BrowserFilterFileUpdater.permittedSource(it))
        }
    }
    @Test fun emptyHtmlAndTruncatedListsCannotReplaceWorkingRules() {
        val file = File.createTempFile("filter-test", ".txt")
        try {
            listOf("", "<html>Service unavailable</html>", "! list\n||ads.example^").forEach {
                file.writeText(it); assertFalse(BrowserFilterFileUpdater.validate(file))
            }
            file.writeText("[Adblock Plus 2.0]\n" + (1..100).joinToString("\n") { "||ads$it.example^" })
            assertTrue(BrowserFilterFileUpdater.validate(file))
        } finally { file.delete() }
    }
}
