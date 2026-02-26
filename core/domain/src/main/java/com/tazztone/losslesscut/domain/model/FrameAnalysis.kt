package com.tazztone.losslesscut.domain.model

public data class FrameAnalysis(
    val timeMs: Long,
    val meanLuma: Double,         // Black frames: mean Y value (0-255)
    val blurVariance: Double,     // Blur: Laplacian variance (higher = sharper)
    val sceneDistance: Int?,       // Scene: pHash hamming distance from previous frame
    val freezeDiff: Double?       // Freeze: Pixel Mean Absolute Difference from previous
)
