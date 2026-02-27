package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis

public interface IVisualSegmentDetector {
    public suspend fun analyze(
        uri: String, 
        sampleIntervalMs: Long,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<FrameAnalysis>
}
