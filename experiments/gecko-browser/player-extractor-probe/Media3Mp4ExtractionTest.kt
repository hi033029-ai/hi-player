package com.example.playback

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.test.utils.FakeExtractorInput
import androidx.media3.test.utils.FakeExtractorOutput
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Synthetic MP4 parsing regression only: this does not decode or render media. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class Media3Mp4ExtractionTest {
    @Test fun avcWithAudioExtractsAndSeeks() = checkFixture("avc.mp4", MimeTypes.VIDEO_H264)
    @Test fun hevcWithAudioExtractsAndSeeks() = checkFixture("hevc.mp4", MimeTypes.VIDEO_H265)

    private fun checkFixture(name: String, videoMime: String) {
        val bytes = javaClass.classLoader!!.getResourceAsStream("mp4/$name")!!.use { it.readBytes() }
        val method = OptimizedPlayerBuilder::class.java.getDeclaredMethod("extractorsFactory")
        method.isAccessible = true
        val factory = method.invoke(OptimizedPlayerBuilder) as DefaultExtractorsFactory
        val extractor = factory.createExtractors().filterIsInstance<Mp4Extractor>().single()
        val input = FakeExtractorInput.Builder().setData(bytes).build()
        val output = FakeExtractorOutput()
        assertTrue(extractor.sniff(input))
        input.resetPeekPosition()
        extractor.init(output)
        readToEnd(extractor, input)
        assertTrue(output.tracksEnded)
        val tracks = (0 until output.trackOutputs.size()).map { output.trackOutputs.valueAt(it) }
        val video = tracks.single { it.lastFormat?.sampleMimeType == videoMime }
        val audio = tracks.single { it.lastFormat?.sampleMimeType == MimeTypes.AUDIO_AAC }
        assertEquals(videoMime, video.lastFormat!!.sampleMimeType)
        assertEquals(MimeTypes.AUDIO_AAC, audio.lastFormat!!.sampleMimeType)
        assertTrue(video.sampleCount > 0)
        assertTrue(audio.sampleCount > 0)
        val seekMap = output.seekMap!!
        assertTrue(seekMap.isSeekable)
        assertTrue(seekMap.durationUs in 1_900_000..2_200_000)
        val seekPoint = seekMap.getSeekPoints(1_000_000).first
        assertTrue(seekPoint.position in 0 until bytes.size.toLong())
        output.clearTrackOutputs()
        input.setPosition(seekPoint.position.toInt())
        extractor.seek(seekPoint.position, seekPoint.timeUs)
        readToEnd(extractor, input)
        assertTrue(video.sampleCount > 0)
        assertTrue(audio.sampleCount > 0)
        extractor.release()
    }

    private fun readToEnd(extractor: Extractor, input: FakeExtractorInput) {
        val position = PositionHolder()
        var iterations = 0
        while (true) {
            check(++iterations < 100_000) { "Extractor did not reach end of input" }
            when (extractor.read(input, position)) {
                Extractor.RESULT_END_OF_INPUT -> return
                Extractor.RESULT_SEEK -> input.setPosition(position.position.toInt())
            }
        }
    }
}
