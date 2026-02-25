package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.TimeRangeMs
import com.tazztone.losslesscut.domain.model.VisualDetectionConfig

public interface IVisualSegmentDetector {
    public suspend fun detect(uri: String, config: VisualDetectionConfig): List<TimeRangeMs>
}
