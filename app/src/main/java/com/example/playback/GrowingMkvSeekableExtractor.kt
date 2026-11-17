package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import java.io.Closeable
import java.io.File
import java.io.IOException
import android.os.SystemClock
import com.example.player.DownloadingFilesRegistry

/**
 * Replaces only the Matroska extractor in a normal Media3 extractor list.
 * Every other extractor and its configured order is preserved.
 */
@UnstableApi
object GrowingMkvExtractorsFactory {
    fun wrap(
        delegateFactory: DefaultExtractorsFactory,
        context: Context,
        uri: Uri,
        isDownloading: () -> Boolean,
        onSeekFrontier: (Long) -> Unit = {},
    ): ExtractorsFactory = ExtractorsFactory {
        delegateFactory.createExtractors().map { extractor ->
            if (extractor is MatroskaExtractor) {
                val index = createGrowingMkvClusterIndex(
                    context = context.applicationContext,
                    uri = uri,
                    isDownloading = isDownloading,
                )
                GrowingMkvSeekableExtractor(
                    delegate = extractor,
                    clusterIndex = index,
                    onSeekFrontier = onSeekFrontier,
                )
            } else {
                extractor
            }
        }.toTypedArray()
    }
}

@UnstableApi
internal fun createGrowingMkvClusterIndex(
    context: Context,
    uri: Uri,
    isDownloading: () -> Boolean,
): GrowingMkvClusterIndex = GrowingMkvClusterIndex(
    sourceFactory = { openGrowingMkvByteSource(context.applicationContext, uri) },
    isDownloading = isDownloading,
)

/**
 * Wraps Media3's ordinary Matroska parser and replaces only its unseekable map.
 * The incremental index reads validated top-level Cluster boundaries directly
 * from the growing file; it never derives byte offsets from sample callbacks or
 * proportional time-to-file-size arithmetic.
 */
