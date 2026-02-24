package com.tazztone.losslesscut.domain.engine

import com.tazztone.losslesscut.domain.model.WaveformResult

public interface AudioWaveformExtractor {
    public suspend fun extract(
        uri: String, 
        bucketCount: Int = 1000,
        onProgress: ((WaveformResult) -> Unit)? = null
    ): WaveformResult?
}
