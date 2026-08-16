package com.tazztone.losslesscut.domain.model

public typealias TimeRangeMs = LongRange

public data class VisualDetectionConfig(
    val strategy: VisualStrategy,
    val sensitivityThreshold: Float,
    val minSegmentDurationMs: Long,
    val sampleIntervalFrames: Int = VisualDetectionConfigDefaults.DEFAULT_SAMPLE_INTERVAL_FRAMES
) {
    public companion object {
        public const val AUTO_INTERVAL: Int = 0
        private const val DURATION_1_MIN_MS = 60_000L
        private const val DURATION_5_MIN_MS = 300_000L
        private const val DURATION_20_MIN_MS = 1_200_000L
        public const val SMART_INTERVAL_1_FRAME: Int = 1
        public const val SMART_INTERVAL_2_FRAMES: Int = 2
        public const val SMART_INTERVAL_5_FRAMES: Int = 5
        public const val SMART_INTERVAL_10_FRAMES: Int = 10

        public fun calculateSmartInterval(durationMs: Long): Int = when {
            durationMs <= 0L -> SMART_INTERVAL_1_FRAME
            durationMs < DURATION_1_MIN_MS -> SMART_INTERVAL_1_FRAME
            durationMs < DURATION_5_MIN_MS -> SMART_INTERVAL_2_FRAMES
            durationMs < DURATION_20_MIN_MS -> SMART_INTERVAL_5_FRAMES
            else -> SMART_INTERVAL_10_FRAMES
        }
    }
}

public object VisualDetectionConfigDefaults {
    public const val DEFAULT_SAMPLE_INTERVAL_FRAMES: Int = 5
    public const val AUTO_INTERVAL_SETTING: Int = 0
}

public enum class VisualStrategy {
    SCENE_CHANGE,     // pHash inter-frame Hamming distance
    BLUR_QUALITY,     // Tenengrad / Laplacian variance
    FREEZE_FRAME,     // Pixel diff / histogram delta near-zero
    BLACK_FRAMES      // Mean luma < threshold
}

