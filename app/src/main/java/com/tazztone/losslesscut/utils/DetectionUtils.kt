package com.tazztone.losslesscut.utils

import kotlin.math.absoluteValue

object DetectionUtils {

    /**
     * Finds silent regions in a waveform.
     * @param waveform Normalised amplitude data (0.0 to 1.0).
     * @param threshold Amplitude threshold (e.g., 0.05 for 5%).
     * @param minDurationMs Minimum duration of silence to be considered.
     * @param totalDurationMs Total duration of the media in milliseconds.
     * @return List of silence ranges in milliseconds.
     */
    fun findSilence(
        waveform: FloatArray,
        threshold: Float,
        minDurationMs: Long,
        totalDurationMs: Long
    ): List<LongRange> {
        if (waveform.isEmpty() || totalDurationMs <= 0) return emptyList()

        val msPerBucket = totalDurationMs.toDouble() / waveform.size
        val minBuckets = (minDurationMs / msPerBucket).toInt().coerceAtLeast(1)

        val silentRanges = mutableListOf<LongRange>()
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
                    if (durationBuckets >= minBuckets) {
                        val startMs = (silenceStartBucket * msPerBucket).toLong()
                        val endMs = (i * msPerBucket).toLong()
                        silentRanges.add(startMs..endMs)
                    }
                    silenceStartBucket = -1
                }
            }
        }

        // Handle silence at the end
        if (silenceStartBucket != -1) {
            val durationBuckets = waveform.size - silenceStartBucket
            if (durationBuckets >= minBuckets) {
                val startMs = (silenceStartBucket * msPerBucket).toLong()
                silentRanges.add(startMs..totalDurationMs)
            }
        }

        return silentRanges
    }
}
