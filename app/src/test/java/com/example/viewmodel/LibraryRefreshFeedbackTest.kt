package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class LibraryRefreshFeedbackTest {
    @Test fun firstEmptyScanHasFeedbackAndFinishesCleanly() {
        val feedback = LibraryRefreshFeedback()
        feedback.begin(hasContent = false)
        assertTrue(feedback.visible.value)
        feedback.finish()
        assertFalse(feedback.visible.value)
    }
    @Test fun periodicEmptyRefreshNeverReopensScannerAfterFirstPass() {
        val feedback = LibraryRefreshFeedback()
        feedback.begin(false); feedback.finish()
        repeat(3) {
            feedback.request(false); feedback.begin(false)
            assertFalse(feedback.visible.value)
            feedback.finish()
        }
    }
    @Test fun cachedContentNeverGetsAScanningBannerEvenOnManualRefresh() {
        val feedback = LibraryRefreshFeedback()
        feedback.request(true); feedback.begin(true)
        assertFalse(feedback.visible.value)
        feedback.finish()
    }
    @Test fun userCanExplicitlyRescanAnEmptyLibrary() {
        val feedback = LibraryRefreshFeedback()
        feedback.begin(false); feedback.finish()
        feedback.request(true); feedback.begin(false)
        assertTrue(feedback.visible.value)
        feedback.finish(); assertFalse(feedback.visible.value)
    }
    @Test fun requestDuringScanIsRetainedForCoalescedFollowup() {
        val feedback = LibraryRefreshFeedback()
        feedback.begin(false); feedback.request(true); feedback.finish()
        feedback.begin(false); assertTrue(feedback.visible.value)
        feedback.finish()
    }
}
