package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoFilePickerTest {
    @Test fun onlyOnePickerMayBeOutstanding() {
        val gate = GeckoFilePickerGate()
        val first = gate.begin()!!
        assertNull(gate.begin())
        assertTrue(gate.finish(first))
        assertNotNull(gate.begin())
    }
    @Test fun dismissedPageCannotReceiveLatePickerResultOrReplaceOutstandingPicker() {
        val gate = GeckoFilePickerGate()
        val first = gate.begin()!!
        gate.invalidate(first)
        assertNull(gate.begin())
        assertFalse(gate.finish(first))
        val next = gate.begin()!!
        assertFalse(gate.finish(first))
        assertTrue(gate.finish(next))
    }
    @Test fun mimeHintsAreNormalizedAndDeduplicated() {
        val mimes = geckoPickerMimeTypes(arrayOf(" IMAGE/PNG ", ".jpg", "image/png")) { if (it == "jpg") "image/jpeg" else null }
        assertEquals(listOf("image/jpeg", "image/png"), mimes)
    }
    @Test fun unknownOrWildcardHintDoesNotHideFiles() {
        assertTrue(geckoPickerMimeTypes(arrayOf("image/png", ".unknown")) { null }.isEmpty())
        assertTrue(geckoPickerMimeTypes(arrayOf("*/*")) { null }.isEmpty())
        assertTrue(geckoPickerMimeTypes(null) { null }.isEmpty())
    }
}
