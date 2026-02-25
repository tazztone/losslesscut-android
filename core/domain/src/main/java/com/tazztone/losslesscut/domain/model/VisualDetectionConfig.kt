package com.tazztone.losslesscut.domain.model

public typealias TimeRangeMs = LongRange

public data class VisualDetectionConfig(
    val strategy: VisualStrategy,
    val sensitivityThreshold: Float,
    val minSegmentDurationMs: Long,
    val sampleIntervalMs: Long = 500L  // 2 FPS equivalent
)

public enum class VisualStrategy {
    SCENE_CHANGE,     // pHash inter-frame Hamming distance
    BLUR_QUALITY,     // Tenengrad / Laplacian variance
    FREEZE_FRAME,     // Pixel diff / histogram delta near-zero
    BLACK_FRAMES      // Mean luma < threshold
}
