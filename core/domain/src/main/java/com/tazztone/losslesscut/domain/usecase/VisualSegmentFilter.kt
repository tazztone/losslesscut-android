package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualStrategy

public object VisualSegmentFilter {
    private const val MIN_VISIBLE_STAMP_DURATION_MS = 100L
    
    public fun filter(
        frames: List<FrameAnalysis>,
        strategy: VisualStrategy,
        threshold: Float,
        minSegmentMs: Long
    ): List<LongRange> {
        if (frames.isEmpty()) return emptyList()

        // Analysis results
        val resultRanges = mutableListOf<LongRange>()
        var currentRangeStart: Long? = null
        var currentRangeEnd: Long? = null

        // Group contiguous blocks of matches
        for (frame in frames) {
            if (isMatch(frame, strategy, threshold)) {
                if (currentRangeStart == null) {
                    currentRangeStart = frame.timeMs
                }
                currentRangeEnd = frame.timeMs
            } else {
                if (currentRangeStart != null && currentRangeEnd != null) {
                    resultRanges.add(currentRangeStart..currentRangeEnd)
                    currentRangeStart = null
                    currentRangeEnd = null
                }
            }
        }
        if (currentRangeStart != null && currentRangeEnd != null) {
            resultRanges.add(currentRangeStart..currentRangeEnd)
        }

        // Fix: Visual Regression. Expand single-frame matches to have a duration
        // so they are visible as mask rects in the seeker.
        // We use minSegmentMs or a fixed fallback if minSegmentMs is 0.
        val displayPadding = if (minSegmentMs > 0) minSegmentMs else MIN_VISIBLE_STAMP_DURATION_MS
        
        val expandedRanges = resultRanges.map { range ->
            if (range.first == range.last) {
                // Expand slightly so it's a visible rect, but don't exceed video bounds (implicit)
                val start = (range.first - displayPadding / 2).coerceAtLeast(0)
                val end = start + displayPadding
                start..end
            } else {
                range
            }
        }

        val filteredRanges = expandedRanges.filter { (it.last - it.first) >= minSegmentMs }

        return mergeOverlappingRanges(filteredRanges)
    }

    private fun mergeOverlappingRanges(ranges: List<LongRange>): List<LongRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        var currentStart = sorted[0].first
        var currentEnd = sorted[0].last

        for (i in 1 until sorted.size) {
            val range = sorted[i]
            if (range.first <= currentEnd) {
                currentEnd = maxOf(currentEnd, range.last)
            } else {
                merged.add(currentStart..currentEnd)
                currentStart = range.first
                currentEnd = range.last
            }
        }
        merged.add(currentStart..currentEnd)
        return merged
    }

    private fun isMatch(frame: FrameAnalysis, strategy: VisualStrategy, threshold: Float): Boolean {
        return when (strategy) {
            VisualStrategy.BLACK_FRAMES -> frame.meanLuma < threshold
            VisualStrategy.BLUR_QUALITY -> frame.blurVariance < threshold
            VisualStrategy.FREEZE_FRAME -> frame.freezeDiff != null && frame.freezeDiff < threshold
            VisualStrategy.SCENE_CHANGE -> frame.sceneDistance != null && frame.sceneDistance > threshold
        }
    }
}
