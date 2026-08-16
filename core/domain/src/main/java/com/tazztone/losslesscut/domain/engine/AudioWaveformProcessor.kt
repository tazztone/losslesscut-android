package com.tazztone.losslesscut.domain.engine

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure logic for processing raw PCM data into waveform RMS energy / peaks and normalizing them.
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
    public const val PERCEPTUAL_EXPONENT: Double = 0.75

    public data class WaveformBufferInfo(
        val buffer: ByteArray,
        val size: Int,
        val startTimeUs: Long,
        val totalDurationUs: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val isFloatPcm: Boolean = false
    )

    public class RmsAccumulator(bucketCount: Int) {
        public val sumSquares: DoubleArray = DoubleArray(bucketCount)
        public val sampleCounts: IntArray = IntArray(bucketCount)
        public val buckets: FloatArray = FloatArray(bucketCount)

        public fun toFinalRmsBuckets(): FloatArray {
            for (i in buckets.indices) {
                val count = sampleCounts[i]
                buckets[i] = if (count > 0) {
                    sqrt(sumSquares[i] / count).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
            return buckets
        }
    }

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
     * Normalizes a FloatArray so the maximum value is 1.0.
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
     * Applies a perceptual power curve (amp^0.75) to normalized amplitudes for natural UI display.
     */
    public fun applyPerceptualCurve(buckets: FloatArray, power: Double = PERCEPTUAL_EXPONENT) {
        for (i in buckets.indices) {
            val v = buckets[i].toDouble()
            if (v > 0.0) {
                buckets[i] = v.pow(power).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    private const val BYTES_PER_SHORT_SAMPLE = 2
    private const val BYTES_PER_FLOAT_SAMPLE = 4
    private const val SHORT_LOOKAHEAD_OFFSET = 1
    private const val FLOAT_LOOKAHEAD_OFFSET = 3
    private const val FLOAT_BYTE_OFFSET_1 = 1
    private const val FLOAT_BYTE_OFFSET_2 = 2
    private const val FLOAT_BYTE_OFFSET_3 = 3
    private const val SHIFT_8 = 8
    private const val SHIFT_16 = 16
    private const val SHIFT_24 = 24

    /**
     * Updates RMS accumulator with PCM data from a buffer.
     * Accurately distributes samples across all buckets they span and handles both 16-bit and 32-bit float PCM.
     */
    public fun updateBucketsRms(
        info: WaveformBufferInfo,
        accumulator: RmsAccumulator
    ) {
        if (info.totalDurationUs <= 0 || info.size <= 0 || info.sampleRate <= 0) return

        val bucketCount = accumulator.buckets.size
        val bytesPerSample = if (info.isFloatPcm) {
            BYTES_PER_FLOAT_SAMPLE * info.channelCount
        } else {
            BYTES_PER_SHORT_SAMPLE * info.channelCount
        }
        val step = bytesPerSample

        val usPerSample = US_PER_SEC / info.sampleRate.toDouble()
        val bucketRatio = (bucketCount - 1).toDouble() / info.totalDurationUs.toDouble()
        val timeOffsetBucket = info.startTimeUs.toDouble() * bucketRatio
        val samplesToBucket = usPerSample * bucketRatio
        val invMaxShort = 1.0 / Short.MAX_VALUE

        val sumSq = accumulator.sumSquares
        val counts = accumulator.sampleCounts
        val buckets = accumulator.buckets

        val limit = info.size - (if (info.isFloatPcm) FLOAT_LOOKAHEAD_OFFSET else SHORT_LOOKAHEAD_OFFSET)
        for (j in 0 until limit step step) {
            val sampleIdxInFullBuffer = j / bytesPerSample
            val bucketIdx = (timeOffsetBucket + sampleIdxInFullBuffer * samplesToBucket)
                .toInt().coerceIn(0, bucketCount - 1)

            val normalizedVal: Double = if (info.isFloatPcm) {
                val b0 = info.buffer[j].toInt() and BYTE_MASK
                val b1 = info.buffer[j + FLOAT_BYTE_OFFSET_1].toInt() and BYTE_MASK
                val b2 = info.buffer[j + FLOAT_BYTE_OFFSET_2].toInt() and BYTE_MASK
                val b3 = info.buffer[j + FLOAT_BYTE_OFFSET_3].toInt() and BYTE_MASK
                val bits = b0 or (b1 shl SHIFT_8) or (b2 shl SHIFT_16) or (b3 shl SHIFT_24)
                java.lang.Float.intBitsToFloat(bits).toDouble().coerceIn(-1.0, 1.0)
            } else {
                val low = info.buffer[j].toInt() and BYTE_MASK
                val high = info.buffer[j + 1].toInt() shl BITS_PER_BYTE
                val sample = (high or low).toShort().toInt()
                sample * invMaxShort
            }

            sumSq[bucketIdx] += normalizedVal * normalizedVal
            counts[bucketIdx]++

            // Keep live running RMS for streaming progress
            val c = counts[bucketIdx]
            if (c > 0) {
                buckets[bucketIdx] = sqrt(sumSq[bucketIdx] / c).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Legacy/direct bucket updater.
     */
    public fun updateBuckets(
        info: WaveformBufferInfo,
        buckets: FloatArray,
        step: Int = if (info.isFloatPcm) {
            BYTES_PER_FLOAT_SAMPLE * info.channelCount
        } else {
            BYTES_PER_SHORT_SAMPLE * info.channelCount
        }
    ) {
        if (info.totalDurationUs <= 0 || info.size <= 0 || info.sampleRate <= 0) return

        val bucketCount = buckets.size
        val bytesPerSample = if (info.isFloatPcm) {
            BYTES_PER_FLOAT_SAMPLE * info.channelCount
        } else {
            BYTES_PER_SHORT_SAMPLE * info.channelCount
        }

        val usPerSample = US_PER_SEC / info.sampleRate.toDouble()
        val bucketRatio = (bucketCount - 1).toDouble() / info.totalDurationUs.toDouble()
        val timeOffsetBucket = info.startTimeUs.toDouble() * bucketRatio
        val samplesToBucket = usPerSample * bucketRatio
        val invMaxShort = 1.0f / Short.MAX_VALUE

        val limit = info.size - (if (info.isFloatPcm) FLOAT_LOOKAHEAD_OFFSET else SHORT_LOOKAHEAD_OFFSET)
        for (j in 0 until limit step step) {
            val sampleIdxInFullBuffer = j / bytesPerSample
            val bucketIdx = (timeOffsetBucket + sampleIdxInFullBuffer * samplesToBucket)
                .toInt().coerceIn(0, bucketCount - 1)

            val normalizedAbsVal: Float = if (info.isFloatPcm) {
                val b0 = info.buffer[j].toInt() and BYTE_MASK
                val b1 = info.buffer[j + FLOAT_BYTE_OFFSET_1].toInt() and BYTE_MASK
                val b2 = info.buffer[j + FLOAT_BYTE_OFFSET_2].toInt() and BYTE_MASK
                val b3 = info.buffer[j + FLOAT_BYTE_OFFSET_3].toInt() and BYTE_MASK
                val bits = b0 or (b1 shl SHIFT_8) or (b2 shl SHIFT_16) or (b3 shl SHIFT_24)
                java.lang.Float.intBitsToFloat(bits).absoluteValue.coerceIn(0f, 1f)
            } else {
                val low = info.buffer[j].toInt() and BYTE_MASK
                val high = info.buffer[j + 1].toInt() shl BITS_PER_BYTE
                val sample = (high or low).toShort().toInt()
                if (sample < 0) -sample * invMaxShort else sample * invMaxShort
            }

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
     * Uses max-pooling to preserve peaks and dynamic range.
     */
    public fun downsample(source: FloatArray, targetCount: Int): FloatArray {
        require(targetCount > 0) { "targetCount must be positive, was $targetCount" }
        if (targetCount >= source.size) return source.clone()

        val target = FloatArray(targetCount)
        val sourceSize = source.size
        val factor = sourceSize.toDouble() / targetCount
        
        var start = 0
        for (i in 0 until targetCount) {
            val end = ((i + 1) * factor).toInt().coerceAtMost(sourceSize)
            
            var max = 0f
            for (j in start until end) {
                if (source[j] > max) max = source[j]
            }
            target[i] = max
            start = end
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

