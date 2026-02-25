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
}
