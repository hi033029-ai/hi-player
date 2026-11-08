package com.example.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSpeedMeterTest {
    @Test fun averagesBurstsAcrossTheWindow() {
        val meter = DownloadSpeedMeter()
        meter.reset(now = 0)
        assertEquals(0, meter.sample(bytes = 0, now = 0))
        assertEquals(2_000_000, meter.sample(bytes = 800_000, now = 400_000_000))
        assertEquals(1_000_000, meter.sample(bytes = 800_000, now = 800_000_000))
        assertEquals(1_000_000, meter.sample(bytes = 1_200_000, now = 1_200_000_000))
    }

    @Test fun oldSamplesLeaveTheWindow() {
        val meter = DownloadSpeedMeter(windowNanos = 1_000_000_000)
        meter.reset(now = 0)
        meter.sample(bytes = 1_000_000, now = 400_000_000)
        assertEquals(500_000, meter.sample(bytes = 1_400_000, now = 1_200_000_000))
    }

    @Test fun retryAndBackwardBytesNeverCreateNegativeSpeed() {
        val meter = DownloadSpeedMeter()
        meter.reset(bytes = 5_000, now = 100)
        assertEquals(0, meter.sample(bytes = 4_000, now = 200))
        meter.reset(bytes = 10_000, now = 1_000)
        assertEquals(0, meter.sample(bytes = 10_000, now = 1_000))
    }
}
