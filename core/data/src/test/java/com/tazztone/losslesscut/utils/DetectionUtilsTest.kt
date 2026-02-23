package com.tazztone.losslesscut.utils

import com.tazztone.losslesscut.domain.model.DetectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionUtilsTest {

    @Test
    fun `test findSilence basic detection`() {
        // Mock waveform: 0 is silence, 1 is loud
        // 0-100ms loud, 100-300ms silent, 300-500ms loud
        val waveform = FloatArray(500) { i ->
            if (i in 100 until 300) 0.01f else 0.5f
        }
        
        val silence = DetectionUtils.findSilence(
            waveform = waveform,
            threshold = 0.02f,
            minSilenceMs = 50,
            totalDurationMs = 500,
            paddingMs = 0,
            minSegmentMs = 0
        )
        
        assertEquals(1, silence.size)
        assertEquals(100L..300L, silence[0])
    }

    @Test
    fun `test findSilence with padding`() {
        val waveform = FloatArray(500) { i ->
            if (i in 100 until 300) 0.01f else 0.5f
        }
        
        val silence = DetectionUtils.findSilence(
            waveform = waveform,
            threshold = 0.02f,
            minSilenceMs = 50,
            totalDurationMs = 500,
            paddingMs = 20,
            minSegmentMs = 0
        )
        
        // Silence should be shrunk by padding (kept segments grow)
        assertEquals(1, silence.size)
        assertEquals(120L..280L, silence[0])
    }

    @Test
    fun `test minSegmentMs merging`() {
        // High loudness at 0-50, silence 50-100, high 100-110 (SHORT), silence 110-200, high 200-300
        val waveform = FloatArray(300) { i ->
            when (i) {
                in 0 until 50 -> 0.5f
                in 50 until 100 -> 0.01f
                in 100 until 110 -> 0.5f // Short keeping segment (10ms)
                in 110 until 200 -> 0.01f
                else -> 0.5f
            }
        }
        
        val silence = DetectionUtils.findSilence(
            waveform = waveform,
            threshold = 0.02f,
            minSilenceMs = 10,
            totalDurationMs = 300,
            paddingMs = 0,
            minSegmentMs = 50 // Segments shorter than 50ms should be merged (silence removed)
        )
        
        // The 10ms kept segment at 100-110 is too short. 
        // It should be merged by removing the adjacent silence.
        // After merging 50-100 and 110-200 (by removing 100-110), we get one big silence.
        assertEquals(1, silence.size)
        assertEquals(50L..200L, silence[0])
    }

    @Test
    fun `test findSilence with empty waveform or zero duration`() {
        val result1 = DetectionUtils.findSilence(FloatArray(0), 0.1f, 100, 1000)
        assertTrue(result1.isEmpty())

        val result2 = DetectionUtils.findSilence(FloatArray(100), 0.1f, 100, 0)
        assertTrue(result2.isEmpty())
    }
}
