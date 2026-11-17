package com.example.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit physical-device suite, not selected by the emulator workflow.
 * Generate assets with HI_PLAYER_PHYSICAL_FIXTURES=true. Missing media is a
 * test failure. Decoder metadata/heartbeat assertions are not colorimetry.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackPhysicalDeviceTest {
    @Test(timeout = 100_000) fun uhdMain10OriginalStartsRendersAndSeeks() {
        PlaybackDeviceSmokeTest().playMedia3("playback_main10_hdr10.mkv", "video/x-matroska", grade = false, expectedHdr = true)
    }
    @Test(timeout = 100_000) fun uhdMain10GradeUsesHdrAwareProcessor() {
        PlaybackDeviceSmokeTest().playMedia3("playback_main10_hdr10.mkv", "video/x-matroska", grade = true, expectedHdr = true)
    }
}
