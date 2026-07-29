package com.tazztone.losslesscut.domain.cache

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.model.WaveformResult

public interface IAnalysisCache {
    public fun getWaveform(clip: MediaClip): WaveformResult?
    public fun saveWaveform(clip: MediaClip, waveform: WaveformResult)

    public fun getFrameAnalysis(
        clip: MediaClip,
        strategy: VisualStrategy,
        sampleIntervalMs: Long
    ): List<FrameAnalysis>?

    public fun saveFrameAnalysis(
        clip: MediaClip,
        strategy: VisualStrategy,
        sampleIntervalMs: Long,
        analysis: List<FrameAnalysis>
    )

    public fun getCacheUsageBytes(): Long
    public fun clearCache()
    public fun updateCachePolicy(maxSizeBytes: Long, maxAgeDays: Int)
}
