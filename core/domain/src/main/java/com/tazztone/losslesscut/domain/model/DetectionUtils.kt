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

    public suspend fun findSilence(
        waveform: FloatArray,
        totalDurationMs: Long,
        threshold: Float
    ): List<LongRange> {
        if (waveform.isEmpty() || totalDurationMs <= 0) {
            return emptyList()
        }

        val msPerBucket = totalDurationMs.toDouble() / waveform.size
        return getRawSilenceRanges(
            waveform, threshold, msPerBucket, totalDurationMs
        )
    }

    private suspend fun getRawSilenceRanges(
        waveform: FloatArray,
        threshold: Float,
        msPerBucket: Double,
        totalDurationMs: Long
    ): List<LongRange> {
        val ranges = mutableListOf<LongRange>()
        var startBucket = -1

        for (i in waveform.indices) {
            if (i and (YIELD_INTERVAL - 1) == 0) yield()
            val isSilent = waveform[i].absoluteValue <= threshold

            if (isSilent) {
                if (startBucket == -1) startBucket = i
            } else if (startBucket != -1) {
                ranges.add((startBucket * msPerBucket).toLong()..(i * msPerBucket).toLong())
                startBucket = -1
            }
        }

        if (startBucket != -1) {
            ranges.add((startBucket * msPerBucket).toLong()..totalDurationMs)
        }
        return ranges
    }

    /**
     * Filters out silence ranges that are shorter than minSilenceMs,
     * unless they touch the start or end of the clip.
     */
    public fun filterShortSilences(
        ranges: List<LongRange>,
        minSilenceMs: Long,
        totalDurationMs: Long
    ): List<LongRange> {
        if (minSilenceMs <= 0) return ranges
        return ranges.filter { range ->
            val duration = range.last - range.first
            val isAtStart = range.first == 0L
            val isAtEnd = range.last >= totalDurationMs
            isAtStart || isAtEnd || duration >= minSilenceMs
        }
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
