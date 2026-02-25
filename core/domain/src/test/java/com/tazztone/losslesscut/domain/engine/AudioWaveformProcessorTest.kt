package com.tazztone.losslesscut.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.absoluteValue

public class AudioWaveformProcessorTest {

    @Test
    public fun testFindPeak_allZeros(): Unit {
        val buffer = ByteArray(100) { 0 }
        val peak = AudioWaveformProcessor.findPeak(buffer, buffer.size)
        assertEquals(0, peak)
    }

    @Test
    public fun testFindPeak_positiveValues(): Unit {
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
    public fun testFindPeak_negativeValues(): Unit {
        // Sample 1: 0x0080 (-32768 - Max negative Short)
        val buffer = byteArrayOf(
            0x00, 0x80.toByte(), 0x00, 0x00
        )
        val peak = AudioWaveformProcessor.findPeak(buffer, buffer.size)
        assertEquals(32768, peak)
    }

    @Test
    public fun testNormalize(): Unit {
        val buckets = floatArrayOf(0.1f, 0.5f, 0.2f)
        AudioWaveformProcessor.normalize(buckets)
        // Max is 0.5, so divide all by 0.5
        assertEquals(0.2f, buckets[0], 0.001f)
        assertEquals(1.0f, buckets[1], 0.001f)
        assertEquals(0.4f, buckets[2], 0.001f)
    }

    @Test
    public fun testNormalize_allZeros(): Unit {
        val buckets = floatArrayOf(0f, 0f, 0f)
        AudioWaveformProcessor.normalize(buckets)
        assertEquals(0f, buckets[0], 0.001f)
    }

    @Test
    public fun testGetBucketIndex(): Unit {
        val duration = 1000L
        val bucketCount = 10
        
        assertEquals(0, AudioWaveformProcessor.getBucketIndex(0, duration, bucketCount))
        assertEquals(4, AudioWaveformProcessor.getBucketIndex(500, duration, bucketCount))
        assertEquals(9, AudioWaveformProcessor.getBucketIndex(1000, duration, bucketCount))
        assertEquals(9, AudioWaveformProcessor.getBucketIndex(1200, duration, bucketCount)) // Coerced
    }

    @Test
    public fun testCalculateUiBucketCount(): Unit {
        assertEquals(500, AudioWaveformProcessor.calculateUiBucketCount(1000)) // 1s -> 10, but min 500
        assertEquals(1000, AudioWaveformProcessor.calculateUiBucketCount(100_000)) // 100s -> 1000
        assertEquals(5000, AudioWaveformProcessor.calculateUiBucketCount(1_000_000)) // 1000s -> 5000
    }

    @Test
    public fun testCalculateEngineBucketCount(): Unit {
        assertEquals(100, AudioWaveformProcessor.calculateEngineBucketCount(1000)) // 1s -> 100 Hz
        assertEquals(10000, AudioWaveformProcessor.calculateEngineBucketCount(100_000)) // 100s -> 10000 buckets
    }

    @Test
    public fun testUpdateBuckets(): Unit {
        val buckets = FloatArray(10)
        val buffer = ByteArray(40) { (it % 10).toByte() }
        
        AudioWaveformProcessor.updateBuckets(
            info = AudioWaveformProcessor.WaveformBufferInfo(
                buffer = buffer,
                size = buffer.size,
                startTimeUs = 0,
                totalDurationUs = 1000,
                sampleRate = 44100,
                channelCount = 2
            ),
            buckets = buckets
        )
        
        assertTrue(buckets.any { it > 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    public fun testDownsample_InvalidTargetCount(): Unit {
        val source = floatArrayOf(0.1f, 0.2f)
        AudioWaveformProcessor.downsample(source, 0)
    }
}
