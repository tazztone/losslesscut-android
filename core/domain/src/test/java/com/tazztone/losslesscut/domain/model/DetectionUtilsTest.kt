package com.tazztone.losslesscut.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class DetectionUtilsTest {

    @Test
    fun testFindSilence_basic() = runTest {
        val waveform = floatArrayOf(0.1f, 0.0f, 0.0f, 0.5f)
        val duration = 1000L
        val config = DetectionUtils.SilenceDetectionConfig(
            threshold = 0.2f,
            minSilenceMs = 200,
            paddingStartMs = 0,
            paddingEndMs = 0,
            minSegmentMs = 0
        )
        
        val ranges = DetectionUtils.findSilence(waveform, duration, config)
        
        // Index 0, 1, 2 are silence (< 0.2f).
        // Sample duration = 1000/4 = 250ms per sample.
        // silence is from index 0 to 3 (start of next non-silence sample).
        // startMs = 0 * 250 = 0.
        // endMs = 3 * 250 = 750.
        assertEquals(1, ranges.size)
        assertEquals(0L..750L, ranges[0])
    }

    @Test
    fun testFindSilence_dualPadding() = runTest {
        val waveform = floatArrayOf(0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f)
        val duration = 6000L // 1000ms per sample
        val config = DetectionUtils.SilenceDetectionConfig(
            threshold = 0.1f,
            minSilenceMs = 1000,
            paddingStartMs = 500, // Prefix
            paddingEndMs = 1500,  // Postfix
            minSegmentMs = 0
        )
        
        val ranges = DetectionUtils.findSilence(waveform, duration, config)
        
        // Raw silence: 1000..5000 (indices 1,2,3,4)
        // With prefix 500: start = 1000 + 500 = 1500
        // With postfix 1500: end = 5000 - 1500 = 3500
        assertEquals(1, ranges.size)
        assertEquals(1500L..3500L, ranges[0])
    }

    @Test
    fun testFindSilence_minSegmentMerging() = runTest {
        val waveform = FloatArray(300) { i ->
            when (i) {
                in 0 until 50 -> 0.5f
                in 50 until 100 -> 0.01f
                in 100 until 110 -> 0.5f // Short keeping segment (10ms)
                in 110 until 200 -> 0.01f
                else -> 0.5f
            }
        }
        
        val config = DetectionUtils.SilenceDetectionConfig(
            threshold = 0.02f,
            minSilenceMs = 10,
            paddingStartMs = 0,
            paddingEndMs = 0,
            minSegmentMs = 50 
        )
        
        val silence = DetectionUtils.findSilence(waveform, 300, config)
        
        assertEquals(1, silence.size)
        assertEquals(50L..200L, silence[0])
    }

    @Test
    fun testFindSilence_edgeCases() = runTest {
        val config = DetectionUtils.SilenceDetectionConfig(0.1f, 100, 0, 0, 0)
        
        assertTrue(DetectionUtils.findSilence(FloatArray(0), 1000, config).isEmpty())
        assertTrue(DetectionUtils.findSilence(FloatArray(100), 0, config).isEmpty())
    }
}
