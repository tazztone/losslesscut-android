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
}
