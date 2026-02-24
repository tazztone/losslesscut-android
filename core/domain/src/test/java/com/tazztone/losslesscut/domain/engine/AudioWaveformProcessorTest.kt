package com.tazztone.losslesscut.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWaveformProcessorTest {

    @Test
    fun testFindPeak_allZeros() {
        val buffer = ByteArray(100) { 0 }
        val peak = AudioWaveformProcessor.findPeak(buffer, buffer.size)
        assertEquals(0, peak)
    }

    @Test
    fun testFindPeak_positiveValues() {
        // 16-bit PCM, little-endian. 
        // Sample 1: 0x0001 (1)
        // Sample 2: 0x00FF (255)
        // Sample 3: 0xFF7F (32767 - Max positive Short)
        val buffer = byteArrayOf(
            0x01, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0x00, 0x00, 0x00,
            0xFF.toByte(), 0x7F, 0x00, 0x00
        )
        val peak = AudioWaveformProcessor.findPeak(buffer, buffer.size)
        assertEquals(32767, peak)
    }

    @Test
    fun testFindPeak_negativeValues() {
        // Sample 1: 0x0080 (-32768 - Max negative Short)
        val buffer = byteArrayOf(
            0x00, 0x80.toByte(), 0x00, 0x00
        )
        val peak = AudioWaveformProcessor.findPeak(buffer, buffer.size)
        assertEquals(32768, peak)
    }

    @Test
    fun testNormalize() {
        val buckets = floatArrayOf(0.1f, 0.5f, 0.2f)
        AudioWaveformProcessor.normalize(buckets)
        // Max is 0.5, so divide all by 0.5
        assertEquals(0.2f, buckets[0], 0.001f)
        assertEquals(1.0f, buckets[1], 0.001f)
        assertEquals(0.4f, buckets[2], 0.001f)
    }

    @Test
    fun testNormalize_allZeros() {
        val buckets = floatArrayOf(0f, 0f, 0f)
        AudioWaveformProcessor.normalize(buckets)
        assertEquals(0f, buckets[0], 0.001f)
    }

    @Test
    fun testGetBucketIndex() {
        val duration = 1000L
        val bucketCount = 10
        
        assertEquals(0, AudioWaveformProcessor.getBucketIndex(0, duration, bucketCount))
        assertEquals(4, AudioWaveformProcessor.getBucketIndex(500, duration, bucketCount))
        assertEquals(9, AudioWaveformProcessor.getBucketIndex(1000, duration, bucketCount))
        assertEquals(9, AudioWaveformProcessor.getBucketIndex(1200, duration, bucketCount)) // Coerced
    }
}
