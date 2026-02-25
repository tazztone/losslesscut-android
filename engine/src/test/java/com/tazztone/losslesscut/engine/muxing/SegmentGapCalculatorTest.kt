package com.tazztone.losslesscut.engine.muxing

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentGapCalculatorTest {

    @Test
    fun `calculateGapUs uses audio frame duration when larger than video`() {
        // Audio: 1024 * 1,000,000 / 44100 = ~23219 Us
        // Video: 1,000,000 / 60 = ~16666 Us
        val result = SegmentGapCalculator.calculateGapUs(44100, 60f)
        assertEquals(23219L, result)
    }

    @Test
    fun `calculateGapUs uses video frame duration when larger than audio`() {
        // Audio: 1024 * 1,000,000 / 96000 = ~10666 Us
        // Video: 1,000,000 / 24 = ~41666 Us
        val result = SegmentGapCalculator.calculateGapUs(96000, 24f)
        assertEquals(41666L, result)
    }

    @Test
    fun `calculateGapUs handles zero or negative FPS`() {
        // Audio: 44100 -> ~23219 Us
        // Video: default 33333 Us
        val result = SegmentGapCalculator.calculateGapUs(44100, 0f)
        assertEquals(33333L, result)
    }
}
