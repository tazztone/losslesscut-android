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

        if (minSegmentMs <= 0 || paddedRanges.isEmpty()) return paddedRanges
        
        // Filter by minSegmentMs:
        // Identify kept segments (the gaps between silence ranges)
        // If a kept segment is too short, we merge it by removing one of the adjacent silence ranges.
        val currentSilence = paddedRanges.toMutableList()
        
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i <= currentSilence.size) {
                val segStart = if (i == 0) 0L else currentSilence[i - 1].last
                val segEnd = if (i == currentSilence.size) totalDurationMs else currentSilence[i].first
                
                if (segEnd - segStart < minSegmentMs) {
                    // This kept segment is too short. Merge it into adjacent silence.
                    if (currentSilence.isEmpty()) {
                        // If no silence exists but total duration is too short, 
                        // we can't really "silence" it unless we want to silence the whole thing.
                        // Usually we just leave it.
                        break
                    }
                    
                    if (i > 0 && i < currentSilence.size) {
                        // In middle: merge previous and next silence
                        val merged = currentSilence[i - 1].first..currentSilence[i].last
                        currentSilence.removeAt(i)
                        currentSilence.removeAt(i - 1)
                        currentSilence.add(i - 1, merged)
                        changed = true
                        break
                    } else if (i == 0) {
                        // At start: extend first silence to 0
                        val merged = 0L..currentSilence[0].last
                        currentSilence.removeAt(0)
                        currentSilence.add(0, merged)
                        changed = true
                        break
                    } else {
                        // At end: extend last silence to totalDuration
                        val merged = currentSilence.last().first..totalDurationMs
                        currentSilence.removeAt(currentSilence.size - 1)
                        currentSilence.add(merged)
                        changed = true
                        break
                    }
                }
                i++
            }
        }
        return currentSilence
    }
}
