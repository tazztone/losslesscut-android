package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.TimeRangeMs
import com.tazztone.losslesscut.domain.model.VisualStrategy

public object VisualSegmentFilter {
    
    public fun filter(
        frames: List<FrameAnalysis>,
        strategy: VisualStrategy,
        threshold: Float,
        minSegmentMs: Long
    ): List<TimeRangeMs> {
        if (frames.isEmpty()) return emptyList()

        // Analysis results
        val resultRanges = mutableListOf<TimeRangeMs>()
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

        return resultRanges.filter { (it.last - it.first) >= minSegmentMs }
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
