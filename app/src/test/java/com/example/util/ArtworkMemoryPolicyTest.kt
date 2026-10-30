package com.example.util

import org.junit.Assert.*
import org.junit.Test

class ArtworkMemoryPolicyTest {
    @Test fun smallHeapsDoNotReceiveAFixed24MegabyteFloor() {
        assertEquals(4 * 1024, ArtworkMemoryPolicy.cacheBudgetKb(64L * 1024))
        assertEquals(2 * 1024, ArtworkMemoryPolicy.cacheBudgetKb(64L * 1024, audio = true))
    }
    @Test fun largeHeapsStillHaveBoundedImageBudgets() {
        assertEquals(32 * 1024, ArtworkMemoryPolicy.cacheBudgetKb(4L * 1024 * 1024))
        assertEquals(12 * 1024, ArtworkMemoryPolicy.cacheBudgetKb(4L * 1024 * 1024, audio = true))
    }
    @Test fun sampledDecodingUsesPowerOfTwoDownsampling() {
        assertEquals(16, ArtworkMemoryPolicy.sampleSize(4000, 3000, 192))
        assertEquals(1, ArtworkMemoryPolicy.sampleSize(64, 64, 192))
        assertEquals(1, ArtworkMemoryPolicy.sampleSize(0, 0, 192))
        assertEquals(1, ArtworkMemoryPolicy.sampleSize(4000, 3000, 0))
    }
}
