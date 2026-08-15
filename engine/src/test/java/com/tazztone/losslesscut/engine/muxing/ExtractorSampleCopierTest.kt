package com.tazztone.losslesscut.engine.muxing

import android.media.MediaExtractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class ExtractorSampleCopierTest {

    @Test
    fun `copy pumps samples until endUs`() = runTest {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>(relaxed = true)
        val timeMapper = SampleTimeMapper()
        val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)

        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 1024,
            durationUs = 1000L,
            hasVideoTrack = true
        )

        val buffer = ByteBuffer.allocateDirect(1024)

        val format = mockk<android.media.MediaFormat>()
        every { format.getString(android.media.MediaFormat.KEY_MIME) } returns "video/hevc"
        every { extractor.getTrackFormat(0) } returns format

        // Mock 3 samples: 0, 500, 1000
        every { extractor.readSampleData(any(), any()) } returnsMany listOf(10, 10, 10, -1)
        every { extractor.sampleTime } returnsMany listOf(0L, 500L, 1000L, -1L)
        every { extractor.sampleTrackIndex } returns 0
        every { extractor.sampleFlags } returns 1 // SYNC
        every { extractor.advance() } returnsMany listOf(true, true, true, false)
        every { extractor.selectTrack(any()) } returns Unit
        every { extractor.seekTo(any(), any()) } returns Unit

        val results = copier.copy(plan, 0L, 1000L, buffer)

        assertEquals(1, results.size)
        assertEquals(1000L, results[0]) // Last sample time for track 0
        
        verify(exactly = 3) { muxerWriter.writeSampleData(0, any(), any()) }
    }

    @Test
    fun `copy respects endUs and stops early`() = runTest {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>(relaxed = true)
        val timeMapper = SampleTimeMapper()
        val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)

        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 1024,
            durationUs = 1000L,
            hasVideoTrack = true
        )

        val buffer = ByteBuffer.allocateDirect(1024)

        val format = mockk<android.media.MediaFormat>()
        every { format.getString(android.media.MediaFormat.KEY_MIME) } returns "video/hevc"
        every { extractor.getTrackFormat(0) } returns format

        // Samples: 0, 500, 1100
        every { extractor.readSampleData(any(), any()) } returns 10
        every { extractor.sampleTime } returnsMany listOf(0L, 500L, 1100L) // 1100 > 1000
        every { extractor.sampleTrackIndex } returns 0
        every { extractor.sampleFlags } returns 1
        every { extractor.advance() } returns true
        every { extractor.selectTrack(any()) } returns Unit
        every { extractor.seekTo(any(), any()) } returns Unit

        val results = copier.copy(plan, 0L, 1000L, buffer)

        // Should only process 0 and 500. 1100 is > 1000.
        verify(exactly = 2) { muxerWriter.writeSampleData(0, any(), any()) }
        assertEquals(500L, results[0])
    }

    @Test
    fun `copy shifts PTS based on effectiveStartUs and globalOffsetUs`() = runTest {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>(relaxed = true)
        val timeMapper = SampleTimeMapper()
        val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)

        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 1024,
            durationUs = 2000L,
            hasVideoTrack = true
        )

        val buffer = ByteBuffer.allocateDirect(1024)

        val format = mockk<android.media.MediaFormat>()
        every { format.getString(android.media.MediaFormat.KEY_MIME) } returns "video/hevc"
        every { extractor.getTrackFormat(0) } returns format

        // Samples: 500ms, 1000ms. EndUs=2000ms.
        // First sample at 500ms will be the effectiveStartUs.
        every { extractor.readSampleData(any(), any()) } returns 10
        every { extractor.sampleTime } returnsMany listOf(500000L, 1000000L) 
        every { extractor.sampleTrackIndex } returns 0
        every { extractor.sampleFlags } returns 1
        every { extractor.advance() } returnsMany listOf(true, false)
        every { extractor.selectTrack(any()) } returns Unit
        every { extractor.seekTo(any(), any()) } returns Unit

        // globalOffsetUs = 2,000,000 Us (2 seconds)
        val results = copier.copy(plan, 0L, 2000000L, buffer, 2000000L)

        // First sample: map(500ms, 500ms, 2000ms) -> 2000ms
        // Second sample: map(1000ms, 500ms, 2000ms) -> 2500ms
        
        val bufferInfoSlot = mutableListOf<android.media.MediaCodec.BufferInfo>()
        verify { muxerWriter.writeSampleData(0, any(), capture(bufferInfoSlot)) }
        
        assertEquals(2000000L, bufferInfoSlot[0].presentationTimeUs)
        assertEquals(2500000L, bufferInfoSlot[1].presentationTimeUs)
        
        // Relative last sample time: 2500ms - 2000ms = 500ms
        assertEquals(500000L, results[0])
    }

    @Test
    fun `copy ignores metadata track start timestamp for effectiveStartUs`() = runTest {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>(relaxed = true)
        val timeMapper = SampleTimeMapper()
        val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)

        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0, 1 to 1),
            isVideoTrackMap = mapOf(0 to false, 1 to true),
            bufferSize = 1024,
            durationUs = 2000L,
            hasVideoTrack = true
        )

        val buffer = ByteBuffer.allocateDirect(1024)

        // Track 0 is metadata (application/octet-stream), starts at 0ms.
        // Track 1 is video (video/hevc), starts at 500ms.
        // The first sample read is metadata (track 0) at 0ms.
        // The second sample read is video (track 1) at 500ms.
        every { extractor.readSampleData(any(), any()) } returns 10
        every { extractor.sampleTime } returnsMany listOf(0L, 500000L)
        every { extractor.sampleTrackIndex } returnsMany listOf(0, 1)
        every { extractor.sampleFlags } returns 1
        every { extractor.advance() } returnsMany listOf(true, false)
        every { extractor.selectTrack(any()) } returns Unit
        every { extractor.seekTo(any(), any()) } returns Unit

        val metaFormat = mockk<android.media.MediaFormat>()
        every { metaFormat.getString(android.media.MediaFormat.KEY_MIME) } returns "application/octet-stream"

        val videoFormat = mockk<android.media.MediaFormat>()
        every { videoFormat.getString(android.media.MediaFormat.KEY_MIME) } returns "video/hevc"

        every { extractor.getTrackFormat(0) } returns metaFormat
        every { extractor.getTrackFormat(1) } returns videoFormat

        val results = copier.copy(plan, 0L, 2000000L, buffer, 0L)

        val bufferInfoSlot = mutableListOf<android.media.MediaCodec.BufferInfo>()
        verify { muxerWriter.writeSampleData(any(), any(), capture(bufferInfoSlot)) }

        // Since metadata sample at 0ms occurred before any primary track, it should have been skipped.
        // So only 1 sample (video at 500ms) should be written.
        assertEquals(1, bufferInfoSlot.size)
        // Video sample maps: map(500ms, 500ms, 0) -> 0
        assertEquals(0L, bufferInfoSlot[0].presentationTimeUs)
        
        // Relative last sample time for video track (track 1, mapped to muxer 1):
        // presUs = 0, globalOffsetUs = 0 -> relativeTime = 0
        assertEquals(0L, results[1])
    }

    @Test
    fun `copy clamps out of order B-frame timestamps to track start time`() = runTest {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>(relaxed = true)
        val timeMapper = SampleTimeMapper()
        val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)

        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0, 1 to 1),
            isVideoTrackMap = mapOf(0 to true, 1 to false),
            bufferSize = 1024,
            durationUs = 20000000L,
            hasVideoTrack = true
        )

        val buffer = ByteBuffer.allocateDirect(1024)

        // Mock 3 samples:
        // 1. Audio track 1 at 13054666us (first primary sample, sets effectiveStartUs)
        // 2. Video track 0 (I-frame) at 13068333us -> maps to 13667us relative PTS
        // 3. Video track 0 (B-frame) at 13035000us -> maps to max(0, 13035000-13054666) = 0us
        // Without fix: B-frame is written at 0us, which is < Video start time (13667us) and crashes MediaMuxer.
        // With fix: B-frame is clamped to track start (13667us).
        every { extractor.readSampleData(any(), any()) } returns 10
        every { extractor.sampleTime } returnsMany listOf(13054666L, 13068333L, 13035000L)
        every { extractor.sampleTrackIndex } returnsMany listOf(1, 0, 0)
        every { extractor.sampleFlags } returns 1
        every { extractor.advance() } returnsMany listOf(true, true, false)
        every { extractor.selectTrack(any()) } returns Unit
        every { extractor.seekTo(any(), any()) } returns Unit

        val videoFormat = mockk<android.media.MediaFormat>()
        every { videoFormat.getString(android.media.MediaFormat.KEY_MIME) } returns "video/hevc"

        val audioFormat = mockk<android.media.MediaFormat>()
        every { audioFormat.getString(android.media.MediaFormat.KEY_MIME) } returns "audio/mp4a-latm"

        every { extractor.getTrackFormat(0) } returns videoFormat
        every { extractor.getTrackFormat(1) } returns audioFormat

        val results = copier.copy(plan, 0L, 20000000L, buffer, 0L)

        val bufferInfoSlot = mutableListOf<android.media.MediaCodec.BufferInfo>()
        verify { muxerWriter.writeSampleData(any(), any(), capture(bufferInfoSlot)) }

        // There are 3 samples written (1 audio, 2 video)
        assertEquals(3, bufferInfoSlot.size)

        // The first video sample is I-frame (written second) -> expected PTS = 13667us
        assertEquals(13667L, bufferInfoSlot[1].presentationTimeUs)

        // The second video sample is B-frame (written third) -> expected clamped PTS = 13667us
        assertEquals(13667L, bufferInfoSlot[2].presentationTimeUs)
        
        // Relative last sample time for video track (track 0, mapped to muxer 0):
        assertEquals(13667L, results[0])
    }

    @Test
    fun `copy does not write samples before an unsnapped start`() = runTest {
        val extractor = mockk<MediaExtractor>()
        val muxerWriter = mockk<MuxerWriter>(relaxed = true)
        val copier = ExtractorSampleCopier(extractor, muxerWriter, SampleTimeMapper())
        val plan = SelectedTrackPlan(
            trackMap = mapOf(0 to 0),
            isVideoTrackMap = mapOf(0 to true),
            bufferSize = 1024,
            durationUs = 2000L,
            hasVideoTrack = true
        )
        val format = mockk<android.media.MediaFormat>()
        every { format.getString(android.media.MediaFormat.KEY_MIME) } returns "video/avc"
        every { extractor.getTrackFormat(0) } returns format
        every { extractor.selectTrack(any()) } returns Unit
        every { extractor.seekTo(any(), any()) } returns Unit
        every { extractor.readSampleData(any(), any()) } returnsMany listOf(10, 10)
        every { extractor.sampleTime } returnsMany listOf(500L, 1000L)
        every { extractor.sampleTrackIndex } returns 0
        every { extractor.sampleFlags } returnsMany listOf(0, MediaExtractor.SAMPLE_FLAG_SYNC)
        every { extractor.advance() } returnsMany listOf(true, false)

        val result = copier.copy(plan, startUs = 750L, endUs = 1500L, ByteBuffer.allocateDirect(1024))

        verify { extractor.seekTo(750L, MediaExtractor.SEEK_TO_NEXT_SYNC) }
        verify(exactly = 1) { muxerWriter.writeSampleData(0, any(), any()) }
        assertEquals(0L, result[0])
    }
}
