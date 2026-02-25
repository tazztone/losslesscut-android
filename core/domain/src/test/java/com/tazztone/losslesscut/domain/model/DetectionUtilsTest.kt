package com.tazztone.losslesscut.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

public class DetectionUtilsTest {

    @Test
    public fun testFindSilence_basic(): Unit = runTest {
        val waveform = floatArrayOf(0.1f, 0.0f, 0.0f, 0.5f)
        val duration = 1000L
        
        val ranges = DetectionUtils.findSilence(waveform, duration, 0.2f)
        
        // Index 0, 1, 2 are silence (< 0.2f).
        // Sample duration = 1000/4 = 250ms per sample.
        // silence is from index 0 to 3 (start of next non-silence sample).
        // startMs = 0 * 250 = 0.
        // endMs = 3 * 250 = 750.
        assertEquals(1, ranges.size)
        assertEquals(0L..750L, ranges[0])
    }

    @Test
    public fun testFindSilence_raw(): Unit = runTest {
        val waveform = floatArrayOf(0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f)
        val duration = 6000L // 1000ms per sample
        
        val ranges = DetectionUtils.findSilence(waveform, duration, 0.1f)
        
        // Raw silence: 1000..5000 (indices 1,2,3,4)
        assertEquals(1, ranges.size)
        assertEquals(1000L..5000L, ranges[0])
    }

    @Test
    public fun testApplyPaddingAndFilter_basic(): Unit {
        val ranges = listOf(1000L..5000L)
        val result = DetectionUtils.applyPaddingAndFilter(ranges, 200, 300, 10000L)
        
        assertEquals(1, result.size)
        assertEquals(1200L..4700L, result[0])
    }

    @Test
    public fun testApplyPaddingAndFilter_edgeAware(): Unit {
        // Range starts at 0, should NOT be padded at start
        // Range ends at totalDuration, should NOT be padded at end
        val ranges = listOf(0L..2000L, 8000L..10000L)
        val result = DetectionUtils.applyPaddingAndFilter(ranges, 500, 500, 10000L)
        
        assertEquals(2, result.size)
        assertEquals(0L..1500L, result[0])   // 0 stays 0, 2000 shrinks to 1500
        assertEquals(8500L..10000L, result[1]) // 8000 shrinks to 8500, 10000 stays 10000
    }

    @Test
    public fun testFindSilence_noMerging(): Unit = runTest {
        val waveform = FloatArray(300) { i ->
            when (i) {
                in 0 until 50 -> 0.5f
                in 50 until 100 -> 0.01f
                in 100 until 110 -> 0.5f // Short keeping segment (10ms)
                in 110 until 200 -> 0.01f
                else -> 0.5f
            }
        }
        
        val silence = DetectionUtils.findSilence(waveform, 300, 0.02f)
        
        // Should return two ranges
        assertEquals(2, silence.size)
        assertEquals(50L..100L, silence[0])
        assertEquals(110L..200L, silence[1])
    }

    @Test
    public fun testFilterShortSilences_basic(): Unit {
        val ranges = listOf(0L..100L, 200L..500L, 800L..1000L)
        // minSilence = 200ms
        // 0..100: dur 100. BUT at start -> keep
        // 200..500: dur 300 -> keep
        // 800..1000: dur 200. AT end -> keep
        val filtered = DetectionUtils.filterShortSilences(ranges, 200, 1000)
        assertEquals(3, filtered.size)
    }

    @Test
    public fun testFilterShortSilences_internalDropped(): Unit {
        val ranges = listOf(200L..300L) // dur 100
        val filtered = DetectionUtils.filterShortSilences(ranges, 200, 1000)
        assertTrue(filtered.isEmpty())
    }

    @Test
    public fun testFilterShortSilences_boundaryExempt(): Unit {
        val ranges = listOf(0L..100L, 900L..1000L) 
        val filtered = DetectionUtils.filterShortSilences(ranges, 500, 1000)
        assertEquals(2, filtered.size)
    }

}
