package com.tazztone.losslesscut.domain.model

import kotlinx.coroutines.yield
import kotlin.math.absoluteValue

public object DetectionUtils {

    private const val YIELD_INTERVAL = 1024

    public data class SilenceDetectionConfig(
        val threshold: Float,
        val minSilenceMs: Long,
        val paddingStartMs: Long,
        val paddingEndMs: Long
    )

    /**
     * Finds silent regions in a waveform. Returns RAW unpadded ranges.
     */
    public suspend fun findSilence(
        waveform: FloatArray,
        totalDurationMs: Long,
        config: SilenceDetectionConfig
    ): List<LongRange> {
        if (waveform.isEmpty() || totalDurationMs <= 0) {
            return emptyList()
        }

        val msPerBucket = totalDurationMs.toDouble() / waveform.size
        return getRawSilenceRanges(
            waveform, config.threshold, config.minSilenceMs, msPerBucket, totalDurationMs
        )
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
            if (i and (YIELD_INTERVAL - 1) == 0) yield()
            val isSilent = waveform[i].absoluteValue <= threshold

            if (isSilent) {
                if (startBucket == -1) startBucket = i
            } else if (startBucket != -1) {
                val durationBuckets = i - startBucket
                val isAtStart = startBucket == 0
                if (isAtStart || durationBuckets >= minSilenceBuckets) {
                    ranges.add((startBucket * msPerBucket).toLong()..(i * msPerBucket).toLong())
                }
                startBucket = -1
            }
        }

        if (startBucket != -1) {
            // Silence that goes to the end is always included regardless of duration
            ranges.add((startBucket * msPerBucket).toLong()..totalDurationMs)
        }
        return ranges
    }

    public fun applyPaddingAndFilter(
        ranges: List<LongRange>,
        paddingStartMs: Long,
        paddingEndMs: Long,
        totalDurationMs: Long
    ): List<LongRange> {
        return ranges.map { range ->
            // Don't apply padding at clip boundaries — there's no speech to protect there
            val isAtClipStart = range.first == 0L
            val isAtClipEnd = range.last >= totalDurationMs
            val start = if (isAtClipStart) range.first else (range.first + paddingStartMs).coerceAtMost(range.last)
            val end = if (isAtClipEnd) range.last else (range.last - paddingEndMs).coerceAtLeast(start)
            start..end
        }
    }

    /**
     * Merges silence ranges that are separated by a "keep" segment shorter than minSegmentMs.
     */
    public fun mergeCloseSilences(
        ranges: List<LongRange>,
        minSegmentMs: Long
    ): List<LongRange> {
        if (ranges.size < 2 || minSegmentMs <= 0) return ranges

        val merged = mutableListOf<LongRange>()
        var current = ranges[0]

        for (i in 1 until ranges.size) {
            val next = ranges[i]
            if (next.first - current.last < minSegmentMs) {
                current = current.first..next.last
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