@UnstableApi
internal class GrowingMkvSeekableExtractor(
    private val delegate: Extractor,
    private val clusterIndex: GrowingMkvClusterIndex,
    private val onSeekFrontier: (Long) -> Unit = {},
) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun getSniffFailureDetails() = delegate.getSniffFailureDetails()

    private var output: ExtractorOutput? = null
    private var originalMap: SeekMap? = null
    private var lastScanMs = -1L

    override fun init(output: ExtractorOutput) {
        this.output = output
        delegate.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = output.track(id, type)

            override fun endTracks() = output.endTracks()

            override fun seekMap(seekMap: SeekMap) {
                // Preserve a real Cues-based map verbatim. Only an unseekable
                // map gets the validated growing-prefix Cluster index.
                originalMap = seekMap
                clusterIndex.scanAvailable() // extractor loader thread, never SeekMap/UI
                onSeekFrontier(clusterIndex.latestTimeUs()?.div(1_000) ?: 0)
                output.seekMap(GrowingMkvSeekMap(seekMap, clusterIndex))
            }
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val now = SystemClock.elapsedRealtime()
        if (lastScanMs < 0 || now - lastScanMs >= 500) {
            lastScanMs = now
            val before = clusterIndex.snapshot
            clusterIndex.scanAvailable()
            if (before != clusterIndex.snapshot) originalMap?.let {
                onSeekFrontier(clusterIndex.latestTimeUs()?.div(1_000) ?: 0)
                output?.seekMap(GrowingMkvSeekMap(it, clusterIndex))
            }
        }
        return delegate.read(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() {
        try { delegate.release() } finally { clusterIndex.close() }
    }

    override fun getUnderlyingImplementation(): Extractor = delegate.getUnderlyingImplementation()
}

/** Random-access, read-only view of a source that may still be growing. */
internal interface GrowingMkvByteSource : Closeable {
    fun length(): Long

    /** Returns the number of bytes read, or zero at EOF. */
    fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int
}

internal fun interface GrowingMkvByteSourceFactory {
    fun open(): GrowingMkvByteSource?
}

/**
 * Incrementally indexes complete Matroska Clusters up to the safe downloaded
 * frontier. Non-Cluster payloads are skipped by their EBML sizes; Cluster payload
 * blocks are not read, only the Cluster header and its Timecode child.
 */
@UnstableApi
internal class GrowingMkvClusterIndex(
    private val sourceFactory: GrowingMkvByteSourceFactory,
    private val isDownloading: () -> Boolean,
) : Closeable {
    private var source: GrowingMkvByteSource? = null
    @Volatile var snapshot: List<SeekPoint> = emptyList()
        private set
    private val checkpoints = ArrayList<SeekPoint>()
    private var videoTrack: Long? = null
    private var segmentChildrenOffset = NO_OFFSET
    private var timecodeScaleNs = DEFAULT_TIMECODE_SCALE_NS

    @Volatile
    var lastScannedOffset: Long = NO_OFFSET
        private set

    @Synchronized
    fun scanAvailable(): List<SeekPoint> {
        val randomAccess = source ?: try {
            sourceFactory.open()?.also { source = it }
        } catch (_: IOException) { null } ?: return snapshot
        try {
                val safeDownloadedBytes = randomAccess.length().coerceAtLeast(0L)
                if (segmentChildrenOffset == NO_OFFSET) {
                    val segmentStart = findSegmentChildrenOffset(randomAccess, safeDownloadedBytes)
                        ?: return snapshot
                    segmentChildrenOffset = segmentStart
                    lastScannedOffset = segmentStart
                }

                var offset = lastScannedOffset
                var budget = 4096
                while (offset >= 0L && offset < safeDownloadedBytes && budget-- > 0) {
                    val header = readElementHeader(randomAccess, offset, safeDownloadedBytes)
                        ?: break
                    val size = header.size
                    val declaredEnd = size?.let { safeAdd(header.bodyOffset, it) }
                    val endOffset = if (header.elementId == CLUSTER_ELEMENT_ID) {
                        val cluster = scanCluster(randomAccess, header.bodyOffset,
                            minOf(declaredEnd ?: safeDownloadedBytes, safeDownloadedBytes))
                        cluster.second?.let { timeUs -> insertCheckpoint(SeekPoint(timeUs, offset)) }
                        // Unknown-size Cluster ends only at a parsed level-one header.
                        declaredEnd ?: cluster.first ?: break
                    } else declaredEnd ?: break
                    if (endOffset > safeDownloadedBytes || endOffset <= offset) break
                    when (header.elementId) {
                        INFO_ELEMENT_ID -> readTimecodeScale(randomAccess, header.bodyOffset, endOffset)
                            ?.let { if (it > 0) timecodeScaleNs = it }
                        TRACKS_ELEMENT_ID -> readVideoTrack(randomAccess, header.bodyOffset, endOffset)
                            ?.let { videoTrack = it }
                    }
                    // Advance only after the complete top-level element is inside
                    // the safety frontier. An incomplete Cluster is retried later.
                    offset = endOffset
                    lastScannedOffset = endOffset
                }
        } catch (_: IOException) {
            // Keep the last successfully indexed offset; the next map query can retry.
        }
        snapshot = checkpoints.toList()
        return snapshot
    }

    fun seekPointAtOrBefore(timeUs: Long): SeekPoint? {
        val points = snapshot
        if (points.isEmpty()) return null
        var low = 0
        var high = points.lastIndex
        var best = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (points[middle].timeUs <= timeUs) {
                best = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return points[if (best >= 0) best else 0]
    }

    fun latestTimeUs(): Long? = snapshot.lastOrNull()?.timeUs

    override fun close() { source?.close(); source = null }

    private fun insertCheckpoint(point: SeekPoint) {
        if (checkpoints.any { it.position == point.position }) return
        var low = 0
        var high = checkpoints.size
        while (low < high) {
            val middle = (low + high) ushr 1
            val current = checkpoints[middle]
            when {
                current.timeUs <= point.timeUs -> low = middle + 1
                else -> high = middle
            }
        }
        checkpoints.add(low, point)
    }

    private fun findSegmentChildrenOffset(source: GrowingMkvByteSource, safeLimit: Long): Long? {
        var offset = 0L
        while (offset < safeLimit) {
            val header = readElementHeader(source, offset, safeLimit) ?: return null
            if (header.elementId == SEGMENT_ELEMENT_ID) return header.bodyOffset
            val size = header.size ?: return null
            val next = safeAdd(header.bodyOffset, size) ?: return null
            if (next <= offset || next > safeLimit) return null
            offset = next
        }
        return null
    }

    private fun readTimecodeScale(
        source: GrowingMkvByteSource,
        infoBodyOffset: Long,
        infoEndOffset: Long,
    ): Long? {
        var offset = infoBodyOffset
        while (offset < infoEndOffset) {
            val child = readElementHeader(source, offset, infoEndOffset) ?: return null
            val size = child.size ?: return null
            val end = safeAdd(child.bodyOffset, size) ?: return null
            if (end > infoEndOffset || end <= offset) return null
            if (child.elementId == TIMECODE_SCALE_ELEMENT_ID) {
                return readUnsignedInteger(source, child.bodyOffset, size, infoEndOffset)
            }
            offset = end
        }
        return null
    }

    /** Skip EBML children by their sizes. Never search block payloads for magic bytes. */
    private fun scanCluster(source: GrowingMkvByteSource, start: Long, limit: Long): Pair<Long?, Long?> {
        var offset = start
        var timestamp: Long? = null
        var keyframeRelative: Long? = null
        var budget = 8192
        while (offset < limit && budget-- > 0) {
            val child = readElementHeader(source, offset, limit) ?: break
            if (child.elementId in LEVEL_ONE_IDS) return offset to keyframeRelative?.let { relative ->
                timestamp?.let { toTimeUs((it + relative).coerceAtLeast(0), timecodeScaleNs) }
            }
            val size = child.size ?: break
            val end = safeAdd(child.bodyOffset, size) ?: break
            if (end > limit || end <= offset) break
            if (child.elementId == TIMECODE_ELEMENT_ID) {
                timestamp = readUnsignedInteger(source, child.bodyOffset, size, limit)
            } else if (child.elementId == 0xA3L && keyframeRelative == null) {
                keyframeRelative = keyframeTime(source, child.bodyOffset, end, simple = true)
            } else if (child.elementId == 0xA0L && keyframeRelative == null) {
                var block: Pair<Long, Long>? = null
                var reference = false
                var pos = child.bodyOffset
                while (pos < end) {
                    val entry = readElementHeader(source, pos, end) ?: break
                    val entryEnd = safeAdd(entry.bodyOffset, entry.size ?: break) ?: break
                    if (entryEnd > end || entryEnd <= pos) break
                    if (entry.elementId == 0xFBL) reference = true
                    if (entry.elementId == 0xA1L) block = entry.bodyOffset to entryEnd
                    pos = entryEnd
                }
                if (!reference) block?.let { keyframeRelative = keyframeTime(source, it.first, it.second, false) }
            }
            offset = end
        }
        return null to keyframeRelative?.let { relative ->
            timestamp?.let { toTimeUs((it + relative).coerceAtLeast(0), timecodeScaleNs) }
        }
    }

    private fun keyframeTime(source: GrowingMkvByteSource, start: Long, end: Long, simple: Boolean): Long? {
        val track = readVint(source, start, end, 8, true) ?: return null
        if (track.value != videoTrack) return null
        val header = readExact(source, start + track.length, 3, end) ?: return null
        if (simple && (header[2].toInt() and 0x80) == 0) return null
        // Require at least one payload byte, not an empty/truncated Block header.
        if (end <= start + track.length + 3) return null
        return (((header[0].toInt() and 255) shl 8) or (header[1].toInt() and 255)).toShort().toLong()
    }

    private fun readVideoTrack(source: GrowingMkvByteSource, start: Long, limit: Long): Long? {
        var pos = start
        while (pos < limit) {
            val entry = readElementHeader(source, pos, limit) ?: return null
            val end = safeAdd(entry.bodyOffset, entry.size ?: return null) ?: return null
            if (end > limit || end <= pos) return null
            if (entry.elementId == 0xAEL) {
                var number: Long? = null
                var type: Long? = null
                var childPos = entry.bodyOffset
                while (childPos < end) {
                    val child = readElementHeader(source, childPos, end) ?: break
                    val childSize = child.size ?: break
                    val childEnd = safeAdd(child.bodyOffset, childSize) ?: break
                    if (childEnd > end || childEnd <= childPos) break
                    if (child.elementId == 0xD7L) number = readUnsignedInteger(source, child.bodyOffset, childSize, end)
                    if (child.elementId == 0x83L) type = readUnsignedInteger(source, child.bodyOffset, childSize, end)
                    childPos = childEnd
                }
                if (type == 1L) return number
            }
            pos = end
        }
        return null
    }

    private fun readElementHeader(
        source: GrowingMkvByteSource,
        offset: Long,
        safeLimit: Long,
    ): ElementHeader? {
        val id = readVint(source, offset, safeLimit, MAX_ELEMENT_ID_BYTES, stripMarker = false)
            ?: return null
        val size = readVint(source, offset + id.length, safeLimit, MAX_SIZE_VINT_BYTES, stripMarker = true)
            ?: return null
        val bodyOffset = safeAdd(offset, (id.length + size.length).toLong()) ?: return null
        if (bodyOffset > safeLimit) return null
        return ElementHeader(id.value, bodyOffset, size.value.takeUnless { size.unknown })
    }

    private fun readVint(
        source: GrowingMkvByteSource,
        offset: Long,
        safeLimit: Long,
        maximumLength: Int,
        stripMarker: Boolean,
    ): Vint? {
        val first = readUnsignedByte(source, offset, safeLimit) ?: return null
        var marker = 0x80
        var length = 1
        while (length <= maximumLength && (first and marker) == 0) {
            marker = marker ushr 1
            length++
        }
        if (length > maximumLength || offset + length > safeLimit) return null

        val encoded = readExact(source, offset, length, safeLimit) ?: return null
        var value = if (stripMarker) (encoded[0].toInt() and (marker - 1)).toLong()
        else (encoded[0].toInt() and 0xFF).toLong()
        for (index in 1 until length) {
            value = (value shl 8) or (encoded[index].toInt() and 0xFF).toLong()
        }
        val unknown = stripMarker && value == ((1L shl (7 * length)) - 1L)
        return Vint(value, length, unknown)
    }

    private fun readUnsignedInteger(
        source: GrowingMkvByteSource,
        offset: Long,
        size: Long,
        safeLimit: Long,
    ): Long? {
        if (size !in 1L..MAX_UNSIGNED_INTEGER_BYTES.toLong()) return null
        val bytes = readExact(source, offset, size.toInt(), safeLimit) ?: return null
        var value = 0L
        bytes.forEach { value = (value shl 8) or (it.toInt() and 0xFF).toLong() }
        return value.takeIf { it >= 0L }
    }

    private fun readUnsignedByte(source: GrowingMkvByteSource, offset: Long, safeLimit: Long): Int? =
        readExact(source, offset, 1, safeLimit)?.get(0)?.toInt()?.and(0xFF)

    private fun readExact(
        source: GrowingMkvByteSource,
        position: Long,
        length: Int,
        safeLimit: Long,
    ): ByteArray? {
        if (position < 0L || length < 0 || position > safeLimit - length) return null
        val bytes = ByteArray(length)
        var count = 0
        while (count < length) {
            val read = source.readAt(position + count, bytes, count, length - count)
            if (read <= 0) return null
            count += read
        }
        return bytes
    }

    private fun safeAdd(first: Long, second: Long): Long? =
        if (second < 0L || first > Long.MAX_VALUE - second) null else first + second

    private fun toTimeUs(rawTimecode: Long, scaleNs: Long): Long {
        val product = try {
            Math.multiplyExact(rawTimecode, scaleNs)
        } catch (_: ArithmeticException) {
            return java.math.BigInteger.valueOf(rawTimecode)
                .multiply(java.math.BigInteger.valueOf(scaleNs))
                .divide(THOUSAND_BIG_INTEGER)
                .min(LONG_MAX_BIG_INTEGER)
                .toLong()
        }
        return product / 1_000L
    }

    private data class ElementHeader(
        val elementId: Long,
        val bodyOffset: Long,
        val size: Long?,
    )

    private data class Vint(val value: Long, val length: Int, val unknown: Boolean)

    private companion object {
        const val NO_OFFSET = -1L
        const val DEFAULT_TIMECODE_SCALE_NS = 1_000_000L
        const val MAX_ELEMENT_ID_BYTES = 4
        const val MAX_SIZE_VINT_BYTES = 8
        const val MAX_UNSIGNED_INTEGER_BYTES = 8
        const val SEGMENT_ELEMENT_ID = 0x18538067L
        const val INFO_ELEMENT_ID = 0x1549A966L
        const val TRACKS_ELEMENT_ID = 0x1654AE6BL
        val LEVEL_ONE_IDS = setOf(0x114D9B74L, 0x1549A966L, 0x1654AE6BL, 0x1F43B675L,
            0x1C53BB6BL, 0x1941A469L, 0x1043A770L, 0x1254C367L)
        const val CLUSTER_ELEMENT_ID = 0x1F43B675L
        const val TIMECODE_SCALE_ELEMENT_ID = 0x2AD7B1L
        const val TIMECODE_ELEMENT_ID = 0xE7L
        val THOUSAND_BIG_INTEGER = java.math.BigInteger.valueOf(1_000L)
        val LONG_MAX_BIG_INTEGER = java.math.BigInteger.valueOf(Long.MAX_VALUE)
    }
}

/** Dynamic map backed by the validated Cluster starts found in the file prefix. */
@UnstableApi
internal class GrowingMkvSeekMap(
    private val original: SeekMap,
    private val clusterIndex: GrowingMkvClusterIndex,
) : SeekMap {
    override fun isSeekable(): Boolean =
        original.isSeekable || clusterIndex.snapshot.size >= MIN_CLUSTERS_FOR_SEEK

    override fun getDurationUs(): Long =
        original.durationUs.takeUnless { it == androidx.media3.common.C.TIME_UNSET }
            ?: clusterIndex.latestTimeUs()
            ?: androidx.media3.common.C.TIME_UNSET

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        if (original.isSeekable) return original.getSeekPoints(timeUs)
        val point = clusterIndex.seekPointAtOrBefore(timeUs)
            ?: return original.getSeekPoints(timeUs)
        return SeekMap.SeekPoints(point)
    }

    private companion object {
        const val MIN_CLUSTERS_FOR_SEEK = 2
    }
}

private fun openGrowingMkvByteSource(context: Context, uri: Uri): GrowingMkvByteSource? {
    val key = if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()
    val resolved = DownloadingFilesRegistry.completedUri(key)?.let(Uri::parse) ?: uri
    val descriptor = try {
        if (resolved.scheme == "file") {
            ParcelFileDescriptor.open(File(resolved.path ?: return null), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(resolved, "r")
        }
    } catch (_: Exception) {
        null
    } ?: return null

    return object : GrowingMkvByteSource {
        private val fileDescriptor = descriptor.fileDescriptor

        override fun length(): Long = try {
            Os.fstat(fileDescriptor).st_size.coerceAtLeast(0L)
        } catch (_: ErrnoException) {
            -1L
        }

        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int = try {
            Os.pread(fileDescriptor, target, offset, length, position)
        } catch (error: ErrnoException) {
            throw IOException("Unable to scan growing Matroska source", error)
        }

        override fun close() = descriptor.close()
    }
}
