package com.tazztone.losslesscut.engine.muxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PureLogicTests {

    @Test
    fun `SampleTimeMapper maps relative and global offsets correctly`() {
        val mapper = SampleTimeMapper()
        
        // Basic mapping: 1000 original, starts at 500
        assertEquals(500L, mapper.map(1000L, 500L, 0L))
        
        // Global offset (e.g. merging): 1000 original, starts at 500, global offset 2000
        assertEquals(2500L, mapper.map(1000L, 500L, 2000L))
        
        // Clamping: 400 original, starts at 500 -> 0 (not negative)
        assertEquals(2000L, mapper.map(400L, 500L, 2000L))
    }

    @Test
    fun `SegmentGapCalculator calculates gap correctly`() {
        val calculator = SegmentGapCalculator
        
        // Audio 44.1kHz, Video 30 FPS
        // Audio Frame Duration = 1024 / 44100 * 1,000,000 = 23,219 Us
        // Video Frame Duration = 1 / 30 * 1,000,000 = 33,333 Us
        // Gap should be max(23219, 33333) = 33333
        assertEquals(33333L, SegmentGapCalculator.calculateGapUs(44100, 30f))
        
        // Audio 48kHz, Video 60 FPS
        // Audio Frame Duration = 1024 / 48000 * 1,000,000 = 21,333 Us
        // Video Frame Duration = 1 / 60 * 1,000,000 = 16,666 Us
        // Gap should be max(21333, 16666) = 21333
        assertEquals(21333L, SegmentGapCalculator.calculateGapUs(48000, 60f))
    }

    @Test
    fun `MergeValidator validates codecs`() {
        val validator = MergeValidator()
        
        // Matching mime should not throw
        validator.validateCodec("test.mp4", "video/avc", "video/avc", "video")
        
        // Mismatching mime should throw IOException
        try {
            validator.validateCodec("test.mp4", "video/hevc", "video/avc", "video")
            assert(false) { "Should have thrown IOException" }
        } catch (e: Exception) {
            assertTrue(e is java.io.IOException)
            assertTrue(e.message?.contains("Codec mismatch") == true)
        }
    }
}
