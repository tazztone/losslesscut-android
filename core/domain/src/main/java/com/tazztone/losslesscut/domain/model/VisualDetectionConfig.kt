package com.tazztone.losslesscut.domain.model

public typealias TimeRangeMs = LongRange

public data class VisualDetectionConfig(
    val strategy: VisualStrategy,
    val sensitivityThreshold: Float,
    val minSegmentDurationMs: Long,
    val sampleIntervalFrames: Int = 5  // Step every 5th frame (~166ms at 30fps)
)

public enum class VisualStrategy {
    SCENE_CHANGE,     // pHash inter-frame Hamming distance
    BLUR_QUALITY,     // Tenengrad / Laplacian variance
    FREEZE_FRAME,     // Pixel diff / histogram delta near-zero
    BLACK_FRAMES      // Mean luma < threshold
}
