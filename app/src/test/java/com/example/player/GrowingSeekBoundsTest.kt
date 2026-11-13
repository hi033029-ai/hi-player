package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Test

class GrowingSeekBoundsTest {
    @Test
    fun `forward seek is limited to buffer with a safety margin`() {
        assertEquals(19_250L, GrowingSeekBounds.clamp(30_000L, 10_000L, 20_000L, isGrowing = true))
    }

    @Test
    fun `forward seek stays at current position when no future media is buffered`() {
        assertEquals(10_000L, GrowingSeekBounds.clamp(20_000L, 10_000L, 0L, isGrowing = true))
    }

    @Test
    fun `backward seeks remain available while file grows`() {
        assertEquals(5_000L, GrowingSeekBounds.clamp(5_000L, 10_000L, 12_000L, isGrowing = true))
    }

    @Test
    fun `completed media does not receive growing-file clamp`() {
        assertEquals(30_000L, GrowingSeekBounds.clamp(30_000L, 10_000L, 12_000L, isGrowing = false))
    }
}
