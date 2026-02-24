package com.tazztone.losslesscut.domain.engine

import kotlin.math.absoluteValue

/**
 * Pure logic for processing raw PCM data into waveform peaks and normalizing them.
 */
object AudioWaveformProcessor {

    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF

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
     * Maps a timestamp to a bucket index.
     */
    fun getBucketIndex(presentationTimeUs: Long, totalDurationUs: Long, bucketCount: Int): Int {
        if (totalDurationUs <= 0) return 0
        return ((presentationTimeUs.toDouble() / totalDurationUs) * (bucketCount - 1))
            .toInt()
            .coerceIn(0, bucketCount - 1)
    }
}
