package com.tazztone.losslesscut.domain.model

import kotlinx.coroutines.yield
import kotlin.math.absoluteValue

public object DetectionUtils {

    private const val YIELD_THRESHOLD = 1000
    private const val MIN_RANGE_DURATION_MS = 10L

    public data class SilenceDetectionConfig(
        val threshold: Float,
        val minSilenceMs: Long,
        val paddingStartMs: Long,
        val paddingEndMs: Long,
        val minSegmentMs: Long
    )

    /**
     * Finds silent regions in a waveform.
     */
    public suspend fun findSilence(
        waveform: FloatArray,
        totalDurationMs: Long,
        config: SilenceDetectionConfig
    ): List<LongRange> {
        if (waveform.isEmpty() || totalDurationMs <= 0) {
            return emptyList()
        }

        val sampleDurationMs = totalDurationMs.toDouble() / waveform.size
        val rawRanges = getRawSilenceRanges(
            waveform, config.threshold, config.minSilenceMs, sampleDurationMs, totalDurationMs
        )
        
        return if (rawRanges.isEmpty()) {
            emptyList()
        } else {
            val filtered = filterByMinSegmentMs(rawRanges, config.minSegmentMs, totalDurationMs)
            applyPaddingAndFilter(filtered, config.paddingStartMs, config.paddingEndMs)
        }
    }

    private suspend fun getRawSilenceRanges(
        waveform: FloatArray,
        threshold: Float,
        minSilenceMs: Long,
        msPerBucket: Double,
        totalDurationMs: Long
    ): List<LongRange> {
        val minSilenceBuckets = (minSilenceMs / msPerBucket).toInt().coerceAtLeast(1)
        val ranges = mutableListOf<LongRange>()
        var startBucket = -1

        for (i in waveform.indices) {
            if (i % YIELD_THRESHOLD == 0) yield()
            val isSilent = waveform[i].absoluteValue <= threshold

            if (isSilent) {
                if (startBucket == -1) startBucket = i
            } else if (startBucket != -1) {
                if (i - startBucket >= minSilenceBuckets) {
                    ranges.add((startBucket * msPerBucket).toLong()..(i * msPerBucket).toLong())
                }
                startBucket = -1
            }
        }

        if (startBucket != -1 && waveform.size - startBucket >= minSilenceBuckets) {
            ranges.add((startBucket * msPerBucket).toLong()..totalDurationMs)
        }
        return ranges
    }

    private suspend fun filterByMinSegmentMs(
        ranges: List<LongRange>,
        minSegmentMs: Long,
        totalDurationMs: Long
    ): List<LongRange> {
        if (minSegmentMs <= 0) return ranges

        val merged = mutableListOf<LongRange>()
        var current = ranges[0]
        
        if (current.first < minSegmentMs) {
            current = 0L..current.last
        }
        
        for (i in 1 until ranges.size) {
            yield()
            val next = ranges[i]
            if (next.first - current.last < minSegmentMs) {
                current = current.first..next.last
            } else {
                merged.add(current)
                current = next
            }
        }
        
        if (totalDurationMs - current.last < minSegmentMs) {
            current = current.first..totalDurationMs
        }
        merged.add(current)
        return merged
    }

    private fun applyPaddingAndFilter(
        ranges: List<LongRange>,
        paddingStartMs: Long,
        paddingEndMs: Long
    ): List<LongRange> {
        return ranges.map { range ->
            val start = (range.first + paddingStartMs).coerceAtMost(range.last)
            val end = (range.last - paddingEndMs).coerceAtLeast(start)
            start..end
        }.filter { it.last - it.first >= MIN_RANGE_DURATION_MS }
    }
}
