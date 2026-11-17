package com.example.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.SeekMap
import com.example.player.ColorPresets
import com.example.player.shouldApplyColorPreset
import com.example.player.shouldUseNativeHdrOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class GrowingMkvSeekableExtractorTest {
    @Test
    fun indexesExactClusterHeaderOffsetsAndTimecodeScale() {
        val content = minimalMkv(
            cluster(timecode = 0),
            cluster(timecode = 1_000),
        )
        val source = MemoryByteSource(content)
        val index = GrowingMkvClusterIndex(
            sourceFactory = GrowingMkvByteSourceFactory { source },
            isDownloading = { true },
        )

        val points = index.scanAvailable()
        val expectedOffsets = clusterHeaderOffsets(content)

        assertEquals(2, points.size)
        assertEquals(0L, points[0].timeUs)
        assertEquals(expectedOffsets[0], points[0].position)
        assertEquals(1_000_000L, points[1].timeUs)
        assertEquals(expectedOffsets[1], points[1].position)
        assertEquals(content.size.toLong(), index.lastScannedOffset)
    }

    @Test
    fun doesNotIndexClusterUntilItsEntireDeclaredSizeIsInsideSafeFrontier() {
        val first = cluster(timecode = 0)
        val second = cluster(timecode = 1_000)
        val partialSecond = second.copyOf(second.size - 1)
        val content = minimalMkv(first, partialSecond)
        val source = MemoryByteSource(content)
        val index = GrowingMkvClusterIndex(
            sourceFactory = GrowingMkvByteSourceFactory { source },
            isDownloading = { true },
        )

        assertEquals(1, index.scanAvailable().size)
    }

    @Test
    fun rescansOnlyNewlyAppendedElementsAndSeekabilityBecomesAvailableAtTwoClusters() {
        val first = minimalMkv(cluster(timecode = 0))
        val source = MemoryByteSource(first)
        val index = GrowingMkvClusterIndex(
            sourceFactory = GrowingMkvByteSourceFactory { source },
            isDownloading = { true },
        )
        val seekMap = GrowingMkvSeekMap(SeekMap.Unseekable(10_000_000L), index)

        assertFalse(seekMap.isSeekable)
        val previousScannedOffset = index.lastScannedOffset

        source.append(cluster(timecode = 1_000))

        assertTrue(seekMap.isSeekable)
        assertEquals(2, index.scanAvailable().size)
        assertTrue(index.lastScannedOffset > previousScannedOffset)
        assertEquals(1_000_000L, seekMap.getSeekPoints(1_500_000L).first.timeUs)
    }

    @Test
    fun stopsUpdatingPartialIndexAfterRegistryReportsDownloadComplete() {
        val source = MemoryByteSource(minimalMkv(cluster(timecode = 0)))
        var downloading = true
        val index = GrowingMkvClusterIndex(
            sourceFactory = GrowingMkvByteSourceFactory { source },
            isDownloading = { downloading },
        )

        assertEquals(1, index.scanAvailable().size)
        downloading = false
        source.append(cluster(timecode = 1_000))

        assertEquals(1, index.scanAvailable().size)
    }

    @Test
    fun seekMapSelectsValidatedClusterAtOrBeforeRequestedTime() {
        val content = minimalMkv(
            cluster(timecode = 0),
            cluster(timecode = 1_000),
            cluster(timecode = 2_000),
        )
        val index = GrowingMkvClusterIndex(
            sourceFactory = GrowingMkvByteSourceFactory { MemoryByteSource(content) },
            isDownloading = { true },
        )
        val seekMap = GrowingMkvSeekMap(SeekMap.Unseekable(10_000_000L), index)

        assertTrue(seekMap.isSeekable)
        assertEquals(1_000_000L, seekMap.getSeekPoints(1_500_000L).first.timeUs)
        assertEquals(clusterHeaderOffsets(content)[1], seekMap.getSeekPoints(1_500_000L).first.position)
        assertEquals(2_000_000L, seekMap.getSeekPoints(2_500_000L).first.timeUs)
    }

    @Test
    fun activePresetGradesExplicitlyWhileOriginalAndNeutralRemainNative() {
        assertTrue(shouldUseNativeHdrOutput(ColorPresets.HDR_DISABLED, enhancementEnabled = true))
        assertTrue(shouldUseNativeHdrOutput(ColorPresets.NEUTRAL, enhancementEnabled = true))
        listOf(
            ColorPresets.VIVID_HDR,
            ColorPresets.CINEMATIC,
            ColorPresets.HDR_BRIGHT,
            ColorPresets.NATURAL_HDR,
        ).forEach { preset ->
            assertTrue(shouldApplyColorPreset(preset, enhancementEnabled = true))
            assertFalse(shouldUseNativeHdrOutput(preset, enhancementEnabled = true))
        }
        assertTrue(shouldUseNativeHdrOutput(ColorPresets.VIVID_HDR, enhancementEnabled = false))
    }

    private fun cluster(timecode: Int): ByteArray {
        val payload = unsignedBytes(timecode.toLong())
        val timecodeElement = element(byteArrayOf(0xE7.toByte()), payload)
        return element(byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75), timecodeElement)
    }

    private fun minimalMkv(vararg clusters: ByteArray): ByteArray {
        val ebmlHeader = element(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), byteArrayOf())
        val segmentHeader = byteArrayOf(
            0x18, 0x53, 0x80.toByte(), 0x67,
            0xFF.toByte(), // Unknown-size Segment, common for growing/streamed files.
        )
        val timecodeScale = element(
            byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte()),
            byteArrayOf(0x0F, 0x42, 0x40), // 1,000,000 ns, the Matroska default.
        )
        val info = element(
            byteArrayOf(0x15, 0x49, 0xA9.toByte(), 0x66),
            timecodeScale,
        )
        return concat(ebmlHeader, segmentHeader, info, *clusters)
    }

    private fun clusterHeaderOffsets(content: ByteArray): List<Long> {
        val clusterId = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75)
        val offsets = ArrayList<Long>()
        for (index in 0..content.size - clusterId.size) {
            if (clusterId.indices.all { content[index + it] == clusterId[it] }) {
                offsets += index.toLong()
            }
        }
        return offsets
    }

    private fun element(id: ByteArray, body: ByteArray): ByteArray =
        concat(id, byteArrayOf((0x80 or body.size).toByte()), body)

    private fun unsignedBytes(value: Long): ByteArray {
        var remaining = value
        var count = 1
        while (remaining ushr (count * 8) != 0L && count < 8) count++
        return ByteArray(count) { index ->
            val shift = (count - index - 1) * 8
            (value ushr shift).toByte()
        }
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val result = ByteArray(arrays.sumOf { it.size })
        var offset = 0
        arrays.forEach { bytes ->
            bytes.copyInto(result, destinationOffset = offset)
            offset += bytes.size
        }
        return result
    }

    private class MemoryByteSource(initialContent: ByteArray) : GrowingMkvByteSource {
        private var content = initialContent.copyOf()

        fun append(bytes: ByteArray) {
            content = content + bytes
        }

        override fun length(): Long = content.size.toLong() + SAFETY_TAIL_BYTES

        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (position < 0L || position >= content.size) return 0
            val count = minOf(length, content.size - position.toInt())
            content.copyInto(
                destination = target,
                destinationOffset = offset,
                startIndex = position.toInt(),
                endIndex = position.toInt() + count,
            )
            return count
        }

        override fun close() = Unit

        private companion object {
            const val SAFETY_TAIL_BYTES = 256L * 1024L
        }
    }
}
