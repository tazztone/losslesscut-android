package com.tazztone.losslesscut.domain.engine

import kotlin.math.absoluteValue

/**
 * Pure logic for processing raw PCM data into waveform peaks and normalizing them.
 */
object AudioWaveformProcessor {

    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF
    private const val US_PER_SEC = 1_000_000.0
    private const val TARGET_BUCKETS_PER_SEC = 10
    private const val MIN_BUCKET_COUNT = 500
    private const val MAX_BUCKET_COUNT = 5000
    private const val MS_PER_SEC = 1000.0

    data class WaveformBufferInfo(
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
    fun findPeak(buffer: ByteArray, size: Int, step: Int = 4): Int {
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
    fun normalize(buckets: FloatArray) {
        val maxPeak = buckets.maxOrNull() ?: 0f
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
    fun updateBuckets(
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
    fun getBucketIndex(presentationTimeUs: Long, totalDurationUs: Long, bucketCount: Int): Int {
        if (totalDurationUs <= 0) return 0
        return ((presentationTimeUs.toDouble() / totalDurationUs) * (bucketCount - 1))
            .toInt()
            .coerceIn(0, bucketCount - 1)
    }

    /**
     * Calculates an appropriate bucket count based on duration.
     * Target: 10 buckets per second, min 500, max 5000.
     */
    fun calculateAdaptiveBucketCount(durationMs: Long): Int {
        val calculated = (durationMs / MS_PER_SEC * TARGET_BUCKETS_PER_SEC).toInt()
        return calculated.coerceIn(MIN_BUCKET_COUNT, MAX_BUCKET_COUNT)
    }
}
