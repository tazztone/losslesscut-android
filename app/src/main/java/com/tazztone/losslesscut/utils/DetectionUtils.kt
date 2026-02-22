package com.tazztone.losslesscut.utils

import kotlin.math.absoluteValue

object DetectionUtils {

    /**
     * Finds silent regions in a waveform.
     * @param waveform Normalised amplitude data (0.0 to 1.0).
     * @param threshold Amplitude threshold (e.g., 0.05 for 5%).
     * @param minSilenceMs Minimum duration of silence to be considered.
     * @param totalDurationMs Total duration of the media in milliseconds.
     * @param paddingMs Additional time to keep around silent cuts (shrinks silence).
     * @param minSegmentMs Minimum duration for any kept segment.
     * @return List of silence ranges in milliseconds.
     */
    fun findSilence(
        waveform: FloatArray,
        threshold: Float,
        minSilenceMs: Long,
        totalDurationMs: Long,
        paddingMs: Long = 0,
        minSegmentMs: Long = 0
    ): List<LongRange> {
        if (waveform.isEmpty() || totalDurationMs <= 0) return emptyList()

        val msPerBucket = totalDurationMs.toDouble() / waveform.size
        val minSilenceBuckets = (minSilenceMs / msPerBucket).toInt().coerceAtLeast(1)

        val rawSilenceRanges = mutableListOf<LongRange>()
        var silenceStartBucket = -1

        for (i in waveform.indices) {
            val isSilent = waveform[i].absoluteValue <= threshold

            if (isSilent) {
                if (silenceStartBucket == -1) {
                    silenceStartBucket = i
                }
            } else {
                if (silenceStartBucket != -1) {
                    val durationBuckets = i - silenceStartBucket
                    if (durationBuckets >= minSilenceBuckets) {
                        val startMs = (silenceStartBucket * msPerBucket).toLong()
                        val endMs = (i * msPerBucket).toLong()
                        rawSilenceRanges.add(startMs..endMs)
                    }
                    silenceStartBucket = -1
                }
            }
        }

        if (silenceStartBucket != -1) {
            val durationBuckets = waveform.size - silenceStartBucket
            if (durationBuckets >= minSilenceBuckets) {
                val startMs = (silenceStartBucket * msPerBucket).toLong()
                rawSilenceRanges.add(startMs..totalDurationMs)
            }
        }

        if (rawSilenceRanges.isEmpty()) return emptyList()

        // Apply Padding
        val paddedRanges = rawSilenceRanges.map { range ->
            val start = (range.first + paddingMs).coerceAtMost(range.last)
            val end = (range.last - paddingMs).coerceAtLeast(start)
            start..end
        }.filter { it.last - it.first >= 10 } // Filter out tiny/empty ranges after padding

        if (minSegmentMs <= 0 || paddedRanges.isEmpty()) return paddedRanges

        // Filter by minSegmentMs:
        // We have [0...Range0Start], [Range0End...Range1Start], ..., [RangeLastEnd...TotalDuration]
        // These are the kept segments.
        val finalRanges = mutableListOf<LongRange>()
        var currentPaddedRanges = paddedRanges.toMutableList()
        
        var i = 0
        while (i <= currentPaddedRanges.size) {
            val segmentStart = if (i == 0) 0L else currentPaddedRanges[i - 1].last
            val segmentEnd = if (i == currentPaddedRanges.size) totalDurationMs else currentPaddedRanges[i].first
            
            val segmentDuration = segmentEnd - segmentStart
            if (segmentDuration < minSegmentMs) {
                // Segment too short! We should merge it by removing one of the adjacent silence ranges.
                // If it's the first segment, remove the first silence range.
                // If it's the last, remove the last silence range.
                // Otherwise, remove the shorter silence range or the one after it.
                if (i == 0 && currentPaddedRanges.isNotEmpty()) {
                    currentPaddedRanges.removeAt(0)
                    i = 0 // Restart check from beginning
                    continue
                } else if (i == currentPaddedRanges.size && currentPaddedRanges.isNotEmpty()) {
                    currentPaddedRanges.removeAt(currentPaddedRanges.size - 1)
                    i = 0
                    continue
                } else if (i > 0 && i < currentPaddedRanges.size) {
                    // Middle segment too short. Merge it with the next one by removing the silence between them.
                    currentPaddedRanges.removeAt(i)
                    i = 0
                    continue
                }
            }
            i++
        }

        return currentPaddedRanges
    }
}
