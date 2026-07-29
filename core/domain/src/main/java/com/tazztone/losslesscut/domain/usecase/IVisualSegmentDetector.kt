package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualStrategy

public interface IVisualSegmentDetector {
    public suspend fun analyze(
        uri: String, 
        sampleIntervalMs: Long,
        strategy: VisualStrategy = VisualStrategy.FREEZE_FRAME,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<FrameAnalysis>
}
