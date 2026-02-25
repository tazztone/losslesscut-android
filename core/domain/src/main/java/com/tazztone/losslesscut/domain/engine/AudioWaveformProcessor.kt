package com.tazztone.losslesscut.domain.engine

import kotlin.math.absoluteValue

/**
 * Pure logic for processing raw PCM data into waveform peaks and normalizing them.
 */
public object AudioWaveformProcessor {

    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF
    private const val US_PER_SEC = 1_000_000.0
    
    /**
     * Fixed engine resolution: 100 buckets per second (10ms precision).
     */
    public const val ENGINE_RESOLUTION_HZ: Int = 100
    
    private const val TARGET_BUCKETS_PER_SEC = 10
    private const val MIN_BUCKET_COUNT = 500
    private const val MAX_BUCKET_COUNT = 5000
    private const val MS_PER_SEC = 1000.0

    public data class WaveformBufferInfo(
        val buffer: ByteArray,
        val size: Int,
        val startTimeUs: Long,
        val totalDurationUs: Long,
        val sampleRate: Int,
        val channelCount: Int
    )

    /**
     * Extracts the peak amplitude from a buffer of 16-bit PCM data.
     * Assumes 16-bit samples, interleaved (if multi-channel).
     * @param buffer Byte array containing PCM data.
     * @param size Number of bytes to read from the buffer.
     * @param step Number of bytes per sample (e.g., 4 for 16-bit stereo).
     * @return The maximum absolute value found in the buffer (0 to 32767).
     */
    public fun findPeak(buffer: ByteArray, size: Int, step: Int = 4): Int {
        var peak = 0
        for (j in 0 until size - 1 step step) {
            val low = buffer[j].toInt() and BYTE_MASK
            val high = buffer[j + 1].toInt() shl BITS_PER_BYTE
            val sample = (high or low).toShort().toInt()
            val absVal = sample.absoluteValue
            if (absVal > peak) peak = absVal
        }
        return peak
    }

    /**
     * Normalizes a FloatArray of peaks so the maximum value is 1.0.
     */
    public fun normalize(buckets: FloatArray) {
        val maxPeak = buckets.maxOrNull() ?: 0f
        normalize(buckets, maxPeak)
    }

    /**
     * Normalizes a FloatArray using a known maximum peak.
     */
    public fun normalize(buckets: FloatArray, maxPeak: Float) {
        if (maxPeak > 0f) {
            for (i in buckets.indices) {
                buckets[i] /= maxPeak
            }
        }
    }

    /**
     * Updates multiple buckets with PCM data from a buffer.
     * Accurately distributes samples across all buckets they span.
     */
    public fun updateBuckets(
        info: WaveformBufferInfo,
        buckets: FloatArray,
        step: Int = 2 * info.channelCount
    ) {
        if (info.totalDurationUs <= 0 || info.size <= 0 || info.sampleRate <= 0) return

        val bucketCount = buckets.size
        val bytesPerSample = 2 * info.channelCount

        for (j in 0 until info.size - 1 step step) {
            val sampleIdxInFullBuffer = j / bytesPerSample
            val sampleTimeUs = info.startTimeUs + 
                (sampleIdxInFullBuffer.toDouble() / info.sampleRate * US_PER_SEC).toLong()
            
            val bucketIdx = ((sampleTimeUs.toDouble() / info.totalDurationUs) * (bucketCount - 1))
                .toInt().coerceIn(0, bucketCount - 1)

            val low = info.buffer[j].toInt() and BYTE_MASK
            val high = info.buffer[j + 1].toInt() shl BITS_PER_BYTE
            val sample = (high or low).toShort().toInt()
            val normalizedAbsVal = sample.absoluteValue.toFloat() / Short.MAX_VALUE

            if (normalizedAbsVal > buckets[bucketIdx]) {
                buckets[bucketIdx] = normalizedAbsVal
            }
        }
    }

    /**
     * Maps a timestamp to a bucket index.
     */
    public fun getBucketIndex(presentationTimeUs: Long, totalDurationUs: Long, bucketCount: Int): Int {
        if (totalDurationUs <= 0) return 0
        return ((presentationTimeUs.toDouble() / totalDurationUs) * (bucketCount - 1))
            .toInt()
            .coerceIn(0, bucketCount - 1)
    }

    /**
     * Calculates the engine bucket count for a given duration.
     * Always 100 buckets per second (10ms resolution).
     */
    public fun calculateEngineBucketCount(durationMs: Long): Int {
        return ((durationMs / MS_PER_SEC) * ENGINE_RESOLUTION_HZ).toInt().coerceAtLeast(1)
    }

    /**
     * Calculates an appropriate bucket count for UI display.
     * Target: 10 buckets per second, min 500, max 5000.
     */
    public fun calculateUiBucketCount(durationMs: Long): Int {
        val calculated = (durationMs / MS_PER_SEC * TARGET_BUCKETS_PER_SEC).toInt()
        return calculated.coerceIn(MIN_BUCKET_COUNT, MAX_BUCKET_COUNT)
    }

    /**
     * Downsamples a high-resolution waveform to a target bucket count for UI display.
     * Uses max-pooling to preserve peaks.
     */
    public fun downsample(source: FloatArray, targetCount: Int): FloatArray {
        require(targetCount > 0) { "targetCount must be positive, was $targetCount" }
        if (targetCount >= source.size) return source.clone()

        val target = FloatArray(targetCount)
        val sourceSize = source.size
        
        for (i in 0 until targetCount) {
            val start = (i.toDouble() / targetCount * sourceSize).toInt()
            val end = (((i + 1).toDouble() / targetCount * sourceSize).toInt()).coerceAtMost(sourceSize)
            
            var max = 0f
            for (j in start until end) {
                if (source[j] > max) max = source[j]
            }
            target[i] = max
        }
        return target
    }

    /**
     * Fills edge buckets that are 0 due to codec delay or padding.
     * Propagates the first/last non-zero values to the edges.
     */
    public fun fillEdgeBuckets(buckets: FloatArray) {
        val firstNonZero = buckets.indexOfFirst { it > 0f }
        if (firstNonZero > 0) {
            for (i in 0 until firstNonZero) buckets[i] = buckets[firstNonZero]
        }
        val lastNonZero = buckets.indexOfLast { it > 0f }
        if (lastNonZero >= 0 && lastNonZero < buckets.size - 1) {
            for (i in lastNonZero + 1 until buckets.size) buckets[i] = buckets[lastNonZero]
        }
    }
}
