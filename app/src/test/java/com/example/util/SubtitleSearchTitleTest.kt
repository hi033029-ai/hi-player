package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSearchTitleTest {
    @Test fun movieReleaseDetailsAreRemovedFromTheInitialQuery() {
        assertEquals("The Odyssey", subtitleSearchTitle("The Odyssey (2026) IMAX 2160p UHD BluRay REMUX HDR 10bit HEVC Hindi-Tamil.mkv"))
        assertEquals("Blade Runner 2049", subtitleSearchTitle("Blade.Runner.2049.4K.HDR.mkv.part"))
        assertEquals("1917", subtitleSearchTitle("1917.Blu-ray.1080p.mkv"))
    }

    @Test fun episodeNumbersAndNonLatinTitlesArePreserved() {
        assertEquals("The Blue Road S01E02", subtitleSearchTitle("The.Blue.Road.S01E02.WEB-DL.DDP5.1.mkv"))
        assertEquals("الرحلة", subtitleSearchTitle("الرحلة_HDR10_HEVC.mp4"))
    }

    @Test fun aStreamAddressIsNeverUsedAsTheQuery() {
        assertEquals("", subtitleSearchTitle("https://example.org/private/video.mkv?token=secret"))
    }
}
